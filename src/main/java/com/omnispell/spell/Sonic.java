package com.omnispell.spell;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Sound as a weapon. Thunder is not lightning: it is the crack and roll of sound, so Thunder, Sound, Echo and
 * Vibration attacks travel as rings of sound in the spell's colour, and a shockwave rolls out in front of the
 * caster, striking and throwing back everything in it.
 */
public final class Sonic {
	private Sonic() {
	}

	/** Rings of the spell's colour from one point to another, like a warden's blast but tinted. */
	public static void rings(ServerLevel level, Vec3 from, Vec3 to, int color) {
		Vec3 step = to.subtract(from);
		int n = Math.max(1, (int) Math.ceil(step.length()));
		DustParticleOptions dust = new DustParticleOptions(color, 1.6f);
		Vec3 dir = step.normalize();
		Vec3 up = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 a = dir.cross(up).normalize();
		Vec3 b = dir.cross(a).normalize();
		for (int i = 1; i <= n; i++) {
			Vec3 q = from.add(step.scale(i / (double) n));
			double r = 0.35 + 0.25 * Math.sin(i * 0.9);
			for (int k = 0; k < 10; k++) {
				double t = k * Math.PI * 2 / 10;
				Vec3 o = q.add(a.scale(Math.cos(t) * r)).add(b.scale(Math.sin(t) * r));
				level.sendParticles(dust, o.x, o.y, o.z, 1, 0, 0, 0, 0);
			}
		}
		level.playSound(null, net.minecraft.core.BlockPos.containing(from), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6f, 1.4f);
	}

	/** Who a shockwave catches: hostile creatures in front of the caster, within reach. */
	static List<LivingEntity> caught(Cast cast, double reach) {
		ServerPlayer p = cast.player;
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : Targeting.around(p, p.position(), reach, false)) {
			if (e == p || !e.isAlive() || Defenses.ally(p, e)) continue;
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye).multiply(1, 0, 1);
			if (to.lengthSqr() < 1e-4 || to.normalize().dot(look) < 0.6) continue; // about a hundred degrees wide
			out.add(e);
		}
		out.sort((x, y) -> Double.compare(x.distanceToSqr(p), y.distanceToSqr(p)));
		return out;
	}

	/**
	 * A thunderclap: a wave of sound rolls out in front of you. The nearest foe takes the whole spell and its form;
	 * everything else in the wave takes most of it; all of them are thrown back.
	 */
	public static boolean shockwave(Cast cast) {
		ServerPlayer p = cast.player;
		ServerLevel level = cast.level;
		Spell spell = cast.spell;
		int tier = Math.max(1, Math.min(5, spell.tier));
		double reach = 6 + tier + (spell.hasCharge("Maxima") ? 3 : 0);
		int color = Conjuring.tintFor(spell);
		Vec3 eye = p.getEyePosition().add(0, -0.3, 0);
		Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
		// The wave: arcs of colour widening as they go, and the boom at its heart.
		DustParticleOptions dust = new DustParticleOptions(color, 1.8f);
		Vec3 side = look.cross(new Vec3(0, 1, 0)).normalize();
		for (double d = 1; d <= reach; d += 1.0) {
			double half = d * 0.9;
			for (double s = -half; s <= half; s += 0.6) {
				Vec3 at = eye.add(look.scale(d)).add(side.scale(s));
				level.sendParticles(dust, at.x, at.y, at.z, 1, 0, 0.15, 0, 0);
			}
			if (((int) d) % 3 == 0) {
				Vec3 c = eye.add(look.scale(d));
				level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			}
		}
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.9f, 1.2f);
		level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.6f);
		List<LivingEntity> hit = caught(cast, reach);
		double base = spell.code.primaryValue > 0 ? spell.code.primaryValue : tier * 10.0;
		for (int i = 0; i < hit.size(); i++) {
			LivingEntity e = hit.get(i);
			CombatEffects.strike(cast, e, i == 0 ? base : base * 0.75, i == 0);
			if (i == 0 && e.isAlive()) AttackForms.apply(cast, e, base);
			CombatEffects.riders(cast, e, Math.max(1, spell.code.duration));
			if (e.isAlive() && com.omnispell.boss.Bosses.defOf(e) == null) {
				Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1);
				away = away.lengthSqr() < 1e-4 ? look : away.normalize();
				double push = 0.9 + 0.15 * tier;
				e.setDeltaMovement(away.x * push, 0.35, away.z * push);
				e.syncVelocity = true;
			}
		}
		if (hit.isEmpty()) cast.cooldownFactor = 0.5; // a wave that caught nothing costs less
		return true;
	}
}
