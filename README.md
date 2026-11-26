# Omnispell (Fabric, Minecraft 26.3)

Craft your own spells from words of power, in a normal Minecraft world. Based on the
Omnispell web game. The Soul Bind mod is built in: everything Soul Bind did still works,
and its description is kept below.

**Status: first milestone.** Wands, the Book of Words, word scrolls, spell crafting,
casting, mana, arcane essence and wells work. Waypoints, potions, runes, bosses,
structures and realms are not built yet.

## Install

Put `omnispell-<version>.jar` in your `mods` folder next to Fabric API. Everyone who
plays needs it (it adds screens and key binds). Remove the old `soulbind` jar: Omnispell
replaces it.

## How it plays

- A new player's Soul Bind bundle holds a **starter wand** and the **Book of Words**.
- Open the book (right-click it, or press `G`). On the **Craft** tab pick a kind of
  spell, click a slot, click a word. Craft costs arcane essence. Save the result and it
  goes on the wand you are holding.
- Hold the wand and press `Z X C V B` to cast its five spells, or right-click for the
  selected one. `H` shows or hides the mana display. All keys can be changed.
- **Combat spells** (attack, defense, enchantment, illusion, charm, conjuration) are
  written by the Omnispell website's spell generator, unchanged. The same words always
  give the same spell. Each new combination is requested once and then remembered.
- **Exploration spells** (bridges, teleports, invisibility and about forty other
  effects) come from Omnispell's fixed table: base + command + intention decide the effect.
- **Words** come from Word Scrolls in structure chests and, rarely, from hostile mobs.
  A scroll always teaches a word you do not know. The Wither, the Warden and the Ender
  Dragon each drop a scroll of their own.
- **Wands** are crafted: plank, core, plank of the same wood in a vertical line.
  The wood sets mana bonuses, the core sets how many words a spell may hold.
- **Mana** has five reserves (Barrier, Restoration, Control, Mirror, Phase). Their size
  comes from the wand's wood, your armor material, and the Reservoir and Wellspring
  enchantments.
- **Arcane essence** is drawn from an Arcane Well: use it, wait for the surge, use it
  again. With essence full and a glass bottle in hand, three catches make an Arcane Potion.

The book's Guide tab explains all of this in the game.

## Settings

`config/omnispell.json` is written on first run. It holds the generator's address and
the balance numbers (how long a "turn" is, damage scale, essence costs, scroll chances).
Set `useGenerator` to `false` to play without any internet connection: combat spells
are then made by simple built-in rules.

## Operator commands

`/omnispell kit`, `words all`, `word <name>`, `types`, `essence <n>`, `mana`,
`scroll <1-5>`, `wand <wood> <core>`, `reset`.

## Building and testing

`./gradlew build` makes the jar in `build/libs/` (JDK 25). `tools/run_client_test.sh`
starts a real game in a virtual display and plays through the mod, saving screenshots to
`build/run/clientGameTest/screenshots`. `python3 tools/gen_assets.py` regenerates the
textures, recipes and names.

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
