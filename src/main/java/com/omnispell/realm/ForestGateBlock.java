package com.omnispell.realm;

import com.omnispell.item.OmniItems;
import com.omnispell.spell.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The gate to the Enchanted Forest. Use it in the overworld to step into the
 * forest at the same place; a gate is left where you arrive, and using any gate
 * in the forest brings you back.
 */
public class ForestGateBlock extends Block {
	public ForestGateBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player user, BlockHitResult hit) {
		if (!(user instanceof ServerPlayer player) || !(world instanceof ServerLevel level)) return InteractionResult.SUCCESS;
		boolean inForest = level.dimension() == Realms.FOREST;
		if (!inForest && level.dimension() != Level.OVERWORLD) {
			player.sendOverlayMessage(Component.literal("The gate only answers under the open sky of the overworld.").withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		ServerLevel target = level.getServer().getLevel(inForest ? Level.OVERWORLD : Realms.FOREST);
		if (target == null) {
			player.sendOverlayMessage(Component.literal("The forest does not answer. (The realm is missing from this world.)").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		// Arrive at the same place, on the surface, with a gate under your feet to come back by.
		target.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
		BlockPos gate = existingGate(target, pos);
		if (gate == null) {
			int y = target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
			y = Math.max(target.getMinY() + 2, Math.min(y, target.getMaxY() - 4));
			gate = new BlockPos(pos.getX(), y, pos.getZ());
			// Never arrive in water or lava: stand the gate on a small platform if needed.
			if (!target.getFluidState(gate.below()).isEmpty() || target.getBlockState(gate.below()).isAir()) {
				for (BlockPos p : BlockPos.betweenClosed(gate.offset(-1, -1, -1), gate.offset(1, -1, 1))) target.setBlockAndUpdate(p.immutable(), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
			}
			target.setBlockAndUpdate(gate, OmniItems.FOREST_GATE.defaultBlockState());
			target.setBlockAndUpdate(gate.above(), Blocks.AIR.defaultBlockState());
			target.setBlockAndUpdate(gate.above(2), Blocks.AIR.defaultBlockState());
		}
		Vec3 from = Vec3.atCenterOf(pos);
		Fx.helix(level, from, 2.5, 0.8, 0xB48CFF, ParticleTypes.END_ROD);
		level.playSound(null, pos, SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 0.3f, 1.4f);
		player.teleportTo(target, gate.getX() + 0.5, gate.getY() + 1.0, gate.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
		player.fallDistance = 0;
		Fx.helix(target, Vec3.atCenterOf(gate), 2.5, 0.8, 0xB48CFF, ParticleTypes.END_ROD);
		player.sendOverlayMessage(Component.literal(inForest ? "The overworld" : "The Enchanted Forest").withStyle(ChatFormatting.LIGHT_PURPLE));
		return InteractionResult.SUCCESS;
	}

	private static BlockPos existingGate(ServerLevel level, BlockPos around) {
		for (BlockPos p : BlockPos.betweenClosed(around.getX() - 3, level.getMinY(), around.getZ() - 3, around.getX() + 3, level.getMaxY() - 1, around.getZ() + 3)) {
			if (level.getBlockState(p).is(OmniItems.FOREST_GATE)) return p.immutable();
		}
		return null;
	}
}
