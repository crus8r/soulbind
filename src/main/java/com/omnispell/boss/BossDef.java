package com.omnispell.boss;

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

	/** A creature a boss can wear. The first one in a boss's list that exists in this game is used. */
	public record Body(String entity, double scale) {
	}

	public final String id;
	public final String name;
	public final int tier;
	public final List<Body> bodies = new ArrayList<>();
	public final String element;
	public final String weakness;
	public final String lesson;       // shown in the book once defeated
	public final List<String> abilities = new ArrayList<>();

	private BossDef(String id, String name, int tier, String element, String weakness, String lesson) {
		this.id = id;
		this.name = name;
		this.tier = tier;
		this.element = element;
		this.weakness = weakness;
		this.lesson = lesson;
	}

	/** Adds a possible body: a creature id (from Minecraft or one of the pack's mods) and how large to make it. */
	private BossDef body(String entity, double scale) {
		bodies.add(new Body(entity, scale));
		return this;
	}

	private BossDef attacks(String... names) {
		abilities.addAll(List.of(names));
		return this;
	}

	public static final Map<String, BossDef> ALL = new LinkedHashMap<>();

	private static void add(BossDef def) {
		ALL.put(def.id, def);
	}

	// Each boss prefers a distinctive creature from the content pack and falls back to a
	// vanilla one, so the mod still works alone. Its attacks are its own either way.
	static {
		add(new BossDef("thornback_alpha", "Thornback Alpha", 0, "nature", "fire", "Strike at an element's weakness.")
				.body("naturalist:bear", 1.25).body("naturalist:boar", 1.8).body("minecraft:polar_bear", 1.3)
				.attacks("thorns", "charge", "thorn_volley"));
		add(new BossDef("mistveil_serpent", "Mistveil Serpent", 1, "shadow", "light", "Manage what afflicts you.")
				.body("naturalist:snake", 3.0).body("minecraft:cave_spider", 2.4)
				.attacks("poison_cloud", "vanish"));
		add(new BossDef("ironbark_guardian", "Ironbark Guardian", 1, "nature", "fire", "Break a shield with sustained damage.")
				.body("aerialhell:aerial_tree_mimic", 1.2).body("variantsandventures:thicket", 1.7).body("minecraft:zombie", 1.8)
				.attacks("regen_shield", "root_slam"));
		add(new BossDef("ember_tyrant", "Ember Tyrant", 2, "fire", "ice", "Prepare your defenses first.")
				.body("friendsandfoes:wildfire", 1.3).body("aerialhell:fire_spirit", 2.0).body("minecraft:blaze", 1.9)
				.attacks("fire_nova", "meteor"));
		add(new BossDef("frostweaver_arachne", "Frostweaver Arachne", 2, "ice", "fire", "Do not be held still.")
				.body("aerialhell:crystal_spider", 1.8).body("minecraft:spider", 2.3)
				.attacks("web", "ice_spikes"));
		add(new BossDef("stormcaller_djinn", "Stormcaller Djinn", 3, "arcane", "shadow", "Guard your mana.")
				.body("aerialhell:electro_spirit", 2.2).body("minecraft:breeze", 1.8)
				.attacks("lightning", "gale"));
		add(new BossDef("void_sentinel", "Void Sentinel", 3, "shadow", "light", "Make every cast count.")
				.body("mutantmonsters:mutant_enderman", 1.0).body("aerialhell:shadow_automaton", 1.4).body("minecraft:wither_skeleton", 1.7)
				.attacks("silence", "void_grasp"));
		add(new BossDef("plagueborn_hydra", "Plagueborn Hydra", 4, "nature", "fire", "One foe may become many.")
				.body("naturalist:jungle_scorpion", 3.5).body("naturalist:komodo_dragon", 2.0).body("minecraft:zoglin", 1.6)
				.attacks("poison_cloud", "acid_spit", "split"));
		add(new BossDef("celestial_arbiter", "Celestial Arbiter", 4, "light", "shadow", "Patience: do not strike a mirror.")
				.body("aerialhell:lunatic_priest", 1.2).body("friendsandfoes:iceologer", 1.5).body("minecraft:evoker", 1.6)
				.attacks("reflect", "judgement"));
		add(new BossDef("abyssal_leviathan", "Abyssal Leviathan", 5, "shadow", "light", "Endure.")
				.body("aerialhell:shadow_troll", 1.5).body("mutantmonsters:mutant_skeleton", 1.2).body("minecraft:ravager", 1.4)
				.attacks("exhaust", "tidal_slam", "void_grasp"));
		add(new BossDef("chrono_wyrm", "Chrono Wyrm", 6, "arcane", "nature", "Adapt when time itself shifts.")
				.body("uselessreptile:wyvern", 1.2).body("uselessreptile:lightning_chaser", 1.0).body("minecraft:enderman", 1.9)
				.attacks("time", "silence", "lightning", "rewind"));
		add(new BossDef("razza", "Razza, the Unbound", 7, "arcane", "light", "Everything.")
				.body("aerialhell:chained_god", 1.1).body("friendsandfoes:illusioner", 1.9).body("minecraft:illusioner", 2.2)
				.attacks("lightning", "meteor", "cage", "silence", "judgement", "void_grasp", "time", "tidal_slam", "split"));
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
