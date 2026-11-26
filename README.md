# Omnispell (Fabric, Minecraft 26.3)

Craft your own spells from words of power, in a normal Minecraft world. Based on the
Omnispell web game. The Soul Bind mod is built in: everything Soul Bind did still works,
and its description is kept below.

**Status: playable, still growing.** Built: wands, the Book of Words, word scrolls,
spell crafting and casting, mana, arcane essence, waypoints, potion brewing, arcane
shrines and boss lairs that generate in the world, eleven lair bosses and Razza, and a
content pack of companion mods. Not built yet: runes and charms, familiars, the sky and
enchanted-forest realms, and villager trades for scrolls.

## Install

Put `omnispell-<version>.jar` in your `mods` folder next to Fabric API. Everyone who
plays needs it (it adds screens and key binds). Remove the old `soulbind` jar: Omnispell
replaces it.

## How it plays

- A new player's Soul Bind bundle holds a **starter wand** and the **Book of Words**.
- Open the book (right-click it, or press `G`). On the **Craft** tab pick a kind of
  spell, click a slot, click a word. Craft costs arcane essence. Save the result and it
  goes on the wand you are holding.
- Hold the wand: a bar of five squares appears above your health, each showing its key
  (`Z X C V B` at first). Press a key once to see the spell's name, again to cast. A grey
  cover drains off the square as the spell's cooldown runs. Keys are changed in the
  book's Spellbook tab, where double-tap can also be switched off.
- **Left-click** with the wand casts the selected spell; sneak + left-click steps to the
  next slot. If the spell is cooling or the slot is empty, the wand throws a weak arcane
  bolt for 5 Force mana. **Hold right-click** to raise the wand's ward: unseen until
  struck, it turns half of a hit aside, throws the attacker back and drains Barrier mana.
  Sprinting with a wand out is a third faster and spends Phase mana; **double-tap sprint
  in the air** to walk on air for a few seconds of Phase mana. Shift-click the wand in the
  inventory (or sneak + right-click) to carry it in the off hand. `H` shows or hides mana.
  Maxima and Minima are known from the start and do not count against the word limit.
- There are four kinds of spell: Attack, Defense, Environment and Conjuration.
- **Combat spells** (attack, defense, conjuration) are
  written by the Omnispell website's spell generator, unchanged. The same words always
  give the same spell. Each new combination is requested once and then remembered.
- **Environment spells** follow fixed laws: the command and the intention choose one of
  about forty effects (tunnels, craters, pits, moats, walls, domes, pillars, stairs,
  paths, mending, growth, trees, weather, lights, ore-finding, strikes from the sky,
  elemental fields, water breathing and other attunements, teleports, bridges...). The
  base word's element leaves its mark on everything the spell touches (fire scorches,
  ice freezes, water floods, void drags inward) and its tier sets the reach.
- **Words** come from Word Scrolls in structure chests, from librarians, clerics and
  wandering traders for emeralds, and, rarely, from hostile mobs.
  A scroll always teaches a word you do not know. The Wither, the Warden and the Ender
  Dragon each drop a scroll of their own.
- **Wands** are crafted: plank, core, plank of the same wood in a vertical line.
  The wood sets mana bonuses, the core sets how many words a spell may hold.
- **Mana** has five reserves: Barrier, Restoration, Spirit, Phase, Force. Attack spells of
  fire, ice, storm, stone and nature cost Force (as does the wand's bolt); those of shadow,
  light, void, blood and the arcane cost Spirit. A pip on each spell square shows its reserve. Their size
  comes from the wand's wood, your armor material, and the Reservoir and Wellspring
  enchantments.
- **Arcane essence** is drawn from an Arcane Well: use it, wait for the surge, use it
  again. With essence full and a glass bottle in hand, three catches make an Arcane Potion.
  Wells stand in **arcane shrines** that generate across the land.
- **Boss lairs** are igloo-like domes. The door's block shows the tier: hay, wood, stone,
  iron, gold, diamond, netherite. A sealed lair cannot be broken by hand: carve the door
  with an environment spell of high enough tier. Step near the seal inside and the boss wakes. Bosses
  drop word scrolls and unlock new kinds of spell. **Razza** is called by using a wand
  on the dragon egg after the Ender Dragon is dead.
- **Potions** are brewed in an Alchemy Cauldron (cauldron + blaze rod + amethyst shard):
  blaze powder to charge it, one of ten liquids (water, honey, milk, lava, dragon's breath
  and more), up to
  three ingredients and two catalysts, a heat source underneath, then a glass bottle.
  The Omnispell potioneer decides the result; a recipe always gives the same potion.
- An **Arcane Focus** in the off hand is the wizard's shield: 40% of each hit is paid from
  Barrier mana. The Book of Words is always kept on death.
- **Waypoints** are crafted in pairs. Place both and use either to travel to the other.
- **The Enchanted Forest** is Omnispell's own realm, reached by a craftable Forest Gate.
  Everything hostile there is half again as strong and scrolls found there are rarer.
  It is the only source of faewood, fae dust and familiar eggs, and holds ruins with
  chests and nests where eggs are found.
- **Charms**: enchant a gem (amethyst, lapis, quartz, prismarine, emerald, echo shard,
  diamond, netherite) at an enchanting table with a **Runic Bookshelf** (faewood planks
  and books) within two blocks. Each gem leans to certain charms; diamond and netherite
  usually take more than one. On an anvil, armor + charmed gem sets the charm into the
  armor. Hover over armor to see its charm slots; the Attunement enchantment adds slots.
- **Familiars** hatch from eggs found in the forest. The egg's name, shell colour and
  band show its leanings. Soak it (lava, powder snow or water cauldron, or an Arcane
  Well) to raise a stat, up to eight times, five minutes apart. Sneak-use it on the
  ground to hatch. The familiar follows and fights for you; sneak-use an empty hand on
  it to pocket it. Hold the egg or pocketed familiar in one hand and use a wand to read
  its stats.

The book's Guide and Lore tabs explain all of this in the game, including every custom
enchantment and the gate to each realm.

## Small comforts

A small x y z readout sits in the top-left corner; dark sight lets you make out caves and
nights without the Night Vision effect. Both are switched in the book's Spellbook tab.
Players cannot hurt each other unless `friendlyFire` is set to true in
`config/omnispell.json`. Spells can be nicknamed and archived in the Spellbook tab.

An **Arcanist** is a villager whose workstation is an Arcane Well: they sell word
scrolls of rising rarity, an Arcane Focus and charmed gems.

## Playing together

Everyone needs the same mods (the pack file is the easy way). The pack includes e4mc:
open your world with *Open to LAN* and it prints a public address your friends can join,
with no port forwarding and no server to run. Omnispell also runs on a dedicated Fabric
server.

## The content pack

`pack/mods.txt` lists the companion mods (structures, villages, terrain, biomes,
creatures, a sky dimension). `python3 tools/make_pack.py` turns it into
`pack/omnispell-pack.mrpack`, which the Modrinth app and Prism Launcher can install in
one step; each release on GitHub includes it. The pack only points at each mod's own
download. Mods that crashed with Minecraft 26.3 in testing are listed at the bottom of
`pack/mods.txt` with the reason. Omnispell works with or without the pack.

## Settings

`config/omnispell.json` is written on first run. It holds the generator's address and
the balance numbers (how long a "turn" is, damage scale, essence costs, scroll chances).
Set `useGenerator` to `false` to play without any internet connection: combat spells
are then made by simple built-in rules.

## Operator commands

`/omnispell kit`, `words all`, `word <name>`, `types`, `essence <n>`, `mana`,
`scroll <1-5>`, `wand <wood> <core>`, `reset`.

## Building and testing

`./gradlew build` makes the jar in `build/libs/` (JDK 25).

- `tools/run_client_test.sh` starts a real game in a virtual display and plays through
  the mod, saving screenshots to `build/run/clientGameTest/screenshots`.
- `OMNISPELL_WORLDGEN=1 tools/run_client_test.sh` checks that shrines and lairs generate
  in a normal world and that a lair's boss wakes; `OMNISPELL_WORLDGEN=survey` visits
  sixteen sites and reports how each sits in the terrain.
- `OMNISPELL_PACK_DIR=pack/mods` in front of either runs it with the whole content pack.
- `OMNISPELL_LIVE=1` adds one real request to the spell generator.
- `python3 tools/gen_assets.py` regenerates textures, recipes, names and world data.
- `tools/build_structures.sh` rebuilds the structure files from
  `src/gametest/java/com/omnispell/test/StructureBuilder.java`.

---

# Soul Bind

A balanced alternative to keepInventory. Keep weapons/tools/armor at the cost of some durability, and keep items by storing them in a bundle/shulker box/ender chest enchanted with Soul Bind.

## How it plays

Soul Bind has three levels. Books come from:

- Endermen killed by a player: 25% chance of a Soul Bind I book. Endermen in the End never drop it.
- Chests that hold enchanted books or ender pearls: about 11% contain a Soul Bind I
  book (dungeons, mineshafts, temples, strongholds, mansions, ancient cities, ocean
  ruins, outposts, bastions, trial chamber corridors).
- The enchanting table, rarely. Higher levels need a stronger table.

Using them:

- Books stack to 16, and only with books of the same level.
- Combine two books of the same level in an anvil to get the next level.
- Apply a book to an item in an anvil. Soul Bind I on a fresh item costs 2 levels;
  higher levels and heavily worked items cost more.
- Works on anything with durability (tools, weapons, armor, elytra, shields, bows...),
  plus bundles, shulker boxes and ender chests.

When you die, soul-bound items stay in the same slots:

| Level | Tools, weapons, armor lose | Each worn armor piece saves |
|-------|----------------------------|-----------------------------|
| I     | 20% of max durability      | no XP                       |
| II    | 12% of max durability      | 10% of your XP              |
| III   | 6% of max durability       | 20% of your XP              |

- A full set of Soul Bind III armor keeps 80% of your XP.
- If an item is too worn to pay, it drops like normal. Repair it to keep it safe.
- Bundles, shulker boxes and ender chests keep their contents and lose one level:
  III, then II, then I, then unenchanted.
- A placed shulker box (or ender chest) keeps its bind and gets it back when broken.
- A grindstone removes Soul Bind and gives back very little XP.

## Added in 1.1.0

- **Soul Bind IV and V** exist only on bundles, shulker boxes and ender chests. Books
  from drops, chests and the enchanting table stop at III; combine in an anvil to go
  higher. Tools, weapons and armor are capped at III.
- **Unbreaking on containers.** Bundles, shulker boxes and ender chests can take
  Unbreaking. Each death it gives a chance to keep the Soul Bind level instead of
  losing one: I = 50%, II = 67%, III = 75%.
- **Enchanted bundles.** About 3% of the chests that can hold a Soul Bind book also
  hold a bundle with Soul Bind or Unbreaking (I-III).
- **Starter bundle.** Every player gets one Soul Bind II + Unbreaking II bundle the
  first time they join. With Omnispell it holds a starter wand and the Book of Words.
- **Wayfinder.** A common, cheap enchantment for helmets and filled maps. Sneak while
  holding a map and a short dotted trail points toward the map's target (its marked
  X or icon, or its center if it has none).
  - On a helmet it works with any map and costs 1 durability per second while showing.
  - On a map it is free, and is used up when you arrive at the target.

## Tuning

All balance numbers are constants at the top of
`src/main/java/com/soulbind/SoulBind.java`. The anvil price is `anvil_cost` in
`src/main/resources/data/soulbind/enchantment/soul_bind.json` (books pay half).

## Building

Easiest: push this folder to a GitHub repository. GitHub builds the mod by itself
and puts the finished jar on the repository's Releases page.

On your own computer: install JDK 25, then run `gradlew build` (Windows) or
`./gradlew build` (Mac/Linux) in this folder. The jar appears in `build/libs/`.

Put the jar in your `mods` folder next to Fabric API.
