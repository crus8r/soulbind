package com.omnispell.waypoint;

import com.omnispell.net.OmniNet;
import com.omnispell.player.Mana;
import com.omnispell.player.ManaPool;
import com.omnispell.player.PlayerStore;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One end of a waypoint pair. Use it to travel to the other end. Sneak and use
 * it to rename the pair or change its colour (both ends change together).
 *
 * Travel costs Phase mana, more the farther it is. With no Phase mana left, or
 * to cross between dimensions, it takes an ender pearl.
 */
public class WaypointBlock extends Block {
	public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, Waypoints.COLORS.length - 1);

	public WaypointBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(COLOR, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(COLOR);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		// Let a waypoint in hand be placed against this one; anything else uses the waypoint.
		if (stack.getItem() instanceof WaypointItem) return InteractionResult.PASS;
		return use(level, pos, player);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return use(level, pos, player);
	}

	private InteractionResult use(Level level, BlockPos pos, Player user) {
		if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
		String pairId = Waypoints.pairAt(server, pos);
		if (pairId == null) {
			player.sendOverlayMessage(Component.literal("This waypoint has lost its bond. Break it and place it again."));
			return InteractionResult.SUCCESS;
		}
		Waypoints.Pair pair = Waypoints.pair(server.getServer(), pairId);
		if (player.isShiftKeyDown()) {
			ServerPlayNetworking.send(player, new OmniNet.OpenWaypoint(pairId, pair.name, pair.color));
			return InteractionResult.SUCCESS;
		}
		Waypoints.End other = Waypoints.partner(server, pos);
		if (other == null) {
			player.sendOverlayMessage(Component.literal("Its partner has not been placed yet.").withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		ServerLevel target = Waypoints.levelOf(server.getServer(), other);
		if (target == null) {
			player.sendOverlayMessage(Component.literal("Its partner is somewhere that no longer exists."));
			return InteractionResult.SUCCESS;
		}
		BlockPos to = other.pos();
		boolean sameWorld = target == server;
		double distance = sameWorld ? Math.sqrt(pos.distSqr(to)) : Double.MAX_VALUE;
		int manaCost = phaseCost(distance);

		boolean free = player.getAbilities().instabuild;
		if (!free) {
			boolean needsPearl = !sameWorld || Mana.available(player, ManaPool.PHASE) < manaCost;
			if (needsPearl) {
				if (!takePearl(player)) {
					player.sendOverlayMessage(Component.literal(sameWorld
							? "Not enough Phase mana (" + manaCost + " needed). An ender pearl would also do."
							: "Crossing between worlds takes an ender pearl.").withStyle(ChatFormatting.RED));
					return InteractionResult.SUCCESS;
				}
			} else {
				Mana.spend(player, ManaPool.PHASE, manaCost);
			}
		}

		depart(server, pos, pair.color);
		player.teleportTo(target, to.getX() + 0.5, to.getY() + 1.0, to.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
		player.fallDistance = 0;
		depart(target, to, pair.color);
		String name = pair.name.isEmpty() ? Waypoints.COLOR_NAMES[pair.color] + " waypoint" : pair.name;
		player.sendOverlayMessage(Component.literal(name).withStyle(s -> s.withColor(Waypoints.COLORS[pair.color])));
		PlayerStore.syncVitals(player);
		return InteractionResult.SUCCESS;
	}

	/** Phase mana for a jump: 1 nearby, up to 4 for the far side of the world. */
	public static int phaseCost(double distance) {
		if (distance <= 500) return 1;
		if (distance <= 2000) return 2;
		if (distance <= 8000) return 3;
		return 4;
	}

	private static boolean takePearl(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.ENDER_PEARL)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	private static void depart(ServerLevel level, BlockPos pos, int color) {
		level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(Waypoints.COLORS[color], 1.4f),
				pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 30, 0.3, 0.8, 0.3, 0.0);
		level.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.4);
		level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.8f, 1.2f);
	}

	/** Breaking a waypoint gives the item back, still bonded to its partner. */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server) {
			String pairId = Waypoints.pairAt(server, pos);
			if (pairId != null) {
				Waypoints.Pair pair = Waypoints.pair(server.getServer(), pairId);
				Waypoints.remove(server, pos);
				if (!player.getAbilities().instabuild) {
					ItemStack drop = WaypointItem.bonded(pairId, pair.color, pair.name, 1);
					ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
					entity.setDefaultPickUpDelay();
					level.addFreshEntity(entity);
				}
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
