package com.omnispell.alchemy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.omnispell.Omnispell;
import com.omnispell.data.WordBank;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Asks the Omnispell website's potioneer (the brew-potion function) what a
 * recipe makes. The website brews afresh every time; here each recipe is asked
 * once per server and remembered in config/omnispell/potions, so a recipe
 * always gives the same potion and repeat brews cost nothing.
 */
public final class PotionGenerator {
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	/** Tests can swap this for a stand-in. */
	public static java.util.function.Function<JsonObject, CompletableFuture<JsonObject>> transport = PotionGenerator::post;

	private PotionGenerator() {
	}

	public static String recipeKey(String baseId, List<String> itemKeys, String heat) {
		List<String> sorted = new ArrayList<>(itemKeys);
		sorted.sort(null);
		return baseId + "@" + heat + "+" + String.join("+", sorted);
	}

	private static Path cacheFile(String key) {
		String safe = key.replaceAll("[^A-Za-z0-9_+@.-]", "_");
		if (safe.length() > 180) safe = safe.substring(0, 150) + "_" + Integer.toHexString(key.hashCode());
		return FabricLoader.getInstance().getConfigDir().resolve("omnispell").resolve("potions").resolve(safe + ".json");
	}

	static JsonObject request(Alchemy.Base base, List<Alchemy.Ingredient> ingredients, List<String> warnings, boolean unstable) {
		JsonObject body = new JsonObject();
		JsonObject b = new JsonObject();
		b.addProperty("name", base.name);
		b.addProperty("potency", base.potency);
		b.addProperty("temperature", base.temperature);
		body.add("base", b);
		JsonArray list = new JsonArray();
		for (Alchemy.Ingredient ing : ingredients) {
			JsonObject o = new JsonObject();
			o.addProperty("name", ing.name);
			o.addProperty("category", ing.category);
			o.addProperty("potency", ing.potency);
			o.addProperty("temperature", ing.temperature);
			o.addProperty("isEssential", ing.role != null);
			if (ing.role != null) o.addProperty("essentialRole", ing.role);
			if (ing.manifestation != null) o.addProperty("manifestation", ing.manifestation);
			list.add(o);
		}
		body.add("ingredients", list);
		JsonArray warn = new JsonArray();
		for (String w : warnings) warn.add(w);
		body.add("warnings", warn);
		// The website's cauldrons hold 4, 6 or 8 parts, and the larger ones are unstable. Here a brew
		// is unstable when its volatility says so, which tells the potioneer to give it a downside.
		body.addProperty("slotCount", unstable ? 6 : Math.max(4, ingredients.size()));
		body.addProperty("filledCount", ingredients.size());
		body.addProperty("purityBonus", 0);
		body.addProperty("gameMode", false);
		return body;
	}

	private static CompletableFuture<JsonObject> post(JsonObject body) {
		String url = Omnispell.CONFIG.generatorUrl.replace("/generate-spell", "/brew-potion");
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
				throw new IllegalStateException("The potioneer sent something unreadable (HTTP " + response.statusCode() + ")");
			}
			if (response.statusCode() != 200 || json == null || !json.has("potion")) {
				throw new IllegalStateException(json != null && json.has("error") ? json.get("error").getAsString() : "HTTP " + response.statusCode());
			}
			return json;
		});
	}

	public static CompletableFuture<Brew> brew(String key, Alchemy.Base base, List<Alchemy.Ingredient> ingredients, List<String> warnings, boolean unstable) {
		Path file = cacheFile(key);
		Brew brew = new Brew();
		try {
			if (Files.exists(file)) {
				brew.read(WordBank.GSON.fromJson(Files.readString(file), JsonObject.class));
				return CompletableFuture.completedFuture(brew);
			}
		} catch (Exception e) {
			Omnispell.LOG.warn("Could not read saved potion {}", file, e);
		}
		if (!Omnispell.CONFIG.useGenerator) {
			fallback(brew, base, ingredients, warnings);
			return CompletableFuture.completedFuture(brew);
		}
		return transport.apply(request(base, ingredients, warnings, unstable)).handle((json, error) -> {
			if (error != null) {
				Omnispell.LOG.warn("Potioneer unavailable for {}: {}", key, error.getMessage());
				fallback(brew, base, ingredients, warnings);
				return brew;
			}
			JsonObject potion = json.getAsJsonObject("potion");
			brew.read(potion);
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, potion.toString());
			} catch (Exception e) {
				Omnispell.LOG.warn("Could not save potion {}", file, e);
			}
			return brew;
		});
	}

	/** The website's no-AI brewing rules, used when the potioneer cannot be reached. */
	static void fallback(Brew brew, Alchemy.Base base, List<Alchemy.Ingredient> ingredients, List<String> warnings) {
		brew.offline = true;
		int[] counts = new int[4];
		String[] cats = {"flora", "fauna", "arcana", "compound"};
		double potency = 0;
		boolean weaponized = false;
		for (Alchemy.Ingredient ing : ingredients) {
			for (int i = 0; i < cats.length; i++) if (cats[i].equals(ing.category)) counts[i]++;
			potency += ing.potency;
			if ("weaponizer".equals(ing.role)) weaponized = true;
		}
		int dominant = 0;
		for (int i = 1; i < 4; i++) if (counts[i] > counts[dominant]) dominant = i;
		String[][] adjectives = {{"Herbal", "Verdant", "Botanical"}, {"Bestial", "Primal", "Feral"}, {"Arcane", "Mystic", "Ethereal"}, {"Alchemical", "Compound", "Synthetic"}};
		String label = switch (base.name) {
			case "Nectar", "Milk" -> "Restorative";
			case "Dragon's Blood" -> "Draught";
			case "Molten Lava" -> "Volatile Mixture";
			default -> "Tonic";
		};
		brew.tier = Math.max(1, Math.min(5, (int) Math.round(potency / Math.max(1, ingredients.size()) + (ingredients.size() > 4 ? 1 : 0))));
		brew.name = adjectives[dominant][ingredients.size() % 3] + " " + label;
		boolean hostile = weaponized || label.equals("Draught") || label.equals("Volatile Mixture");
		if (hostile) {
			brew.codeType = "damage";
			brew.type = "offensive";
			brew.value = brew.tier * 10;
			brew.element = base.name.equals("Molten Lava") ? "fire" : "neutral";
			brew.description = "A potent offensive concoction.";
			brew.effect = "Deals " + (int) brew.value + " damage when thrown at a target.";
		} else if (label.equals("Restorative")) {
			brew.codeType = "heal";
			brew.type = "healing";
			brew.value = brew.tier * 8 + 10;
			brew.description = "Restores vitality on consumption.";
			brew.effect = "Restores " + (int) brew.value + " HP when consumed.";
		} else {
			brew.codeType = "buff";
			brew.type = "utility";
			brew.value = brew.tier * 5;
			brew.duration = brew.tier + 1;
			brew.description = "Enhances the drinker's capabilities.";
			brew.effect = "Boosts combat effectiveness by " + (int) brew.value + "% for " + brew.duration + " turns.";
		}
		if (ingredients.size() > 4 || !warnings.isEmpty()) brew.downside = "Slightly volatile: minor side effects possible.";
	}
}
