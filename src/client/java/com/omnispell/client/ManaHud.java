package com.omnispell.client;

import com.omnispell.item.WandItem;
import com.omnispell.player.ManaPool;
import com.omnispell.spell.Spell;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * What shows while a wand is held: a bar of five squares above the health bar,
 * one per spell, each showing its key; the spell's name when its key is pressed;
 * a grey cover on a square that drains away as the spell's cooldown runs out;
 * and (optionally) the five mana reserves.
 */
public final class ManaHud {
	public static final int SQUARE = 16, GAP = 2;

	private ManaHud() {
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.hud.isHidden()) return;
		flash(g);
		if (ClientSettings.get().showCoords && !client.debugEntries.isOverlayVisible()) {
			String at = client.player.getBlockX() + "  " + client.player.getBlockY() + "  " + client.player.getBlockZ();
			g.text(client.font, at, 4, 4, 0xC0E8ECF8, true);
		}
		ItemStack wand = WandItem.heldWand(client.player);
		if (wand.isEmpty()) return;
		Font font = client.font;
		int w = g.guiWidth();
		int h = g.guiHeight();

		spellBar(g, client, font, wand, w, h);
		mana(g, font, h, ClientSettings.get().showMana);
	}

	/** Left edge and top of the spell bar: directly above the hearts (and the armor row, when there is one). */
	public static int barX(int w) {
		return w / 2 - 91;
	}

	public static int barY(Minecraft client, int h) {
		int y = h - 39 - SQUARE - 3;
		if (client.player.getArmorValue() > 0) y -= 10;
		if (client.player.getAbsorptionAmount() > 0 || client.player.getMaxHealth() > 20) y -= 10;
		if (client.player.getAbilities().instabuild) y = h - 22 - SQUARE - 4;
		return y;
	}

	private static void spellBar(GuiGraphicsExtractor g, Minecraft client, Font font, ItemStack wand, int w, int h) {
		int x0 = barX(w);
		int y0 = barY(client, h);
		int selected = WandItem.selected(wand);
		for (int i = 0; i < WandItem.SLOTS; i++) {
			int x = x0 + i * (SQUARE + GAP);
			Spell spell = WandItem.spell(wand, i);
			g.fill(x, y0, x + SQUARE, y0 + SQUARE, 0xC0101420);
			if (spell != null) g.fill(x + 1, y0 + SQUARE - 3, x + SQUARE - 1, y0 + SQUARE - 1, 0xFF000000 | SpellLook.color(spell));
			ManaPool pool = com.omnispell.spell.Casting.poolOf(spell);
			if (pool != null) g.fill(x + SQUARE - 5, y0 + 1, x + SQUARE - 1, y0 + 5, 0xFF000000 | pool.color); // which reserve this spell draws on
			String key = keyLabel(i);
			g.centeredText(font, key, x + SQUARE / 2, y0 + 3, spell == null ? 0xFF4A5268 : 0xFFE8ECF8);
			if (spell != null) {
				float cd = ClientState.cooldownFraction(i);
				if (cd > 0) {
					// The classic grey cover: full after a cast, draining away from the top.
					int cover = Math.max(1, Math.round((SQUARE - 2) * cd));
					g.fill(x + 1, y0 + SQUARE - 1 - cover, x + SQUARE - 1, y0 + SQUARE - 1, 0xB0707684);
				}
			}
			g.outline(x, y0, SQUARE, SQUARE, i == selected && spell != null ? 0xFF7FB4FF : 0xFF2E3654);
		}

		// The name appears only after a key press, and fades.
		long age = ClientState.clientTick - ClientState.shownAtTick;
		int slot = ClientState.shownSlot;
		if (!ClientState.hideName && slot >= 0 && slot < WandItem.SLOTS && age >= 0 && age < 50) {
			Spell spell = WandItem.spell(wand, slot);
			int alpha = age < 35 ? 255 : Math.max(16, 255 - (int) ((age - 35) * 16));
			String text = spell == null ? "Empty" : spell.label();
			int color = spell == null ? 0x8090B0 : (SpellLook.tierColor(spell.tier) & 0xFFFFFF);
			int cx = x0 + (WandItem.SLOTS * (SQUARE + GAP) - GAP) / 2;
			int tw = font.width(text);
			int tx = Math.max(x0, cx - tw / 2);
			g.fill(tx - 3, y0 - 13, tx + tw + 3, y0 - 2, (Math.min(alpha, 0x90) << 24) | 0x101420);
			g.text(font, text, tx, y0 - 11, (alpha << 24) | color, false);
		}
	}

	/** A short label for a spell key: "Z", "F4", "M4" for a mouse button, "·" when unbound. */
	public static String keyLabel(int slot) {
		if (OmnispellClient.CAST[slot].isUnbound()) return "·";
		String name = OmnispellClient.CAST[slot].getTranslatedKeyMessage().getString();
		if (name.length() <= 2) return name.toUpperCase();
		String compact = name.replace("Button ", "M").replace("Keypad ", "K").replace("Left ", "L").replace("Right ", "R");
		return compact.length() <= 3 ? compact : compact.substring(0, 2);
	}

	/** The mana panel. With the panel switched off, a reserve still shows for a moment whenever it is drawn on. */
	private static void mana(GuiGraphicsExtractor g, Font font, int h, boolean all) {
		int x = 6;
		ManaPool[] pools = ManaPool.values();
		int y = h / 2 - pools.length * 11 / 2;
		if (all) {
			g.fill(x - 3, y - 13, x + 106, y + pools.length * 11 + 2, 0x90101420);
			g.text(font, "✦ " + ClientState.essence + " / " + ClientState.essenceCap, x, y - 10, 0xFF7FD4FF, true);
		}
		for (int i = 0; i < pools.length; i++) {
			long sinceSpent = ClientState.clientTick - ClientState.manaSpentAt[i];
			boolean flashing = ClientState.manaSpentAt[i] > 0 && sinceSpent >= 0 && sinceSpent < 12;
			boolean recent = ClientState.manaSpentAt[i] > 0 && sinceSpent >= 0 && sinceSpent < 50;
			if (!all && !recent) continue;
			int by = y + i * 11;
			if (!all) g.fill(x - 3, by, x + 106, by + 10, 0x90101420);
			float fraction = (float) Math.max(0, Math.min(1, ClientState.mana[i] / ClientState.manaMax[i]));
			int color = 0xFF000000 | pools[i].color;
			g.fill(x, by + 2, x + 40, by + 8, 0xFF1B2030);
			g.fill(x, by + 2, x + Math.round(40 * fraction), by + 8, fraction < 0.2f ? 0xFFB03030 : color);
			g.outline(x - 1, by + 1, 42, 8, flashing ? 0xFFFFFFFF : 0xFF3A4260);
			g.text(font, pools[i].label, x + 45, by + 1, flashing ? 0xFFFFFFFF : fraction <= 0.01f ? 0xFF8A5050 : 0xFFC8D0E0, false);
		}
		if (all && ClientState.corruption >= 1) {
			g.text(font, "Corruption " + (int) ClientState.corruption + "%", x, y + pools.length * 11 + 5, ClientState.corruption >= 50 ? 0xFFFF5555 : 0xFFC080FF, true);
		}
	}

	/** A brief tint of the whole screen for spells whose visuals call for a flash, pulse or darkening. */
	private static void flash(GuiGraphicsExtractor g) {
		long age = ClientState.clientTick - ClientState.flashAtTick;
		if (age < 0 || age > 8 || ClientState.flashKind.equals("none")) return;
		float strength = (1f - age / 8f) * (0.08f + 0.04f * ClientState.flashIntensity);
		int color = ClientState.flashKind.equals("darken") ? 0x000000 : ClientState.flashColor;
		if (ClientState.flashKind.equals("darken")) strength *= 2f;
		int alpha = Math.min(160, Math.round(255 * strength));
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | (color & 0xFFFFFF));
	}
}
