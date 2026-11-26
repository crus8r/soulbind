package com.omnispell.item;

import com.omnispell.charm.Charms;
import com.omnispell.player.Mana;
import com.omnispell.player.ManaPool;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * A wizard's attire: the hat, the robe and the boots. None of them guards the
 * body, none wears out, all are kept on death, and each holds one charm more
 * than ordinary armor. The robe makes the wand's ward stop a blow outright; the
 * boots let the ward carry you over lava and water and soften a fall.
 */
public final class Attire {
	private Attire() {
	}

	public static boolean isAttire(ItemStack stack) {
		return stack.is(OmniItems.WIZARD_HAT) || stack.is(OmniItems.WIZARD_ROBE) || stack.is(OmniItems.WIZARD_BOOTS);
	}

	public static boolean wearingRobe(Player player) {
		return player.getItemBySlot(EquipmentSlot.CHEST).is(OmniItems.WIZARD_ROBE);
	}

	public static boolean wearingBoots(Player player) {
		return player.getItemBySlot(EquipmentSlot.FEET).is(OmniItems.WIZARD_BOOTS);
	}

	/** Kept on death, but a charm may be shaken loose: one in four pieces loses one. */
	public static void onDeath(ItemStack stack, Player player, RandomSource random) {
		if (!isAttire(stack) || random.nextFloat() >= 0.25f) return;
		var sets = Charms.stored(stack);
		if (sets.isEmpty()) return;
		var lost = sets.get(random.nextInt(sets.size()));
		Charms.remove(stack, lost);
		player.sendSystemMessage(Component.literal("The " + lost.gem() + " charm was shaken loose from your " + stack.getHoverName().getString() + " when you fell.").withStyle(ChatFormatting.GRAY));
	}

	/**
	 * Every tick: a player holding the ward up in wizard's boots floats over lava,
	 * walks on water and falls softly, paying Phase and Barrier mana as they go.
	 */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!WandItem.warding(player) || !wearingBoots(player) || player.getAbilities().flying || player.isSpectator()) continue;
			ServerLevel level = player.level();
			Vec3 motion = player.getDeltaMovement();
			BlockPos feet = player.blockPosition();
			boolean lava = player.isInLava();
			boolean water = player.isInWater() && !player.isUnderWater();
			boolean falling = !player.onGround() && motion.y < -0.4 && player.fallDistance > 2.5;
			if (!lava && !water && !falling) continue;
			double phase = lava ? 0.03 : water ? 0.008 : 0.02;
			double barrier = lava ? 0.35 : falling ? 0.08 : 0.0;
			if (Mana.available(player, ManaPool.PHASE) < phase || Mana.available(player, ManaPool.BARRIER) < barrier) continue;
			Mana.spend(player, ManaPool.PHASE, phase);
			Mana.spend(player, ManaPool.BARRIER, barrier);
			if (lava) {
				player.clearFire();
				player.setDeltaMovement(motion.x * 1.1, Math.max(motion.y, 0.22), motion.z * 1.1);
			} else if (water) {
				player.setDeltaMovement(motion.x, Math.max(motion.y, 0.08), motion.z); // up onto the surface, and kept there
			} else {
				boolean ground = !level.getBlockState(feet.below()).canBeReplaced() || !level.getBlockState(feet.below(2)).canBeReplaced();
				if (ground && motion.y < -0.6) {
					player.setDeltaMovement(motion.x, 0.55, motion.z); // a bounce off the ground below
					level.playSound(null, feet, net.minecraft.sounds.SoundEvents.SLIME_JUMP, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.6f);
				} else {
					player.setDeltaMovement(motion.x, Math.max(motion.y, -0.25), motion.z);
				}
				player.fallDistance = 0;
			}
			player.syncVelocity = true;
			if (player.tickCount % 2 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.05, player.getZ(), 3, 0.35, 0.02, 0.35, 0.01);
				level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 0.2, player.getZ(), 2, 0.4, 0.05, 0.4, 0.02);
			}
		}
	}
}
