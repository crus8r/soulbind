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
				"3. Hold the wand and press a spell key (Z X C V B by default) or right-click to cast the selected spell. Sneak + right-click steps to the next slot.",
				"Change the keys under Options > Controls > Omnispell."));
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
		s.put("Mana", List.of(
				"You have five mana reserves. Each pays for one kind of magic and refills on its own.",
				"Barrier pays for shields. Restoration pays for healing. Control pays for stuns, charms, fear and confusion. Mirror pays for reflected damage. Phase pays for dodging, teleporting and vanishing.",
				"Attack spells cost no mana. They are limited by their cooldown: stronger spells take longer to be ready again.",
				"Your reserves grow with what you carry, not with a level. The wand's wood raises some of them. Gold and leather armor hold the most magic, chainmail and copper some, iron a little, diamond and netherite none.",
				"The Reservoir enchantment (armor) enlarges every reserve. Wellspring (armor) refills them faster.",
				"Press H to show or hide the mana display."));
		s.put("Arcane essence", List.of(
				"Essence is what a new spell is made from. You only spend it when crafting.",
				"Draw it from an Arcane Well: use the well, wait for the surge (a chime and a burst of light), then use the well again before the surge fades. Too early or too late and it slips away.",
				"When your essence is full, hold a glass bottle at the well. Three catches fill it into an Arcane Potion, which restores essence anywhere. Potions stack to 16.",
				"Until wells are found in the world, one can be built: stone bricks around an amethyst shard (top middle) and a water bucket (centre)."));
		s.put("Waypoints", List.of(
				"Waypoints come in pairs. Place both ends, then use either one to travel to the other.",
				"Recipe (makes a pair): an ender pearl on top; amethyst shard, stone bricks, amethyst shard in the middle row; three stone bricks along the bottom.",
				"Each pair you make takes the next of twenty colours, so you can tell your pairs apart.",
				"Sneak and use a placed waypoint to name the pair or change its colour. Both ends change together.",
				"Travel costs Phase mana: 1 within 500 blocks, 2 within 2,000, 3 within 8,000, 4 beyond. With no Phase mana left, an ender pearl pays instead.",
				"Crossing between worlds always takes an ender pearl.",
				"Breaking a waypoint returns it, still bonded to its partner. A lone waypoint from an odd stack has no partner and cannot be placed."));
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
