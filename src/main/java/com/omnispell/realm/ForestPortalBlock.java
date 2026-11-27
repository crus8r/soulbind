package com.omnispell.realm;

import com.omnispell.item.OmniItems;
import com.omnispell.spell.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The portal to the Enchanted Forest, made like a Nether portal: a frame of mossy stone, four
 * wide and five tall, woken with an ender pearl. Walk into it to cross; a portal is raised
 * where you arrive, and walking into that one brings you back.
 */
public class ForestPortalBlock extends Block {
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	private static final VoxelShape X_SHAPE = Block.box(0, 0, 6, 16, 16, 10);
	private static final VoxelShape Z_SHAPE = Block.box(6, 0, 0, 10, 16, 16);

	public ForestPortalBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
	}

	/** The stones a frame may be built of. */
	public static boolean frame(BlockState state) {
		return state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.MOSSY_STONE_BRICKS);
	}

	/** Break the frame and the portal goes out. */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		Direction.Axis axis = state.getValue(AXIS);
		boolean inPlane = direction.getAxis() == Direction.Axis.Y || direction.getAxis() == axis;
		if (inPlane && !neighbour.is(this) && !frame(neighbour)) return Blocks.AIR.defaultBlockState();
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	/**
	 * An ender pearl used on a frame block wakes the portal, if the block is part of a whole frame:
	 * four wide and five tall, like a Nether portal's, with the two-by-three inside it empty. Corners are not needed.
	 */
	public static boolean light(ServerLevel level, BlockPos clicked) {
		if (!frame(level.getBlockState(clicked))) return false;
		for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
			Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
			// every place the clicked block could sit in a frame: a = along the frame, h = up it; the inside is a 0..1, h 0..2
			for (int a = -1; a <= 2; a++) {
				for (int h = -1; h <= 3; h++) {
					boolean edgeA = a == -1 || a == 2, edgeH = h == -1 || h == 3;
					if (edgeA == edgeH) continue; // inside, or a corner
					BlockPos origin = clicked.relative(along, -a).above(-h);
					if (!whole(level, origin, along)) continue;
					BlockState portal = OmniItems.FOREST_PORTAL.defaultBlockState().setValue(AXIS, axis);
					for (int ia = 0; ia <= 1; ia++) for (int ih = 0; ih <= 2; ih++) level.setBlock(origin.relative(along, ia).above(ih), portal, 18);
					level.playSound(null, origin, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0f, 0.8f);
					level.playSound(null, origin, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.6f);
					Fx.helix(level, Vec3.atCenterOf(origin.above()), 2.5, 0.8, 0xB48CFF, ParticleTypes.END_ROD);
					return true;
				}
			}
		}
		return false;
	}

	private static boolean whole(ServerLevel level, BlockPos origin, Direction along) {
		for (int a = 0; a <= 1; a++) {
			if (!frame(level.getBlockState(origin.relative(along, a).below())) || !frame(level.getBlockState(origin.relative(along, a).above(3)))) return false;
			for (int h = 0; h <= 2; h++) if (!level.getBlockState(origin.relative(along, a).above(h)).canBeReplaced()) return false;
		}
		for (int h = 0; h <= 2; h++) {
			if (!frame(level.getBlockState(origin.relative(along, -1).above(h))) || !frame(level.getBlockState(origin.relative(along, 2).above(h)))) return false;
		}
		return true;
	}

	@Override
	protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
		if (!(entity instanceof ServerPlayer player) || !(world instanceof ServerLevel level)) return;
		if (player.isOnPortalCooldown()) {
			player.setPortalCooldown(); // standing in it does not send you back and forth
			return;
		}
		boolean inForest = level.dimension() == Realms.FOREST;
		if (!inForest && level.dimension() != Level.OVERWORLD) return;
		ServerLevel target = level.getServer().getLevel(inForest ? Level.OVERWORLD : Realms.FOREST);
		if (target == null) {
			player.sendOverlayMessage(Component.literal("The forest does not answer. (The realm is missing from this world.)").withStyle(ChatFormatting.RED));
			player.setPortalCooldown();
			return;
		}
		target.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
		BlockPos there = existing(target, pos);
		if (there == null) there = raise(target, pos, state.getValue(AXIS));
		level.playSound(null, pos, SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 0.3f, 1.4f);
		player.setPortalCooldown();
		player.teleportTo(target, there.getX() + 0.5, there.getY(), there.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
		player.setPortalCooldown();
		player.fallDistance = 0;
		Fx.helix(target, Vec3.atCenterOf(there), 2.5, 0.8, 0xB48CFF, ParticleTypes.END_ROD);
		player.sendOverlayMessage(Component.literal(inForest ? "The overworld" : "The Enchanted Forest").withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/** The foot of a portal already standing near here on the other side, if there is one. */
	private static BlockPos existing(ServerLevel level, BlockPos around) {
		BlockPos found = null;
		for (BlockPos p : BlockPos.betweenClosed(around.getX() - 4, level.getMinY(), around.getZ() - 4, around.getX() + 4, level.getMaxY() - 1, around.getZ() + 4)) {
			if (level.getBlockState(p).is(OmniItems.FOREST_PORTAL) && !level.getBlockState(p.below()).is(OmniItems.FOREST_PORTAL)) {
				found = p.immutable();
				break;
			}
		}
		return found;
	}

	/** Raises a portal on the surface on the other side, and returns the foot of it. */
	private static BlockPos raise(ServerLevel level, BlockPos at, Direction.Axis axis) {
		Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		y = Math.max(level.getMinY() + 3, Math.min(y, level.getMaxY() - 6));
		BlockPos origin = new BlockPos(at.getX(), y, at.getZ());
		BlockState stone = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
		// a floor to stand on either side, in case it opens over water or a drop
		for (int a = -1; a <= 2; a++) {
			for (int side = -1; side <= 1; side++) {
				BlockPos floor = origin.relative(along, a).relative(along.getClockWise(), side).below();
				if (side == 0 || level.getBlockState(floor).canBeReplaced()) level.setBlockAndUpdate(floor, stone);
				if (side != 0) for (int h = 0; h <= 2; h++) level.setBlockAndUpdate(floor.above(h + 1), Blocks.AIR.defaultBlockState());
			}
		}
		for (int a = -1; a <= 2; a++) {
			for (int h = 0; h <= 3; h++) {
				boolean inside = a >= 0 && a <= 1 && h <= 2;
				if (!inside) level.setBlockAndUpdate(origin.relative(along, a).above(h), stone);
			}
		}
		BlockState portal = OmniItems.FOREST_PORTAL.defaultBlockState().setValue(AXIS, axis);
		for (int a = 0; a <= 1; a++) for (int h = 0; h <= 2; h++) level.setBlock(origin.relative(along, a).above(h), portal, 18);
		return origin;
	}
}
