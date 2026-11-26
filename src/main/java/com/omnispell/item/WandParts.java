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
		public final Map<com.omnispell.player.ManaPool, Integer> mana = new java.util.EnumMap<>(com.omnispell.player.ManaPool.class);
		public String ability;        // a power of its own, worked in CoreAbilities

		Core mana(com.omnispell.player.ManaPool pool, int percent) {
			mana.put(pool, percent);
			return this;
		}

		Core ability(String id) {
			ability = id;
			return this;
		}

		Core element(String id) {
			element = id;
			return this;
		}

		Core damage(double percent) {
			damage = percent;
			return this;
		}

		Core crit(double percent) {
			crit = percent;
			return this;
		}

		Core mending(double percent) {
			mending = percent;
			return this;
		}

		Core regen(double percent) {
			regen = percent;
			return this;
		}

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

		// Cores with a power of their own. Each one is worth building a wand around.
		var B = com.omnispell.player.ManaPool.BARRIER; var R = com.omnispell.player.ManaPool.RESTORATION;
		var S = com.omnispell.player.ManaPool.SPIRIT; var P = com.omnispell.player.ManaPool.PHASE; var F = com.omnispell.player.ManaPool.FORCE;
		core("rabbit_foot", "Rabbit's Foot", 0xC8A07A, 4, "Light on your feet. You jump higher while it is in hand, and luck turns some spells into critical strikes.").crit(8).ability("rabbit");
		core("phantom_membrane", "Phantom Membrane", 0x9DA9C6, 4, "You drift instead of fall while it is in hand. Deeper Phase reserves.").mana(P, 40).ability("phantom");
		core("skeleton_skull", "Skeleton Skull", 0xD9D9C9, 4, "Bones know arrows. Arrows and other projectiles hurt you a quarter less while it is in hand.").crit(6).ability("skeleton");
		core("zombie_head", "Zombie Head", 0x4E7A3A, 4, "Hungry. Every creature your spells kill feeds you and mends you a little.").mana(R, 15).ability("zombie");
		core("turtle_scute", "Turtle Scute", 0x47BF4A, 4, "A hard shell. Deeper Barrier reserves and stronger shields.").mana(B, 30).mending(10);
		core("prismarine_crystals", "Prismarine", 0xA9DCCB, 4, "The sea's cold light. Ice spells gain power, and Restoration runs deeper.").element("ice").mana(R, 15);
		core("magma_cream", "Magma Cream", 0xE8A33B, 4, "Molten. Fire spells gain power, and Force runs deeper.").element("fire").mana(F, 15);
		core("ghast_tear", "Ghast Tear", 0xCFE6E6, 5, "Grief made into healing. Much stronger healing, and Restoration runs deeper.").mending(25).mana(R, 25);
		core("shulker_shell", "Shulker Shell", 0x976997, 5, "Spells that strike lift their target into the air for a moment. Barrier runs deeper.").mana(B, 25).ability("shulker");
		core("creeper_head", "Creeper Head", 0x5DBA4A, 5, "Critical strikes burst outward, hurting everything near the target. Breaks no blocks.").crit(10).ability("creeper");
		core("piglin_head", "Piglin Head", 0xE7B39D, 5, "Gold-hungry. Piglins take you for one of their own while it is in hand, and mana refills faster.").regen(20).ability("piglin");
		core("wither_rose", "Wither Rose", 0x2A2A2A, 5, "Spells that strike leave the target withering, and you cannot wither while it is in hand.").element("shadow").ability("wither_rose");
		core("ender_eye", "Eye of Ender", 0x4E9C6E, 5, "An enderman's instinct. Projectiles often pass right through you while it is in hand. Phase runs much deeper.").mana(P, 60).ability("ender");
		core("heart_of_the_sea", "Heart of the Sea", 0x3FA8D8, 6, "A conduit in your hand: you breathe, see and mine underwater while you hold it. Restoration and Barrier run deeper.").mana(R, 20).mana(B, 20).ability("sea");
		core("heavy_core", "Heavy Core", 0x50535A, 6, "Spells that strike throw their target far back. You barely budge when hit.").damage(10).ability("heavy");
		core("sculk_catalyst", "Sculk Catalyst", 0x0F2B33, 6, "Death feeds it. Every creature your spells kill refills your Spirit and Force.").element("shadow").mana(S, 20).ability("sculk");
		core("wither_skeleton_skull", "Wither Skull", 0x1C1C1C, 6, "The Wither's own. Shadow spells gain power, spells that strike wither hard, and Spirit runs deeper.").element("shadow").mana(S, 25).damage(8).ability("wither");
		core("dragon_breath", "Dragon's Breath", 0xC86AC8, 6, "Void spells gain power, and Spirit and Force run deeper.").element("void").mana(S, 20).mana(F, 20);
		core("enchanted_golden_apple", "Enchanted Apple", 0xF7E25B, 6, "A charmed life. In hand or on your hotbar: fire, lava and projectiles do half their harm, paid for in Barrier mana, and while Barrier lasts they cannot kill you.").mending(15).mana(B, 20).ability("apple");
		core("dragon_head", "Dragon Head", 0x2B1F33, 7, "The dragon's strength. All spells hit much harder, and Force runs deep.").damage(22).mana(F, 30);
		core("beacon", "Beacon", 0x8FF5EA, 7, "A beacon's light in your hand: Haste and Speed while you hold it, every reserve runs deeper, and light spells gain power.").element("light").mana(B, 15).mana(R, 15).mana(S, 15).mana(P, 15).mana(F, 15).ability("beacon");
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
