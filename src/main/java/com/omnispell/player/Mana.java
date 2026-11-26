package com.omnispell.player;

import com.omnispell.Omnispell;
import com.omnispell.item.WandItem;
import com.omnispell.item.WandParts;
import com.soulbind.SoulBind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Mana reserves: how large they are, how they refill and how spells spend them.
 * Size comes from what you carry, not from a level: the wand's wood, what your
 * armor is made of, and the Reservoir and Wellspring enchantments.
 */
public final class Mana {
	public static final String RESERVOIR = "omnispell:reservoir";
	public static final String WELLSPRING = "omnispell:wellspring";
	public static final String ALACRITY = "omnispell:alacrity";

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private Mana() {
	}

	/**
	 * Percent each worn piece adds to every reserve. Soft, short-lived armor holds
	 * more magic; the toughest armor holds none.
	 */
	private static int armorPercent(ItemStack stack) {
		if (stack.isEmpty()) return 0;
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (path.startsWith("golden_")) return 15;
		if (path.startsWith("leather_")) return 12;
		if (path.startsWith("chainmail_") || path.equals("turtle_helmet")) return 8;
		if (path.startsWith("copper_")) return 6;
		if (path.startsWith("iron_")) return 3;
		return 0;
	}

	public static double max(ServerPlayer player, ManaPool pool) {
		double percent = 0;
		ItemStack wand = WandItem.heldWand(player);
		if (!wand.isEmpty()) {
			Integer bonus = WandParts.wood(WandItem.wood(wand)).mana.get(pool);
			if (bonus != null) percent += bonus;
		}
		for (EquipmentSlot slot : ARMOR) {
			ItemStack piece = player.getItemBySlot(slot);
			percent += armorPercent(piece);
			percent += 10 * SoulBind.enchantmentLevel(piece, RESERVOIR);
		}
		percent += com.omnispell.charm.Charms.manaPercent(player);
		return Math.round(pool.baseMax * (1.0 + percent / 100.0) * 10.0) / 10.0;
	}

	/** How fast reserves refill, as a multiplier. */
	public static double regenMultiplier(ServerPlayer player) {
		double percent = 0;
		ItemStack wand = WandItem.heldWand(player);
		if (!wand.isEmpty()) percent += WandParts.core(WandItem.core(wand)).regen;
		for (EquipmentSlot slot : ARMOR) {
			percent += 15 * SoulBind.enchantmentLevel(player.getItemBySlot(slot), WELLSPRING);
		}
		percent += com.omnispell.charm.Charms.regenPercent(player) + com.omnispell.familiar.Familiars.spiritPercent(player);
		return 1.0 + percent / 100.0;
	}

	/** Percent taken off spell cooldowns by worn armor: Alacrity gives 4% per level per piece, and chainmail 3% per piece. */
	public static double alacrityPercent(ServerPlayer player) {
		double percent = com.omnispell.charm.Charms.cooldownPercent(player);
		for (EquipmentSlot slot : ARMOR) {
			ItemStack piece = player.getItemBySlot(slot);
			percent += 4 * SoulBind.enchantmentLevel(piece, ALACRITY);
			if (!piece.isEmpty() && BuiltInRegistries.ITEM.getKey(piece.getItem()).getPath().startsWith("chainmail_")) percent += 3;
		}
		return percent;
	}

	/** Called once a second. */
	public static void regenerate(ServerPlayer player) {
		PlayerData data = PlayerStore.get(player);
		double multiplier = regenMultiplier(player);
		for (ManaPool pool : ManaPool.values()) {
			double max = max(player, pool);
			double current = data.mana[pool.ordinal()];
			if (current < max) {
				current = Math.min(max, current + max / Omnispell.CONFIG.manaRefillSeconds * multiplier);
			} else if (current > max) {
				current = max; // took the armor off
			}
			data.mana[pool.ordinal()] = current;
		}
		if (data.corruption > 0) data.corruption = Math.max(0, data.corruption - 0.5);
	}

	public static double available(ServerPlayer player, ManaPool pool) {
		return PlayerStore.get(player).mana[pool.ordinal()];
	}

	/**
	 * Spends up to {@code amount} from a reserve and returns how much was actually
	 * there. An effect that asks for 30 with 12 left gets 12, like the website clamps.
	 */
	public static double spend(ServerPlayer player, ManaPool pool, double amount) {
		PlayerData data = PlayerStore.get(player);
		double used = Math.max(0, Math.min(amount, data.mana[pool.ordinal()]));
		data.mana[pool.ordinal()] -= used;
		return used;
	}
}
