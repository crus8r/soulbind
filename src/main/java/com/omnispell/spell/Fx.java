package com.omnispell.spell;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Turns a spell's visual manifest (pattern, colours, particles, intensity) into
 * Minecraft particles and sounds. The keys are the same presets the website uses.
 */
public final class Fx {
	private Fx() {
	}

	public static int color(String key) {
		if (key == null) return 0x7052EB;
		if (key.startsWith("#") && key.length() == 7) {
			try {
				return Integer.parseInt(key.substring(1), 16);
			} catch (NumberFormatException ignored) {
				return 0x7052EB;
			}
		}
		return switch (key) {
			case "fire" -> 0xF25A26;
			case "ice" -> 0x59C3F2;
			case "shadow" -> 0x7029A3;
			case "light" -> 0xFAD46B;
			case "nature" -> 0x2EB84A;
			case "holy" -> 0xFFE680;
			case "dark" -> 0x59264D;
			case "blood" -> 0xB81414;
			case "spirit" -> 0x94D1D1;
			case "poison" -> 0x3F981B;
			case "neutral" -> 0x99A3B8;
			default -> 0x7052EB; // arcane
		};
	}

	/** A vanilla particle that matches the manifest's particle preset. */
	public static ParticleOptions themed(String key) {
		if (key == null) return ParticleTypes.ENCHANT;
		return switch (key) {
			case "embers" -> ParticleTypes.FLAME;
			case "snowflakes" -> ParticleTypes.SNOWFLAKE;
			case "wisps" -> ParticleTypes.SOUL;
			case "motes" -> ParticleTypes.END_ROD;
			case "shards" -> ParticleTypes.CRIT;
			case "drops" -> ParticleTypes.SPLASH;
			case "skulls" -> ParticleTypes.SCULK_SOUL;
			case "leaves" -> ParticleTypes.CHERRY_LEAVES;
			case "runes" -> ParticleTypes.ENCHANT;
			case "bubbles" -> ParticleTypes.BUBBLE_POP;
			case "feathers" -> ParticleTypes.CLOUD;
			default -> ParticleTypes.ENCHANTED_HIT; // sparks
		};
	}

	private static DustParticleOptions dust(int color, float size) {
		return new DustParticleOptions(color & 0xFFFFFF, size);
	}

	public static void line(ServerLevel level, Vec3 from, Vec3 to, int color, ParticleOptions extra, int intensity) {
		Vec3 delta = to.subtract(from);
		double length = delta.length();
		if (length < 0.01) return;
		Vec3 step = delta.scale(1.0 / length);
		double spacing = 0.35;
		DustParticleOptions dust = dust(color, 0.9f + intensity * 0.15f);
		int n = 0;
		for (double d = 0.8; d <= length; d += spacing, n++) {
			Vec3 p = from.add(step.scale(d));
			level.sendParticles(dust, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
			if (extra != null && n % 3 == 0) level.sendParticles(extra, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
		}
	}

	public static void ring(ServerLevel level, Vec3 center, double radius, int color, ParticleOptions extra) {
		DustParticleOptions dust = dust(color, 1.2f);
		int points = Math.max(16, (int) (radius * 12));
		for (int i = 0; i < points; i++) {
			double angle = Math.PI * 2 * i / points;
			double x = center.x + Math.cos(angle) * radius;
			double z = center.z + Math.sin(angle) * radius;
			level.sendParticles(dust, x, center.y + 0.15, z, 1, 0.0, 0.05, 0.0, 0.0);
			if (extra != null && i % 4 == 0) level.sendParticles(extra, x, center.y + 0.2, z, 1, 0.05, 0.1, 0.05, 0.01);
		}
	}

	public static void burst(ServerLevel level, Vec3 center, int color, ParticleOptions extra, int intensity) {
		int count = 12 + intensity * 10;
		double spread = 0.3 + intensity * 0.12;
		level.sendParticles(dust(color, 1.4f), center.x, center.y, center.z, count, spread, spread, spread, 0.0);
		if (extra != null) level.sendParticles(extra, center.x, center.y, center.z, count / 2, spread, spread, spread, 0.04);
	}

	public static void helix(ServerLevel level, Vec3 base, double height, double radius, int color, ParticleOptions extra) {
		DustParticleOptions dust = dust(color, 1.0f);
		int points = 36;
		for (int i = 0; i < points; i++) {
			double t = (double) i / points;
			double angle = t * Math.PI * 4;
			double x = base.x + Math.cos(angle) * radius;
			double z = base.z + Math.sin(angle) * radius;
			level.sendParticles(dust, x, base.y + t * height, z, 1, 0.0, 0.0, 0.0, 0.0);
			if (extra != null && i % 6 == 0) level.sendParticles(extra, x, base.y + t * height, z, 1, 0.0, 0.0, 0.0, 0.01);
		}
	}

	public static void rain(ServerLevel level, Vec3 center, double radius, int color, ParticleOptions extra) {
		level.sendParticles(dust(color, 1.1f), center.x, center.y + 3.0, center.z, 40, radius * 0.5, 1.2, radius * 0.5, 0.0);
		if (extra != null) level.sendParticles(extra, center.x, center.y + 3.0, center.z, 20, radius * 0.5, 1.0, radius * 0.5, 0.02);
	}

	/**
	 * Plays a spell's visuals: from the caster's hand toward {@code to} for directed
	 * patterns, around {@code to} for area patterns.
	 */
	public static void play(ServerLevel level, Spell.Visual visual, Vec3 from, Vec3 to) {
		int c1 = color(visual.color1);
		int c2 = color(visual.color2);
		ParticleOptions extra = themed(visual.particles);
		int intensity = Math.max(1, Math.min(5, visual.intensity));
		switch (visual.pattern == null ? "directional" : visual.pattern) {
			case "beam" -> {
				line(level, from, to, c1, extra, intensity);
				line(level, from.add(0, 0.12, 0), to.add(0, 0.12, 0), c2, null, intensity);
				burst(level, to, c1, extra, intensity);
			}
			case "radial", "nova", "pulse" -> {
				ring(level, to, 1.2 + intensity * 0.5, c1, extra);
				ring(level, to, 0.6 + intensity * 0.25, c2, null);
				burst(level, to.add(0, 1.0, 0), c1, extra, intensity);
			}
			case "spiral", "vortex" -> {
				helix(level, to, 2.2, 0.9, c1, extra);
				helix(level, to.add(0, 0.1, 0), 2.2, 0.6, c2, null);
			}
			case "rain" -> {
				rain(level, to, 2.0 + intensity * 0.4, c1, extra);
				burst(level, to.add(0, 0.5, 0), c2, null, intensity);
			}
			case "wave" -> {
				line(level, from, to, c1, extra, intensity);
				ring(level, to, 1.5 + intensity * 0.3, c2, extra);
			}
			case "scatter" -> {
				burst(level, to.add(0, 0.8, 0), c1, extra, intensity + 1);
				burst(level, to.add(0, 0.8, 0), c2, null, intensity);
			}
			default -> { // directional
				line(level, from, to, c1, extra, intensity);
				burst(level, to, c2, extra, intensity);
			}
		}
	}

	public static void sound(ServerLevel level, Vec3 at, String element, float volume) {
		SoundEvent sound = switch (element == null ? "" : element) {
			case "fire" -> SoundEvents.FIRECHARGE_USE;
			case "ice" -> SoundEvents.GLASS_BREAK;
			case "shadow", "dark", "void" -> SoundEvents.WARDEN_SONIC_CHARGE;
			case "light", "holy" -> SoundEvents.AMETHYST_BLOCK_CHIME;
			case "nature", "wind" -> SoundEvents.AZALEA_LEAVES_BREAK;
			case "blood" -> SoundEvents.HONEY_BLOCK_SLIDE;
			case "water" -> SoundEvents.GENERIC_SPLASH;
			default -> SoundEvents.EVOKER_CAST_SPELL;
		};
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, 1.0f + (level.getRandom().nextFloat() - 0.5f) * 0.2f);
	}
}
