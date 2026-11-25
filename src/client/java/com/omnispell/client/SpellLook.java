package com.omnispell.client;

import com.omnispell.spell.Fx;
import com.omnispell.spell.Spell;

/** Colours and a one-letter mark for a spell, for the HUD and the book. */
public final class SpellLook {
	private SpellLook() {
	}

	public static int color(Spell spell) {
		return Fx.color(spell.visual.color1);
	}

	public static int color2(Spell spell) {
		int c = Fx.color(spell.visual.color2);
		// Darken the inner square a little so the letter on top stays readable.
		int r = (c >> 16 & 0xFF) * 6 / 10, g = (c >> 8 & 0xFF) * 6 / 10, b = (c & 0xFF) * 6 / 10;
		return r << 16 | g << 8 | b;
	}

	public static String glyph(Spell spell) {
		return switch (spell.type) {
			case "attack" -> "A";
			case "defense" -> "D";
			case "enchantment" -> "E";
			case "illusion" -> "I";
			case "charm" -> "C";
			case "conjuration" -> "S";
			default -> "X";
		};
	}

	public static int tierColor(int tier) {
		return switch (tier) {
			case 1 -> 0xFFB0B8C8;
			case 2 -> 0xFF6FCF7F;
			case 3 -> 0xFF5FA8FF;
			case 4 -> 0xFFC080FF;
			default -> 0xFFFFC040;
		};
	}
}
