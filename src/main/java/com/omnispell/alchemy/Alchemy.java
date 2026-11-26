package com.omnispell.alchemy;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.omnispell.data.WordBank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What can go into an alchemy cauldron. Each Minecraft item that can be brewed
 * has the properties Omnispell's potioneer works with: a category, a potency, a
 * preferred temperature, and for a few "essential" ingredients a special role.
 */
public final class Alchemy {
	public static final class Ingredient {
		public String name;          // the name the potioneer knows it by
		public String category;      // flora, fauna, arcana, compound
		public int potency;          // 1-5
		public String temperature;   // high, low, room
		public String role;          // activator, harmonizer, duration, weaponizer, consumability, temperature_negator, or null
		public String manifestation; // eyes, lungs, body, skin, legs, or null
	}

	public static final class Base {
		public String name;
		public int potency;
		public String temperature;
		public int model;            // which filled-cauldron look to use
	}

	private static final Map<String, Ingredient> INGREDIENTS = new LinkedHashMap<>();
	private static final Map<String, Base> BASES = new LinkedHashMap<>();

	private Alchemy() {
	}

	public static void load() {
		if (!INGREDIENTS.isEmpty()) return;
		String path = "/data/omnispell/omnispell/alchemy.json";
		try (Reader reader = new InputStreamReader(Alchemy.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
			JsonObject root = WordBank.GSON.fromJson(reader, JsonObject.class);
			Map<String, Ingredient> ingredients = WordBank.GSON.fromJson(root.get("ingredients"), new TypeToken<LinkedHashMap<String, Ingredient>>() { }.getType());
			Map<String, Base> bases = WordBank.GSON.fromJson(root.get("bases"), new TypeToken<LinkedHashMap<String, Base>>() { }.getType());
			INGREDIENTS.putAll(ingredients);
			BASES.putAll(bases);
		} catch (Exception e) {
			throw new IllegalStateException("Omnispell could not read " + path, e);
		}
	}

	/** The item's id without "minecraft:", or the full id for other mods' items. */
	public static String key(ItemStack stack) {
		var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
	}

	public static Ingredient ingredient(String key) {
		return INGREDIENTS.get(key);
	}

	public static Ingredient ingredient(ItemStack stack) {
		return stack.isEmpty() ? null : INGREDIENTS.get(key(stack));
	}

	public static Base base(String id) {
		return BASES.get(id);
	}

	public static Map<String, Ingredient> ingredients() {
		return INGREDIENTS;
	}
}
