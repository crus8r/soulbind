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
 * The on-screen display while a wand is held: the five mana reserves, arcane
 * essence, and the wand's five spells with their cooldowns.
 */
public final class ManaHud {
	private ManaHud() {
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.hud.isHidden()) return;
		flash(g);
		ItemStack wand = WandItem.heldWand(client.player);
		if (wand.isEmpty() || !ClientState.hudVisible) return;
		Font font = client.font;
		int w = g.guiWidth();
		int h = g.guiHeight();

		// ----- mana reserves, left side -----
		int x = 6;
		int y = h / 2 - 34;
		g.fill(x - 3, y - 13, x + 106, y + 5 * 11 + 2, 0x90101420);
		g.text(font, "✦ " + ClientState.essence + " / " + ClientState.essenceCap, x, y - 10, 0xFF7FD4FF, true);
		ManaPool[] pools = ManaPool.values();
		for (int i = 0; i < pools.length; i++) {
			int by = y + i * 11;
			float fraction = (float) Math.max(0, Math.min(1, ClientState.mana[i] / ClientState.manaMax[i]));
			int color = 0xFF000000 | pools[i].color;
			g.fill(x, by + 2, x + 40, by + 8, 0xFF1B2030);
			g.fill(x, by + 2, x + Math.round(40 * fraction), by + 8, fraction < 0.2f ? 0xFFB03030 : color);
			g.outline(x - 1, by + 1, 42, 8, 0xFF3A4260);
			g.text(font, pools[i].label, x + 45, by + 1, fraction <= 0.01f ? 0xFF8A5050 : 0xFFC8D0E0, false);
		}
		if (ClientState.corruption >= 1) {
			g.text(font, "Corruption " + (int) ClientState.corruption + "%", x, y + 5 * 11 + 5, ClientState.corruption >= 50 ? 0xFFFF5555 : 0xFFC080FF, true);
		}

		// ----- spell slots, above the hotbar -----
		int slot = 20;
		int gap = 3;
		int total = WandItem.SLOTS * slot + (WandItem.SLOTS - 1) * gap;
		int sx = w / 2 - total / 2;
		int sy = h - 62 - slot;
		for (int i = 0; i < WandItem.SLOTS; i++) {
			int bx = sx + i * (slot + gap);
			Spell spell = WandItem.spell(wand, i);
			boolean selected = i == WandItem.selected(wand);
			g.fill(bx, sy, bx + slot, sy + slot, 0xB0101420);
			if (spell != null) {
				int c = 0xFF000000 | SpellLook.color(spell);
				g.fill(bx + 3, sy + 3, bx + slot - 3, sy + slot - 3, c);
				g.fill(bx + 5, sy + 5, bx + slot - 5, sy + slot - 5, 0xFF000000 | SpellLook.color2(spell));
				g.centeredText(font, SpellLook.glyph(spell), bx + slot / 2, sy + 6, 0xFFFFFFFF);
				float cd = ClientState.cooldownFraction(i);
				if (cd > 0) {
					int cover = Math.round((slot - 2) * cd);
					g.fill(bx + 1, sy + 1, bx + slot - 1, sy + 1 + cover, 0xC0000000);
				}
			}
			g.outline(bx, sy, slot, slot, selected ? 0xFF7FD4FF : 0xFF3A4260);
			String key = OmnispellClient.CAST[i].getTranslatedKeyMessage().getString();
			if (key.length() <= 2) g.text(font, key, bx + 2, sy - 9, 0xFF8090B0, true);
		}
		Spell current = WandItem.spell(wand, WandItem.selected(wand));
		if (current != null) g.centeredText(font, current.name, w / 2, sy - 20, 0xFFB8C8FF);
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
