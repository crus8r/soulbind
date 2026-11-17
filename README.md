# Soul Bind (Fabric, Minecraft 26.3)

A balanced alternative to keepInventory. No setup or config.

## How it plays

Soul Bind has three levels. Books come from:

- Endermen killed by a player: 25% chance of a Soul Bind I book (half as often as a
  pearl). Endermen in the End never drop it.
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
