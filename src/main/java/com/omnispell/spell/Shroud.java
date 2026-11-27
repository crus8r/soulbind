package com.omnispell.spell;

import com.omnispell.net.OmniNet;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Full invisibility: a strong enough invisibility spell hides what the caster
 * wears and carries too, not just their body. Every client that can see the
 * caster is told, and leaves their armour and held items undrawn until it ends.
 */
public final class Shroud {
	private static final Map<UUID, Long> UNTIL = new HashMap<>();

	private Shroud() {
	}

	public static void apply(ServerPlayer player, int ticks) {
		UNTIL.put(player.getUUID(), Tasks.now() + ticks);
		announce(player, ticks);
		loseTrack(player);
	}

	public static boolean active(ServerPlayer player) {
		return UNTIL.getOrDefault(player.getUUID(), 0L) > Tasks.now();
	}

	/** Who each invisible player has struck while unseen: those creatures know where they are until the invisibility ends. */
	private static final Map<UUID, java.util.Set<UUID>> EXPOSED = new HashMap<>();

	/** Can this creature not find this player? True for anyone invisible, except to what they have attacked while invisible. */
	public static boolean hiddenFrom(ServerPlayer player, net.minecraft.world.entity.Entity looker) {
		if (!player.hasEffect(MobEffects.INVISIBILITY)) return false;
		java.util.Set<UUID> knows = EXPOSED.get(player.getUUID());
		return looker == null || knows == null || !knows.contains(looker.getUUID());
	}

	/** A blow struck while invisible gives the striker away to the one struck. */
	public static void struck(net.minecraft.world.entity.LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player && victim != player && player.hasEffect(MobEffects.INVISIBILITY)) {
			EXPOSED.computeIfAbsent(player.getUUID(), k -> new java.util.HashSet<>()).add(victim.getUUID());
		}
	}

	/** Every creature hunting the player forgets them, except those the player struck while unseen. */
	private static void loseTrack(ServerPlayer player) {
		for (net.minecraft.world.entity.Mob mob : player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(48))) {
			if (!hiddenFrom(player, mob)) continue;
			if (mob.getTarget() == player) mob.setTarget(null);
			if (mob.getLastHurtByMob() == player) mob.setLastHurtByMob(null);
			var brain = mob.getBrain();
			if (brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET)
					&& brain.getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET).orElse(null) == player) {
				brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
			}
			if (brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT)
					&& player.getUUID().equals(brain.getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT).orElse(null))) {
				brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT);
			}
		}
	}

	/** Twice a second: whoever is invisible, by spell or potion, is lost to everything hunting them. */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.hasEffect(MobEffects.INVISIBILITY)) loseTrack(player);
			else EXPOSED.remove(player.getUUID());
		}
	}

	private static void announce(ServerPlayer player, int ticks) {
		OmniNet.Shroud message = new OmniNet.Shroud(player.getId(), ticks);
		ServerPlayNetworking.send(player, message);
		for (ServerPlayer other : PlayerLookup.tracking(player)) {
			if (other != player) ServerPlayNetworking.send(other, message);
		}
	}

	/** Once a second: tell newcomers who can now see a shrouded player, and end it early if the invisibility is gone. */
	public static void everySecond(MinecraftServer server) {
		if (UNTIL.isEmpty()) return;
		long now = Tasks.now();
		UNTIL.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) return true;
			boolean over = entry.getValue() <= now || !player.hasEffect(MobEffects.INVISIBILITY);
			announce(player, over ? 0 : (int) (entry.getValue() - now));
			if (!over) loseTrack(player);
			return over;
		});
	}
}
