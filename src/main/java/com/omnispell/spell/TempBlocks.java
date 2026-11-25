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
			case "wind" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.WHITE);
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
}
