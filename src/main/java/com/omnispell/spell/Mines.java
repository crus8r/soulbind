package com.omnispell.spell;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Mines: what a bed or a bridge of something that cannot be slept on or walked across becomes.
 * A bed of fire is a fire mine that bursts when an enemy steps on it; a bed of water is a
 * balloon that pops under whoever lands on it, so the fall does no harm; a bed of air throws
 * you high. A bridge of fire is a fuse: a line of fire mines that all go off, one after
 * another, when the first is touched. A bridge of lightning is a tripwire, a live wire strung
 * across the way that shocks whatever crosses it.
 */
public final class Mines {
	private Mines() {
	}

	private static final class Mine {
		final ServerLevel level;
		final BlockPos pos;
		final String element;
		final UUID owner;
		final Cast cast;
		final long until;
		final int fuse; // mines laid as one fuse line share a number; 0 for none
		boolean spent;

		Mine(ServerLevel level, BlockPos pos, String element, UUID owner, Cast cast, long until, int fuse) {
			this.level = level;
			this.pos = pos;
			this.element = element;
			this.owner = owner;
			this.cast = cast;
			this.until = until;
			this.fuse = fuse;
		}
	}

	private record Wire(ServerLevel level, Vec3 from, Vec3 to, UUID owner, Cast cast, long until) {
	}

	private static final List<Mine> MINES = new ArrayList<>();
	private static final List<Wire> WIRES = new ArrayList<>();
	private static int nextFuse = 1;

	public static boolean handles(String toggle) {
		return "mine".equals(toggle) || "fuse".equals(toggle) || "tripwire".equals(toggle);
	}

	/** The element a mine is made of. The thunder words make lightning. */
	public static String element(Spell spell) {
		for (String id : spell.words) if (id.equals("base:Thunder") || id.equals("base:Lightning") || id.equals("base:Storm")) return "storm";
		return spell.element == null ? "arcane" : spell.element;
	}

	/** Water and air mines help whoever lands on them; the rest are traps that spare you and your friends. */
	private static boolean kindly(String element) {
		return element.equals("water") || element.equals("wind");
	}

	public static double cooldownSeconds(String toggle) {
		return toggle.equals("mine") ? 4 : 15;
	}

	public static String describe(String toggle, Spell spell) {
		String e = element(spell);
		int t = Math.max(1, Math.min(5, spell.tier));
		return switch (toggle) {
			case "fuse" -> "Lays a fuse of " + (5 + t) + " fire mines in a line ahead of you. When an enemy touches one, they all go off, one after another, down the line.";
			case "tripwire" -> "Strings a live wire of lightning from you to where you look, up to 16 blocks. For " + (30 + 10 * t) + " seconds it shocks every hostile creature that crosses it.";
			default -> "Sets a mine on the ground where you look. It lies hidden for five minutes. " + switch (e) {
				case "fire" -> "When an enemy steps on it, it bursts into flame.";
				case "storm" -> "When an enemy steps on it, lightning strikes.";
				case "water" -> "It is a water balloon: whoever lands on it, you included, splashes down unharmed, however far they fell.";
				case "wind" -> "Whoever steps on it, you included, is thrown high into the air and drifts down gently.";
				case "ice" -> "When an enemy steps on it, it is frozen in a block of ice.";
				case "shadow" -> "When an enemy steps on it, darkness bursts and blinds everything near.";
				case "light" -> "When an enemy steps on it, a flash blinds everything near and burns the undead.";
				case "void" -> "When an enemy steps on it, it is flung away through space.";
				case "blood" -> "When an enemy steps on it, it bleeds, and its blood mends you.";
				case "stone" -> "When an enemy steps on it, stone spikes throw it up.";
				default -> "When an enemy steps on it, arcane force bursts out.";
			};
		};
	}

	public static boolean cast(Cast c, String toggle) {
		ServerPlayer p = c.player;
		ServerLevel level = c.level;
		String element = element(c.spell);
		int tier = Math.max(1, Math.min(5, c.spell.tier));
		long life = (long) (6000 * (c.spell.hasCharge("Duratus") ? 1.6 : 1.0));
		switch (toggle) {
			case "tripwire" -> {
				Vec3 from = p.position().add(0, 0.6, 0);
				BlockHitResult hit = Targeting.block(p, 16);
				Vec3 to = (hit.getType() == HitResult.Type.MISS ? p.getEyePosition().add(p.getLookAngle().scale(16)) : hit.getLocation()).add(0, 0.6, 0);
				WIRES.add(new Wire(level, from, to, p.getUUID(), c, level.getGameTime() + (30 + 10L * tier) * 20));
				level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.8f);
				Fx.line(level, from, to, 0xBFE6FF, ParticleTypes.ELECTRIC_SPARK, 4);
				return true;
			}
			case "fuse" -> {
				Vec3 flat = p.getLookAngle().multiply(1, 0, 1);
				if (flat.lengthSqr() < 0.01) return c.fail("Look along the ground where the fuse should run.");
				flat = flat.normalize();
				int fuse = nextFuse++;
				int laid = 0;
				for (int i = 1; i <= 5 + tier; i++) {
					BlockPos at = ground(level, BlockPos.containing(p.position().add(flat.scale(i * 2.0))));
					if (at == null) continue;
					MINES.add(new Mine(level, at, "fire", p.getUUID(), c, level.getGameTime() + life, fuse));
					level.sendParticles(ParticleTypes.SMALL_FLAME, at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, 3, 0.2, 0.0, 0.2, 0.0);
					laid++;
				}
				level.playSound(null, p.blockPosition(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
				return laid > 0 || c.fail("There is no ground ahead to lay a fuse on.");
			}
			default -> {
				BlockHitResult hit = Targeting.block(p, 12);
				BlockPos at = hit.getType() == HitResult.Type.MISS ? ground(level, p.blockPosition()) : ground(level, hit.getBlockPos().relative(hit.getDirection()));
				if (at == null) return c.fail("There is no ground there for a mine.");
				// No more than six of your mines at once; the oldest goes.
				List<Mine> mine = new ArrayList<>();
				for (Mine m : MINES) if (m.owner.equals(p.getUUID()) && m.fuse == 0 && !m.spent) mine.add(m);
				if (mine.size() >= 6) mine.get(0).spent = true;
				MINES.add(new Mine(level, at, element, p.getUUID(), c, level.getGameTime() + life, 0));
				level.sendParticles(new DustParticleOptions(Fx.color(c.spell.visual.color1), 1.0f), at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, 10, 0.3, 0.0, 0.3, 0.0);
				level.playSound(null, at, SoundEvents.TRIPWIRE_ATTACH, SoundSource.PLAYERS, 1.0f, 0.8f);
				return true;
			}
		}
	}

	/** The air block resting on the ground at or just below a spot, or null if there is none within a few blocks. */
	private static BlockPos ground(ServerLevel level, BlockPos start) {
		BlockPos pos = start;
		for (int i = 0; i < 6; i++) {
			if (level.getBlockState(pos).canBeReplaced() && !level.getBlockState(pos.below()).canBeReplaced()) return pos.immutable();
			pos = pos.below();
		}
		return null;
	}

	private static boolean friend(Mine m, LivingEntity e) {
		if (e.getUUID().equals(m.owner)) return true;
		ServerPlayer owner = m.level.getServer().getPlayerList().getPlayer(m.owner);
		return owner != null ? Defenses.ally(owner, e) : e instanceof net.minecraft.world.entity.player.Player;
	}

	/** Every other tick: mines watch for feet, wires for crossers. */
	public static void tick(MinecraftServer server) {
		if (MINES.isEmpty() && WIRES.isEmpty()) return;
		for (Iterator<Mine> it = MINES.iterator(); it.hasNext(); ) {
			Mine m = it.next();
			long now = m.level.getGameTime();
			if (m.spent || now > m.until) {
				it.remove();
				continue;
			}
			if (now % 30 == 0) {
				// a faint sign, for those who look closely
				m.level.sendParticles(new DustParticleOptions(colour(m.element), 0.6f), m.pos.getX() + 0.5, m.pos.getY() + 0.05, m.pos.getZ() + 0.5, 1, 0.15, 0.0, 0.15, 0.0);
			}
			boolean kind = kindly(m.element);
			AABB box = kind ? new AABB(m.pos).inflate(0.5, 0, 0.5).expandTowards(0, 6, 0) : new AABB(m.pos).inflate(0.25, 0, 0.25);
			LivingEntity stepper = null;
			for (LivingEntity e : m.level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
				if (e.isSpectator()) continue;
				if (kind) {
					// A balloon or a spring: anyone, landing on it or standing on it.
					if (e.getDeltaMovement().y < -0.2 || (e.onGround() && e.getY() < m.pos.getY() + 0.5)) {
						stepper = e;
						break;
					}
				} else if (!friend(m, e)) {
					stepper = e;
					break;
				}
			}
			if (stepper != null) trigger(m, stepper);
		}
		MINES.removeIf(m -> m.spent);
		for (Iterator<Wire> it = WIRES.iterator(); it.hasNext(); ) {
			Wire w = it.next();
			long now = w.level.getGameTime();
			if (now > w.until) {
				it.remove();
				continue;
			}
			if (now % 10 != 0) continue;
			Fx.line(w.level, w.from, w.to, 0xBFE6FF, ParticleTypes.ELECTRIC_SPARK, 2);
			AABB span = new AABB(w.from, w.to).inflate(1.0);
			ServerPlayer owner = w.level.getServer().getPlayerList().getPlayer(w.owner);
			for (LivingEntity e : w.level.getEntitiesOfClass(LivingEntity.class, span, LivingEntity::isAlive)) {
				if (owner != null && (e == owner || Defenses.ally(owner, e))) continue;
				if (distanceToSegment(e.getBoundingBox().getCenter(), w.from, w.to) > 0.9) continue;
				hurt(w.cast, e, 2 + w.cast.spell.tier, "storm");
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2));
				w.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 1, e.getZ(), 10, 0.3, 0.5, 0.3, 0.1);
				w.level.playSound(null, e.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.3f, 1.8f);
			}
		}
	}

	private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / Math.max(1e-6, ab.lengthSqr())));
		return p.distanceTo(a.add(ab.scale(t)));
	}

	private static int colour(String element) {
		return switch (element) {
			case "fire" -> 0xFF6A20;
			case "water" -> 0x3A9BE8;
			case "wind" -> 0xE8F4F0;
			case "storm" -> 0xBFE6FF;
			case "ice" -> 0xA8E4FF;
			case "shadow" -> 0x3A2A5A;
			case "light" -> 0xFFF2A8;
			case "void" -> 0x9A5AE0;
			case "blood" -> 0xB0102A;
			case "stone" -> 0x8A7A68;
			default -> 0xC08AF0;
		};
	}

	private static void hurt(Cast c, LivingEntity target, double amount, String element) {
		float damage = (float) (amount * c.damageBonus(element));
		Modifiers.withSpellElement(element, () -> target.hurtServer(c.level, c.level.damageSources().indirectMagic(c.player, c.player), damage));
	}

	/** The mine goes off. A fuse takes the rest of its line with it, one after another. */
	private static void trigger(Mine m, LivingEntity stepper) {
		if (m.spent) return;
		m.spent = true;
		burst(m, stepper);
		if (m.fuse == 0) return;
		int delay = 4;
		for (Mine other : MINES) {
			if (other == m || other.fuse != m.fuse || other.spent) continue;
			other.spent = true;
			Tasks.later(delay, () -> burst(other, null));
			delay += 4;
		}
	}

	private static void burst(Mine m, LivingEntity stepper) {
		ServerLevel level = m.level;
		Cast c = m.cast;
		int tier = Math.max(1, Math.min(5, c.spell.tier));
		Vec3 at = Vec3.atBottomCenterOf(m.pos);
		List<LivingEntity> near = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3), LivingEntity::isAlive)) {
			if (e.position().distanceTo(at) <= 3 && (kindly(m.element) || !friend(m, e))) near.add(e);
		}
		if (stepper != null && !near.contains(stepper)) near.add(stepper); // whoever set it off, even mid-fall above it
		switch (m.element) {
			case "fire" -> {
				level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, 30, 0.8, 0.4, 0.8, 0.05);
				level.playSound(null, m.pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
				for (LivingEntity e : near) {
					hurt(c, e, 3 + 2 * tier, "fire");
					e.igniteForSeconds(4);
					Vec3 out = e.position().subtract(at).multiply(1, 0, 1);
					if (out.lengthSqr() > 0.01) e.push(out.normalize().x * 0.7, 0.4, out.normalize().z * 0.7);
					e.syncVelocity = true;
				}
			}
			case "storm" -> {
				var bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.snapTo(at.x, at.y, at.z);
					bolt.setVisualOnly(true);
					level.addFreshEntity(bolt);
				}
				for (LivingEntity e : near) hurt(c, e, 4 + 2 * tier, "storm");
			}
			case "water" -> {
				level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 60, 0.8, 0.3, 0.8, 0.2);
				level.playSound(null, m.pos, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.2f, 1.4f);
				for (LivingEntity e : near) {
					if (e != stepper && e.position().distanceTo(at) > 2.5) continue;
					e.fallDistance = 0;
					e.clearFire();
					e.setDeltaMovement(e.getDeltaMovement().x * 0.5, 0.3, e.getDeltaMovement().z * 0.5);
					e.syncVelocity = true;
					e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, false, false)); // a gentle way down, however high it caught them
				}
			}
			case "wind" -> {
				level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
				level.playSound(null, m.pos, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
				for (LivingEntity e : near) {
					if (e != stepper && e.position().distanceTo(at) > 2) continue;
					e.fallDistance = 0;
					e.setDeltaMovement(e.getDeltaMovement().x, 1.2 + 0.12 * tier, e.getDeltaMovement().z);
					e.syncVelocity = true;
					e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, false, false));
				}
			}
			case "ice" -> {
				for (LivingEntity e : near) {
					if (e.position().distanceTo(at) > 2) continue;
					AttackForms.manifest(c, e, "encase", "ice", 6 + 2 * tier);
					e.setTicksFrozen(Math.max(e.getTicksFrozen(), 200));
				}
				level.playSound(null, m.pos, SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f);
			}
			case "shadow" -> {
				level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1, at.z, 50, 1.5, 0.8, 1.5, 0.02);
				for (LivingEntity e : near) {
					e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
					e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0));
					if (e instanceof net.minecraft.world.entity.Mob mob) mob.setTarget(null);
				}
			}
			case "light" -> {
				level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 60, 1.5, 1.0, 1.5, 0.2);
				for (LivingEntity e : near) {
					e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
					e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
					if (e.isInvertedHealAndHarm()) {
						e.igniteForSeconds(5);
						hurt(c, e, 2 + tier, "light");
					}
				}
			}
			case "void" -> {
				for (LivingEntity e : near) {
					if (MobControl.immune(e)) continue;
					Vec3 from = e.position();
					for (int i = 0; i < 10; i++) {
						double a = level.getRandom().nextDouble() * Math.PI * 2, d = 6 + level.getRandom().nextDouble() * 4;
						if (e.randomTeleport(from.x + Math.cos(a) * d, from.y + 2, from.z + Math.sin(a) * d, true, state -> true)) break;
					}
					level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.5, 0.3, 0.5);
				}
			}
			case "blood" -> {
				ServerPlayer owner = level.getServer().getPlayerList().getPlayer(m.owner);
				for (LivingEntity e : near) {
					if (owner != null) MobControl.dot(owner, e, 1.5f + 0.5f * tier, 4, "blood");
					if (owner != null && owner.isAlive()) owner.heal(1.0f + 0.5f * tier);
				}
				level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y + 0.8, at.z, 20, 0.8, 0.4, 0.8, 0.1);
			}
			case "stone" -> {
				for (LivingEntity e : near) {
					hurt(c, e, 3 + 2 * tier, "stone");
					e.setDeltaMovement(e.getDeltaMovement().x, 0.7, e.getDeltaMovement().z);
					e.syncVelocity = true;
				}
				level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(m.pos.below())), at.x, at.y + 0.3, at.z, 40, 0.6, 0.2, 0.6, 0.2);
				level.playSound(null, m.pos, SoundEvents.POINTED_DRIPSTONE_FALL, SoundSource.PLAYERS, 1.0f, 0.6f);
			}
			default -> {
				level.sendParticles(ParticleTypes.WITCH, at.x, at.y + 0.8, at.z, 40, 1.0, 0.6, 1.0, 0.05);
				for (LivingEntity e : near) {
					hurt(c, e, 3 + 2 * tier, "arcane");
					e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
				}
			}
		}
	}
}
