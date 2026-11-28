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

	/** Tests (and offline servers) can swap these for stand-ins: the website's generator, and the Minecraft Oracle. */
	public static java.util.function.Function<JsonObject, CompletableFuture<JsonObject>> transport = body -> post(Omnispell.CONFIG.generatorUrl, body);
	public static java.util.function.Function<JsonObject, CompletableFuture<JsonObject>> oracle = body -> post(Omnispell.CONFIG.oracleUrl, body);

	private SpellGenerator() {
	}

	/** Where a reply is kept: "spells" for the website's generator, "oracle" for the Minecraft Oracle. */
	private static Path cacheFile(String folder, String spellId) {
		String safe = spellId.replaceAll("[^A-Za-z0-9_,|:()-]", "_").replace('|', '-').replace(':', '.');
		if (safe.length() > 180) safe = safe.substring(0, 150) + "_" + Integer.toHexString(spellId.hashCode());
		return FabricLoader.getInstance().getConfigDir().resolve("omnispell").resolve(folder).resolve(safe + ".json");
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

	private static CompletableFuture<JsonObject> post(String url, JsonObject body) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
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
	 * Fills in a combat spell. Completes with true if the Oracle (or the website's generator, or the local
	 * copy of an earlier reply) answered, false if the built-in fallback was used. The future completes on
	 * a background thread.
	 *
	 * The Minecraft Oracle is asked first. Each request carries only this spell's words, and the reply is
	 * kept under exactly those words, so one spell never colours another.
	 */
	public static CompletableFuture<Boolean> generate(Spell spell, String discoverer) {
		Path planned = cacheFile("oracle", spell.id);
		if (read(spell, planned)) return CompletableFuture.completedFuture(true);
		if (!Omnispell.CONFIG.useGenerator) {
			Fallback.fill(spell);
			return CompletableFuture.completedFuture(false);
		}
		if (!Omnispell.CONFIG.useOracle) return fromWebsite(spell, discoverer);
		return oracle.apply(request(spell, null)).thenApply(json -> {
			JsonObject generated = json.getAsJsonObject("spell");
			spell.readGenerated(generated);
			save(planned, generated);
			return true;
		}).exceptionallyCompose(error -> {
			Omnispell.LOG.warn("The Minecraft Oracle is unavailable for {} ({}); asking the website's generator", spell.id, error.getMessage());
			return fromWebsite(spell, discoverer);
		});
	}

	/**
	 * Clears the replies this server kept from the website's generator, before spells were planned for Minecraft:
	 * none of them fit any more, so their words go to the new Oracle the next time someone crafts them. Spells
	 * already in spellbooks are not touched (they are legacy spells).
	 */
	public static void purgeLegacyCache() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("omnispell").resolve("spells");
		if (!Files.isDirectory(dir)) return;
		int purged = 0;
		try (var files = Files.list(dir)) {
			for (Path file : (Iterable<Path>) files::iterator) {
				if (file.toString().endsWith(".json") && Files.deleteIfExists(file)) purged++;
			}
			Files.deleteIfExists(dir);
		} catch (Exception e) {
			Omnispell.LOG.warn("Could not clear the old spell replies in {}", dir, e);
		}
		if (purged > 0) Omnispell.LOG.info("Cleared {} old spell replies; the Minecraft Oracle will make those spells afresh", purged);
	}

	/** The website's generator, used only when the Minecraft Oracle cannot be reached. Its replies are not kept. */
	private static CompletableFuture<Boolean> fromWebsite(Spell spell, String discoverer) {
		return transport.apply(request(spell, discoverer)).handle((json, error) -> {
			if (error != null) {
				Omnispell.LOG.warn("Spell generator unavailable for {}: {}", spell.id, error.getMessage());
				Fallback.fill(spell);
				return false;
			}
			spell.readGenerated(json.getAsJsonObject("spell"));
			spell.offline = true; // not planned for Minecraft: unstable, and crafted again once the Oracle is back
			return false;
		});
	}

	private static boolean read(Spell spell, Path file) {
		try {
			if (Files.exists(file)) {
				spell.readGenerated(WordBank.GSON.fromJson(Files.readString(file), JsonObject.class));
				return true;
			}
		} catch (Exception e) {
			Omnispell.LOG.warn("Could not read saved spell {}", file, e);
		}
		return false;
	}

	private static void save(Path file, JsonObject generated) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, generated.toString());
		} catch (Exception e) {
			Omnispell.LOG.warn("Could not save spell {}", file, e);
		}
	}
}
