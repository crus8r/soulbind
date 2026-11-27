package com.omnispell.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks conjured by spells (bridges, walls). They replace only air and vanish
 * again, so a spell can never be used to make permanent building material.
 */
public final class TempBlocks {
	private TempBlocks() {
	}

	/** The block a conjured structure is made of, by element. All of these drop nothing when broken. */
	public static Block material(String element) {
		return switch (element == null ? "" : element) {
			case "fire" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.ORANGE);
			case "ice" -> Blocks.ICE;
			case "shadow" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.BLACK);
			case "light" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.YELLOW);
			case "stone" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.GRAY);
			case "wind" -> com.omnispell.item.OmniItems.HARDENED_AIR;
			case "water" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.LIGHT_BLUE);
			case "blood" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.RED);
			case "void" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.PURPLE);
			default -> Blocks.GLASS;
		};
	}

	/** Places a temporary block if the space is empty. Returns true if it was placed. */
	public static boolean place(ServerLevel level, BlockPos pos, Block block, int ticks) {
		BlockState existing = level.getBlockState(pos);
		if (!existing.isAir() && !existing.canBeReplaced()) return false;
		if (!level.getFluidState(pos).isEmpty() && !existing.isAir()) {
			// Covering water or lava is the point of a bridge, but only the surface.
		}
		BlockPos fixed = pos.immutable();
		BlockState state = block.defaultBlockState();
		level.setBlockAndUpdate(fixed, state);
		Tasks.later(ticks, () -> {
			if (level.getBlockState(fixed).is(block)) {
				level.setBlockAndUpdate(fixed, existing.getFluidState().isEmpty() ? Blocks.AIR.defaultBlockState() : existing);
				level.sendParticles(ParticleTypes.POOF, fixed.getX() + 0.5, fixed.getY() + 0.5, fixed.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
			}
		});
		return true;
	}

	/** True where water boils away: the Nether and anywhere else that hot. */
	public static boolean scorching(ServerLevel level, BlockPos pos) {
		return Boolean.TRUE.equals(level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES, pos));
	}

	/**
	 * Ice magic laid on lava where it is too hot to hold: the lava freezes over, then melts back
	 * to lava after ten seconds at the weakest and twenty at the strongest.
	 */
	public static boolean freezeLava(ServerLevel level, BlockPos pos, int tier) {
		BlockState state = level.getBlockState(pos);
		if (!state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) || !state.getFluidState().isSource()) return false;
		BlockPos fixed = pos.immutable();
		int ticks = 200 + 50 * (Math.max(1, Math.min(5, tier)) - 1);
		level.setBlockAndUpdate(fixed, Blocks.PACKED_ICE.defaultBlockState());
		level.sendParticles(ParticleTypes.SNOWFLAKE, fixed.getX() + 0.5, fixed.getY() + 1.0, fixed.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.01);
		Tasks.later(ticks - 40, () -> {
			if (level.getBlockState(fixed).is(Blocks.PACKED_ICE)) level.sendParticles(ParticleTypes.DRIPPING_LAVA, fixed.getX() + 0.5, fixed.getY() + 1.0, fixed.getZ() + 0.5, 3, 0.3, 0.0, 0.3, 0.0);
		});
		Tasks.later(ticks, () -> {
			if (!level.getBlockState(fixed).is(Blocks.PACKED_ICE)) return;
			level.setBlockAndUpdate(fixed, Blocks.LAVA.defaultBlockState());
			level.sendParticles(ParticleTypes.LAVA, fixed.getX() + 0.5, fixed.getY() + 1.0, fixed.getZ() + 0.5, 2, 0.2, 0.0, 0.2, 0.0);
		});
		return true;
	}

	/** Ice and snow: what the Nether's heat will not let stand. */
	public static boolean melts(Block block) {
		return block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE || block == Blocks.SNOW_BLOCK || block == Blocks.POWDER_SNOW;
	}

	/** How long ice holds in the heat: ten seconds at tier 1, twenty at tier 5. */
	public static int meltTicks(int tier) {
		return 200 + 50 * (Math.max(1, Math.min(5, tier)) - 1);
	}

	/** Sets ice where it will melt: after a while whatever was there before comes back, lava included. */
	public static void placeMelting(ServerLevel level, BlockPos pos, Block block, int tier) {
		BlockPos fixed = pos.immutable();
		BlockState before = level.getBlockState(fixed);
		level.setBlockAndUpdate(fixed, block.defaultBlockState());
		Tasks.later(meltTicks(tier), () -> {
			if (!level.getBlockState(fixed).is(block)) return;
			level.setBlockAndUpdate(fixed, before.canBeReplaced() && before.getFluidState().isEmpty() ? Blocks.AIR.defaultBlockState() : before);
			level.sendParticles(ParticleTypes.FALLING_WATER, fixed.getX() + 0.5, fixed.getY() + 0.8, fixed.getZ() + 0.5, 3, 0.3, 0.1, 0.3, 0.0);
		});
	}
}
