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
		return switch (codeType) {
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
