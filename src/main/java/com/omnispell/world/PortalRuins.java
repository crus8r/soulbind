package com.omnispell.world;

import com.mojang.serialization.MapCodec;
import com.omnispell.Omnispell;
import com.omnispell.item.OmniItems;
import com.omnispell.item.WordScrollItem;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Ruins of the other gates, as the ruined portals are of the Nether's. A ruined
 * Forest Gate lies in old woods with honey pooled where lava would be, and its
 * gate has withered: it must be mended before it opens. A ruined Stellar portal
 * stands in the cold, its frame broken and partly raw Stellar Debris that must
 * be mined and worked before the portal can be finished.
 */
public final class PortalRuins {
	private PortalRuins() {
	}

	private static void put(WorldGenLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, 2);
	}

	private static BlockPos settle(WorldGenLevel level, BlockPos pos) {
		BlockPos.MutableBlockPos at = pos.mutable();
		for (int n = 0; n < 8 && level.getBlockState(at.below()).canBeReplaced(); n++) at.move(Direction.DOWN);
		for (int n = 0; n < 8 && !level.getBlockState(at).canBeReplaced(); n++) at.move(Direction.UP);
		return at.immutable();
	}

	private static boolean flatEnough(WorldGenLevel level, BlockPos center, int r) {
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int dx = -r; dx <= r; dx += r) {
			for (int dz = -r; dz <= r; dz += r) {
				int y = settle(level, center.offset(dx, 0, dz)).getY();
				lo = Math.min(lo, y);
				hi = Math.max(hi, y);
			}
		}
		return hi - lo <= 3 && level.getFluidState(center.below()).isEmpty();
	}

	/** A ruined Forest Gate: mossy stones, honey where lava would be, the withered gate, a chest. */
	public record ForestGateRuin() implements Feature {
		public static final ForestGateRuin INSTANCE = new ForestGateRuin();
		public static final MapCodec<ForestGateRuin> CODEC = MapCodec.unit(INSTANCE);

		@Override
		public MapCodec<ForestGateRuin> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			BlockPos center = settle(level, origin);
			if (!flatEnough(level, center, 4)) return false;
			Block[] stone = {Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSS_BLOCK, Blocks.MOSSY_COBBLESTONE, Blocks.CRACKED_STONE_BRICKS};
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > 4.3) continue;
					BlockPos foot = settle(level, center.offset(dx, 0, dz));
					if (Math.abs(foot.getY() - center.getY()) > 3) continue;
					if (random.nextInt(3) > 0) put(level, foot.below(), stone[random.nextInt(stone.length)].defaultBlockState());
					// Honey has gathered in hollows of the old floor, where a Nether ruin would have lava.
					if (d > 1.5 && d < 3.5 && random.nextInt(7) == 0) put(level, foot.below(), Blocks.HONEY_BLOCK.defaultBlockState());
					if (d > 3.3 && random.nextInt(3) == 0) {
						int h = 1 + random.nextInt(3);
						for (int k = 0; k < h; k++) put(level, foot.above(k), stone[random.nextInt(2)].defaultBlockState());
						if (random.nextBoolean()) put(level, foot.above(h), Blocks.AZALEA_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
					}
				}
			}
			BlockPos gate = settle(level, center);
			put(level, gate.below(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
			put(level, gate, OmniItems.WITHERED_GATE.defaultBlockState());
			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (random.nextInt(3) == 0) put(level, settle(level, gate.relative(side)), Blocks.FLOWERING_AZALEA.defaultBlockState());
			}
			BlockPos chest = settle(level, center.offset(2 + random.nextInt(2), 0, -1 + random.nextInt(3)));
			put(level, chest, Blocks.CHEST.defaultBlockState());
			RandomizableContainer.setBlockEntityLootTable(level, random, chest, ResourceKey.create(Registries.LOOT_TABLE, Omnispell.id("chests/ruined_forest_gate")));
			return true;
		}
	}

	/** A ruined Stellar portal: a broken frame, part of it raw debris, powder snow and ice where lava would be. Needs Aerial Hell. */
	public record SkyPortalRuin() implements Feature {
		public static final SkyPortalRuin INSTANCE = new SkyPortalRuin();
		public static final MapCodec<SkyPortalRuin> CODEC = MapCodec.unit(INSTANCE);

		@Override
		public MapCodec<SkyPortalRuin> codec() {
			return CODEC;
		}

		private static Block aerial(String path) {
			return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath("aerialhell", path)).orElse(null);
		}

		private static Item aerialItem(String path) {
			return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("aerialhell", path)).orElse(Items.AIR);
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			Block frame = aerial("stellar_portal_frame_block");
			Block debris = aerial("stellar_portal_frame_ore");
			if (frame == null || debris == null) return false; // only when the sky realm is installed
			BlockPos center = settle(level, origin);
			if (!flatEnough(level, center, 4)) return false;
			// The cold of the heights has come down with it: powder snow and ice where a Nether ruin would have lava.
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > 4.3) continue;
					BlockPos foot = settle(level, center.offset(dx, 0, dz));
					if (Math.abs(foot.getY() - center.getY()) > 3) continue;
					int roll = random.nextInt(10);
					if (roll < 3) put(level, foot.below(), Blocks.PACKED_ICE.defaultBlockState());
					else if (roll < 5 && d > 1.5) put(level, foot.below(), Blocks.POWDER_SNOW.defaultBlockState());
					else if (roll < 7) put(level, foot.below(), Blocks.SNOW_BLOCK.defaultBlockState());
					if (random.nextInt(4) == 0 && level.getBlockState(foot).isAir()) put(level, foot, Blocks.SNOW.defaultBlockState());
				}
			}
			// A portal frame like the Nether's, 4 wide and 5 high, standing on the snow; broken and partly unworked.
			BlockPos base = settle(level, center);
			boolean alongX = random.nextBoolean();
			Direction side = alongX ? Direction.EAST : Direction.SOUTH;
			int fallen = 0;
			for (int w = 0; w < 4; w++) {
				for (int h = 0; h < 5; h++) {
					boolean edge = w == 0 || w == 3 || h == 0 || h == 4;
					BlockPos pos = base.relative(side, w - 1).above(h);
					if (!edge) {
						put(level, pos, Blocks.AIR.defaultBlockState());
						continue;
					}
					int roll = random.nextInt(10);
					if (roll < 3 && h > 0) {
						fallen++; // a gap: the piece lies somewhere nearby, or is lost
						continue;
					}
					put(level, pos, (roll < 6 ? debris : frame).defaultBlockState());
				}
			}
			for (int n = 0; n < fallen / 2; n++) {
				put(level, settle(level, center.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3)), frame.defaultBlockState());
			}
			BlockPos chest = settle(level, center.offset(alongX ? 0 : 3, 0, alongX ? 3 : 0));
			put(level, chest, Blocks.CHEST.defaultBlockState());
			if (level.getBlockEntity(chest) instanceof Container box) {
				ItemStack[] goods = {new ItemStack(aerialItem("fluorite"), 1 + random.nextInt(3)), new ItemStack(aerialItem("ruby"), random.nextInt(3) == 0 ? 1 : 0),
						new ItemStack(Items.PACKED_ICE, 2 + random.nextInt(4)), new ItemStack(Items.FEATHER, 2 + random.nextInt(5)),
						new ItemStack(Items.AMETHYST_SHARD, 1 + random.nextInt(3)), random.nextInt(3) == 0 ? WordScrollItem.ofRarity(2 + random.nextInt(2)) : ItemStack.EMPTY};
				int slot = 0;
				for (ItemStack stack : goods) if (!stack.isEmpty() && stack.getCount() > 0) box.setItem((slot += 1 + random.nextInt(4)) % 27, stack);
			}
			return true;
		}
	}

	public static void register() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Omnispell.id("ruined_forest_gate"), ForestGateRuin.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Omnispell.id("ruined_sky_portal"), SkyPortalRuin.CODEC);
		TagKey<Biome> woods = TagKey.create(Registries.BIOME, Omnispell.id("has_ruined_forest_gate"));
		TagKey<Biome> cold = TagKey.create(Registries.BIOME, Omnispell.id("has_ruined_sky_portal"));
		BiomeModifications.addFeature(context -> context.hasTag(woods), GenerationStep.Decoration.SURFACE_STRUCTURES,
				ResourceKey.create(Registries.PLACED_FEATURE, Omnispell.id("ruined_forest_gate")));
		BiomeModifications.addFeature(context -> context.hasTag(cold), GenerationStep.Decoration.SURFACE_STRUCTURES,
				ResourceKey.create(Registries.PLACED_FEATURE, Omnispell.id("ruined_sky_portal")));
	}
}
