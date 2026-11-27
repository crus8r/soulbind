package com.omnispell.spell;

import com.omnispell.Omnispell;
import com.omnispell.item.WandItem;
import com.omnispell.item.WandParts;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** One cast in progress: who, what, and how much the wand adds. */
public final class Cast {
	public final ServerPlayer player;
	public final ServerLevel level;
	public final Spell spell;
	public final WandParts.Wood wood;
	public final WandParts.Core core;
	public final double scale;     // website numbers -> Minecraft health
	public final double range;
	/** Set by an effect to make the cooldown shorter (0.5 = half) or zero. */
	public double cooldownFactor = 1.0;
	/** Why the cast did nothing, shown to the player. Null if it worked. */
	public String failure;

	public Cast(ServerPlayer player, ItemStack wand, Spell spell) {
		this.player = player;
		this.level = player.level();
		this.spell = spell;
		this.wood = WandParts.wood(WandItem.wood(wand));
		this.core = WandParts.core(WandItem.core(wand));
		this.scale = Omnispell.CONFIG.valueScale;
		this.range = Omnispell.CONFIG.spellRange * (spell.hasCharge("Focalis") ? 1.4 : 1.0);
	}

	/** Wand bonus to damage for a spell of this element. */
	public double damageBonus(String element) {
		double percent = wood.damage + core.damage + com.omnispell.player.Mana.armorDamage(player);
		if (element != null && (element.equals(wood.element) || element.equals(core.element))) percent += 20;
		return 1.0 + percent / 100.0;
	}

	/** Wand bonus to healing and shields. */
	public double mendingBonus() {
		return 1.0 + (core.mending + com.omnispell.player.Mana.armorMending(player)) / 100.0;
	}

	public int turns(double turns) {
		return Omnispell.CONFIG.turnsToTicks(turns);
	}

	public void tell(String text) {
		player.sendOverlayMessage(Component.literal(text));
	}

	public boolean fail(String why) {
		failure = why;
		return false;
	}
}
