package com.omnispell.spell;

import com.omnispell.Omnispell;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a combat spell really does in the game, worked out from the game's own rules rather than from what the
 * Oracle wrote: how it travels, what form it takes, its numbers in hearts and seconds, the reserve it draws on
 * and every toggle it switches on. Used to check the Oracle's work.
 */
public final class Mechanics {
	private Mechanics() {
	}

	private static String hearts(double websiteValue) {
		double h = websiteValue * Omnispell.CONFIG.valueScale / 2.0;
		return String.format(Locale.ROOT, h == Math.floor(h) ? "%.0f" : "%.1f", h) + " heart" + (h == 1 ? "" : "s");
	}

	private static String seconds(double turns) {
		return String.format(Locale.ROOT, "%.0f", turns * Omnispell.CONFIG.turnSeconds) + "s";
	}

	/** How an attack leaves the wand, by the same rules casting uses. */
	public static String delivery(Spell spell) {
		String weapon = Weapons.kind(spell);
		if (weapon != null) return weapon.equals("bow") ? "three magic arrows, then the spell" : weapon.equals("arrow") ? "a magic arrow, then the spell" : "the wand becomes a " + weapon + " for a while";
		if (Surges.wallAttack(spell)) return "a wall that rolls forward";
		if (Beams.piercing(spell)) return "a beam that pierces everything in line";
		String planned = spell.planned("delivery");
		String form = AttackForms.commandForm(spell);
		if ("shockwave".equals(planned) || (planned == null && Conjuring.sonic(spell) && (spell.hasCharge("Sparsus") || "area".equals(planned) || "burst".equals(form) || "blast".equals(form))))
			return "a shockwave of sound in front of you";
		String kind = Beams.storm(spell) ? "a lightning bolt" : Conjuring.sonic(spell) ? "rings of sound" : "a bolt";
		if (spell.hasCharge("Sparsus") || "area".equals(planned)) kind += " that bursts over everything near where it lands";
		return kind;
	}

	private static String toggle(String text) {
		String[] p = text.split(":");
		String id = p[0].trim();
		String v = p.length > 1 ? p[1].trim() : "";
		String d = p.length > 2 ? seconds(parse(p[2])) : "";
		return switch (id) {
			case "summoned_creature" -> "a creature (" + (p.length > 3 ? p[3].replace('_', ' ') : "conjured") + ")";
			case "chain_lightning" -> "chains " + v + "% to more foes";
			case "bleed" -> "bleeding";
			case "life_siphon" -> "heals you " + v + "% of damage dealt";
			default -> id.replace('_', ' ') + (v.isEmpty() || v.equals("1") ? "" : " " + v) + (d.isEmpty() ? "" : " for " + d);
		};
	}

	private static double parse(String s) {
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** The spell, plainly: one line each for how it is cast and what it does. */
	public static List<String> explain(Spell spell) {
		List<String> out = new ArrayList<>();
		Spell.EffectCode code = spell.code;
		var pool = Casting.poolOf(spell);
		out.add("Tier " + spell.tier + " " + spell.type + ", draws on " + (pool == null ? "nothing" : pool.label) + " (" + Math.round(Casting.manaCost(spell)) + " mana), cooldown about "
				+ Math.round(Casting.cooldownTurns(spell) * Omnispell.CONFIG.turnSeconds * ("attack".equals(spell.type) ? 0.7 : 1.0)) + "s.");
		switch (spell.type) {
			case "attack" -> {
				out.add("Travels as " + delivery(spell) + "; element " + AttackForms.element(spell) + ".");
				out.add("Hits for about " + hearts(code.primaryValue > 0 ? code.primaryValue : spell.tier * 10.0) + ".");
				String forms = AttackForms.describe(spell);
				if (!forms.isEmpty()) out.add(forms);
			}
			case "defense" -> out.add(Defenses.describe(spell) + " Amount about " + hearts(code.primaryValue) + ", lasting about " + seconds(Math.max(2, code.duration)) + ".");
			case "conjuration" -> {
				String name = null;
				for (String m : code.modifiers) if (m.startsWith("summoned_creature")) {
					String[] p = m.split(":");
					if (p.length > 3) name = p[3];
				}
				out.add(Conjuring.describe(spell, name));
			}
			default -> {
				switch (code.type) {
					case "heal" -> out.add("Heals you about " + hearts(code.primaryValue) + ".");
					case "shield" -> out.add("Gives you a shield of about " + hearts(code.primaryValue) + " for " + seconds(Math.max(1, code.duration)) + ".");
					case "buff", "utility" -> out.add("Strengthens you for " + seconds(Math.max(2, code.duration)) + (code.secondary == null ? (code.modifiers.isEmpty() ? ": more spell damage (" + Math.round(code.primaryValue) + "%)" : "") : ": " + code.secondary.replace('_', ' ')) + ".");
					case "damage" -> out.add("Strikes what you look at for about " + hearts(code.primaryValue) + ".");
					default -> out.add("Works on what you look at" + (spell.hasCharge("Sparsus") ? " and everything near it" : "") + ": "
							+ (code.status == null || code.status.equals("none") || code.status.equals("null") ? "weakened and slowed" : code.status) + " for " + seconds(Math.max(1, code.duration))
							+ " (fully on weak creatures, less on strong, never bosses).");
				}
				if (!"buff".equals(code.type) && code.secondary != null) out.add("Also: " + code.secondary.replace('_', ' ') + ".");
			}
		}
		if ("attack".equals(spell.type) && code.status != null && !code.status.equals("none") && !code.status.equals("null")) out.add("Leaves it " + code.status + " for " + seconds(Math.max(1, code.duration)) + ".");
		List<String> toggles = new ArrayList<>();
		for (String m : code.modifiers) if (!m.startsWith("summoned_creature")) toggles.add(toggle(m));
		if (!toggles.isEmpty()) out.add("Toggles: " + String.join(", ", toggles) + ".");
		return out;
	}
}
