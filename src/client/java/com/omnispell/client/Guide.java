package com.omnispell.client;

import com.omnispell.item.WandParts;
import com.omnispell.player.ManaPool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The Guide tab of the book: what things are and how to unlock them. */
public final class Guide {
	private Guide() {
	}

	public static Map<String, List<String>> sections() {
		Map<String, List<String>> s = new LinkedHashMap<>();
		s.put("Getting started", List.of(
				"You are not a person. You are what is left of every wizard Razza erased: a pattern, an echo, come back for what was taken.",
				"Your Soul Bind bundle holds a starter wand, this book and a pair of waypoints. They stay with you when you die, as long as they are in the bundle or soul-bound.",
				"1. Open the Craft tab and build a spell from the words you know.",
				"2. Save it. It goes into the first free slot of the wand you are holding.",
				"3. Hold the wand. A bar of five squares appears above your health, one per spell, each showing its key (Z X C V B at first).",
				"Press a spell's key once to see its name. Press it again to cast. A grey cover on the square drains away as the spell becomes ready again; stronger spells take longer.",
				"Right-click casts the spell you last selected. Sneak + right-click steps to the next slot.",
				"Change the keys in the Spellbook tab: click the key under a slot, then press the key you want. Double-tap can be switched off there, so one press casts."));
		s.put("Words of power", List.of(
				"Every spell is made of words. You begin with a handful and must find the rest.",
				"Word Scrolls teach one word you do not know yet, so a scroll is never wasted. Rarer scrolls hold rarer words.",
				"Scrolls are found in the chests of generated structures and, rarely, on hostile creatures. Strongholds, bastions, ancient cities and End cities hold the rarest.",
				"The more words you know, the less often a new scroll appears.",
				"Treatises unlock a whole kind of spell: Enchantment, Illusion, Charm, then Conjuration. They lie in the rarer vaults.",
				"The Wither, the Warden and the Ender Dragon each guard a word of their own."));
		s.put("Crafting a spell", List.of(
				"Pick the kind of spell, then fill its slots: click a slot, then click a word. Words that do not suit the spell are greyed and sorted to the bottom.",
				"Base: the element or force. Command: what it does. Intention: why. Use + to add a Charge, Source or Attribute.",
				"Crafting costs arcane essence: 5 for every two words. Discarding a spell returns half.",
				"Attack, Defense and the other combat spells are interpreted by the Oracle. The same words always give the same spell, for everyone.",
				"Exploration spells follow fixed laws: Base + Command + Intention decide the effect. Light + Envelop + Concealment is invisibility. Experiment.",
				"How many words a spell may hold depends on the best wand core you carry."));
		List<String> wands = new ArrayList<>();
		wands.add("Recipe: one plank, a core, and another plank of the same wood, in a vertical line in a crafting grid.");
		wands.add("A wand holds five spells. The spells stay on the wand, so a second wand is a second loadout. Your mana does not reset when you switch.");
		wands.add("WOODS");
		for (WandParts.Wood wood : WandParts.WOODS.values()) {
			StringBuilder sb = new StringBuilder(wood.label + ": " + wood.blurb);
			for (Map.Entry<ManaPool, Integer> e : wood.mana.entrySet()) sb.append(" +").append(e.getValue()).append("% ").append(e.getKey().label).append('.');
			if (wood.damage > 0) sb.append(" +").append((int) wood.damage).append("% spell damage.");
			wands.add(sb.toString());
		}
		wands.add("CORES");
		for (WandParts.Core core : WandParts.CORES.values()) {
			if (core.id.equals("nether_star")) continue; // boss cores are for you to discover
			wands.add(core.label + " (" + core.words + " words): " + core.blurb);
		}
		wands.add("Some cores come only from great foes. Those are not written here.");
		s.put("Wands", wands);
		s.put("Materials", List.of(
				"Every material has a nature. The same nature shows wherever the material is used, so what you learn here applies to anything made from it.",
				"Gold is the great vessel. Nothing holds or restores mana as readily, and nothing wears out as fast.",
				"Leather is soft and holds magic well.",
				"Copper conducts. Mana flows back through it quickly.",
				"Iron steadies. Shields and healing hold stronger, though it stores little mana itself.",
				"Chainmail is light and quick. Spells return sooner.",
				"Diamond is precision: a spell may strike twice as hard. It is too hard to hold mana.",
				"Netherite is overwhelming force. It holds no mana at all.",
				"Amethyst resonates with the arcane. Quartz carries light. Echo shards carry shadow, and quicken it.",
				"Emerald is life: healing and shields grow much stronger.",
				"Blaze rods are raw power. Breeze rods are swiftness: spells are ready again sooner.",
				"Ender pearls are instinct: sometimes a spell lands twice as hard.",
				"Flint is only flint. It works.",
				"WOODS",
				"Oak guards and reflects. Spruce guards and mends. Jungle mends above all. Dark oak controls and reflects. Mangrove reflects. Cherry phases and controls. Pale oak controls life and death. Acacia and bamboo lift everything a little. Birch gives no mana, only force.",
				"Crimson wood feeds fire. Warped wood feeds shadow.",
				"ENCHANTMENTS",
				"Reservoir enlarges every mana reserve. Wellspring refills them faster. Alacrity shortens the wait between casts. All three go on armor."));
		s.put("Mana", List.of(
				"You have five mana reserves. Each pays for one kind of magic and refills on its own.",
				"Barrier pays for shields. Restoration pays for healing. Control pays for stuns, charms, fear and confusion. Mirror pays for reflected damage. Phase pays for dodging, teleporting and vanishing.",
				"Attack spells cost no mana. They are limited by their cooldown: stronger spells take longer to be ready again. Some wand cores and armor shorten it.",
				"Your reserves grow with what you carry, not with a level. The wand's wood raises some of them. Gold and leather armor hold the most magic, chainmail and copper some, iron a little, diamond and netherite none.",
				"The Materials page says what each material is good for.",
				"Press H to show or hide the mana display."));
		s.put("Arcane essence", List.of(
				"Essence is what a new spell is made from. You only spend it when crafting.",
				"Draw it from an Arcane Well: use the well, wait for the surge (a chime and a burst of light), then use the well again before the surge fades. Too early or too late and it slips away.",
				"When your essence is full, hold a glass bottle at the well. Three catches fill it into an Arcane Potion, which restores essence anywhere. Potions stack to 16.",
				"Wells stand in arcane shrines across the world. One can also be built: stone bricks around an amethyst shard (top middle) and a water bucket (centre)."));
		s.put("Waypoints", List.of(
				"Waypoints come in pairs. Place both ends, then use either one to travel to the other.",
				"Recipe (makes a pair): an ender pearl on top; amethyst shard, stone bricks, amethyst shard in the middle row; three stone bricks along the bottom.",
				"Each pair you make takes the next of twenty colours, so you can tell your pairs apart.",
				"Sneak and use a placed waypoint to name the pair or change its colour. Both ends change together.",
				"Travel costs Phase mana: 1 within 500 blocks, 2 within 2,000, 3 within 8,000, 4 beyond. With no Phase mana left, an ender pearl pays instead.",
				"Crossing between worlds always takes an ender pearl.",
				"Breaking a waypoint returns it, still bonded to its partner. A lone waypoint from an odd stack has no partner and cannot be placed."));
		s.put("Potions", List.of(
				"Brewing needs an Alchemy Cauldron: a cauldron, a blaze rod and an amethyst shard. Like all brewing, it waits for the Nether.",
				"1. Charge it with blaze powder. Each powder is good for four brews.",
				"2. Pour in a liquid. Water is versatile. Honey heals. Milk soothes and cleanses. Lava destroys. Dragon's breath is the strongest and strangest.",
				"3. Add up to three ingredients. Almost anything gathered can be brewed: flowers, crops and fungi; bones, eyes, scales and tears; pearls, shards and rods; metals and gems.",
				"4. Add up to two catalysts if you wish. Lapis draws out an ingredient's nature. Honeycomb lets flora and fauna mix. Redstone makes it last. Fermented spider eye turns a brew into a weapon. Sugar makes a harsh brew safe to drink. Prismarine crystals cancel the heat.",
				"5. Heat matters. Fire, a campfire, lava or magma underneath is high heat. Soul fire, a soul campfire or ice is low heat. Anything else is room heat. Ingredients brewed at the wrong heat make an unstable potion.",
				"6. Use a glass bottle on the cauldron. Each brew fills three bottles.",
				"The same recipe at the same heat always makes the same potion. Gunpowder makes a potion for throwing. Glowstone dust makes potions that restore or drain mana.",
				"Use a potion to drink it. Harmful potions are thrown where you look instead. Potions stack to 16 and there is no limit on how many you carry.",
				"Empty hand on the cauldron shows what is inside. Sneak + empty hand tips it out."));
		s.put("Shrines and lairs", List.of(
				"Arcane shrines stand in the open across the world: a stone platform, four amethyst-crowned pillars, and an arcane well at the centre. Draw your essence there.",
				"Boss lairs are white domes of snow and ice, like great igloos, with a single sealed door.",
				"The door's material tells you how dangerous the lair is: hay, then wood, stone, iron, gold, diamond, and netherite for the worst of them. Break the door and the block is yours.",
				"Inside is a dark seal. Step close and the boss breaks out. It has a boss bar, an element it resists and one it is weak to, and tricks of its own.",
				"Each boss drops word scrolls, rarer for harder lairs. Your first victories also teach new kinds of spell: Enchantment, Illusion, Charm, then Conjuration.",
				"The Bosses tab keeps count.",
				"Razza is not in this world. When the Ender Dragon is dead, take a wand to its egg. The egg is not used up, so bring your friends."));
		s.put("Dark magic", List.of(
				"Spells made with forbidden words are dark magic. Each cast adds corruption.",
				"The higher your corruption, the more likely a dark spell fizzles or turns on you. It fades slowly with time."));
		s.put("Soul Bind", List.of(
				"Soul-bound items stay with you when you die, at a cost. Your starter bundle is soul-bound, and so is everything inside it.",
				"Books drop from endermen and lie in dungeon chests. Combine two of the same level in an anvil for the next level.",
				"Gear loses durability each time it is kept. A bundle, shulker box or ender chest loses one level instead."));
		return s;
	}
}
