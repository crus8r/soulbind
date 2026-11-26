package com.omnispell.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The dictionary, spell types and exploration spell table, loaded once from the
 * files exported from the Omnispell website data.
 */
public final class WordBank {
	public static final Gson GSON = new Gson();

	/** Order of word types in a spell formula and in the book. */
	public static final List<String> WORD_TYPES = List.of("base", "command", "intention", "charge", "source", "attribute");

	public static final class Slot {
		public String type;
		public boolean required;
		public String label;
	}

	public static final class SpellType {
		public String id;
		public String name;
		public String description;
		public List<String> rules;
		public List<Slot> wordSlots;
	}

	public static final class ExploreSpell {
		public String id, base, command, intention, latin, name, description, toggle, pattern, shape, motion;
		public boolean fizzle, siphon;
		public int intensity;
	}

	public static final class Flavor {
		public String adj, material, color, color2, glowColor, particle, element;
	}

	private static final Map<String, Word> WORDS = new LinkedHashMap<>();
	private static final Map<String, SpellType> TYPES = new LinkedHashMap<>();
	private static final Map<String, Set<String>> COMPATIBLE = new LinkedHashMap<>();
	private static final Map<String, ExploreSpell> EXPLORE = new LinkedHashMap<>();
	private static final Map<String, Flavor> FLAVORS = new LinkedHashMap<>();

	private WordBank() {
	}

	public static void load() {
		if (!WORDS.isEmpty()) return;
		JsonObject root = read("words.json");
		List<Word> words = GSON.fromJson(root.get("words"), new TypeToken<List<Word>>() { }.getType());
		for (Word word : words) WORDS.put(word.id, word);
		// Sparsus is the mod's own charge word: it spreads a spell over everything near its target. A rare find.
		Word sparsus = new Word();
		sparsus.id = "charge:Sparsus (Area of effect)";
		sparsus.type = "charge";
		sparsus.english = "Sparsus (Area of effect)";
		sparsus.spelling = "Sparsus";
		sparsus.description = "Spreads the spell over everything near its target";
		sparsus.rarity = 4;
		WORDS.put(sparsus.id, sparsus);
		List<SpellType> types = GSON.fromJson(root.get("spellTypes"), new TypeToken<List<SpellType>>() { }.getType());
		for (SpellType type : types) {
			// Enchantment, illusion and charm are retired: four kinds of spell remain.
			if (type.id.equals("enchantment") || type.id.equals("illusion") || type.id.equals("charm")) continue;
			if (type.id.equals("exploration")) {
				type.name = "Environment Spells";
				type.description = "Spells that shape the land and help you cross it: carve, raise, mend, bridge, travel and reveal.";
				type.rules = List.of("Base + Action + Intent decide the effect. The same three always do the same thing.",
						"The base word is also the material: Earth builds with dirt, Ice with packed ice, Wood with planks.",
						"A higher-tier base word reaches further. Maxima raises the tier by one.");
			}
			TYPES.put(type.id, type);
		}
		JsonObject compat = root.getAsJsonObject("intentionCompatibility");
		for (String key : compat.keySet()) {
			Set<String> names = GSON.fromJson(compat.getAsJsonObject(key).get("compatible"), new TypeToken<Set<String>>() { }.getType());
			if (key.equals("exploration")) names.addAll(List.of("Malice", "Warding", "Creation", "Preservation", "Banishment", "Soothing", "Transportation"));
			COMPATIBLE.put(key, names);
		}

		JsonObject explore = read("exploration.json");
		List<ExploreSpell> spells = GSON.fromJson(explore.get("spells"), new TypeToken<List<ExploreSpell>>() { }.getType());
		for (ExploreSpell spell : spells) EXPLORE.put(spell.id, spell);
		Map<String, Flavor> flavors = GSON.fromJson(explore.get("flavors"), new TypeToken<Map<String, Flavor>>() { }.getType());
		FLAVORS.putAll(flavors);
	}

	private static JsonObject read(String name) {
		String path = "/data/omnispell/omnispell/" + name;
		try (Reader reader = new InputStreamReader(WordBank.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
			return GSON.fromJson(reader, JsonObject.class);
		} catch (Exception e) {
			throw new IllegalStateException("Omnispell could not read " + path, e);
		}
	}

	public static Word word(String id) {
		return WORDS.get(id);
	}

	public static Iterable<Word> words() {
		return WORDS.values();
	}

	public static List<Word> wordsOfType(String type) {
		List<Word> list = new ArrayList<>();
		for (Word word : WORDS.values()) {
			if (word.type.equals(type) && !word.excluded) list.add(word);
		}
		return list;
	}

	public static int obtainableCount() {
		int count = 0;
		for (Word word : WORDS.values()) if (!word.excluded) count++;
		return count;
	}

	public static SpellType spellType(String id) {
		return TYPES.get(id);
	}

	public static Iterable<SpellType> spellTypes() {
		return TYPES.values();
	}

	/** True if this intention suits the spell type. Only a hint on the website, and only a hint here. */
	public static boolean intentionFits(String spellType, Word intention) {
		Set<String> names = COMPATIBLE.get(spellType);
		return names == null || names.contains(intention.english);
	}

	public static ExploreSpell exploreSpell(String base, String command, String intention) {
		return EXPLORE.get(base + "-" + command + "-" + intention);
	}

	public static Flavor flavor(String exploreBase) {
		return FLAVORS.get(exploreBase);
	}
}
