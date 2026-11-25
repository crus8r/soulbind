package com.omnispell.spell;

import com.omnispell.data.Word;
import com.omnispell.data.WordBank;

/**
 * Used only when the generator cannot be reached. Makes a plain, working spell
 * from the same tier and school rules the generator starts from, and marks it
 * "unstable" so it can be re-crafted for free once the connection is back.
 */
public final class Fallback {
	private Fallback() {
	}

	/** The generator's rule-based starting tier. */
	public static int tier(Spell spell) {
		int count = 0;
		int sum = 0;
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word != null && word.tier != null) {
				sum += word.tier;
				count++;
			}
		}
		if (count == 0) return 1;
		double average = (double) sum / count;
		int tier = (int) Math.round(average);
		if (spell.words.size() >= 4 && average >= 3) tier++;
		return Math.max(1, Math.min(5, tier));
	}

	/** The generator's rule-based school. */
	public static String school(Spell spell) {
		boolean forbidden = false, divine = false, ethereal = false, arcane = false, illusion = false, elemental = false;
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word == null || word.category == null) continue;
			switch (word.category) {
				case "Forbidden" -> forbidden = true;
				case "Divine" -> divine = true;
				case "Ethereal" -> ethereal = true;
				case "Arcane" -> arcane = true;
				case "Illusion" -> illusion = true;
				case "Elemental", "Complex elemental" -> elemental = true;
				default -> {
				}
			}
		}
		if (forbidden) return "Dark Magic";
		if (divine) return "Holy Magic";
		if (ethereal) return "Spirit Magic";
		if (arcane) return "Arcane Magic";
		if (illusion) return "Illusion Magic";
		if (elemental) return "Elemental Magic";
		return "General Magic";
	}

	public static String element(Spell spell) {
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word == null || !word.type.equals("base") || word.explore == null) continue;
			return switch (word.explore) {
				case "fire" -> "fire";
				case "ice", "water" -> "ice";
				case "shadow", "void", "blood" -> "shadow";
				case "light" -> "light";
				case "wind" -> "nature";
				default -> "neutral";
			};
		}
		return "arcane";
	}

	public static void fill(Spell spell) {
		spell.offline = true;
		spell.tier = tier(spell);
		if (spell.hasCharge("Minima")) spell.tier = Math.max(1, spell.tier - 1);
		spell.school = school(spell);
		spell.dark = spell.school.equals("Dark Magic");
		Spell.EffectCode code = spell.code;
		code.element = element(spell);
		code.modifiers.clear();
		int t = spell.tier;
		double boost = spell.hasCharge("Maxima") ? 1.4 : 1.0;
		switch (spell.type) {
			case "defense" -> {
				code.type = "shield";
				code.primaryValue = Math.round((t * 5 + 10) * boost);
				code.duration = Math.min(3, t) + (spell.hasCharge("Duratus") ? 1 : 0);
				spell.effect = "Raises a barrier that absorbs up to " + (int) code.primaryValue + " damage for " + code.duration + " turns.";
			}
			case "enchantment" -> {
				code.type = "buff";
				code.primaryValue = t * 8;
				code.duration = 3 + (spell.hasCharge("Duratus") ? 2 : 0);
				code.modifiers.add("damage_amplify:" + (10 + t * 5) + ":" + code.duration);
				spell.effect = "Increases your spell damage by " + (10 + t * 5) + "% for " + code.duration + " turns.";
			}
			case "illusion", "charm" -> {
				code.type = "status";
				code.status = spell.type.equals("charm") ? "charm" : "confusion";
				code.duration = Math.max(1, Math.min(3, t)) + (spell.hasCharge("Duratus") ? 1 : 0);
				spell.effect = "Leaves the target " + (spell.type.equals("charm") ? "charmed" : "confused") + " for " + code.duration + " turns.";
			}
			case "conjuration" -> {
				code.type = "summon";
				code.primaryValue = t * 6;
				code.duration = 3;
				code.modifiers.add("summoned_creature:" + (t * 6) + ":3:Conjured_Spirit");
				spell.effect = "Summons a spirit that deals " + (t * 6) + " damage each turn for 3 turns.";
			}
			default -> {
				code.type = "damage";
				code.primaryValue = Math.round(t * 10 * boost);
				code.duration = 0;
				spell.effect = "Deals " + (int) code.primaryValue + " " + code.element + " damage.";
			}
		}
		if (spell.hasCharge("Rapidus")) code.modifiers.add("cooldown_reduction:1:1");
		if (spell.hasCharge("Focalis")) code.modifiers.add("armor_pierce:" + (t >= 4 ? 15 : 8) + ":3");
		spell.description = "An unstable weave, shaped without the Oracle. Re-craft it when the connection returns.";
		spell.visual.color1 = code.element;
		spell.visual.color2 = code.element.equals("shadow") ? "dark" : code.element.equals("light") ? "holy" : "neutral";
		spell.visual.intensity = t;
		spell.visual.pattern = switch (spell.type) {
			case "defense" -> "radial";
			case "charm" -> "spiral";
			case "illusion" -> "vortex";
			case "enchantment" -> "pulse";
			case "conjuration" -> "scatter";
			default -> "directional";
		};
		spell.visual.particles = switch (code.element) {
			case "fire" -> "embers";
			case "ice" -> "snowflakes";
			case "shadow" -> "wisps";
			case "light" -> "motes";
			case "nature" -> "leaves";
			default -> "runes";
		};
	}
}
