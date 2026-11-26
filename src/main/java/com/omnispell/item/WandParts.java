package com.omnispell.item;

import com.omnispell.player.ManaPool;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What each wand wood and core does. A wand is two planks of one wood with a core
 * between them, so every wood/core pair is a different wand.
 * The woods carry the mana bonuses of the ten Omnispell wand woods.
 */
public final class WandParts {
	public static final class Wood {
		public final String id;       // "oak" -> minecraft:oak_planks
		public final String label;
		public final int color;
		public final String blurb;
		public final Map<ManaPool, Integer> mana = new EnumMap<>(ManaPool.class); // percent
		public double damage;         // extra spell damage, percent
		public String element;        // spells of this element get +20% power

		Wood(String id, String label, int color, String blurb) {
			this.id = id;
			this.label = label;
			this.color = color;
			this.blurb = blurb;
		}

		Wood mana(ManaPool pool, int percent) {
			mana.put(pool, percent);
			return this;
		}

		Wood all(int percent) {
			for (ManaPool pool : ManaPool.values()) mana.put(pool, percent);
			return this;
		}

		Wood damage(double percent) {
			damage = percent;
			return this;
		}

		Wood element(String name) {
			element = name;
			return this;
		}
	}

	public static final class Core {
		public final String id;       // item path, e.g. "amethyst_shard"
		public final String label;
		public final int color;
		public final int words;       // how many words a spell crafted with this wand may hold
		public final String blurb;
		public double damage;         // percent
		public double mending;        // extra healing and shield strength, percent
		public double crit;           // chance for double damage, percent
		public double haste;          // cooldowns shortened by this percent
		public double regen;          // mana refills faster by this percent
		public String element;

		Core(String id, String label, int color, int words, String blurb) {
			this.id = id;
			this.label = label;
			this.color = color;
			this.words = words;
			this.blurb = blurb;
		}
	}

	public static final Map<String, Wood> WOODS = new LinkedHashMap<>();
	public static final Map<String, Core> CORES = new LinkedHashMap<>();

	static {
		wood(new Wood("oak", "Oak", 0xB8945F, "Sturdy and loyal.").mana(ManaPool.BARRIER, 30).mana(ManaPool.FORCE, 10));
		wood(new Wood("birch", "Birch", 0xD7CB8D, "Pure offensive focus.").damage(10));
		wood(new Wood("spruce", "Spruce", 0x7A5A33, "Noble and defensive.").mana(ManaPool.BARRIER, 20).mana(ManaPool.RESTORATION, 15));
		wood(new Wood("jungle", "Jungle", 0xB88764, "Graceful and adaptive.").mana(ManaPool.RESTORATION, 30).mana(ManaPool.BARRIER, 10));
		wood(new Wood("acacia", "Acacia", 0xBA6337, "Balanced and versatile.").all(5));
		wood(new Wood("dark_oak", "Dark Oak", 0x4F3218, "Powerful and unyielding.").mana(ManaPool.SPIRIT, 25).mana(ManaPool.FORCE, 20));
		wood(new Wood("mangrove", "Mangrove", 0x7B3636, "A warrior's wand.").mana(ManaPool.BARRIER, 30).mana(ManaPool.FORCE, 10));
		wood(new Wood("cherry", "Cherry", 0xE7C2BB, "A mystical guardian.").mana(ManaPool.PHASE, 25).mana(ManaPool.SPIRIT, 15));
		wood(new Wood("bamboo", "Bamboo", 0xD3BD5A, "Rare and temperamental.").all(10));
		wood(new Wood("pale_oak", "Pale Oak", 0xE5DBD9, "Ancient power over life and death.").mana(ManaPool.SPIRIT, 30).mana(ManaPool.PHASE, 15));
		wood(new Wood("crimson", "Crimson", 0x7E3A56, "Grown in fire. Fire spells burn hotter.").element("fire").damage(5));
		wood(new Wood("warped", "Warped", 0x398382, "Grown in shadow. Shadow spells strike deeper.").element("shadow").mana(ManaPool.PHASE, 15));

		core("flint", "Flint", 0x565656, 3, "A beginner's core. It works.");
		core("amethyst_shard", "Amethyst", 0xB38EF3, 3, "Arcane spells gain power.").element = "arcane";
		core("copper_ingot", "Copper", 0xE77C56, 3, "Mana refills a little faster.").regen = 15;
		core("iron_ingot", "Iron", 0xD8D8D8, 4, "Dependable. Stronger shields and healing.").mending = 10;
		core("gold_ingot", "Gold", 0xFDF55F, 4, "Mana refills much faster.").regen = 35;
		core("quartz", "Quartz", 0xEEE6DE, 4, "Light spells gain power.").element = "light";
		core("emerald", "Emerald", 0x41F384, 4, "Nature answers. Much stronger healing and shields.").mending = 20;
		core("blaze_rod", "Blaze Rod", 0xFFCB33, 5, "Raw power. All spells hit harder.").damage = 12;
		core("breeze_rod", "Breeze Rod", 0xB9C7E8, 5, "Swift. Spells are ready again sooner.").haste = 20;
		core("ender_pearl", "Ender Pearl", 0x2CCDB1, 5, "Instinct. A chance for a spell to strike twice as hard.").crit = 12;
		core("diamond", "Diamond", 0x6EFCF2, 5, "Lethal precision. A high chance to strike twice as hard.").crit = 20;
		core("echo_shard", "Echo Shard", 0x0A5060, 6, "Spirit. Shadow spells gain power and return sooner.").element = "shadow";
		CORES.get("echo_shard").haste = 10;
		core("enchanting_table", "Obsidian", 0x24163A, 4, "A plain obsidian core. The table it came from leaves enchantments on the wand, by chance: Unbreaking, Soul Bind, or both.");
		core("totem_of_undying", "Totem", 0xF2D35B, 6, "Life clings to it. Never lost when you die. Once every ten minutes, a blow that would kill you leaves you standing, while it is in hand or on your hotbar.").mending = 15;
		core("netherite_ingot", "Netherite", 0x5A575A, 6, "Overwhelming force.").damage = 25;
		core("nether_star", "Nether Star", 0xF6F8C8, 7, "The Wither's heart. Power, speed and mana together.").damage = 20;
		CORES.get("nether_star").haste = 15;
		CORES.get("nether_star").regen = 25;
	}

	private WandParts() {
	}

	private static void wood(Wood wood) {
		WOODS.put(wood.id, wood);
	}

	private static Core core(String id, String label, int color, int words, String blurb) {
		Core core = new Core(id, label, color, words, blurb);
		CORES.put(id, core);
		return core;
	}

	public static Wood wood(String id) {
		return WOODS.getOrDefault(id, WOODS.get("oak"));
	}

	public static Core core(String id) {
		return CORES.getOrDefault(id, CORES.get("flint"));
	}

	/** The plank item for a wood: nether woods and bamboo follow the same naming. */
	public static String plankItem(String woodId) {
		return "minecraft:" + woodId + "_planks";
	}
}
