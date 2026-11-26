package com.omnispell.boss;

import com.omnispell.item.OmniItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The seal at the heart of a boss lair. When a player comes close, the boss
 * for the lair's tier breaks out and the seal goes dark for good.
 */
public class BossAltarBlock extends Block {
	public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, 7);
	public static final BooleanProperty SPENT = BooleanProperty.create("spent");

	public BossAltarBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(TIER, 0).setValue(SPENT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(TIER, SPENT);
	}

	/** Called once a second for each player: wakes any unspent seal within a few blocks. */
	public static void wakeNear(ServerPlayer player) {
		if (player.isSpectator() || player.getAbilities().invulnerable && !player.getAbilities().instabuild) return;
		ServerLevel level = player.level();
		BlockPos center = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 3, 4))) {
			BlockState state = level.getBlockState(pos);
			if (!state.is(OmniItems.BOSS_ALTAR) || state.getValue(SPENT)) continue;
			int tier = state.getValue(TIER);
			List<BossDef> choices = BossDef.ofTier(tier);
			if (choices.isEmpty()) continue;
			// Which of the tier's bosses lives here is fixed by where the lair stands.
			BossDef def = choices.get(Math.floorMod(pos.getX() * 31 + pos.getZ() * 17, choices.size()));
			level.setBlockAndUpdate(pos.immutable(), state.setValue(SPENT, true));
			Bosses.spawn(level, Vec3.atBottomCenterOf(pos.above()), def);
			return;
		}
	}
}
