package com.omnispell.realm;

import com.omnispell.item.OmniItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A Forest Gate gone dormant, found in the ruins of old gates. It will not open
 * until it is mended with what a new gate is made from: an emerald, an ender
 * pearl and glow berries, in any order. Then it wakes as a Forest Gate.
 */
public class WitheredGateBlock extends Block {
	public static final BooleanProperty EMERALD = BooleanProperty.create("emerald");
	public static final BooleanProperty PEARL = BooleanProperty.create("pearl");
	public static final BooleanProperty BERRIES = BooleanProperty.create("berries");

	public WitheredGateBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(EMERALD, false).setValue(PEARL, false).setValue(BERRIES, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(EMERALD, PEARL, BERRIES);
	}

	private static BooleanProperty need(ItemStack stack) {
		if (stack.is(Items.EMERALD)) return EMERALD;
		if (stack.is(Items.ENDER_PEARL)) return PEARL;
		if (stack.is(Items.GLOW_BERRIES)) return BERRIES;
		return null;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		BooleanProperty part = need(stack);
		if (part == null || state.getValue(part)) return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
		if (!player.getAbilities().instabuild) stack.shrink(1);
		BlockState mended = state.setValue(part, true);
		server.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 10, 0.3, 0.2, 0.3, 0.0);
		if (mended.getValue(EMERALD) && mended.getValue(PEARL) && mended.getValue(BERRIES)) {
			level.setBlockAndUpdate(pos, OmniItems.FOREST_GATE.defaultBlockState());
			server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 40, 0.4, 0.6, 0.4, 0.05);
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2f, 0.8f);
			player.sendOverlayMessage(Component.literal("The gate wakes. The Enchanted Forest is open to you.").withStyle(ChatFormatting.GREEN));
		} else {
			level.setBlockAndUpdate(pos, mended);
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.0f);
			player.sendOverlayMessage(Component.literal("The gate stirs. Still missing: " + missing(mended)));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) player.sendOverlayMessage(Component.literal("A withered gate. To wake it, it needs: " + missing(state)));
		return InteractionResult.SUCCESS;
	}

	private static String missing(BlockState state) {
		java.util.List<String> left = new java.util.ArrayList<>();
		if (!state.getValue(EMERALD)) left.add("an emerald");
		if (!state.getValue(PEARL)) left.add("an ender pearl");
		if (!state.getValue(BERRIES)) left.add("glow berries");
		return String.join(", ", left) + ".";
	}

}
