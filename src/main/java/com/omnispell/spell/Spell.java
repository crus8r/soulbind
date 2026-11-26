package com.omnispell.spell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.omnispell.data.Word;
import com.omnispell.data.WordBank;

import java.util.ArrayList;
import java.util.List;

/**
 * A crafted spell. For combat spells the description, numbers and toggles come
 * from the Omnispell generator unchanged; this class only stores them.
 */
public final class Spell {
	public String id;            // spell type + sorted word ids: the same words always give the same spell
	public String name;          // the incantation
	public String nickname;      // what the player calls it, if they have named it
	public boolean archived;     // kept, but out of the way in the spellbook
	public String type;          // attack, defense, enchantment, illusion, charm, conjuration, exploration
	public List<String> words = new ArrayList<>();
	public int tier = 1;
	public String description = "";
	public String effect = "";
	public String school = "";
	public boolean dark;
	public boolean offline;      // made without the generator (no connection); can be re-crafted later
	public EffectCode code = new EffectCode();
	public Visual visual = new Visual();

	// Exploration spells only.
	public String toggle;
	public String element;
	public boolean fizzle;
	public boolean siphon;

	public static final class EffectCode {
		public String type = "damage";   // damage, heal, shield, buff, debuff, status, utility, summon
		public double primaryValue;
		public int duration;             // in website "turns"
		public String element = "neutral";
		public String status = "none";
		public String secondary;
		public List<String> modifiers = new ArrayList<>();
	}

	public static final class Visual {
		public String pattern = "directional";
		public String shape = "bolt";
		public String color1 = "arcane";
		public String color2 = "neutral";
		public String particles = "sparks";
		public String motion = "cascade";
		public int intensity = 1;
		public String screenEffect = "none";
	}

	public boolean isExploration() {
		return "exploration".equals(type);
	}

	public boolean has(String wordId) {
		return words.contains(wordId);
	}

	/** True if the spell uses the charge whose name starts with this (Maxima, Minima, Rapidus, Duratus, Focalis). */
	/** The name to show: the player's own name for the spell if it has one, otherwise its incantation. */
	public String label() {
		return nickname == null || nickname.isBlank() ? name : nickname;
	}

	public boolean hasCharge(String name) {
		for (String id : words) {
			if (id.startsWith("charge:" + name)) return true;
		}
		return false;
	}

	public static String idFor(String type, List<String> wordIds) {
		List<String> sorted = new ArrayList<>(wordIds);
		sorted.sort(null);
		return type + "|" + String.join(",", sorted);
	}

	/** The incantation as the website writes it: the chosen spellings separated by spaces. */
	public static String websiteName(List<String> wordIds) {
		StringBuilder sb = new StringBuilder();
		for (String id : wordIds) {
			Word word = WordBank.word(id);
			if (word == null) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(word.spelling);
		}
		return sb.toString();
	}

	/**
	 * The incantation as shown in the game. Some words are written as endings
	 * ("-strix") or beginnings ("transloc-"); those fuse with the word beside them.
	 */
	public static String incantation(List<String> wordIds) {
		List<String> parts = new ArrayList<>();
		List<String> waiting = new ArrayList<>(); // beginnings looking for a word to lead
		for (String id : wordIds) {
			Word word = WordBank.word(id);
			if (word == null) continue;
			String spelling = word.spelling;
			String bare = spelling.replace("-", "");
			if (spelling.endsWith("-")) {
				waiting.add(bare);
			} else if (spelling.startsWith("-") && !parts.isEmpty()) {
				parts.set(parts.size() - 1, parts.get(parts.size() - 1) + bare.toLowerCase());
			} else if (!waiting.isEmpty()) {
				parts.add(waiting.remove(0) + bare.toLowerCase());
			} else {
				parts.add(bare);
			}
		}
		// A beginning with nothing after it leads the first word instead.
		for (String lead : waiting) {
			if (parts.isEmpty()) parts.add(lead);
			else parts.set(0, lead + parts.get(0).toLowerCase());
		}
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return sb.toString();
	}

	/** Reads the generator's reply ({ tier, description, effect, effect_code, visual_manifest, school, is_dark_magic }). */
	public void readGenerated(JsonObject json) {
		tier = clamp(intOf(json, "tier", tier), 1, 5);
		description = stringOf(json, "description", description);
		effect = stringOf(json, "effect", effect);
		school = stringOf(json, "school", school);
		if (json.has("is_dark_magic") && json.get("is_dark_magic").isJsonPrimitive()) dark = json.get("is_dark_magic").getAsBoolean();

		JsonObject ec = objectOf(json, "effect_code");
		if (ec != null) {
			code.type = stringOf(ec, "type", code.type).toLowerCase();
			code.primaryValue = doubleOf(ec, "primary_value", code.primaryValue);
			code.duration = intOf(ec, "duration", code.duration);
			code.element = stringOf(ec, "element", code.element).toLowerCase();
			code.status = stringOf(ec, "status", "none").toLowerCase();
			String sec = stringOf(ec, "secondary", null);
			code.secondary = sec == null || sec.equals("null") ? null : sec.toLowerCase();
			code.modifiers.clear();
			if (ec.has("modifiers") && ec.get("modifiers").isJsonArray()) {
				for (JsonElement e : ec.getAsJsonArray("modifiers")) {
					if (e.isJsonPrimitive()) code.modifiers.add(e.getAsString());
				}
			}
		}
		JsonObject vm = objectOf(json, "visual_manifest");
		if (vm != null) {
			visual.pattern = stringOf(vm, "pattern", visual.pattern);
			visual.shape = stringOf(vm, "shape", visual.shape);
			visual.color1 = stringOf(vm, "color1", visual.color1);
			visual.color2 = stringOf(vm, "color2", visual.color2);
			visual.particles = stringOf(vm, "particles", visual.particles);
			visual.motion = stringOf(vm, "motion", visual.motion);
			visual.intensity = clamp(intOf(vm, "intensity", tier), 1, 5);
			visual.screenEffect = stringOf(vm, "screenEffect", visual.screenEffect);
		}
	}

	private static JsonObject objectOf(JsonObject json, String key) {
		if (!json.has(key)) return null;
		JsonElement e = json.get(key);
		if (e.isJsonObject()) return e.getAsJsonObject();
		if (e.isJsonPrimitive()) { // the database sometimes hands back JSON as text
			try {
				return WordBank.GSON.fromJson(e.getAsString(), JsonObject.class);
			} catch (Exception ignored) {
				return null;
			}
		}
		return null;
	}

	private static String stringOf(JsonObject json, String key, String fallback) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
	}

	private static int intOf(JsonObject json, String key, int fallback) {
		return (int) Math.round(doubleOf(json, key, fallback));
	}

	private static double doubleOf(JsonObject json, String key, double fallback) {
		try {
			return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsDouble() : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
