package com.omnispell.alchemy;

import com.omnispell.player.ManaPool;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one set of brews the book gives away: water, glowstone dust and something of a
 * reserve's nature always makes that reserve's mana potion, at any heat. Gunpowder makes it
 * a splash potion. Everything else in the cauldron is left for players to find out.
 */
public final class ManaPotions {
	public static final Map<String, ManaPool> INGREDIENTS = Map.of(
			"iron_ingot", ManaPool.BARRIER,
			"glistering_melon_slice", ManaPool.RESTORATION,
			"amethyst_shard", ManaPool.SPIRIT,
			"feather", ManaPool.PHASE,
			"magma_cream", ManaPool.FORCE);

	private ManaPotions() {
	}

	/** The mana potion these contents make, or null if they are not one. */
	public static Brew match(String base, List<String> items) {
		if (!"water".equals(base)) return null;
		Set<String> set = new HashSet<>(items);
		if (set.size() != items.size() || !set.remove("glowstone_dust")) return null;
		boolean splash = set.remove("gunpowder");
		if (set.size() != 1) return null;
		ManaPool pool = INGREDIENTS.get(set.iterator().next());
		return pool == null ? null : make(pool, splash);
	}

	public static Brew make(ManaPool pool, boolean splash) {
		Brew brew = new Brew();
		brew.name = (splash ? "Splash " : "") + "Potion of " + pool.label + " Mana";
		brew.description = "Bottled " + pool.label + " mana.";
		brew.effect = splash ? "Restores a third of the " + pool.label + " mana of everyone it splashes, you included."
				: "Restores half your " + pool.label + " mana.";
		brew.tier = 2;
		brew.type = "mana";
		brew.codeType = "mana_restore";
		brew.manaType = pool.name().toLowerCase();
		brew.manaPercent = splash ? 35 : 50;
		brew.splash = splash;
		return brew;
	}
}
