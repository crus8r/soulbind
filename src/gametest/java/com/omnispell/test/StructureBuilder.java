package com.omnispell.test;

import com.omnispell.Omnispell;
import com.omnispell.boss.BossAltarBlock;
import com.omnispell.boss.BossDef;
import com.omnispell.item.OmniItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;

/**
 * Builds Omnispell's structures block by block in a test world and saves them as
 * structure files. Run with OMNISPELL_BUILD_STRUCTURES=1; tools/build_structures.sh
 * copies the results into the mod's data folder. Editing a structure means editing
 * the code here, or opening the saved file in a structure block and re-saving it.
 */
public final class StructureBuilder {
	private StructureBuilder() {
	}

	private static void set(ServerLevel level, BlockPos pos, Block block) {
		level.setBlockAndUpdate(pos, block.defaultBlockState());
	}

	private static void chest(ServerLevel level, BlockPos pos, Direction facing, String lootTable) {
		level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
		if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest) {
			chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Omnispell.id(lootTable)));
		}
	}

	private static void save(ServerLevel level, BlockPos origin, Vec3i size, String name) {
		StructureTemplateManager manager = level.getStructureTemplateManager();
		Identifier id = Omnispell.id(name);
		StructureTemplate template = manager.getOrCreate(id);
		template.fillFromWorld(level, origin, size, false, List.of(Blocks.STRUCTURE_VOID));
		template.setAuthor("Omnispell");
		if (!manager.save(id)) throw new IllegalStateException("could not save structure " + name);
		System.out.println("[omnispell-test] saved structure " + name + " " + size);
	}

	private static Block brick(int x, int z) {
		int h = Math.floorMod(x * 7 + z * 13 + x * z, 9);
		return h == 0 ? Blocks.CRACKED_STONE_BRICKS : h <= 2 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS;
	}

	/** A small open shrine around an arcane well: 9 x 8 x 9. */
	public static void shrine(ServerLevel level, BlockPos o) {
		Vec3i size = new Vec3i(9, 8, 9);
		for (BlockPos p : BlockPos.betweenClosed(o, o.offset(8, 7, 8))) set(level, p.immutable(), Blocks.AIR);
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				boolean corner = (x == 0 || x == 8) && (z == 0 || z == 8);
				if (corner) continue;
				set(level, o.offset(x, 0, z), Blocks.COBBLESTONE);
				set(level, o.offset(x, 1, z), brick(x, z));
			}
		}
		// A ring of dark stone around the well, and the well itself.
		for (int x = 3; x <= 5; x++) for (int z = 3; z <= 5; z++) set(level, o.offset(x, 1, z), Blocks.POLISHED_DEEPSLATE);
		set(level, o.offset(4, 2, 4), OmniItems.ARCANE_WELL);
		// Four pillars crowned with amethyst.
		int[][] pillars = {{1, 1}, {7, 1}, {1, 7}, {7, 7}};
		for (int[] p : pillars) {
			set(level, o.offset(p[0], 2, p[1]), Blocks.CHISELED_STONE_BRICKS);
			set(level, o.offset(p[0], 3, p[1]), Blocks.STONE_BRICK_WALL);
			set(level, o.offset(p[0], 4, p[1]), Blocks.STONE_BRICK_WALL);
			set(level, o.offset(p[0], 5, p[1]), Blocks.CHISELED_STONE_BRICKS);
			set(level, o.offset(p[0], 6, p[1]), Blocks.AMETHYST_BLOCK);
			level.setBlockAndUpdate(o.offset(p[0], 7, p[1]), Blocks.AMETHYST_CLUSTER.defaultBlockState());
		}
		// Light, a little overgrowth, and an offering chest.
		set(level, o.offset(2, 2, 4), Blocks.SOUL_LANTERN);
		set(level, o.offset(6, 2, 4), Blocks.SOUL_LANTERN);
		set(level, o.offset(3, 2, 7), Blocks.MOSS_CARPET);
		set(level, o.offset(6, 2, 2), Blocks.MOSS_CARPET);
		set(level, o.offset(1, 2, 3), Blocks.MOSS_CARPET);
		chest(level, o.offset(4, 2, 7), Direction.NORTH, "chests/arcane_shrine");
		save(level, o, size, "arcane_shrine");
	}

	/** An igloo-like dome with a sealed door of the tier's material: 13 x 9 x 13. */
	public static void lair(ServerLevel level, BlockPos o, int tier) {
		Vec3i size = new Vec3i(13, 9, 13);
		for (BlockPos p : BlockPos.betweenClosed(o, o.offset(12, 8, 12))) set(level, p.immutable(), Blocks.STRUCTURE_VOID);
		double cx = 6, cy = 1, cz = 6;
		for (int x = 0; x < 13; x++) {
			for (int y = 0; y < 9; y++) {
				for (int z = 0; z < 13; z++) {
					double d = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz));
					BlockPos p = o.offset(x, y, z);
					if (y == 0) {
						if (d < 6.6) set(level, p, Blocks.DEEPSLATE_BRICKS);
					} else if (y == 1) {
						if (d < 5.5) set(level, p, Math.floorMod(x + z, 2) == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES);
						else if (d < 6.6) set(level, p, Blocks.SNOW_BLOCK);
					} else if (d >= 5.5 && d < 6.6) {
						set(level, p, Math.floorMod(x * 3 + y * 5 + z * 7, 6) == 0 ? Blocks.PACKED_ICE : Blocks.SNOW_BLOCK);
					} else if (d < 5.5) {
						set(level, p, Blocks.AIR);
					}
				}
			}
		}
		// The door: the tier's block below, a seal stone above. Breaking it yields the block.
		set(level, o.offset(6, 2, 0), BossDef.door(tier));
		set(level, o.offset(6, 3, 0), Blocks.CHISELED_DEEPSLATE);
		set(level, o.offset(6, 2, 1), Blocks.AIR);
		set(level, o.offset(6, 3, 1), Blocks.AIR);
		// The seal at the centre, lights, and the hoard behind it.
		level.setBlockAndUpdate(o.offset(6, 2, 6), OmniItems.BOSS_ALTAR.defaultBlockState().setValue(BossAltarBlock.TIER, tier));
		int[][] lights = {{3, 3}, {9, 3}, {3, 9}, {9, 9}};
		for (int[] l : lights) set(level, o.offset(l[0], 2, l[1]), Blocks.SOUL_LANTERN);
		chest(level, o.offset(6, 2, 10), Direction.NORTH, "chests/boss_lair_" + tier);
		save(level, o, size, "boss_lair_" + tier);
	}

	public static void buildAll(ServerLevel level, BlockPos near) {
		shrine(level, near.offset(10, 0, 10));
		for (int tier = 0; tier < 7; tier++) lair(level, near.offset(30 + tier * 16, 0, 10), tier);
	}
}
