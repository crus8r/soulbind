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
				"Press a spell's key once to select it and see its name. Press it again quickly to cast. Press the key of the selected spell again later and it is deselected: with no spell selected, left-click is the plain bolt.",
				" A grey cover on the square drains away as the spell becomes ready again; stronger spells take longer.",
				"Left-click with the wand casts the spell you last selected. Sneak + left-click steps to the next slot. If that spell is still cooling, or the slot is empty, the wand throws a plain arcane bolt instead. A bolt is weak and costs 5 Force mana, so it is a stopgap and not a weapon to lean on.",
				"Hold right-click to raise the wand's ward. It cannot be seen until something strikes it; then it flashes, turns half the blow aside and throws the attacker back hard. It drains Barrier mana while it is up and more with each hit.",
				"Sprint (Ctrl) with a wand in hand and you run a third faster, spending Phase mana as you go.",
				"In the air with a wand in hand, double-tap sprint to walk on air: the air hardens under your feet until your Phase mana runs out, a few seconds at most. It forgives most of a fall, like hay, but not all of it. Double-tap again or sneak to let go.",
				"To carry the wand in your off hand, shift-click it in your inventory as you would a shield, or sneak + right-click while holding it.",
				"You know Maxima and Minima from the start. Every spell has a Charge slot on the Craft tab: click it, then pick Maxima for a stronger spell a tier higher, or Minima for a smaller, more exact one a tier lower. A charge does not count against your wand's word limit.",
				"Change the keys in the Spellbook tab: click the key under a slot, then press the key you want. Double-tap can be switched off there, so one press casts."));
		s.put("Words of power", List.of(
				"Every spell is made of words. You begin with a handful and must find the rest.",
				"Word Scrolls teach one word you do not know yet, so a scroll is never wasted. Rarer scrolls hold rarer words.",
				"Librarians sell word scrolls for emeralds, a rarer kind at each level of their trade, and clerics and wandering traders sometimes do too.",
				"Scrolls are found in the chests of generated structures and, rarely, on hostile creatures. Strongholds, bastions, ancient cities and End cities hold the rarest.",
				"The more words you know, the less often a new scroll appears.",
				"There are four kinds of spell: Attack, Defense, Environment and Conjuration. You begin with the first three. A treatise in the rarer vaults, or your first boss, teaches Conjuration.",
				"The Wither, the Warden and the Ender Dragon each guard a word of their own.",
				"In creative mode every word and every kind of spell is open, crafting is free, and the Omnispell tab holds a scroll for each word."));
		s.put("Crafting a spell", List.of(
				"Pick the kind of spell, then fill its slots: click a slot, then click a word. Words that do not suit the spell are greyed and sorted to the bottom.",
				"Base: the element or force. Command: what it does. Intention: why. Use + to add a Charge, Source or Attribute.",
				"Crafting costs arcane essence: 5 for every two words. Discarding a spell returns half.",
				"Attack, Defense and Conjuration spells are interpreted by the Oracle. The same words always give the same spell, for everyone.",
				"Environment spells follow fixed laws: Base + Command + Intention decide the effect. Light + Envelop + Concealment is invisibility. Experiment.",
				"UNLOCKING CONJURATION",
				"Conjuration calls creatures and things to fight beside you. You do not begin with it. There are three ways to learn it.",
				"1. Defeat any lair boss past the first tier (a wooden door or better). Your first such victory teaches it at once.",
				"2. Find a Treatise on Conjuration. They lie, about one chest in ten, in the rarer vaults: strongholds, bastions, fortresses, woodland mansions, trial chambers, ancient cities, End cities, the harder boss lairs and the ruins of the Enchanted Forest. Read it like a scroll.",
				"3. In creative mode it is simply open.",
				"Once learned, pick Conjuration on the Craft tab. Summon, Invoke and Awaken with Creation make allies; an Attribute word such as Wolf or Bear shapes what answers.",
				"SPARSUS",
				"A rare charge word, found on Ancient scrolls. Put it in a spell's Charge slot and the spell spreads: an attack or affliction also washes over everything within a few blocks of its target at a little over half strength, and an environment spell reaches one tier further.",
				"Kills made with fire magic leave their meat already cooked.",
				"WHAT SPELLS COST",
				"Attack spells cost Force mana: 7 for a tier 1 spell, 3 more per tier, less with Minima. The wand's plain bolt costs 5. Shields draw on Barrier, healing on Restoration, holds and stuns on Control, reflection on Mirror, and vanishing and stepping through space on Phase. The coloured pip on a spell's square shows which reserve it uses, and that reserve's bar flashes when it is drawn on.",
				"ENVIRONMENT SPELLS",
				"The command and the intention together choose the effect; the base word colours it. There are ten kinds of command and six kinds of intention, so sixty pairings. The spell's description always says what it will do.",
				"Harsh intentions (Malice, Banishment, Weakening, Nullification) break things: carve a tunnel, drill a shaft, blast a crater, open a pit, dig a moat, flatten the land, drive off water and snow, wither plants away, transmute blocks, or call a strike from the sky.",
				"Guarding intentions (Warding, Fortitude, Restoration, Preservation, Soothing) build and tend: wall, ring wall, dome, platform under your feet, mend the land, quicken growth, calm the weather, or attune yourself to your element (water breathing for Water, fire immunity for Fire, slow falling for Air, and so on).",
				"Shaping intentions (Binding, Transformation, Empowerment, Creation) work the world: harden water to ice, change the weather, gather loose items, conjure a spring or a mound, lay a path, raise a tree, shape a golem, or lay a field of your element on the ground.",
				"Travelling intentions (Liberation, Transportation) move you: a launch straight up, a leap forward, bridge, stairs, pillar, teleport, grapple, dash, phase through a wall, a steed.",
				"Insight shows things: living creatures, ores, hidden chests and spawners, the way to a village, a waymark to find your way back, lasting lights. Concealment hides you or fools others: invisibility, smoke, decoys, a thicket of leaves, a lull that makes hunters forget you.",
				"Every one of the sixty pairings does something different.",
				"THE ELEMENT'S MARK",
				"Whatever the spell does, the base word's element leaves its mark on the ground and on any creature caught in it. Fire scorches, leaves flames and fuses sand to glass. Ice freezes water and settles snow. Water floods pits and moats. Wind throws things outward. Light leaves lasting lights. Shadow puts out torches and blinds. Void drags things inward. Blood withers and heals you. Stone slows.",
				"A strike takes the element's shape too: true lightning for Lightning and Thunder, purple lightning for Void, a pillar of flame for Fire, a lance of frost for Ice.",
				"MATERIAL AND REACH",
				"The base word is also what you build with. Earth builds with dirt and leaves grass behind; Sand, Mud, Ice, Wood, Vine and Lava build with their own stuff, and stone-like words with cobblestone. These stay. Stranger words conjure hardened magic in their own colour that fades after a minute or so.",
				"The base word's tier is the spell's reach: a tier 1 tunnel is 5 blocks long, a tier 5 tunnel 22. Maxima adds a tier, Minima takes one away. Breaking spells leave ores standing and drop nothing; harder rock needs a higher tier, and obsidian only yields to tier 5.",
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
				"Brewing uses an Alchemy Cauldron, not a brewing stand: a cauldron, a blaze rod and an amethyst shard. Like all brewing, it waits for the Nether.",
				"1. Charge it with blaze powder. Each powder is good for four brews.",
				"2. Pour in a liquid. It decides the potion's character.",
				"3. Add up to three ingredients. Almost anything gathered can be brewed.",
				"4. Add up to two catalysts if the brew needs them.",
				"5. Mind the heat under the cauldron.",
				"6. Use a glass bottle on the cauldron. Each brew fills three bottles.",
				"Empty hand on the cauldron shows what is inside and how steady it is. Sneak + empty hand tips it out.",
				"LIQUIDS",
				"Water is versatile. Honey heals. Milk soothes and cleanses. Powder snow (Frostmelt) is cold and still. Mushroom stew (Fungal Broth) is earthy; rabbit stew or beetroot soup (Hunter's Stock) is hearty and hot.",
				"Bottled experience (Liquid Memory) is arcane and a little unruly. Lava destroys. Dragon's breath and ominous bottles are the strongest and the most dangerous to brew with.",
				"CATALYSTS",
				"Honeycomb is the harmonizer: it lets flora and fauna mix and steadies a volatile brew. Lapis draws out an ingredient's nature. Redstone makes it last. Fermented spider eye turns a brew into a weapon. Sugar makes a harsh liquid safe. Prismarine crystals cancel the heat.",
				"Catalysts act on ingredients. They are not ingredients themselves.",
				"HEAT",
				"Fire, a campfire, lava or magma underneath is high heat. Soul fire, a soul campfire or ice is low heat. Anything else is room heat. Many ingredients prefer one heat.",
				"WAYS TO RUIN A BREW",
				"Sludge: catalysts with nothing to act on, the same ingredient three times, or more catalyst than substance. A liquid and a pile of lapis is sludge.",
				"Boiling over: wrong heat, flora mixed raw with fauna, very potent ingredients, a dangerous liquid, a weaponizer. Each adds volatility. Too much without honeycomb and the cauldron bursts, the brew is lost, and you are burned.",
				"An uneasy brew still works, but comes out with a side effect.",
				"The same recipe at the same heat always makes the same potion. Gunpowder makes a potion for throwing. Glowstone dust makes potions that restore or drain mana.",
				"Use a potion to drink it. Harmful potions are thrown where you look instead. Potions stack to 16 and there is no limit on how many you carry."));
		s.put("Recipes", List.of(
				"Rows are read top to bottom, left to right. A dash is an empty space.",
				"WAND",
				"Plank / core / plank, in one column, both planks the same wood. Any of twelve woods; the cores are listed under Materials. The wood sets mana, the core sets how many words a spell may hold.",
				"BOOK OF WORDS",
				"Book + amethyst shard + lapis lazuli, anywhere in the grid. The book also stays with you when you die.",
				"WAYPOINTS (makes a bonded pair)",
				"-  ender pearl  -   /   amethyst shard  stone bricks  amethyst shard   /   stone bricks x3",
				"ARCANE WELL",
				"stone bricks  amethyst shard  stone bricks   /   stone bricks  water bucket  stone bricks   /   stone bricks x3",
				"ALCHEMY CAULDRON",
				"Cauldron + blaze rod + amethyst shard, anywhere in the grid. Abandoned ones also stand over old campfires in swamps, dark forests, mushroom fields and the Enchanted Forest.",
				"ARCANE FOCUS",
				"-  gold ingot  -   /   amethyst shard  lapis lazuli  amethyst shard   /   -  gold ingot  -. Held in the off hand it is a wizard's shield: 40% of every hit is paid from your Barrier mana instead of your health.",
				"FOREST GATE",
				"moss block  emerald  moss block   /   amethyst shard  ender pearl  amethyst shard   /   moss block  glow berries  moss block",
				"FAEWOOD PLANKS",
				"One faewood log makes four.",
				"RUNIC BOOKSHELF",
				"faewood planks x3   /   book x3   /   faewood planks x3. Stand it within two blocks of an enchanting table to charm gems.",
				"FAMILIAR EGGS",
				"Not crafted: found in faewood blossom or on what hunts in the Enchanted Forest. Soak in a lava, powder snow or water cauldron, or an Arcane Well. See Familiars under Lore.",
				"CHARMED ARMOR",
				"Anvil: armor on the left, an enchanted gem on the right."));
		s.put("Shrines and lairs", List.of(
				"Arcane shrines stand in the open across the world: a stone platform, four amethyst-crowned pillars, and an arcane well at the centre. Draw your essence there.",
				"Boss lairs are white domes of snow and ice, like great igloos, with a single sealed door.",
				"The door's material tells you how dangerous the lair is: hay, then wood, stone, iron, gold, diamond, and netherite for the worst of them. A sealed lair cannot be broken into by hand. Carve or blast the door with an environment spell: tier 1 opens hay and wood, tier 2 stone, tier 3 iron and gold, tier 4 diamond, tier 5 netherite. Once the boss wakes the seal is gone and the lair is ordinary blocks again.",
				"Inside is a dark seal. Step close and the boss breaks out. It has a boss bar, an element it resists and one it is weak to, and tricks of its own.",
				"Each boss drops word scrolls, rarer for harder lairs. Your first victory also teaches Conjuration.",
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

	/** The Lore tab: the deeper systems, and a list of every enchantment Omnispell adds. */
	public static Map<String, List<String>> lore() {
		Map<String, List<String>> s = new LinkedHashMap<>();
		s.put("Enchantments", List.of(
				"These join the vanilla enchantments. All but the charm enchantments can come from an ordinary enchanting table.",
				"SOUL BIND",
				"Soul Bind I-III. The item stays with you when you die, at a cost in durability or levels. Books drop from endermen and lie in dungeon chests.",
				"WAYFINDER",
				"Wayfinder, on helmets and filled maps. Sneak while holding a map and a dotted trail points to the map's target. On a helmet it works with any map and costs durability; on a map it is free and is used up on arrival.",
				"RESERVOIR",
				"Reservoir I-III, on armor. +10% to every mana reserve per level, per piece.",
				"WELLSPRING",
				"Wellspring I-III, on armor. +15% mana refill speed per level, per piece.",
				"ALACRITY",
				"Alacrity I-III, on armor. -4% spell cooldown per level, per piece.",
				"ATTUNEMENT",
				"Attunement I-II, on armor. One more charm slot per level. Every piece of armor has one slot without it."));
		List<String> charms = new ArrayList<>();
		charms.add("Charm enchantments only ever settle on gems. Each has three levels. However much you wear, one charm never counts for more than " + com.omnispell.charm.Charms.CAP + " levels.");
		for (com.omnispell.charm.Charms.Charm charm : com.omnispell.charm.Charms.ALL.values()) {
			charms.add(charm.name() + ": " + charm.effect() + ".");
		}
		s.put("Charm list", charms);
		List<String> making = new ArrayList<>(List.of(
				"A charm is an enchanted gem set into a piece of armor.",
				"1. Bring faewood home from the Enchanted Forest and build a Runic Bookshelf: faewood planks above and below three books.",
				"2. Stand it within two blocks of an enchanting table. Without one, the table offers a gem nothing. It also counts as a bookshelf.",
				"3. Enchant a gem as you would a tool: one gem, lapis, levels.",
				"4. On an anvil, put armor on the left and the enchanted gem on the right. The charm is set into the armor.",
				"Hover over any armor to see its charm slots and what is in them. If every slot is full, a new charm pushes out the oldest.",
				"GEMS",
				"Each gem leans towards certain charms, though any gem can take any charm. Rarer gems often take two charms at once, and sometimes three."));
		for (Map.Entry<net.minecraft.world.item.Item, com.omnispell.charm.Charms.Gem> gem : com.omnispell.charm.Charms.GEMS.entrySet()) {
			StringBuilder line = new StringBuilder(gem.getValue().name() + " leans to ");
			List<String> favoured = gem.getValue().favoured();
			for (int i = 0; i < favoured.size(); i++) {
				line.append(i == 0 ? "" : i == favoured.size() - 1 ? " and " : ", ").append(com.omnispell.charm.Charms.ALL.get(favoured.get(i)).name());
			}
			int second = (int) Math.round(gem.getValue().second() * 100);
			line.append(second >= 50 ? ". Usually takes more than one." : second >= 20 ? ". Sometimes takes two." : ".");
			making.add(line.toString());
		}
		s.put("Charms", making);
		List<String> familiars = new ArrayList<>(List.of(
				"Familiars hatch from eggs found in the Enchanted Forest: hidden in faewood blossom, or carried by the things that hunt there.",
				"READING AN EGG",
				"The name tells you the creature and its leaning: a Strong Phoenix Egg, a Blank Griffin Egg. The shell's colour is its primary stat and the band around it is its secondary. A blank egg is pale.",
				"Red is Power: it hits harder. Green is Vitality: more health. Yellow is Swiftness: it moves and strikes faster. Blue is Spirit: your mana refills faster while it is out.",
				"Hold the egg in one hand and use your wand with the other to read its stats exactly.",
				"SOAKING",
				"Use the egg on a lava cauldron for Power, a powder snow cauldron for Vitality, a water cauldron for Swiftness, or an Arcane Well for Spirit.",
				"Each soak adds 2 to that stat and uses up the cauldron. The shell needs five minutes between soaks, and takes eight in all.",
				"HATCHING",
				"Sneak and use the egg on the ground. You need not finish soaking: soaks never taken go to its highest stat. An egg never soaked at all splits them 60/40 between its primary and secondary.",
				"LIVING WITH ONE",
				"A familiar follows you, fights what you fight and what hurts you, and cannot be hurt by you. Use a wand on it to see its stats.",
				"Sneak and use an empty hand on it to put it in your pocket; use the pocketed familiar on the ground to let it out. It pockets itself if you leave the realm without it.",
				"If it falls it returns to your pocket and must rest five minutes. You can keep many, but only one walks beside you.",
				"KINDS"));
		for (com.omnispell.familiar.Familiars.Species species : com.omnispell.familiar.Familiars.SPECIES.values()) {
			familiars.add(species.name() + ": gifted in " + species.gift().label + ".");
		}
		s.put("Familiars", familiars);
		s.put("Realms and gates", List.of(
				"Each realm opens a part of the craft, and the further you go the rarer the word scrolls you find there.",
				"THE NETHER",
				"Gate: a frame of obsidian, four wide and five tall, lit with flint and steel.",
				"Opens potions: the Alchemy Cauldron needs a blaze rod, and nether wart, blaze powder, magma cream and ghast tears are strong ingredients. Crimson and warped wood and quartz make wands and charms. Scrolls found here are one step rarer.",
				"THE ENCHANTED FOREST",
				"Gate: craft a Forest Gate. Moss block, emerald, moss block; amethyst shard, ender pearl, amethyst shard; moss block, glow berries, moss block. Set it down and use it. A gate back is made where you arrive.",
				"Opens charms and familiars: faewood for the Runic Bookshelf, familiar eggs in the blossom, fae dust from what you slay. Everything hostile there is half again as strong. Scrolls are two steps rarer.",
				"THE AERIAL HELL",
				"Gate: look for a ruined sky portal in the overworld; its chest often holds a Stellar Lighter. Or mine Stellar Debris, smelt it into Stellar Portal Bricks, craft four into a Stellar Portal Frame, build a frame like a nether portal, and light the inside with a Stellar Lighter (fluorite and ruby).",
				"A realm of floating islands, dungeons and bosses of its own. Scrolls are two steps rarer.",
				"THE END",
				"Gate: find a stronghold with eyes of ender (blaze powder and an ender pearl) and fill the portal's frame with them.",
				"The Ender Dragon guards a word, and its egg is how Razza is called. End cities hold the rarest scrolls, and dragon's breath is the strongest liquid to brew with."));
		s.put("The world", List.of(
				"Razza did not kill the wizards who came before. He unmade them, word by word, and scattered the words so no one could speak against him again.",
				"That is why words lie in chests and in the pockets of monsters, why the old shrines still hold wells of essence, and why the worst of his servants sit sealed in domes of ice with a door that tells you how afraid to be.",
				"The Enchanted Forest is where the unmade went. The familiars there were theirs. The faewood remembers them, which is why only its shelves can teach a gem to hold a charm.",
				"You are what they left. Gather the words. Say his name back to him."));
		return s;
	}
}
