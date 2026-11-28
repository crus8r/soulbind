package com.omnispell.spell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A trail: what a bridge becomes when it is made of something no one can stand on. Lightning, light, wind or
 * shadow cannot be a deck of stone, so the spell leaves a glowing trail behind its caster instead, for a while.
 * Each stretch of it lingers a few seconds, lit, and what it does depends on its element and on what the
 * Oracle planned: foes who follow you along it are struck by lightning, burned, chilled, poisoned, snared,
 * thrown back, blinded or shown up; friends on it may run faster or mend.
 */
public final class Trails {
	private Trails() {
	}

	public static final Set<String> EFFECTS = Set.of("strike", "burn", "chill", "venom", "snare", "repel", "blind", "glow", "swift", "mend");

	private static final class Active {
		final Cast cast;
		final Set<String> effects;
		final String element;
		final int color;
		final long until;
		final int cellTicks;
		final Map<BlockPos, Long> cells = new LinkedHashMap<>();
		final Map<UUID, Long> struck = new HashMap<>();
		BlockPos last;

		Active(Cast cast, Set<String> effects, String element, int color, long until, int cellTicks) {
			this.cast = cast;
			this.effects = effects;
			this.element = element;
			this.color = color;
			this.until = until;
			this.cellTicks = cellTicks;
		}
	}

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();

	/** What the trail does: what the Oracle planned, or else its element's own way. */
	public static Set<String> effects(Spell spell) {
		Set<String> on = new LinkedHashSet<>();
		if (spell.plan != null && spell.plan.extras != null) for (String e : spell.plan.extras) if (EFFECTS.contains(e)) on.add(e);
		boolean hurts = false;
		for (String e : on) if (!e.equals("swift") && !e.equals("mend")) hurts = true;
		if (!hurts) on.add(switch (AttackForms.element(spell)) {
			case "fire" -> "burn";
			case "ice", "water" -> "chill";
			case "nature" -> "venom";
			case "stone" -> "snare";
			case "wind" -> "repel";
			case "shadow", "void" -> "blind";
			case "light" -> "glow";
			default -> "strike"; // lightning above all
		});
		return on;
	}

	/** Seconds the trail keeps being laid, before Duratus. */
	public static int seconds(Spell spell) {
		return 10 + 4 * Math.max(1, Math.min(5, spell.tier));
	}

	public static boolean lay(Cast cast) {
		Spell spell = cast.spell;
		int tier = Math.max(1, Math.min(5, spell.tier));
		int ticks = (int) (seconds(spell) * 20 * (spell.hasCharge("Duratus") ? 1.6 : 1.0));
		if (Masonry.lifeOverride > 0) ticks = Masonry.lifeOverride;
		Active a = new Active(cast, effects(spell), AttackForms.element(spell), Conjuring.tintFor(spell), Tasks.now() + ticks, 80 + 20 * tier);
		ACTIVE.put(cast.player.getUUID(), a);
		mark(a, cast.player.blockPosition());
		cast.level.playSound(null, cast.player.blockPosition(), Beams.storm(spell) ? SoundEvents.LIGHTNING_BOLT_IMPACT : SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.4f);
		return true;
	}

	/** Words for the book. */
	public static String describe(Spell spell) {
		int secs = (int) (seconds(spell) * (spell.hasCharge("Duratus") ? 1.6 : 1.0));
		List<String> foe = new ArrayList<>(), friend = new ArrayList<>();
		for (String e : effects(spell)) {
			switch (e) {
				case "strike" -> foe.add(Beams.storm(spell) ? "struck by lightning" : "struck by its " + AttackForms.element(spell));
				case "burn" -> foe.add("set alight");
				case "chill" -> foe.add("chilled and slowed");
				case "venom" -> foe.add("poisoned");
				case "snare" -> foe.add("held fast");
				case "repel" -> foe.add("thrown back");
				case "blind" -> foe.add("blinded");
				case "glow" -> foe.add("lit up so they show through walls");
				case "swift" -> friend.add("run faster");
				case "mend" -> friend.add("mend");
				default -> {
				}
			}
		}
		String text = "It is no bridge of stone: for " + secs + " seconds it leaves a glowing trail behind you, each stretch lingering a few seconds.";
		if (!foe.isEmpty()) text += " Hostile creatures that follow you along it are " + String.join(" and ", foe) + ".";
		if (!friend.isEmpty()) text += " You and your friends on it " + String.join(" and ", friend) + ".";
		return text;
	}

	private static void mark(Active a, BlockPos feet) {
		ServerLevel level = a.cast.level;
		BlockPos at = feet.immutable();
		a.last = at;
		a.cells.put(at, Tasks.now() + a.cellTicks);
		if (level.getBlockState(at).isAir()) TempBlocks.place(level, at, Blocks.LIGHT, a.cellTicks); // the trail lights the way
		if (a.cast.spell.hasCharge("Maxima")) {
			for (BlockPos side : new BlockPos[]{at.east(), at.west(), at.north(), at.south()}) a.cells.putIfAbsent(side.immutable(), Tasks.now() + a.cellTicks);
		}
	}

	private static ParticleOptions glint(String element) {
		return switch (element) {
			case "storm" -> ParticleTypes.ELECTRIC_SPARK;
			case "fire" -> ParticleTypes.FLAME;
			case "ice", "water" -> ParticleTypes.SNOWFLAKE;
			case "nature" -> ParticleTypes.HAPPY_VILLAGER;
			case "shadow", "void" -> ParticleTypes.SMOKE;
			case "light" -> ParticleTypes.END_ROD;
			case "wind" -> ParticleTypes.CLOUD;
			default -> ParticleTypes.WITCH;
		};
	}

	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) return;
		long now = Tasks.now();
		var it = ACTIVE.entrySet().iterator();
		while (it.hasNext()) {
			Active a = it.next().getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(a.cast.player.getUUID());
			a.cells.values().removeIf(end -> end <= now);
			boolean laying = owner != null && owner.isAlive() && owner.level() == a.cast.level && now < a.until;
			if (!laying && a.cells.isEmpty()) {
				it.remove();
				continue;
			}
			if (laying && !owner.blockPosition().equals(a.last)) mark(a, owner.blockPosition());
			ServerLevel level = a.cast.level;
			if (now % 4 == 0) {
				DustParticleOptions dust = new DustParticleOptions(a.color, 1.2f);
				for (BlockPos c : a.cells.keySet()) {
					level.sendParticles(dust, c.getX() + 0.5, c.getY() + 0.1, c.getZ() + 0.5, 2, 0.3, 0.02, 0.3, 0);
					if (level.getRandom().nextInt(3) == 0) level.sendParticles(glint(a.element), c.getX() + 0.5, c.getY() + 0.2, c.getZ() + 0.5, 1, 0.2, 0.05, 0.2, 0.01);
				}
			}
			if (now % 10 == 0 && owner != null) pulse(a, owner, level, now);
		}
	}

	private static void pulse(Active a, ServerPlayer owner, ServerLevel level, long now) {
		int tier = Math.max(1, Math.min(5, a.cast.spell.tier));
		Set<LivingEntity> seen = new LinkedHashSet<>();
		for (BlockPos c : a.cells.keySet()) {
			seen.addAll(level.getEntitiesOfClass(LivingEntity.class, new AABB(c).inflate(0.2, 0.6, 0.2).move(0, -0.4, 0), LivingEntity::isAlive));
		}
		for (LivingEntity e : seen) {
			boolean friend = e == owner || Defenses.ally(owner, e);
			if (friend) {
				if (a.effects.contains("swift")) e.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, tier >= 4 ? 1 : 0, false, false));
				if (a.effects.contains("mend") && e.getHealth() < e.getMaxHealth()) e.heal(0.5f + 0.25f * tier);
				continue;
			}
			boolean foe = e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof net.minecraft.world.entity.Mob m && m.getTarget() == owner;
			if (!foe || a.struck.getOrDefault(e.getUUID(), 0L) > now) continue;
			a.struck.put(e.getUUID(), now + 20); // at most once a second each
			Vec3 at = e.getBoundingBox().getCenter();
			float damage = (float) ((1.5 + tier) * a.cast.damageBonus(a.element));
			for (String effect : a.effects) {
				switch (effect) {
					case "strike" -> {
						Beams.bolt(level, at.add(0, 6, 0), at, a.color);
						Modifiers.withSpellElement(a.element, () -> e.hurtServer(level, level.damageSources().indirectMagic(owner, owner), damage));
						if (a.element.equals("storm")) level.playSound(null, e.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, 1.6f);
					}
					case "burn" -> {
						e.igniteForSeconds(3);
						Modifiers.withSpellElement("fire", () -> e.hurtServer(level, level.damageSources().indirectMagic(owner, owner), damage * 0.5f));
					}
					case "chill" -> {
						e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2), owner);
						e.setTicksFrozen(Math.max(e.getTicksFrozen(), 160));
					}
					case "venom" -> e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, tier >= 4 ? 1 : 0), owner);
					case "snare" -> {
						if (com.omnispell.boss.Bosses.defOf(e) == null) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 9, false, false), owner);
					}
					case "repel" -> {
						if (com.omnispell.boss.Bosses.defOf(e) != null) break;
						Vec3 away = e.position().subtract(owner.position()).multiply(1, 0, 1);
						if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
						away = away.normalize();
						e.setDeltaMovement(away.x * 0.9, 0.4, away.z * 0.9);
						e.syncVelocity = true;
					}
					case "blind" -> e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), owner);
					case "glow" -> e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0), owner);
					default -> {
					}
				}
			}
		}
	}

	/** True while the player is laying a trail (for tests). */
	public static boolean laying(ServerPlayer player) {
		Active a = ACTIVE.get(player.getUUID());
		return a != null && Tasks.now() < a.until;
	}

	/** How many stretches of the player's trail are lit right now (for tests). */
	public static int cells(ServerPlayer player) {
		Active a = ACTIVE.get(player.getUUID());
		return a == null ? 0 : a.cells.size();
	}

	public static void clear() {
		ACTIVE.clear();
	}
}
