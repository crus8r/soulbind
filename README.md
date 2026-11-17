# Soul Bind (NeoForge, Minecraft 26.3)

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
  first time they join.
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

This branch is the NeoForge version. The Fabric version lives on the `main` branch.

Pushing to this branch makes GitHub build the mod and publish the jar on the
Releases page. To build on your own computer, install JDK 25 and run
`gradlew build` (Windows) or `./gradlew build` (Mac/Linux). The jar appears in
`build/libs/`. Put it in your `mods` folder. NeoForge needs no extra library mod.
