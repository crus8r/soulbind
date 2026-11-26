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

	/** Every creature hunting the player forgets them. */
	private static void loseTrack(ServerPlayer player) {
		for (net.minecraft.world.entity.Mob mob : player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(48))) {
			if (mob.getTarget() == player) mob.setTarget(null);
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
