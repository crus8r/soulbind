package com.omnispell.world;

import com.mojang.serialization.MapCodec;
import com.omnispell.Omnispell;
import com.omnispell.item.OmniItems;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Things to find in the Enchanted Forest: the ruins of the wizards who were
 * unmade, each with a chest, and the nests where familiars leave their eggs.
 */
public final class ForestFeatures {
	private ForestFeatures() {
	}

	private static void put(WorldGenLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, 2);
	}

	private static void chest(WorldGenLevel level, RandomSource random, BlockPos pos, String table) {
		put(level, pos, Blocks.CHEST.defaultBlockState());
		RandomizableContainer.setBlockEntityLootTable(level, random, pos, ResourceKey.create(Registries.LOOT_TABLE, Omnispell.id(table)));
	}

	/** Lowers a position onto solid ground, a few blocks at most. */
	private static BlockPos settle(WorldGenLevel level, BlockPos pos) {
		BlockPos.MutableBlockPos at = pos.mutable();
		for (int n = 0; n < 6 && level.getBlockState(at.below()).canBeReplaced(); n++) at.move(Direction.DOWN);
		for (int n = 0; n < 6 && !level.getBlockState(at).canBeReplaced(); n++) at.move(Direction.UP);
		return at.immutable();
	}

	/** A broken ring of mossy stone, four faewood pillars with lanterns, and a chest in the middle. */
	public record Ruin() implements Feature {
		public static final Ruin INSTANCE = new Ruin();
		public static final MapCodec<Ruin> CODEC = MapCodec.unit(INSTANCE);

		@Override
		public MapCodec<Ruin> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			BlockPos center = settle(level, origin);
			if (!level.getFluidState(center.below()).isEmpty()) return false;
			Block[] stone = {Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS};
			int r = 4;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					BlockPos foot = settle(level, center.offset(dx, 0, dz));
					if (Math.abs(foot.getY() - center.getY()) > 3) continue;
					if (d <= r + 0.3) put(level, foot.below(), (random.nextInt(3) == 0 ? Blocks.MOSS_BLOCK : stone[random.nextInt(stone.length)]).defaultBlockState());
					if (d > r - 0.7 && d <= r + 0.3 && random.nextInt(4) != 0 && Math.abs(dx) > 1 || d > r - 0.7 && d <= r + 0.3 && Math.abs(dz) > 1 && random.nextInt(4) != 0) {
						int height = 1 + random.nextInt(4);
						for (int h = 0; h < height; h++) put(level, foot.above(h), stone[random.nextInt(stone.length)].defaultBlockState());
					}
				}
			}
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					BlockPos foot = settle(level, center.offset(sx * 2, 0, sz * 2));
					int height = 3 + random.nextInt(2);
					for (int h = 0; h < height; h++) put(level, foot.above(h), OmniItems.FAEWOOD_LOG.defaultBlockState());
					put(level, foot.above(height), Blocks.LANTERN.defaultBlockState());
				}
			}
			BlockPos middle = settle(level, center);
			if (random.nextInt(4) == 0) {
				put(level, middle, OmniItems.ARCANE_WELL.defaultBlockState());
				chest(level, random, settle(level, middle.east()), "chests/forest_ruin");
			} else {
				chest(level, random, middle, "chests/forest_ruin");
			}
			if (random.nextInt(3) == 0) put(level, settle(level, middle.west()), OmniItems.RUNIC_BOOKSHELF.defaultBlockState());
			return true;
		}
	}

	/** A ring of faewood blossom around a bed of hay, with the eggs tucked into a barrel at its heart. */
	public record Nest() implements Feature {
		public static final Nest INSTANCE = new Nest();
		public static final MapCodec<Nest> CODEC = MapCodec.unit(INSTANCE);

		@Override
		public MapCodec<Nest> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			BlockPos center = settle(level, origin);
			if (!level.getFluidState(center.below()).isEmpty()) return false;
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > 3.3) continue;
					BlockPos foot = settle(level, center.offset(dx, 0, dz));
					if (Math.abs(foot.getY() - center.getY()) > 2) continue;
					if (d > 2.2) {
						put(level, foot, OmniItems.FAEWOOD_LEAVES.defaultBlockState());
						if (random.nextBoolean()) put(level, foot.above(), OmniItems.FAEWOOD_LEAVES.defaultBlockState());
					} else {
						put(level, foot.below(), (random.nextInt(3) == 0 ? Blocks.MOSS_BLOCK : Blocks.HAY_BLOCK).defaultBlockState());
					}
				}
			}
			BlockPos middle = settle(level, center);
			put(level, middle, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP));
			RandomizableContainer.setBlockEntityLootTable(level, random, middle, ResourceKey.create(Registries.LOOT_TABLE, Omnispell.id("chests/familiar_nest")));
			return true;
		}
	}

	public static void register() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Omnispell.id("forest_ruin"), Ruin.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Omnispell.id("familiar_nest"), Nest.CODEC);
		TagKey<Biome> forest = TagKey.create(Registries.BIOME, Omnispell.id("is_enchanted_forest"));
		for (String name : new String[]{"forest_ruin", "familiar_nest"}) {
			ResourceKey<PlacedFeature> feature = ResourceKey.create(Registries.PLACED_FEATURE, Omnispell.id(name));
			BiomeModifications.addFeature(context -> context.hasTag(forest), GenerationStep.Decoration.SURFACE_STRUCTURES, feature);
		}
	}
}
