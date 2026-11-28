package com.omnispell.spell;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * A rewind: time marks where you stand. A few seconds later you snap back to the mark with the health you had
 * when it was made (never less than you have then), and the fire on you goes out. If a blow would kill you
 * before then, the rewind happens at once instead. A sealed lair between you and the mark keeps you where you
 * are, though your health still comes back.
 */
public final class Rewind {
	private Rewind() {
	}

	private record Mark(ServerLevel level, Vec3 at, float yaw, float pitch, float health, long until) {
	}

	private static final Map<UUID, Mark> MARKS = new HashMap<>();

	/** Does this defense rewind? Time, Memory or Reset in it (or the Oracle planned it). */
	public static boolean rewinds(Spell spell) {
		if (spell == null || !"defense".equals(spell.type)) return false;
		return spell.plannedExtra("rewind") || spell.words.contains("base:Time") || spell.words.contains("base:Memory") || spell.words.contains("command:Reset");
	}

	/** Seconds from the mark to the snap back. */
	public static int seconds(Spell spell) {
		return 4 + Math.max(1, Math.min(5, spell.tier)) / 2;
	}

	public static void mark(Cast cast) {
		ServerPlayer p = cast.player;
		MARKS.put(p.getUUID(), new Mark(cast.level, p.position(), p.getYRot(), p.getXRot(), p.getHealth(), Tasks.now() + seconds(cast.spell) * 20L));
		cast.level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.6, 0.3, 0.02);
		cast.level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
	}

	public static boolean marked(ServerPlayer player) {
		return MARKS.containsKey(player.getUUID());
	}

	/** A blow that would kill a marked player rewinds them instead. True if it did (the blow then does nothing). */
	public static boolean saves(ServerPlayer player, float amount) {
		Mark m = MARKS.get(player.getUUID());
		if (m == null || amount < player.getHealth() + player.getAbsorptionAmount()) return false;
		snap(player, m);
		return true;
	}

	private static void snap(ServerPlayer p, Mark m) {
		MARKS.remove(p.getUUID());
		if (p.level() != m.level) return;
		ServerLevel level = m.level;
		boolean sealed = com.omnispell.boss.Lairs.sealedBetween(level, p.position(), m.at) > 0;
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.6, 0.3, 0.05);
		if (!sealed) {
			p.teleportTo(level, m.at.x, m.at.y, m.at.z, java.util.Set.of(), m.yaw, m.pitch, true);
			p.setDeltaMovement(0, 0, 0);
			p.fallDistance = 0;
		}
		if (p.getHealth() < m.health) p.setHealth(m.health);
		p.clearFire();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.3, 0.6, 0.3, 0.05);
		level.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
		p.sendOverlayMessage(Component.literal(sealed ? "Time folds back, but the lair holds you here." : "Time folds back.").withStyle(net.minecraft.ChatFormatting.AQUA));
	}

	public static void tick(MinecraftServer server) {
		if (MARKS.isEmpty()) return;
		long now = Tasks.now();
		for (var e : new java.util.ArrayList<>(MARKS.entrySet())) {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			Mark m = e.getValue();
			if (p == null || !p.isAlive()) {
				MARKS.remove(e.getKey());
				continue;
			}
			if (now >= m.until) {
				snap(p, m);
				continue;
			}
			// The mark shimmers where it waits.
			if (now % 6 == 0) m.level.sendParticles(ParticleTypes.END_ROD, m.at.x, m.at.y + 0.2 + (now % 30) / 15.0, m.at.z, 1, 0.15, 0.05, 0.15, 0.0);
		}
	}

	public static void clear() {
		MARKS.clear();
	}
}
