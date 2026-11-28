package com.omnispell.alchemy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** A brewed potion, as the potioneer described it. */
public final class Brew {
	public String name = "Mysterious Brew";
	public String description = "An unidentifiable concoction.";
	public String effect = "The potion swirls with uncertain energy.";
	public int tier = 1;
	public String type = "utility";   // healing, offensive, defensive, utility, transformation, curse, mana
	public String downside;
	public boolean offline;
	public boolean splash;            // thrown rather than drunk

	// effect_code
	public String codeType = "buff";  // heal, damage, shield, buff, debuff, resist, status, mana_restore, mana_drain
	public double value;
	public int duration;              // turns
	public String element = "neutral";
	public String resistType;
	public String status = "none";    // none, poison, regen, haste, fortify
	public String manaType;
	public int tint;                  // the colour this brew came out of the cauldron with (0: its kind's own colour)
	public double manaPercent;        // crafted mana potions: restore this share of the pool, so they suit any size of reserve

	public void read(JsonObject json) {
		name = str(json, "name", name);
		description = str(json, "description", description);
		effect = str(json, "effect", effect);
		tier = Math.max(1, Math.min(5, (int) Math.round(num(json, "tier", tier))));
		type = str(json, "type", type).toLowerCase();
		downside = str(json, "downside", null);
		if (json.has("effect_code") && json.get("effect_code").isJsonObject()) {
			JsonObject ec = json.getAsJsonObject("effect_code");
			codeType = str(ec, "type", codeType).toLowerCase();
			value = num(ec, "value", num(ec, "primary_value", value));
			duration = (int) Math.round(num(ec, "duration", duration));
			element = orNull(str(ec, "element", "neutral"), "neutral");
			resistType = orNull(str(ec, "resist_type", null), null);
			status = orNull(str(ec, "status", "none"), "none");
			manaType = orNull(str(ec, "mana_type", null), null);
		}
	}

	private static String orNull(String value, String fallback) {
		return value == null || value.equals("null") || value.isBlank() ? fallback : value.toLowerCase();
	}

	private static String str(JsonObject json, String key, String fallback) {
		JsonElement e = json.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
	}

	private static double num(JsonObject json, String key, double fallback) {
		try {
			JsonElement e = json.get(key);
			return e != null && e.isJsonPrimitive() ? e.getAsDouble() : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** Thrown potions hurt or hinder; everything else is drunk. Gunpowder in the recipe forces a throw. */
	public boolean decideSplash(boolean gunpowder) {
		boolean hostile = codeType.equals("damage") || codeType.equals("debuff") || codeType.equals("mana_drain")
				|| (codeType.equals("status") && status.equals("poison")) || type.equals("offensive") || type.equals("curse");
		splash = hostile || (gunpowder && !codeType.equals("heal"));
		return splash;
	}

	public int color() {
		if (tint != 0) return tint;
		return kindColor();
	}

	/**
	 * Gives a freshly brewed potion a colour of its own: one its name speaks of (a Crimson Draught is crimson, an
	 * Azure Tonic azure), or else its kind's colour turned a little by the recipe, so every recipe looks its own
	 * while a harming brew still looks like one. The same recipe always comes out the same colour.
	 */
	public void colour(String recipe) {
		String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
		String[][] named = {{"crimson", "E0283C"}, {"scarlet", "F0283C"}, {"ruby", "D0103C"}, {"blood", "A01020"}, {"red", "E04030"}, {"rose", "F07090"}, {"pink", "F58CC8"},
				{"orange", "F08A30"}, {"amber", "F0A830"}, {"golden", "F0C838"}, {"gold", "F0C838"}, {"yellow", "F0E040"}, {"sun", "F8D850"}, {"emerald", "30C060"}, {"jade", "50B080"},
				{"green", "58C04A"}, {"verdant", "48B040"}, {"moss", "6A8A3A"}, {"teal", "30A8A0"}, {"azure", "3A9AF0"}, {"sapphire", "2A50D0"}, {"blue", "4A78E8"}, {"sky", "80C0F8"},
				{"frost", "A8E4FF"}, {"ice", "A8E4FF"}, {"indigo", "5A48C8"}, {"violet", "A858E0"}, {"purple", "9040D0"}, {"amethyst", "A070E0"}, {"silver", "C8CCD8"}, {"white", "F0F0F0"},
				{"pearl", "F0E8E0"}, {"black", "282030"}, {"shadow", "3A2A50"}, {"obsidian", "2A1A40"}, {"midnight", "202060"}, {"copper", "C87040"}, {"bronze", "B08040"}};
		for (String[] c : named) {
			if (n.matches(".*\\b" + c[0] + "\\b.*")) {
				tint = Integer.parseInt(c[1], 16);
				return;
			}
		}
		int base = kindColor();
		float[] hsb = java.awt.Color.RGBtoHSB((base >> 16) & 0xFF, (base >> 8) & 0xFF, base & 0xFF, null);
		int h = (recipe == null ? n : recipe).hashCode();
		float hue = hsb[0] + ((h & 0xFF) / 255f - 0.5f) * 0.34f; // up to about sixty degrees either way
		float sat = Math.max(0.35f, Math.min(1f, hsb[1] + (((h >> 8) & 0xFF) / 255f - 0.5f) * 0.3f));
		float bri = Math.max(0.45f, Math.min(1f, hsb[2] + (((h >> 16) & 0xFF) / 255f - 0.5f) * 0.3f));
		tint = java.awt.Color.HSBtoRGB(hue - (float) Math.floor(hue), sat, bri) & 0xFFFFFF;
		if (tint == 0) tint = 1;
	}

	private int kindColor() {
		if (codeType.equals("mana_restore") && BrewItem.pool(manaType) != null) return BrewItem.pool(manaType).color;
		return switch (codeType) {
			case "sludge" -> 0x6E6E64;
			case "heal" -> 0xF2486A;
			case "damage" -> element.equals("fire") ? 0xF2742B : element.equals("ice") ? 0x6FC8F2 : element.equals("shadow") ? 0x7A3DB0 : 0xD23A2E;
			case "shield" -> 0x4F86F0;
			case "resist" -> 0xF0B24F;
			case "debuff" -> 0x6B7A3A;
			case "mana_restore", "mana_drain" -> 0x45D6E8;
			case "status" -> status.equals("poison") ? 0x58A52A : status.equals("regen") ? 0xF27AA0 : 0xE8D94A;
			default -> 0xB06FF2;
		};
	}
}
