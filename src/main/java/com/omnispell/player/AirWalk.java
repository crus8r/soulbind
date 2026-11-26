package com.omnispell.player;

import com.omnispell.item.WandItem;
import com.omnispell.spell.TempBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Air-walking: with a wand in hand and nothing under you, double-tap sprint and
 * the air hardens beneath your feet for as long as your Phase mana lasts, which
 * is not long. It breaks a fall the way a hay bale does: most of it, not all.
 */
public final class AirWalk {
	/** Phase mana per tick: a full starting reserve lasts about four seconds. */
	private static final double COST = 0.06;
	private static final Set<UUID> WALKING = new HashSet<>();

	private AirWalk() {
	}

	private static Block step() {
		return Blocks.STAINED_GLASS.pick(DyeColor.WHITE);
	}

	public static void toggle(ServerPlayer player) {
		if (WALKING.remove(player.getUUID())) return;
		if (WandItem.heldWand(player).isEmpty() || player.onGround() || player.isInWater() || player.getAbilities().flying) return;
		if (Mana.available(player, ManaPool.PHASE) < 0.5) {
			player.sendOverlayMessage(Component.literal("Not enough Phase mana to walk on air."));
			return;
		}
		player.fallDistance *= 0.2; // like landing on hay: most of the fall is forgiven, not all of it
		WALKING.add(player.getUUID());
		player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.8f);
	}

	public static boolean isWalking(ServerPlayer player) {
		return WALKING.contains(player.getUUID());
	}

	public static void forget(ServerPlayer player) {
		WALKING.remove(player.getUUID());
	}

	/** Called every tick. */
	public static void tick(MinecraftServer server) {
		if (WALKING.isEmpty()) return;
		for (UUID id : Set.copyOf(WALKING)) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null || !player.isAlive() || WandItem.heldWand(player).isEmpty() || player.isShiftKeyDown()) {
				WALKING.remove(id);
				continue;
			}
			ServerLevel level = player.level();
			BlockPos under = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
			boolean onStep = level.getBlockState(under).is(step());
			// Back on real ground. A step that has just faded leaves air underfoot for a moment, which is not landing.
			if (player.onGround() && !onStep && !level.getBlockState(under).canBeReplaced()) {
				WALKING.remove(id);
				continue;
			}
			if (Mana.spend(player, ManaPool.PHASE, COST) < COST) {
				WALKING.remove(id);
				player.sendOverlayMessage(Component.literal("Your Phase mana is spent. The air gives way."));
				continue;
			}
			Vec3 motion = player.getDeltaMovement();
			TempBlocks.place(level, under, step(), 12);
			// One more where they are heading, so the next stride has something to land on.
			BlockPos ahead = BlockPos.containing(player.getX() + Math.signum(motion.x) * 0.8, player.getY() - 0.2, player.getZ() + Math.signum(motion.z) * 0.8);
			if (!ahead.equals(under) && motion.horizontalDistanceSqr() > 0.0004) TempBlocks.place(level, ahead, step(), 12);
			if (player.tickCount % 4 == 0) level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(), player.getZ(), 2, 0.2, 0.0, 0.2, 0.0);
		}
	}
}
