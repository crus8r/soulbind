package com.omnispell.spell;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A surging wall: an intangible wall of the spell's element that rolls out from the caster and
 * carries creatures before it until they fetch up against something solid. It touches nothing
 * else: it passes through stone and leaves, and players, pets and friends are not moved.
 *
 * An attack spell with the Wall attribute becomes one, striking every creature it sweeps up. A
 * defense spell with Wall, Tower or Door and a driving command (Repel, Throw, Release and the
 * like) becomes one that only drives enemies off.
 */
public final class Surges {
	private Surges() {
	}

	public static boolean wallAttack(Spell spell) {
		return "attack".equals(spell.type) && (spell.words.contains("attribute:Wall") || "wall".equals(spell.planned("delivery")));
	}

	private static ParticleOptions particle(String element) {
		return switch (element) {
			case "fire" -> ParticleTypes.FLAME;
			case "water" -> ParticleTypes.SPLASH;
			case "ice" -> ParticleTypes.SNOWFLAKE;
			case "storm" -> ParticleTypes.ELECTRIC_SPARK;
			case "stone" -> ParticleTypes.ASH;
			case "nature" -> ParticleTypes.SPORE_BLOSSOM_AIR;
			case "light" -> ParticleTypes.END_ROD;
			case "shadow" -> ParticleTypes.SQUID_INK;
			case "void" -> ParticleTypes.REVERSE_PORTAL;
			case "blood" -> ParticleTypes.DAMAGE_INDICATOR;
			case "wind" -> ParticleTypes.CLOUD;
			default -> ParticleTypes.WITCH;
		};
	}

	public static String describe(boolean attack, String element) {
		String what = switch (element) {
			case "fire" -> "a wall of fire";
			case "water" -> "a wall of water";
			case "ice" -> "a wall of frost";
			case "wind" -> "a wall of wind";
			case "storm" -> "a wall of lightning";
			case "stone" -> "a wall of grinding stone";
			case "light" -> "a wall of light";
			case "shadow" -> "a wall of shadow";
			default -> "a wall of " + element;
		};
		return attack
				? "It sends " + what + " rolling out ahead of you. The wall passes through anything solid, but every creature in its path is struck and carried along until it hits something."
				: "It sends " + what + " rolling out ahead of you that touches nothing but hostile creatures, driving them back until they hit something.";
	}

	/** Rolls a wall out from the caster. For an attack it strikes each creature once with the spell; for a defense it only drives enemies. */
	public static boolean roll(Cast cast, boolean attack) {
		ServerPlayer p = cast.player;
		ServerLevel level = cast.level;
		Spell spell = cast.spell;
		String element = AttackForms.element(spell);
		int tier = Math.max(1, Math.min(5, spell.tier));
		boolean big = spell.hasCharge("Maxima");
		Vec3 look = p.getLookAngle().multiply(1, 0, 1);
		Vec3 dir = look.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : look.normalize();
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		Vec3 start = p.position().add(dir.scale(1.0));
		double halfWidth = big ? 4.5 : 2.5 + tier * 0.3;
		double height = big ? 4 : 3;
		int length = 10 + 2 * tier;
		double push = element.equals("wind") || element.equals("water") ? 0.75 : 0.6;
		double base = spell.code.primaryValue > 0 ? spell.code.primaryValue : tier * 10.0;
		ParticleOptions mark = particle(element);
		Set<Mob> struck = new HashSet<>();
		level.playSound(null, p.blockPosition(), switch (element) {
			case "water" -> SoundEvents.GENERIC_SPLASH;
			case "fire" -> SoundEvents.BLAZE_SHOOT;
			default -> SoundEvents.BREEZE_WIND_CHARGE_BURST.value();
		}, SoundSource.PLAYERS, 1.0f, 0.7f);
		for (int step = 0; step <= length * 2; step++) {
			int at = step;
			Tasks.later(step, () -> {
				Vec3 front = start.add(dir.scale(at * 0.5));
				// the wall itself, drawn as a sheet of its element
				for (double s = -halfWidth; s <= halfWidth; s += 0.8) {
					for (double h = 0.2; h <= height; h += 0.9) {
						Vec3 q = front.add(side.scale(s)).add(0, h, 0);
						level.sendParticles(mark, q.x, q.y, q.z, 1, 0.05, 0.1, 0.05, 0.0);
					}
				}
				// everything it overlaps is carried forward; an attack strikes each creature once
				AABB slab = new AABB(front.add(side.scale(-halfWidth)), front.add(side.scale(halfWidth)).add(0, height, 0)).inflate(0.6, 0.2, 0.6);
				List<Mob> caught = level.getEntitiesOfClass(Mob.class, slab, m -> m.isAlive() && !Defenses.ally(p, m) && (attack || m instanceof net.minecraft.world.entity.monster.Enemy));
				for (Mob mob : caught) {
					double resist = MobControl.immune(mob) || com.omnispell.boss.Bosses.defOf(mob) != null ? 0.25 : 1.0;
					Vec3 v = mob.getDeltaMovement();
					boolean first = struck.add(mob);
					// carried along the ground (a small lift when it first hits), and stopped by whatever solid thing it meets
					mob.setDeltaMovement(dir.x * push * resist + v.x * 0.2, first ? Math.max(v.y, 0.2) : v.y, dir.z * push * resist + v.z * 0.2);
					mob.syncVelocity = true;
					if (first) {
						if (attack) {
							mob.damageCooldownTime = 0;
							CombatEffects.strike(cast, mob, base * 0.7, false);
							String status = spell.code.status;
							if (status != null && !status.equals("none") && !status.equals("null") && mob.isAlive()) CombatEffects.status(cast, mob, status, Math.max(1, spell.code.duration));
						} else {
							switch (element) {
								case "fire" -> mob.igniteForSeconds(2);
								case "ice" -> mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), p);
								case "shadow", "light" -> mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), p);
								default -> {
								}
							}
						}
						if (element.equals("water")) mob.clearFire();
					}
				}
			});
		}
		return true;
	}
}
