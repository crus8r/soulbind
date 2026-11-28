package com.omnispell.spell;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Charged auras a defense spell can leave on its caster for a while. Ram: hostile creatures that come into
 * contact with you are knocked flying (and jolted, burned, chilled... by the spell's element), as if you
 * were as strong as a bear; the faster you are moving, the harder they fly.
 */
public final class Auras {
	private Auras() {
	}

	private record Ram(Cast cast, long until, int tier, String element, int color, Map<UUID, Long> hit) {
	}

	private static final Map<UUID, Ram> RAMS = new HashMap<>();

	public static void ram(Cast cast, int ticks) {
		Spell spell = cast.spell;
		RAMS.put(cast.player.getUUID(), new Ram(cast, Tasks.now() + ticks, Math.max(1, Math.min(5, spell.tier)), AttackForms.element(spell), Conjuring.tintFor(spell), new HashMap<>()));
	}

	public static boolean ramming(ServerPlayer player) {
		Ram r = RAMS.get(player.getUUID());
		return r != null && r.until > Tasks.now();
	}

	public static void tick(MinecraftServer server) {
		if (RAMS.isEmpty()) return;
		long now = Tasks.now();
		var it = RAMS.entrySet().iterator();
		while (it.hasNext()) {
			Ram r = it.next().getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(r.cast.player.getUUID());
			if (p == null || !p.isAlive() || now >= r.until || p.level() != r.cast.level) {
				it.remove();
				continue;
			}
			ServerLevel level = p.level();
			if (now % 3 == 0) {
				DustParticleOptions dust = new DustParticleOptions(r.color, 1.0f);
				level.sendParticles(dust, p.getX(), p.getY() + 0.9, p.getZ(), 3, 0.35, 0.5, 0.35, 0);
				if (r.element.equals("storm")) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.9, p.getZ(), 2, 0.4, 0.5, 0.4, 0.05);
			}
			Vec3 moving = p.getDeltaMovement().multiply(1, 0, 1);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(0.6, 0.2, 0.6), LivingEntity::isAlive)) {
				if (e == p || Defenses.ally(p, e)) continue;
				boolean foe = e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof net.minecraft.world.entity.Mob m && m.getTarget() == p;
				if (!foe || r.hit.getOrDefault(e.getUUID(), 0L) > now) continue;
				r.hit.put(e.getUUID(), now + 15);
				Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1);
				away = away.lengthSqr() < 1e-4 ? p.getLookAngle().multiply(1, 0, 1).normalize() : away.normalize();
				double force = 1.1 + 0.15 * r.tier + Math.min(1.0, moving.length() * 2.5);
				if (com.omnispell.boss.Bosses.defOf(e) == null) {
					e.setDeltaMovement(away.x * force, 0.45, away.z * force);
					e.syncVelocity = true;
				}
				float damage = (float) ((1 + r.tier * 0.75) * r.cast.damageBonus(r.element));
				Modifiers.withSpellElement(r.element, () -> e.hurtServer(level, level.damageSources().indirectMagic(p, p), damage));
				switch (r.element) {
					case "storm" -> Beams.bolt(level, p.getBoundingBox().getCenter(), e.getBoundingBox().getCenter(), r.color);
					case "fire" -> e.igniteForSeconds(2);
					case "ice" -> e.setTicksFrozen(Math.max(e.getTicksFrozen(), 140));
					default -> {
					}
				}
				level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 10, 0.3, 0.3, 0.3, 0.3);
				level.playSound(null, e.blockPosition(), SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 0.7f, 1.3f);
			}
		}
	}

	public static void clear() {
		RAMS.clear();
	}
}
