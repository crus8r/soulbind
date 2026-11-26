package com.omnispell.spell;

import com.omnispell.data.Word;
import com.omnispell.data.WordBank;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Great workings that ride on top of certain exploration spells: infernos, beams from the
 * sky, geysers, tidal waves, blizzards, ice spikes, earthquakes, meteors, bloodletting,
 * shadow tendrils, solar flares and chain lightning.
 *
 * Most are extras. The spell still does what it always did, and at a high enough tier
 * the extra follows it, so a spell can be more than one thing at once. Only one pairing,
 * whose words describe nothing else, takes a new effect in place of its old one. All of
 * them belong to a few exact pairings (or, for lightning, to its own base words), so they
 * stay something to find.
 */
public final class Wonders {
	private Wonders() {
	}

	private record Extra(String toggle, int minTier) {
	}

	/** Pairings that become something new outright. */
	private static final Map<String, String> PRIMARY = Map.of(
			"light-summon-destruction", "sky_beam");

	/** Pairings that keep their effect and gain another from the given tier. */
	private static final Map<String, Extra> EXTRAS = Map.ofEntries(
			Map.entry("fire-grow-destruction", new Extra("inferno", 3)),
			Map.entry("fire-envelop-destruction", new Extra("inferno", 4)),
			Map.entry("fire-pulse-destruction", new Extra("inferno", 4)),
			Map.entry("fire-summon-destruction", new Extra("inferno", 5)),
			Map.entry("fire-launch-destruction", new Extra("meteor", 5)),
			Map.entry("light-grow-destruction", new Extra("sky_beam", 1)),
			Map.entry("light-pulse-destruction", new Extra("solar_flare", 3)),
			Map.entry("water-summon-destruction", new Extra("geyser", 1)),
			Map.entry("water-grow-destruction", new Extra("tidal_wave", 1)),
			Map.entry("water-envelop-destruction", new Extra("tidal_wave", 3)),
			Map.entry("ice-grow-destruction", new Extra("ice_spikes", 1)),
			Map.entry("ice-summon-destruction", new Extra("ice_spikes", 3)),
			Map.entry("ice-envelop-destruction", new Extra("blizzard", 3)),
			Map.entry("ice-pulse-destruction", new Extra("blizzard", 4)),
			Map.entry("stone-pulse-destruction", new Extra("earthquake", 1)),
			Map.entry("stone-pulse-manipulation", new Extra("earthquake", 3)),
			Map.entry("blood-bind-destruction", new Extra("bloodletting", 1)),
			Map.entry("blood-envelop-destruction", new Extra("bloodletting", 2)),
			Map.entry("shadow-grow-destruction", new Extra("tendrils", 1)),
			Map.entry("shadow-grow-manipulation", new Extra("tendrils", 3)));

	public static boolean handles(String toggle) {
		return toggle != null && (PRIMARY.containsValue(toggle) || isExtra(toggle));
	}

	private static boolean isExtra(String toggle) {
		for (Extra e : EXTRAS.values()) if (e.toggle.equals(toggle)) return true;
		return toggle.equals("chain_lightning");
	}

	public static String primary(String entryId) {
		return entryId == null ? null : PRIMARY.get(entryId);
	}

	private static boolean thunder(Spell spell) {
		for (String id : spell.words) if (id.equals("base:Thunder") || id.equals("base:Lightning") || id.equals("base:Storm")) return true;
		return false;
	}

	/** The extra workings this spell carries at its tier. */
	public static List<String> extras(Spell spell) {
		List<String> out = new ArrayList<>();
		String id = Rifts.entryId(spell);
		Extra extra = id == null ? null : EXTRAS.get(id);
		if (extra != null && spell.tier >= extra.minTier && !extra.toggle.equals(spell.toggle)) out.add(extra.toggle);
		if (thunder(spell) && id != null && id.endsWith("-destruction")) out.add("chain_lightning");
		return out;
	}

	/** The extra a pairing would gain at a higher tier, for the book. Null if none. */
	public static String laterExtra(Spell spell) {
		String id = Rifts.entryId(spell);
		Extra extra = id == null ? null : EXTRAS.get(id);
		if (extra == null || spell.tier >= extra.minTier) return null;
		return "At tier " + extra.minTier + " it would also bring " + label(extra.toggle).toLowerCase() + ".";
	}

	public static String label(String toggle) {
		return switch (toggle) {
			case "inferno" -> "An inferno";
			case "sky_beam" -> "Beams from the sky";
			case "meteor" -> "A meteor shower";
			case "solar_flare" -> "A solar flare";
			case "geyser" -> "A geyser";
			case "tidal_wave" -> "A tidal wave";
			case "ice_spikes" -> "Ice spikes";
			case "blizzard" -> "A blizzard";
			case "earthquake" -> "An earthquake";
			case "bloodletting" -> "Bloodletting";
			case "tendrils" -> "Shadow tendrils";
			case "chain_lightning" -> "Chain lightning";
			default -> toggle;
		};
	}

	public static String describe(String toggle, int tier) {
		int t = Math.max(1, Math.min(5, tier));
		return switch (toggle) {
			case "inferno" -> "An inferno rolls outward in waves, " + (5 + t) + " blocks, setting everything it passes ablaze.";
			case "sky_beam" -> (t >= 4 ? "Beams" : "A beam") + " of energy slam down from the sky where you look, searing everything beneath.";
			case "meteor" -> "Meteors streak down from the sky around where you look and burst on impact.";
			case "solar_flare" -> "A solar flare blinds everything within " + (8 + t) + " blocks and sets the undead alight.";
			case "geyser" -> "A geyser bursts up where you look, throwing creatures high into the air and putting out fires.";
			case "tidal_wave" -> "A tidal wave rolls out ahead of you, sweeping creatures before it and dousing flames.";
			case "ice_spikes" -> "Spikes of ice erupt from the ground where you look, flinging creatures up. They melt after a while.";
			case "blizzard" -> "A blizzard settles where you look for " + (6 + t) + " seconds, slowing and freezing what stands in it.";
			case "earthquake" -> "The ground shakes for three seconds, " + (6 + t) + " blocks around, staggering and bruising everything standing on it.";
			case "bloodletting" -> "For four seconds it bleeds every creature near the target, and their blood mends you.";
			case "tendrils" -> "Shadow tendrils rise around where you look, dragging creatures in and holding them.";
			case "chain_lightning" -> "Lightning arcs from the first creature it finds to " + (2 + t) + " more.";
			default -> "";
		};
	}

	/** Book text for the extras a spell carries, and any it would gain later. */
	public static String describeExtras(Spell spell) {
		StringBuilder sb = new StringBuilder();
		for (String extra : extras(spell)) sb.append(' ').append(describe(extra, spell.tier));
		String later = laterExtra(spell);
		if (later != null) sb.append(' ').append(later);
		return sb.toString();
	}

	public static double cooldownSeconds(String toggle) {
		return 12;
	}

	/** Runs the extras after a spell's own effect. */
	public static void afterCast(Cast c) {
		if (c.spell.fizzle) return;
		for (String extra : extras(c.spell)) {
			try {
				cast(c, extra);
			} catch (Exception e) {
				com.omnispell.Omnispell.LOG.error("Extra working {} failed", extra, e);
			}
		}
	}

	private static boolean spared(ServerPlayer caster, LivingEntity e) {
		return e == caster || com.omnispell.familiar.Familiars.isFamiliar(e) || !e.isAlive();
	}

	private static void hurt(Cast c, LivingEntity target, double amount, String element) {
		float damage = (float) (amount * c.damageBonus(element));
		Modifiers.withSpellElement(element, () -> target.hurtServer(c.level, c.level.damageSources().indirectMagic(c.player, c.player), damage));
	}

	private static List<LivingEntity> near(Cast c, Vec3 at, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : Targeting.around(c.player, at, radius, false)) if (!spared(c.player, e)) out.add(e);
		return out;
	}

	/** Self-centred spells (envelop, pulse) happen around the caster; the rest where they look. */
	private static Vec3 center(Cast c) {
		String id = Rifts.entryId(c.spell);
		if (id != null && (id.contains("-envelop-") || id.contains("-pulse-"))) return c.player.position();
		return Targeting.point(c.player, Math.max(16, c.range));
	}

	private static Vec3 ground(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at);
		for (int i = 0; i < 8 && level.getBlockState(pos.below()).isAir(); i++) pos = pos.below();
		return new Vec3(at.x, pos.getY(), at.z);
	}

	public static boolean cast(Cast c, String toggle) {
		ServerLevel level = c.level;
		ServerPlayer p = c.player;
		int tier = Math.max(1, Math.min(5, c.spell.tier));
		double power = c.spell.hasCharge("Maxima") ? 1.5 : c.spell.hasCharge("Minima") ? 0.7 : 1.0;
		WordBank.Flavor flavor = WordBank.flavor(c.spell.element == null ? "light" : c.spell.element);
		int color = Fx.color(flavor == null ? "light" : flavor.color);
		switch (toggle) {
			case "inferno" -> {
				Vec3 at = center(c);
				double max = 5 + tier;
				Set<LivingEntity> burned = new HashSet<>();
				level.playSound(null, BlockPos.containing(at), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2f, 0.6f);
				for (int wave = 1; wave <= 5; wave++) {
					double r = max * wave / 5.0;
					Tasks.later(wave * 5, () -> {
						for (int i = 0; i < 18 + (int) r * 6; i++) {
							double a = i * Math.PI * 2 / (18 + (int) r * 6);
							double x = at.x + Math.cos(a) * r, z = at.z + Math.sin(a) * r;
							level.sendParticles(ParticleTypes.FLAME, x, at.y + 0.3, z, 3, 0.2, 0.5, 0.2, 0.02);
							if (i % 3 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, x, at.y + 1, z, 1, 0.2, 0.4, 0.2, 0.01);
							if (i % 5 == 0) level.sendParticles(ParticleTypes.LAVA, x, at.y + 0.3, z, 1, 0, 0, 0, 0);
						}
						level.playSound(null, BlockPos.containing(at), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7f, 0.5f);
						for (LivingEntity e : near(c, at, r)) {
							if (!burned.add(e)) continue;
							e.igniteForSeconds(5 + tier);
							hurt(c, e, (2 + tier) * power, "fire");
						}
					});
				}
				return true;
			}
			case "sky_beam" -> {
				Vec3 first = ground(level, Targeting.point(p, Math.max(16, c.range)));
				List<Vec3> spots = new ArrayList<>(List.of(first));
				int beams = 1 + (tier >= 4 ? 2 : tier >= 2 ? 1 : 0);
				for (LivingEntity e : near(c, first, 8)) {
					if (spots.size() >= beams) break;
					if (e.position().distanceTo(first) > 2.5) spots.add(e.position());
				}
				for (int i = 0; i < spots.size(); i++) {
					Vec3 at = spots.get(i);
					int delay = i * 6;
					for (int k = 0; k < 10; k += 2) {
						int beat = k;
						Tasks.later(delay + k, () -> Fx.ring(level, at.add(0, 0.1, 0), 2.5 - beat * 0.2, color, null));
					}
					Tasks.later(delay + 10, () -> {
						Fx.line(level, at.add(0, 24, 0), at, color, ParticleTypes.END_ROD, 4);
												level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 40, 1.0, 0.2, 1.0, 0.2);
						level.playSound(null, BlockPos.containing(at), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.9f, 1.5f);
						for (LivingEntity e : near(c, at, 2.5)) {
							hurt(c, e, (6 + 2 * tier) * power, c.spell.element == null ? "light" : c.spell.element);
							e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), p);
							if (e.isInvertedHealAndHarm()) e.igniteForSeconds(4);
						}
					});
				}
				return true;
			}
			case "meteor" -> {
				Vec3 target = ground(level, Targeting.point(p, Math.max(20, c.range)));
				for (int i = 0; i < 3; i++) {
					Vec3 at = i == 0 ? target : ground(level, target.add((level.getRandom().nextDouble() - 0.5) * 8, 0, (level.getRandom().nextDouble() - 0.5) * 8));
					Vec3 from = at.add(-10 + level.getRandom().nextDouble() * 4, 26, -6 + level.getRandom().nextDouble() * 4);
					int delay = i * 8;
					for (int k = 0; k <= 10; k++) {
						double f = k / 10.0;
						Vec3 point = from.lerp(at, f);
						Tasks.later(delay + k, () -> {
							level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 8, 0.3, 0.3, 0.3, 0.02);
							level.sendParticles(ParticleTypes.LARGE_SMOKE, point.x, point.y, point.z, 3, 0.2, 0.2, 0.2, 0.01);
						});
					}
					Tasks.later(delay + 11, () -> {
						level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
						level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 20, 1.0, 0.3, 1.0, 0.1);
						level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
						for (LivingEntity e : near(c, at, 3)) {
							hurt(c, e, (8 + 2 * tier) * power, "fire");
							e.igniteForSeconds(4);
							Vec3 out = e.position().subtract(at).multiply(1, 0, 1);
							if (out.lengthSqr() > 0.01) e.push(out.normalize().x * 0.8, 0.5, out.normalize().z * 0.8);
							e.syncVelocity = true;
						}
					});
				}
				return true;
			}
			case "solar_flare" -> {
				Vec3 at = p.position();
								level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 80, 2, 1, 2, 0.4);
				level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 1.6f);
				for (LivingEntity e : near(c, at, 8 + tier)) {
					e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60 + 10 * tier, 0), p);
					e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0), p);
					if (e.isInvertedHealAndHarm()) {
						e.igniteForSeconds(5);
						hurt(c, e, (3 + tier) * power, "light");
					}
					if (e instanceof net.minecraft.world.entity.Mob mob) mob.setTarget(null);
				}
				return true;
			}
			case "geyser" -> {
				Vec3 at = ground(level, Targeting.point(p, Math.max(16, c.range)));
				for (int k = 0; k < 30; k += 2) {
					int beat = k;
					Tasks.later(k, () -> {
						for (int h = 0; h < 8; h++) level.sendParticles(h % 2 == 0 ? ParticleTypes.SPLASH : ParticleTypes.BUBBLE_COLUMN_UP, at.x, at.y + h * (0.6 + beat * 0.02), at.z, 6, 0.35, 0.2, 0.35, 0.05);
					});
				}
				level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.5f, 0.5f);
				for (LivingEntity e : near(c, at, 2.5)) {
					e.setDeltaMovement(e.getDeltaMovement().x * 0.3, 1.1 + 0.1 * tier, e.getDeltaMovement().z * 0.3);
					e.syncVelocity = true;
					e.clearFire();
					hurt(c, e, (2 + tier) * power, "water");
				}
				for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(at).offset(-3, -1, -3), BlockPos.containing(at).offset(3, 2, 3))) {
					if (level.getBlockState(pos).is(Blocks.FIRE)) level.removeBlock(pos, false);
				}
				return true;
			}
			case "tidal_wave" -> {
				Vec3 look = p.getLookAngle().multiply(1, 0, 1);
				Vec3 dir = look.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : look.normalize();
				Vec3 side = new Vec3(-dir.z, 0, dir.x);
				int length = 10 + 2 * tier;
				Set<LivingEntity> swept = new HashSet<>();
				level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.5f, 0.6f);
				for (int step = 1; step <= length; step++) {
					Vec3 front = p.position().add(dir.scale(step));
					Tasks.later(step, () -> {
						for (int w = -3; w <= 3; w++) {
							Vec3 at = front.add(side.scale(w));
							level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 8, 0.3, 0.6, 0.3, 0.1);
							level.sendParticles(ParticleTypes.FALLING_WATER, at.x, at.y + 1.6, at.z, 3, 0.3, 0.2, 0.3, 0);
						}
						for (LivingEntity e : near(c, front, 3.2)) {
							e.push(dir.x * 0.9, 0.25, dir.z * 0.9);
							e.syncVelocity = true;
							e.clearFire();
							if (swept.add(e)) hurt(c, e, (2 + tier) * power, "water");
						}
						BlockPos base = BlockPos.containing(front);
						for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, -1, -3), base.offset(3, 2, 3))) if (level.getBlockState(pos).is(Blocks.FIRE)) level.removeBlock(pos, false);
					});
				}
				return true;
			}
			case "ice_spikes" -> {
				Vec3 at = ground(level, Targeting.point(p, Math.max(16, c.range)));
				List<LivingEntity> caught = near(c, at, 3.5);
				for (LivingEntity e : caught) {
					e.setDeltaMovement(e.getDeltaMovement().x, 0.8 + 0.08 * tier, e.getDeltaMovement().z);
					e.syncVelocity = true;
					e.setTicksFrozen(Math.max(e.getTicksFrozen(), 100));
					hurt(c, e, (4 + 2 * tier) * power, "ice");
				}
				int spikes = 4 + tier;
				for (int i = 0; i < spikes; i++) {
					double a = i * Math.PI * 2 / spikes + level.getRandom().nextDouble() * 0.5;
					double r = 1.5 + level.getRandom().nextDouble() * 2.0;
					BlockPos foot = BlockPos.containing(at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r);
					int height = 1 + level.getRandom().nextInt(Math.min(3, 1 + tier / 2) + 1);
					Tasks.later(2 + i, () -> {
						for (int h = 0; h < height; h++) {
							BlockPos pos = foot.above(h);
							if (!level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos)).isEmpty()) break;
							if (!TempBlocks.place(level, pos, Blocks.PACKED_ICE, 120 + 20 * tier)) break;
						}
						level.sendParticles(ParticleTypes.SNOWFLAKE, foot.getX() + 0.5, foot.getY() + 1, foot.getZ() + 0.5, 10, 0.3, 0.6, 0.3, 0.05);
					});
				}
				level.playSound(null, BlockPos.containing(at), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
				return true;
			}
			case "blizzard" -> {
				Vec3 at = center(c);
				double radius = 4 + tier;
				int seconds = 6 + tier;
				for (int k = 0; k < seconds * 20; k += 5) {
					int beat = k;
					Tasks.later(k, () -> {
						for (int i = 0; i < 25; i++) {
							double x = at.x + (level.getRandom().nextDouble() * 2 - 1) * radius, z = at.z + (level.getRandom().nextDouble() * 2 - 1) * radius;
							level.sendParticles(ParticleTypes.SNOWFLAKE, x, at.y + 2 + level.getRandom().nextDouble() * 3, z, 1, 0.1, 0, 0.1, 0.05);
						}
						if (beat % 20 == 0) {
							for (LivingEntity e : near(c, at, radius)) {
								e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1), p);
								e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 40, e.getTicksFrozen() + 30));
								if (beat % 40 == 0) hurt(c, e, (1 + tier * 0.5) * power, "ice");
							}
						}
					});
				}
				level.playSound(null, BlockPos.containing(at), SoundEvents.POWDER_SNOW_PLACE, SoundSource.PLAYERS, 1.5f, 0.5f);
				return true;
			}
			case "earthquake" -> {
				Vec3 at = center(c);
				double radius = 6 + tier;
				for (int k = 0; k < 60; k += 10) {
					Tasks.later(k, () -> {
						level.playSound(null, BlockPos.containing(at), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.5f, 0.4f);
						for (LivingEntity e : near(c, at, radius)) {
							if (!e.onGround()) continue;
							e.setDeltaMovement(e.getDeltaMovement().add((level.getRandom().nextDouble() - 0.5) * 0.3, 0.32, (level.getRandom().nextDouble() - 0.5) * 0.3));
							e.syncVelocity = true;
							e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1), p);
							hurt(c, e, (1 + tier * 0.5) * power, "stone");
							BlockState under = level.getBlockState(e.blockPosition().below());
							if (!under.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), e.getX(), e.getY() + 0.1, e.getZ(), 12, 0.5, 0.1, 0.5, 0.1);
						}
						for (int i = 0; i < 16; i++) {
							double a = level.getRandom().nextDouble() * Math.PI * 2, r = level.getRandom().nextDouble() * radius;
							BlockPos pos = BlockPos.containing(at.x + Math.cos(a) * r, at.y - 0.5, at.z + Math.sin(a) * r);
							BlockState ground = level.getBlockState(pos);
							if (!ground.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 4, 0.3, 0.05, 0.3, 0.1);
						}
					});
				}
				return true;
			}
			case "bloodletting" -> {
				Vec3 at = center(c);
				for (int k = 0; k < 80; k += 10) {
					Tasks.later(k, () -> {
						double drained = 0;
						for (LivingEntity e : near(c, at, 5)) {
							float before = e.getHealth();
							hurt(c, e, 1.0 + 0.3 * tier, "blood");
							drained += Math.max(0, before - e.getHealth());
							Fx.line(level, e.getBoundingBox().getCenter(), p.getBoundingBox().getCenter(), 0xB0102A, null, 2);
						}
						if (drained > 0 && p.isAlive()) p.heal((float) (drained * 0.5));
					});
				}
				return true;
			}
			case "tendrils" -> {
				Vec3 at = ground(level, Targeting.point(p, Math.max(16, c.range)));
				List<LivingEntity> caught = near(c, at, 5);
				for (int k = 0; k < 30; k += 2) {
					int beat = k;
					Tasks.later(k, () -> {
						for (LivingEntity e : caught) {
							if (!e.isAlive()) continue;
							Vec3 pull = at.subtract(e.position()).multiply(1, 0, 1);
							if (pull.length() > 1.0 && com.omnispell.boss.Bosses.defOf(e) == null && !MobControl.immune(e)) {
								Vec3 v = pull.normalize().scale(0.25);
								e.setDeltaMovement(v.x, e.getDeltaMovement().y, v.z);
								e.syncVelocity = true;
							}
							if (beat % 4 == 0) Fx.line(level, e.position(), e.position().add(0, 1.4, 0), 0x1A0A24, ParticleTypes.SQUID_INK, 2);
						}
					});
				}
				for (LivingEntity e : caught) {
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 70, 4), p);
					hurt(c, e, (2 + tier) * power, "shadow");
				}
				return true;
			}
			case "chain_lightning" -> {
				LivingEntity first = Targeting.entity(p, Math.max(16, c.range));
				if (first == null) {
					List<LivingEntity> around = near(c, Targeting.point(p, Math.max(16, c.range)), 4);
					first = around.isEmpty() ? null : around.get(0);
				}
				if (first == null) return false;
				var bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.snapTo(first.getX(), first.getY(), first.getZ());
					bolt.setVisualOnly(true);
					level.addFreshEntity(bolt);
				}
				Set<LivingEntity> struck = new HashSet<>();
				LivingEntity current = first;
				for (int jump = 0; jump <= 2 + tier && current != null; jump++) {
					struck.add(current);
					hurt(c, current, (4 + tier) * power * (jump == 0 ? 1.0 : 0.7), "storm");
					LivingEntity next = null;
					double best = 7 * 7;
					for (LivingEntity e : near(c, current.position(), 7)) {
						double d = e.distanceToSqr(current);
						if (!struck.contains(e) && d < best) {
							best = d;
							next = e;
						}
					}
					if (next != null) Fx.line(level, current.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), 0xBFE6FF, ParticleTypes.ELECTRIC_SPARK, 4);
					current = next;
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}
}
