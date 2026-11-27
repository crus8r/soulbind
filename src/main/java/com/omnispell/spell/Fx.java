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
			case "storm", "lightning", "thunder", "electric" -> 0xB8DCFF;
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

	/** Which element a colour key stands for, so each element gets its own particles. */
	public static String elementOf(String colorKey, String fallback) {
		if (colorKey == null) return fallback;
		return switch (colorKey) {
			case "fire", "ice", "shadow", "light", "nature", "arcane", "holy", "dark", "blood", "spirit", "poison", "neutral" -> colorKey;
			default -> fallback;
		};
	}

	private static void puff(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
		if (count > 0) level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
	}

	/**
	 * The burst where a spell lands. Every element has its own mix of particles,
	 * and the display grows with the spell's tier: tier 1 is a flicker, tier 3
	 * adds a shockwave ring, tier 4 a rising column, tier 5 a pillar from the sky.
	 */
	public static void impact(ServerLevel level, Vec3 at, String element, int tier) {
		int t = Math.max(1, Math.min(5, tier));
		int n = 4 + t * 7;
		double spread = 0.2 + t * 0.16;
		Vec3 mid = at.add(0, 0.4, 0);
		int color = color(element);
		switch (element == null ? "arcane" : element) {
			case "fire" -> {
				puff(level, ParticleTypes.FLAME, mid, n, spread, 0.03 + t * 0.01);
				puff(level, ParticleTypes.SMOKE, mid, n / 2, spread, 0.02);
				if (t >= 2) puff(level, ParticleTypes.LAVA, mid, t * 2, spread * 0.5, 0);
				if (t >= 4) puff(level, ParticleTypes.LARGE_SMOKE, mid, t * 3, spread, 0.03);
			}
			case "ice" -> {
				puff(level, ParticleTypes.SNOWFLAKE, mid, n, spread, 0.03);
				puff(level, ParticleTypes.ITEM_SNOWBALL, mid, n / 2, spread, 0.05);
				if (t >= 3) puff(level, ParticleTypes.WHITE_ASH, mid, n * 2, spread * 1.5, 0);
				if (t >= 4) puff(level, ParticleTypes.CLOUD, mid, t * 3, spread, 0.02);
			}
			case "shadow", "dark" -> {
				puff(level, ParticleTypes.SMOKE, mid, n, spread, 0.01);
				puff(level, ParticleTypes.SQUID_INK, mid, n / 3, spread * 0.6, 0.02);
				if (t >= 3) puff(level, ParticleTypes.SCULK_SOUL, mid, t * 2, spread, 0.03);
				if (t >= 4) puff(level, ParticleTypes.REVERSE_PORTAL, mid, n * 2, spread, 0.3);
			}
			case "light", "holy" -> {
				puff(level, ParticleTypes.END_ROD, mid, n, spread, 0.05);
				puff(level, ParticleTypes.GLOW, mid, n / 2, spread, 0.02);
				if (t >= 3) puff(level, ParticleTypes.FIREWORK, mid, t * 4, spread * 0.5, 0.12);
				if (t >= 4) puff(level, net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | color), mid, 1, 0, 0);
			}
			case "nature" -> {
				puff(level, ParticleTypes.CHERRY_LEAVES, mid, n, spread, 0.02);
				puff(level, ParticleTypes.HAPPY_VILLAGER, mid, n / 2, spread, 0);
				if (t >= 3) puff(level, ParticleTypes.SPORE_BLOSSOM_AIR, mid, n * 2, spread * 1.5, 0);
				if (t >= 4) puff(level, ParticleTypes.COMPOSTER, mid, n, spread, 0.05);
			}
			case "blood" -> {
				puff(level, ParticleTypes.DAMAGE_INDICATOR, mid, n / 2, spread, 0.1);
				puff(level, ParticleTypes.CRIMSON_SPORE, mid, n * 2, spread, 0);
				if (t >= 3) puff(level, ParticleTypes.FALLING_LAVA, mid.add(0, 1, 0), n, spread, 0);
			}
			case "spirit" -> {
				puff(level, ParticleTypes.SOUL, mid, n / 2, spread, 0.03);
				puff(level, ParticleTypes.SOUL_FIRE_FLAME, mid, n, spread, 0.02);
				if (t >= 3) puff(level, ParticleTypes.SCULK_SOUL, mid, t * 2, spread, 0.04);
			}
			case "poison" -> {
				puff(level, ParticleTypes.ITEM_SLIME, mid, n, spread, 0.05);
				puff(level, ParticleTypes.SNEEZE, mid, n / 2, spread, 0.02);
				if (t >= 3) puff(level, ParticleTypes.WARPED_SPORE, mid, n * 2, spread * 1.5, 0);
			}
			case "neutral" -> {
				puff(level, ParticleTypes.CRIT, mid, n, spread, 0.2);
				puff(level, ParticleTypes.POOF, mid, n / 3, spread, 0.02);
			}
			default -> { // arcane
				puff(level, ParticleTypes.ENCHANT, mid.add(0, 0.6, 0), n * 2, spread, 0.6);
				puff(level, ParticleTypes.WITCH, mid, n / 2, spread, 0.02);
				if (t >= 3) puff(level, ParticleTypes.ENCHANTED_HIT, mid, n, spread, 0.3);
				if (t >= 4) puff(level, ParticleTypes.PORTAL, mid, n * 2, spread, 0.5);
			}
		}
		ParticleOptions extra = switch (element == null ? "" : element) {
			case "fire" -> ParticleTypes.SMALL_FLAME;
			case "ice" -> ParticleTypes.SNOWFLAKE;
			case "shadow", "dark" -> ParticleTypes.SMOKE;
			case "light", "holy" -> ParticleTypes.END_ROD;
			case "nature" -> ParticleTypes.HAPPY_VILLAGER;
			case "spirit" -> ParticleTypes.SOUL_FIRE_FLAME;
			default -> null;
		};
		if (t >= 3) ring(level, at, 1.0 + t * 0.45, color, extra);
		if (t >= 4) {
			ring(level, at, 2.0 + t * 0.6, color, null);
			helix(level, at, 2.5 + t * 0.5, 0.8, color, extra);
		}
		if (t >= 5) {
			// A pillar from the sky and a last wide shockwave.
			line(level, at.add(0, 14, 0), at, color, extra, 5);
			line(level, at.add(0.3, 14, 0.3), at.add(0.3, 0, 0.3), color, null, 5);
			ring(level, at, 6.0, color, extra);
			puff(level, ParticleTypes.EXPLOSION, mid, 3, 1.2, 0);
		}
	}

	/**
	 * Plays a spell's visuals: from the caster's hand toward {@code to} for directed
	 * patterns, around {@code to} for area patterns, then the element's own burst
	 * where it lands, sized by the spell's intensity.
	 */
	public static void play(ServerLevel level, Spell.Visual visual, Vec3 from, Vec3 to) {
		play(level, visual, from, to, elementOf(visual.color1, "arcane"));
	}

	public static void play(ServerLevel level, Spell.Visual visual, Vec3 from, Vec3 to, String element) {
		int c1 = color(visual.color1);
		int c2 = color(visual.color2);
		ParticleOptions extra = themed(visual.particles);
		int intensity = Math.max(1, Math.min(5, visual.intensity));
		switch (visual.pattern == null ? "directional" : visual.pattern) {
			case "beam" -> {
				line(level, from, to, c1, extra, intensity);
				line(level, from.add(0, 0.12, 0), to.add(0, 0.12, 0), c2, null, intensity);
				if (intensity >= 4) line(level, from.add(0, -0.12, 0), to.add(0, -0.12, 0), c1, null, intensity);
				burst(level, to, c1, extra, intensity);
			}
			case "radial", "nova", "pulse" -> {
				ring(level, to, 1.2 + intensity * 0.5, c1, extra);
				ring(level, to, 0.6 + intensity * 0.25, c2, null);
				burst(level, to.add(0, 1.0, 0), c1, extra, intensity);
			}
			case "spiral", "vortex" -> {
				helix(level, to, 2.2 + intensity * 0.2, 0.9, c1, extra);
				helix(level, to.add(0, 0.1, 0), 2.2 + intensity * 0.2, 0.6, c2, null);
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
		impact(level, to, element, intensity);
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

	/** Stronger spells sound bigger: louder and deeper, with a rumble from tier 4. */
	public static void sound(ServerLevel level, Vec3 at, String element, int tier) {
		int t = Math.max(1, Math.min(5, tier));
		sound(level, at, element, 0.6f + t * 0.15f);
		if (t >= 4) level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.25f * (t - 3), 0.7f);
		if (t >= 5) level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.2f);
	}
}
