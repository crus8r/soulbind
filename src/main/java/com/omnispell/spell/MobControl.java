package com.omnispell.spell;

import com.omnispell.Omnispell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Statuses that spells put on creatures: stun, charm, fear, confusion, distraction,
 * roots and damage over time. The website's turn-based statuses become timed ones.
 */
public final class MobControl {
	private static final class Hold {
		final LivingEntity target;
		final String kind;
		final long until;
		final ServerPlayer caster;
		final Entity lure;   // for decoys

		Hold(LivingEntity target, String kind, long until, ServerPlayer caster, Entity lure) {
			this.target = target;
			this.kind = kind;
			this.until = until;
			this.caster = caster;
			this.lure = lure;
		}
	}

	private static final class Dot {
		final LivingEntity target;
		final ServerPlayer caster;
		final float perTurn;
		final String element;
		int turnsLeft;
		long next;

		Dot(LivingEntity target, ServerPlayer caster, float perTurn, int turns, String element) {
			this.target = target;
			this.caster = caster;
			this.perTurn = perTurn;
			this.turnsLeft = turns;
			this.element = element;
			this.next = Tasks.now() + Omnispell.CONFIG.turnsToTicks(1);
		}
	}

	private static final List<Hold> HOLDS = new ArrayList<>();
	private static final List<Dot> DOTS = new ArrayList<>();

	private MobControl() {
	}

	/** The great bosses shrug off mind and movement control. */
	public static boolean immune(LivingEntity target) {
		return target instanceof WitherBoss || target instanceof EnderDragon || target instanceof Warden;
	}

	public static boolean isControl(String status) {
		return status.equals("stun") || status.equals("charm") || status.equals("fear") || status.equals("confusion");
	}

	/**
	 * Applies a status for a number of ticks. Kinds: stun, charm, fear, confusion,
	 * distracted, root, decoy (needs a lure).
	 */
	public static void apply(ServerPlayer caster, LivingEntity target, String kind, int ticks, Entity lure) {
		if (immune(target) && !kind.equals("distracted")) {
			caster.sendOverlayMessage(net.minecraft.network.chat.Component.literal(target.getName().getString() + " resists."));
			return;
		}
		HOLDS.removeIf(h -> h.target == target && h.kind.equals(kind));
		HOLDS.add(new Hold(target, kind, Tasks.now() + ticks, caster, lure));

		if (target instanceof ServerPlayer) {
			// Against other players the statuses are vanilla effects: no mind control between friends.
			switch (kind) {
				case "stun", "root" -> {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 6));
					target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, ticks, 3));
				}
				case "confusion" -> target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, ticks + 60, 0));
				case "fear" -> {
					target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, ticks, 0));
					target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1));
				}
				case "charm" -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 2));
				default -> target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Math.min(ticks, 100), 0));
			}
			return;
		}
		if (kind.equals("stun") || kind.equals("root")) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 10, false, false));
			if (kind.equals("stun")) target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 10, false, false));
		}
		if (kind.equals("fear")) target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, false));
		enforce(new Hold(target, kind, Tasks.now() + ticks, caster, lure));
	}

	public static boolean held(LivingEntity target, String kind) {
		for (Hold hold : HOLDS) {
			if (hold.target == target && hold.kind.equals(kind) && hold.until > Tasks.now()) return true;
		}
		return false;
	}

	/** Damage every turn for a number of turns (bleed, burning roots, entangle). Amount is in Minecraft health. */
	public static void dot(ServerPlayer caster, LivingEntity target, float perTurn, int turns, String element) {
		for (Dot dot : DOTS) {
			if (dot.target == target && dot.caster == caster) {
				// Bleed stacks: a new application adds its turns to the old one.
				dot.turnsLeft = Math.min(12, dot.turnsLeft + turns);
				return;
			}
		}
		DOTS.add(new Dot(target, caster, perTurn, turns, element));
	}

	private static void enforce(Hold hold) {
		if (!(hold.target instanceof Mob mob)) return;
		ServerLevel level = (ServerLevel) mob.level();
		switch (hold.kind) {
			case "stun" -> {
				mob.getNavigation().stop();
				mob.setTarget(null);
				level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getEyeY() + 0.4, mob.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
			}
			case "root" -> mob.getNavigation().stop();
			case "distracted" -> mob.setTarget(null);
			case "decoy" -> {
				if (hold.lure instanceof LivingEntity lure && lure.isAlive()) mob.setTarget(lure);
			}
			case "charm" -> {
				// A charmed creature turns on the nearest hostile creature instead of you.
				LivingEntity foe = null;
				double best = 144;
				for (LivingEntity other : Targeting.around(hold.caster, mob.position(), 12, false)) {
					if (other == mob || !(other instanceof Monster) || held(other, "charm")) continue;
					double d = other.distanceToSqr(mob);
					if (d < best) {
						best = d;
						foe = other;
					}
				}
				mob.setTarget(foe);
				level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getEyeY() + 0.5, mob.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
			}
			case "confusion" -> {
				// A confused creature attacks whatever is nearest that is not you.
				LivingEntity other = null;
				double best = 100;
				for (LivingEntity near : Targeting.around(hold.caster, mob.position(), 10, false)) {
					if (near == mob) continue;
					double d = near.distanceToSqr(mob);
					if (d < best) {
						best = d;
						other = near;
					}
				}
				mob.setTarget(other);
				level.sendParticles(ParticleTypes.WITCH, mob.getX(), mob.getEyeY() + 0.4, mob.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
			}
			case "fear" -> {
				mob.setTarget(null);
				Vec3 away = mob.position().subtract(hold.caster.position());
				if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
				Vec3 flee = mob.position().add(away.normalize().scale(10));
				mob.getNavigation().moveTo(flee.x, flee.y, flee.z, 1.4);
			}
			default -> {
			}
		}
	}

	/** Every 10 ticks. */
	public static void tick() {
		long now = Tasks.now();
		Iterator<Hold> holds = HOLDS.iterator();
		while (holds.hasNext()) {
			Hold hold = holds.next();
			if (hold.until <= now || !hold.target.isAlive() || hold.target.isRemoved()) {
				holds.remove();
				continue;
			}
			enforce(hold);
		}
		Iterator<Dot> dots = DOTS.iterator();
		while (dots.hasNext()) {
			Dot dot = dots.next();
			if (!dot.target.isAlive() || dot.target.isRemoved() || dot.turnsLeft <= 0) {
				dots.remove();
				continue;
			}
			if (dot.next <= now) {
				dot.next = now + Omnispell.CONFIG.turnsToTicks(1);
				dot.turnsLeft--;
				ServerLevel level = (ServerLevel) dot.target.level();
				Modifiers.withSpellElement(dot.element, () ->
						dot.target.hurtServer(level, level.damageSources().indirectMagic(dot.caster, dot.caster), dot.perTurn));
				level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, dot.target.getX(), dot.target.getY() + 1, dot.target.getZ(), 2, 0.2, 0.3, 0.2, 0.0);
			}
		}
	}
}
