package com.omnispell.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bosses of Omnispell, as they appear in Minecraft. Names, order, elements
 * and the lesson each one teaches come from the Omnispell boss list. Each lair's
 * door is made of the block for its tier, so the door tells you what waits inside.
 */
public final class BossDef {
	/** Door material by tier, easiest first. */
	public static final String[] TIER_NAMES = {"Hay", "Wood", "Stone", "Iron", "Gold", "Diamond", "Netherite"};
	public static final double[] TIER_HEALTH = {60, 100, 150, 220, 300, 400, 520};
	public static final double[] TIER_DAMAGE = {4, 6, 8, 10, 13, 16, 20};

	public static Block door(int tier) {
		return switch (tier) {
			case 0 -> Blocks.HAY_BLOCK;
			case 1 -> Blocks.OAK_PLANKS;
			case 2 -> Blocks.STONE;
			case 3 -> Blocks.IRON_BLOCK;
			case 4 -> Blocks.GOLD_BLOCK;
			case 5 -> Blocks.DIAMOND_BLOCK;
			default -> Blocks.NETHERITE_BLOCK;
		};
	}

	public final String id;
	public final String name;
	public final int tier;
	public final EntityType<? extends Mob> body;
	public final double scale;
	public final String element;
	public final String weakness;
	public final String lesson;       // shown in the book once defeated
	public final List<String> abilities = new ArrayList<>();

	private BossDef(String id, String name, int tier, EntityType<? extends Mob> body, double scale, String element, String weakness, String lesson, String... abilities) {
		this.id = id;
		this.name = name;
		this.tier = tier;
		this.body = body;
		this.scale = scale;
		this.element = element;
		this.weakness = weakness;
		this.lesson = lesson;
		this.abilities.addAll(List.of(abilities));
	}

	public static final Map<String, BossDef> ALL = new LinkedHashMap<>();

	private static void add(BossDef def) {
		ALL.put(def.id, def);
	}

	static {
		add(new BossDef("thornback_alpha", "Thornback Alpha", 0, EntityTypes.POLAR_BEAR, 1.3, "nature", "fire",
				"Strike at an element's weakness.", "thorns"));
		add(new BossDef("mistveil_serpent", "Mistveil Serpent", 1, EntityTypes.CAVE_SPIDER, 2.4, "shadow", "light",
				"Manage what afflicts you.", "poison_cloud"));
		add(new BossDef("ironbark_guardian", "Ironbark Guardian", 1, EntityTypes.ZOMBIE, 1.8, "nature", "fire",
				"Break a shield with sustained damage.", "regen_shield"));
		add(new BossDef("ember_tyrant", "Ember Tyrant", 2, EntityTypes.BLAZE, 1.9, "fire", "ice",
				"Prepare your defenses first.", "fire_nova"));
		add(new BossDef("frostweaver_arachne", "Frostweaver Arachne", 2, EntityTypes.SPIDER, 2.3, "ice", "fire",
				"Do not be held still.", "web"));
		add(new BossDef("stormcaller_djinn", "Stormcaller Djinn", 3, EntityTypes.BREEZE, 1.8, "arcane", "shadow",
				"Guard your mana.", "lightning"));
		add(new BossDef("void_sentinel", "Void Sentinel", 3, EntityTypes.WITHER_SKELETON, 1.7, "shadow", "light",
				"Make every cast count.", "silence"));
		add(new BossDef("plagueborn_hydra", "Plagueborn Hydra", 4, EntityTypes.ZOGLIN, 1.6, "nature", "fire",
				"One foe may become many.", "poison_cloud", "split"));
		add(new BossDef("celestial_arbiter", "Celestial Arbiter", 4, EntityTypes.EVOKER, 1.6, "light", "shadow",
				"Patience: do not strike a mirror.", "reflect"));
		add(new BossDef("abyssal_leviathan", "Abyssal Leviathan", 5, EntityTypes.RAVAGER, 1.4, "shadow", "light",
				"Endure.", "exhaust", "fire_nova"));
		add(new BossDef("chrono_wyrm", "Chrono Wyrm", 6, EntityTypes.ENDERMAN, 1.9, "arcane", "nature",
				"Adapt when time itself shifts.", "time", "silence", "lightning"));
		add(new BossDef("razza", "Razza, the Unbound", 7, EntityTypes.ILLUSIONER, 2.2, "arcane", "light",
				"Everything.", "lightning", "fire_nova", "poison_cloud", "silence", "reflect", "time", "split"));
	}

	public static List<BossDef> ofTier(int tier) {
		List<BossDef> list = new ArrayList<>();
		for (BossDef def : ALL.values()) if (def.tier == tier) list.add(def);
		return list;
	}

	public double health() {
		return tier >= 7 ? 900 : TIER_HEALTH[tier];
	}

	public double damage() {
		return tier >= 7 ? 22 : TIER_DAMAGE[tier];
	}
}
