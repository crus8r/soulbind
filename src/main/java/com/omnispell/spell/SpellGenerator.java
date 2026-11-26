package com.omnispell.spell;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.omnispell.Omnispell;
import com.omnispell.data.Word;
import com.omnispell.data.WordBank;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Asks the Omnispell website's own spell generator for a combat spell. The
 * request is the same one the website sends, so the description, tier, numbers,
 * toggles and visuals are exactly what the website would have produced, and a
 * word combination someone already crafted comes back from the shared memory
 * without using the AI again.
 *
 * Replies are also kept in config/omnispell/spells so a spell is only ever
 * requested once per server.
 */
public final class SpellGenerator {
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	/** Tests (and offline servers) can swap this for a stand-in. */
	public static java.util.function.Function<JsonObject, CompletableFuture<JsonObject>> transport = SpellGenerator::post;

	private SpellGenerator() {
	}

	private static Path cacheFile(String spellId) {
		String safe = spellId.replaceAll("[^A-Za-z0-9_,|:()-]", "_").replace('|', '-').replace(':', '.');
		if (safe.length() > 180) safe = safe.substring(0, 150) + "_" + Integer.toHexString(spellId.hashCode());
		return FabricLoader.getInstance().getConfigDir().resolve("omnispell").resolve("spells").resolve(safe + ".json");
	}

	/** The request body the website sends to generate-spell. */
	static JsonObject request(Spell spell, String discoverer) {
		JsonObject body = new JsonObject();
		body.addProperty("spellName", Spell.websiteName(spell.words));
		body.addProperty("spellType", spell.type);
		JsonArray words = new JsonArray();
		JsonArray spellings = new JsonArray();
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word == null) continue;
			JsonObject w = new JsonObject();
			w.addProperty("english", word.english);
			w.addProperty("spelling", word.spelling);
			w.addProperty("type", word.type);
			if (word.tier != null) w.addProperty("tier", word.tier);
			if (word.category != null) w.addProperty("category", word.category);
			words.add(w);
			spellings.add(word.spelling);
		}
		body.add("words", words);
		body.add("spellings", spellings);
		if (discoverer != null) body.addProperty("discovererName", discoverer);
		return body;
	}

	private static CompletableFuture<JsonObject> post(JsonObject body) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(Omnispell.CONFIG.generatorUrl))
				.timeout(Duration.ofSeconds(Omnispell.CONFIG.generatorTimeoutSeconds))
				.header("Content-Type", "application/json")
				.header("apikey", Omnispell.CONFIG.generatorKey)
				.header("Authorization", "Bearer " + Omnispell.CONFIG.generatorKey)
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
			JsonObject json;
			try {
				json = WordBank.GSON.fromJson(response.body(), JsonObject.class);
			} catch (Exception e) {
				throw new IllegalStateException("The generator sent something unreadable (HTTP " + response.statusCode() + ")");
			}
			if (response.statusCode() != 200 || json == null || !json.has("spell")) {
				String error = json != null && json.has("error") ? json.get("error").getAsString() : "HTTP " + response.statusCode();
				throw new IllegalStateException(error);
			}
			return json;
		});
	}

	/**
	 * Fills in a combat spell. Completes with true if the generator (or the local
	 * copy of an earlier reply) answered, false if the built-in fallback was used.
	 * The future completes on a background thread.
	 */
	public static CompletableFuture<Boolean> generate(Spell spell, String discoverer) {
		Path file = cacheFile(spell.id);
		try {
			if (Files.exists(file)) {
				spell.readGenerated(WordBank.GSON.fromJson(Files.readString(file), JsonObject.class));
				return CompletableFuture.completedFuture(true);
			}
		} catch (Exception e) {
			Omnispell.LOG.warn("Could not read saved spell {}", file, e);
		}
		if (!Omnispell.CONFIG.useGenerator) {
			Fallback.fill(spell);
			return CompletableFuture.completedFuture(false);
		}
		return transport.apply(request(spell, discoverer)).handle((json, error) -> {
			if (error != null) {
				Omnispell.LOG.warn("Spell generator unavailable for {}: {}", spell.id, error.getMessage());
				Fallback.fill(spell);
				return false;
			}
			JsonObject generated = json.getAsJsonObject("spell");
			spell.readGenerated(generated);
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, generated.toString());
			} catch (Exception e) {
				Omnispell.LOG.warn("Could not save spell {}", file, e);
			}
			return true;
		});
	}
}
