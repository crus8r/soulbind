package com.omnispell.spell;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a spell sends out of the wand, when it should be seen to. Lightning is drawn as a jagged bolt
 * that forks and flickers; other elements, when a spell says it is a beam, ray, lance, jet, stream or
 * torrent, as a beam that lingers a moment.
 *
 * Every behaviour here is only for the spells it suits: lightning for lightning and thunder, beams
 * for spells that say they are beams. Nothing else changes.
 */
public final class Beams {
	private Beams() {
	}

	// ------------------------------------------------------------------ which spells

	/** A lightning or thunder spell, by the Oracle's element or the spell's own base word. */
	public static boolean storm(Spell spell) {
		return stormWords(spell) || "lightning".equals(spell.planned("delivery")) || (!spell.isExploration() && AttackForms.element(spell).equals("storm"));
	}

	/** The base word is Lightning or Thunder (an environment spell of either explores like the wind, but it is lightning). */
	public static boolean stormWords(Spell spell) {
		return spell.words.contains("base:Lightning") || spell.words.contains("base:Thunder");
	}

	/** Does the spell say it is a beam (a beam, ray, lance, laser, jet, stream or torrent)? */
	public static boolean beamClaim(Spell spell) {
		if ("beam".equals(spell.planned("delivery"))) return true;
		String text = (SpellCrafting.oracleText(spell) + " " + (spell.effect == null ? "" : spell.effect)).toLowerCase(Locale.ROOT);
		for (String w : new String[]{"beam", "ray ", "rays", "ray,", "ray.", "lance", "laser", "jet ", "jets", "stream", "torrent"}) {
			int i = text.indexOf(w);
			while (i >= 0) {
				if (i == 0 || !Character.isLetter(text.charAt(i - 1))) return true;
				i = text.indexOf(w, i + 1);
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ drawing

	/** A jagged bolt of lightning from one point to another, forking, flickering three times. */
	public static void bolt(ServerLevel level, Vec3 from, Vec3 to, int color) {
		for (int flicker = 0; flicker < 3; flicker++) {
			long seed = level.getRandom().nextLong();
			Runnable draw = () -> drawBolt(level, from, to, color, RandomSource.create(seed));
			if (flicker == 0) draw.run();
			else Tasks.later(flicker * 2, draw);
		}
		level.playSound(null, net.minecraft.core.BlockPos.containing(to), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, 1.6f);
		level.playSound(null, net.minecraft.core.BlockPos.containing(from), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.25f, 1.8f);
	}

	private static void drawBolt(ServerLevel level, Vec3 from, Vec3 to, int color, RandomSource random) {
		Vec3 span = to.subtract(from);
		double length = span.length();
		if (length < 0.1) return;
		Vec3 dir = span.scale(1 / length);
		Vec3 up = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 sideA = dir.cross(up).normalize();
		Vec3 sideB = dir.cross(sideA).normalize();
		int joints = Math.max(3, (int) (length / 1.1));
		double jag = Math.min(0.55, 0.12 + length * 0.04);
		List<Vec3> points = new ArrayList<>();
		points.add(from);
		for (int k = 1; k < joints; k++) {
			Vec3 on = from.add(dir.scale(length * k / joints));
			points.add(on.add(sideA.scale((random.nextDouble() - 0.5) * 2 * jag)).add(sideB.scale((random.nextDouble() - 0.5) * 2 * jag)));
		}
		points.add(to);
		DustParticleOptions glow = new DustParticleOptions(color, 1.1f);
		DustParticleOptions core = new DustParticleOptions(0xF4F8FF, 0.7f);
		for (int k = 0; k + 1 < points.size(); k++) {
			segment(level, points.get(k), points.get(k + 1), glow, core);
			// now and then a fork
			if (k > 0 && random.nextInt(4) == 0) {
				Vec3 at = points.get(k);
				Vec3 fork = at.add(dir.scale(0.6 + random.nextDouble())).add(sideA.scale((random.nextDouble() - 0.5) * 2.4)).add(sideB.scale((random.nextDouble() - 0.5) * 2.4));
				segment(level, at, fork, glow, null);
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 10, 0.2, 0.2, 0.2, 0.15);
	}

	private static void segment(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions glow, ParticleOptions core) {
		Vec3 d = b.subtract(a);
		int steps = Math.max(1, (int) (d.length() / 0.18));
		for (int i = 0; i <= steps; i++) {
			Vec3 q = a.add(d.scale(i / (double) steps));
			level.sendParticles(glow, q.x, q.y, q.z, 1, 0, 0, 0, 0);
			if (core != null && i % 2 == 0) level.sendParticles(core, q.x, q.y, q.z, 1, 0, 0, 0, 0);
		}
	}

	/** A beam of an element from one point to another: a bright core and a glow of its colour, lingering a few ticks. */
	public static void beam(ServerLevel level, Vec3 from, Vec3 to, String element, int color) {
		if (element.equals("storm")) {
			bolt(level, from, to, color);
			return;
		}
		ParticleOptions grain = switch (element) {
			case "fire" -> ParticleTypes.FLAME;
			case "ice" -> ParticleTypes.SNOWFLAKE;
			case "water" -> ParticleTypes.SPLASH;
			case "light" -> ParticleTypes.END_ROD;
			case "shadow" -> ParticleTypes.SQUID_INK;
			case "void" -> ParticleTypes.REVERSE_PORTAL;
			case "nature" -> ParticleTypes.HAPPY_VILLAGER;
			case "wind" -> ParticleTypes.CLOUD;
			case "stone" -> ParticleTypes.ASH;
			case "blood" -> ParticleTypes.DAMAGE_INDICATOR;
			default -> ParticleTypes.WITCH;
		};
		DustParticleOptions glow = new DustParticleOptions(color, 1.5f);
		DustParticleOptions core = new DustParticleOptions(0xFFFFFF, 0.8f);
		for (int t = 0; t < 4; t++) {
			Runnable draw = () -> {
				Vec3 d = to.subtract(from);
				int steps = Math.max(1, (int) (d.length() / 0.2));
				for (int i = 0; i <= steps; i++) {
					Vec3 q = from.add(d.scale(i / (double) steps));
					level.sendParticles(glow, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0);
					if (i % 2 == 0) level.sendParticles(core, q.x, q.y, q.z, 1, 0, 0, 0, 0);
					if (i % 3 == 0) level.sendParticles(grain, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0.01);
				}
			};
			if (t == 0) draw.run();
			else Tasks.later(t, draw);
		}
		level.playSound(null, net.minecraft.core.BlockPos.containing(from), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5f, 1.8f);
	}

	// ------------------------------------------------------------------ chain lightning

	/**
	 * Chain lightning: from the creature it struck, the lightning leaps to the nearest foe within six
	 * blocks, and from that one to the next, three times (four from tier 4), never to a friend.
	 */
	public static void chain(Cast cast, LivingEntity first, double websiteDamage) {
		ServerPlayer caster = cast.player;
		ServerLevel level = cast.level;
		int jumps = 3 + (cast.spell.tier >= 4 ? 1 : 0);
		int color = Conjuring.tintFor(cast.spell);
		Set<LivingEntity> struck = new HashSet<>();
		struck.add(first);
		LivingEntity at = first;
		for (int j = 0; j < jumps; j++) {
			LivingEntity next = null;
			double best = 6 * 6;
			Vec3 here = at.getBoundingBox().getCenter();
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, at.getBoundingBox().inflate(6),
					x -> x.isAlive() && !x.isSpectator() && x != caster && !struck.contains(x) && !Defenses.ally(caster, x))) {
				double d = e.getBoundingBox().getCenter().distanceToSqr(here);
				if (d < best) {
					best = d;
					next = e;
				}
			}
			if (next == null) break;
			bolt(level, here, next.getBoundingBox().getCenter(), color);
			next.damageCooldownTime = 0;
			CombatEffects.strike(cast, next, websiteDamage, false);
			struck.add(next);
			at = next;
		}
	}

	// ------------------------------------------------------------------ an attack that is a beam

	/** Is this attack a beam that pierces: everything in its path is struck, not just the first thing? */
	public static boolean piercing(Spell spell) {
		if (!"attack".equals(spell.type)) return false;
		String delivery = spell.planned("delivery");
		if (delivery != null) return delivery.equals("beam"); // the Oracle planned how it travels
		return beamClaim(spell);
	}

	/**
	 * An attack beam: out of the wand to the first wall or the end of its reach, striking everything along
	 * the way. What it meets first takes the whole spell (and its form); everything behind takes a little
	 * over half. Its element marks each: fire burns, frost slows, water drives back, light shows them up,
	 * shadow blinds, the void withers, poison and nature poison, wind throws back.
	 */
	public static boolean attack(Cast cast) {
		ServerPlayer p = cast.player;
		ServerLevel level = cast.level;
		Spell spell = cast.spell;
		Vec3 hand = Targeting.hand(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		BlockHitResult wall = Targeting.block(p, cast.range);
		double reach = wall.getType() == HitResult.Type.MISS ? cast.range : wall.getLocation().distanceTo(eye);
		Vec3 end = eye.add(look.scale(reach));
		String element = AttackForms.element(spell);
		beam(level, hand, end, element, Conjuring.tintFor(spell));
		List<LivingEntity> path = new ArrayList<>();
		for (LivingEntity e : Targeting.along(p, cast.range, 0.6)) if (!Defenses.ally(p, e)) path.add(e);
		double base = spell.code.primaryValue > 0 ? spell.code.primaryValue : spell.tier * 10.0;
		if (path.isEmpty()) {
			cast.cooldownFactor = 0.5; // a miss costs less
			return true;
		}
		for (int i = 0; i < path.size(); i++) {
			LivingEntity e = path.get(i);
			e.damageCooldownTime = 0;
			CombatEffects.strike(cast, e, i == 0 ? base : base * 0.6, i == 0);
			if (!e.isAlive()) continue;
			mark(cast, e, element, look);
			if (i == 0) {
				Spell.EffectCode code = spell.code;
				if (code.status != null && !code.status.equals("none") && !code.status.equals("null")) CombatEffects.status(cast, e, code.status, Math.max(1, code.duration));
				if (e.isAlive()) AttackForms.apply(cast, e, base);
			}
		}
		return true;
	}

	/** What a beam's element leaves on each thing it passes through. */
	private static void mark(Cast cast, LivingEntity e, String element, Vec3 look) {
		ServerPlayer p = cast.player;
		int tier = Math.max(1, Math.min(5, cast.spell.tier));
		switch (element) {
			case "fire" -> CombatEffects.burn(cast, e, 2 + tier);
			case "ice" -> e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60 + 10 * tier, 1), p);
			case "water", "wind" -> {
				e.setDeltaMovement(e.getDeltaMovement().add(look.x * 0.8, 0.25, look.z * 0.8));
				e.syncVelocity = true;
			}
			case "light" -> e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0), p);
			case "shadow" -> e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), p);
			case "void" -> e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0), p);
			case "nature" -> {
				if (!cast.spell.words.contains("base:Wood") && !cast.spell.words.contains("base:Thorn")) e.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0), p);
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ a creature's breath beam

	/**
	 * A conjured creature whose spell says it breathes or casts a beam sends that beam through its foe
	 * and on, up to twelve blocks, striking every foe of its caster along it.
	 */
	public static void creature(Cast cast, LivingEntity from, LivingEntity foe, double websiteDamage) {
		ServerPlayer owner = cast.player;
		ServerLevel level = cast.level;
		Vec3 start = from.position().add(0, from.getBbHeight() * 0.7, 0);
		Vec3 dir = foe.getBoundingBox().getCenter().subtract(start);
		if (dir.lengthSqr() < 0.01) return;
		dir = dir.normalize();
		Vec3 end = start.add(dir.scale(12));
		var clip = level.clip(new net.minecraft.world.level.ClipContext(start, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, from));
		if (clip.getType() != HitResult.Type.MISS) end = clip.getLocation();
		String element = AttackForms.element(cast.spell);
		beam(level, start, end, element, Conjuring.tintFor(cast.spell));
		Vec3 span = end.subtract(start);
		double length = span.length();
		List<LivingEntity> hit = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().expandTowards(span).inflate(1.0),
				x -> x.isAlive() && x != from && x != owner && !Defenses.ally(owner, x))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(start);
			double along = to.dot(dir);
			if (along < 0 || along > length) continue;
			if (to.subtract(dir.scale(along)).length() <= 0.6 + e.getBbWidth() / 2) hit.add(e);
		}
		for (LivingEntity e : hit) {
			e.damageCooldownTime = 0;
			CombatEffects.strike(cast, e, e == foe ? websiteDamage : websiteDamage * 0.6, false);
			if (e.isAlive()) mark(cast, e, element, dir);
		}
	}

	// ------------------------------------------------------------------ lightning on the move

	/** The environment toggles that carry you somewhere: a lightning one lands with a discharge. */
	private static final Set<String> MOVES = Set.of("grapple", "dash", "leap", "bound", "launch", "current", "blink", "teleport", "phase");

	/** After an environment spell of lightning: the ones that move you discharge where you land, the ones that push shock what they push. */
	public static void afterExplore(Cast cast) {
		Spell spell = cast.spell;
		if (!stormWords(spell) || spell.toggle == null) return;
		ServerPlayer p = cast.player;
		if (MOVES.contains(spell.toggle)) {
			boolean[] flew = {false, false};
			for (int k = 1; k <= 40; k++) {
				Tasks.later(k * 2, () -> {
					if (flew[1] || !p.isAlive()) return;
					if (!p.onGround()) {
						flew[0] = true;
						return;
					}
					if (flew[0] || spell.toggle.equals("blink") || spell.toggle.equals("teleport") || spell.toggle.equals("phase")) {
						flew[1] = true;
						discharge(cast, p.position(), 5);
					}
				});
			}
		} else if (spell.toggle.equals("push") || spell.toggle.equals("gust")) {
			discharge(cast, p.position().add(p.getLookAngle().scale(3)), 5);
		}
	}

	/** A discharge: arcs from where you stand to up to three foes within reach, each jolted. */
	public static int discharge(Cast cast, Vec3 at, double radius) {
		ServerPlayer p = cast.player;
		ServerLevel level = cast.level;
		int tier = Math.max(1, Math.min(5, cast.spell.tier));
		int arcs = 0;
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius),
				x -> x.isAlive() && x != p && !Defenses.ally(p, x) && (x instanceof net.minecraft.world.entity.monster.Enemy
						|| x instanceof net.minecraft.world.entity.Mob m && m.getTarget() == p));
		foes.sort((a, b) -> Double.compare(a.distanceToSqr(at), b.distanceToSqr(at)));
		for (LivingEntity e : foes) {
			if (arcs >= 3) break;
			bolt(level, at.add(0, 1, 0), e.getBoundingBox().getCenter(), Fx.color("storm"));
			float damage = (float) ((2 + tier) * cast.damageBonus("storm"));
			Modifiers.withSpellElement("storm", () -> e.hurtServer(level, level.damageSources().indirectMagic(p, p), damage));
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1), p);
			arcs++;
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, 30, 1.0, 0.2, 1.0, 0.2);
		return arcs;
	}
}
