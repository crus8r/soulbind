package com.omnispell.spell;

import com.omnispell.data.Word;
import com.omnispell.data.WordBank;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rare exploration effects that pull, swallow and bury: black holes, vortices,
 * vacuums, sinking ground and quicksand. Each belongs to only a few exact
 * pairings of base, command kind and intention kind, the ones whose words
 * already describe it, so they are found rather than handed out.
 */
public final class Rifts {
	private Rifts() {
	}

	/** Which pairings (base-command-intention, as the exploration table names them) take which effect. */
	private static final Map<String, String> ENTRIES = Map.ofEntries(
			Map.entry("void-launch-destruction", "black_hole"),
			Map.entry("void-grow-destruction", "black_hole"),
			Map.entry("void-summon-destruction", "black_hole"),
			Map.entry("wind-shift-destruction", "vacuum"),
			Map.entry("wind-bind-destruction", "vacuum"),
			Map.entry("wind-bind-manipulation", "vortex"),
			Map.entry("wind-grow-manipulation", "vortex"),
			Map.entry("wind-grow-destruction", "vortex"),
			Map.entry("stone-shift-destruction", "quicksand"),
			Map.entry("stone-grow-manipulation", "sink"),
			Map.entry("shadow-shift-destruction", "sink"),
			Map.entry("fire-shift-destruction", "sink"),
			Map.entry("water-shift-destruction", "sink"));

	public static boolean handles(String toggle) {
		return switch (toggle == null ? "" : toggle) {
			case "black_hole", "vacuum", "vortex", "sink", "quicksand" -> true;
			default -> false;
		};
	}

	public static String forEntry(String entryId) {
		return entryId == null ? null : ENTRIES.get(entryId);
	}

	/** The pairing a spell's words make, as the exploration table names it. */
	public static String entryId(Spell spell) {
		String base = null, command = null, intention = null;
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word == null) continue;
			switch (word.type) {
				case "base" -> base = word.explore;
				case "command" -> command = word.explore;
				case "intention" -> intention = word.explore;
				default -> {
				}
			}
		}
		return base == null || command == null || intention == null ? null : base + "-" + command + "-" + intention;
	}

	public static String label(String toggle) {
		return switch (toggle) {
			case "black_hole" -> "Black Hole";
			case "vacuum" -> "Vacuum";
			case "vortex" -> "Vortex";
			case "sink" -> "Sinking Ground";
			case "quicksand" -> "Quicksand";
			default -> toggle;
		};
	}

	public static double cooldownSeconds(String toggle) {
		return switch (toggle) {
			case "black_hole" -> 25;
			case "quicksand" -> 16;
			case "sink", "vortex" -> 12;
			default -> 10;
		};
	}

	public static String describe(String toggle, int tier) {
		int t = Math.max(1, Math.min(5, tier));
		return switch (toggle) {
			case "black_hole" -> "Opens a black hole where you look for " + (3 + t / 2) + " seconds. It drags in every creature and loose item within " + (5 + t) + " blocks, crushes what reaches its heart, and collapses with a blast.";
			case "vacuum" -> "Tears the air out of a space " + (2 * (3 + t) + 1) + " blocks across where you look: creatures are sucked inward and left breathless, fires go out, and the air rushes back in with a thump.";
			case "vortex" -> "Catches what you look at in a vortex and holds it spinning in the air" + (t >= 3 ? ", dragging in whatever stands near it" : "") + ".";
			case "sink" -> "The ground where you look turns soft. Creatures standing on it sink in up to their chests and are held there for " + (3 + t) + " seconds, hurting, before the ground lets them go.";
			case "quicksand" -> "Turns the ground where you look to loose gravel for a while. Creatures caught on it sink in up to their chests and are held fast for " + (4 + t) + " seconds.";
			default -> "";
		};
	}

	public static boolean cast(Cast c, String toggle) {
		ServerPlayer p = c.player;
		ServerLevel level = c.level;
		String element = c.spell.element == null ? "void" : c.spell.element;
		int tier = Math.max(1, Math.min(5, c.spell.tier));
		double power = c.spell.hasCharge("Maxima") ? 1.5 : c.spell.hasCharge("Minima") ? 0.7 : 1.0;
		double lasting = c.spell.hasCharge("Duratus") ? 1.6 : 1.0;
		WordBank.Flavor flavor = WordBank.flavor(element);
		int color = Fx.color(flavor == null ? "void" : flavor.color);
		Vec3 at = Targeting.point(p, Math.max(16, c.range));
		return switch (toggle) {
			case "black_hole" -> blackHole(c, at, tier, power, lasting, color);
			case "vacuum" -> vacuum(c, at, tier, power);
			case "vortex" -> {
				LivingEntity target = Targeting.entity(p, Math.max(16, c.range));
				List<LivingEntity> near = target != null ? List.of(target) : Targeting.around(p, at, 3, false);
				LivingEntity first = near.stream().filter(e -> !com.omnispell.familiar.Familiars.isFamiliar(e)).findFirst().orElse(null);
				if (first == null) yield c.fail("There is nothing there for the wind to take hold of.");
				Terraform.vortex(level, p, first, Math.min(5, tier + (power > 1 ? 1 : 0)));
				yield true;
			}
			case "sink" -> {
				int held = (int) ((3 + tier) * 20 * lasting);
				int caught = sinkArea(c, at, 2 + tier / 2 + (power > 1 ? 1 : 0), held, element, null);
				groundRing(level, at, 2 + tier / 2, null);
				if (caught == 0) c.tell("The ground softens, but nothing stands on it.");
				yield true;
			}
			case "quicksand" -> {
				int radius = 2 + tier / 2 + (power > 1 ? 1 : 0);
				int held = (int) ((4 + tier) * 20 * lasting);
				int changed = gravel(level, BlockPos.containing(at), radius, held + 40);
				int caught = sinkArea(c, at, radius, held, "stone", Blocks.GRAVEL.defaultBlockState());
				if (changed == 0 && caught == 0) yield c.fail("There is no ground there to turn.");
				level.playSound(null, BlockPos.containing(at), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
				yield true;
			}
			default -> false;
		};
	}

	private static boolean spared(ServerPlayer caster, Entity e) {
		return e == caster || com.omnispell.familiar.Familiars.isFamiliar(e) || (e instanceof LivingEntity l && MobControl.immune(l));
	}

	private static void hurt(Cast c, LivingEntity target, double amount, String element) {
		float damage = (float) (amount * c.damageBonus(element));
		Modifiers.withSpellElement(element, () -> target.hurtServer(c.level, c.level.damageSources().indirectMagic(c.player, c.player), damage));
	}

	// ----- black hole -----

	public static boolean blackHole(Cast c, Vec3 at, int tier, double power, double lasting, int color) {
		ServerLevel level = c.level;
		ServerPlayer p = c.player;
		Vec3 heart = at.add(0, 1.5, 0);
		double reach = (5 + tier) * Math.sqrt(power);
		int ticks = (int) ((3 + tier / 2) * 20 * lasting);
		level.playSound(null, BlockPos.containing(heart), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.8f, 0.5f);
		for (int n = 0; n < ticks; n += 2) {
			int beat = n;
			Tasks.later(n, () -> {
				// The spiral: matter falling in.
				for (int k = 0; k < 6; k++) {
					double a = beat * 0.35 + k * Math.PI / 3;
					double r = reach * (0.25 + 0.75 * ((beat / 2 + k * 3) % 12) / 12.0);
					Vec3 from = heart.add(Math.cos(a) * r, (k % 3 - 1) * 0.4, Math.sin(a) * r);
					Vec3 in = heart.subtract(from).normalize();
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, 0, in.x, in.y, in.z, 0.6);
				}
				level.sendParticles(ParticleTypes.SQUID_INK, heart.x, heart.y, heart.z, 3, 0.15, 0.15, 0.15, 0.0);
				level.sendParticles(ParticleTypes.SCULK_SOUL, heart.x, heart.y, heart.z, 1, 0.3, 0.3, 0.3, 0.0);
				AABB box = new AABB(heart, heart).inflate(reach);
				for (Entity e : level.getEntities((Entity) null, box, x -> x instanceof LivingEntity || x instanceof ItemEntity)) {
					if (spared(p, e) || e.isSpectator()) continue;
					Vec3 toward = heart.subtract(e.getBoundingBox().getCenter());
					double dist = toward.length();
					if (dist > reach || dist < 0.01) continue;
					double pull = Math.min(0.55, 0.12 + 0.9 / Math.max(1.0, dist)) * (com.omnispell.boss.Bosses.defOf(e) != null ? 0.3 : 1.0);
					Vec3 v = e.getDeltaMovement().scale(0.5).add(toward.normalize().scale(pull));
					e.setDeltaMovement(v);
					e.fallDistance = 0;
					e.syncVelocity = true;
					if (e instanceof LivingEntity living && dist < 1.6 && beat % 10 == 0) hurt(c, living, (2 + tier) * power, "void");
				}
			});
		}
		Tasks.later(ticks, () -> {
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, heart.x, heart.y, heart.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, heart.x, heart.y, heart.z, 120, 0.5, 0.5, 0.5, 1.2);
			level.playSound(null, BlockPos.containing(heart), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 0.5f);
			for (LivingEntity e : Targeting.around(p, heart, 3.5, false)) {
				if (spared(p, e)) continue;
				hurt(c, e, (6 + 3 * tier) * power, "void");
				Vec3 out = e.position().subtract(heart).multiply(1, 0, 1);
				if (out.lengthSqr() < 0.01) out = new Vec3(1, 0, 0);
				out = out.normalize().scale(1.2);
				e.push(out.x, 0.5, out.z);
				e.syncVelocity = true;
			}
		});
		c.tell("Space folds in on itself.");
		return true;
	}

	// ----- vacuum -----

	public static boolean vacuum(Cast c, Vec3 at, int tier, double power) {
		ServerLevel level = c.level;
		ServerPlayer p = c.player;
		double radius = (3 + tier) * Math.sqrt(power);
		Vec3 heart = at.add(0, 1, 0);
		// The air rushing out, drawn as wisps falling inward.
		for (int i = 0; i < 60; i++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double y = level.getRandom().nextDouble() * 2.5 - 0.5;
			Vec3 from = heart.add(Math.cos(a) * radius, y, Math.sin(a) * radius);
			Vec3 in = heart.subtract(from).normalize();
			level.sendParticles(ParticleTypes.CLOUD, from.x, from.y, from.z, 0, in.x, in.y, in.z, 0.5);
		}
		int caught = 0;
		for (LivingEntity e : Targeting.around(p, heart, radius, false)) {
			if (spared(p, e)) continue;
			Vec3 toward = heart.subtract(e.position());
			Vec3 v = toward.normalize().scale(Math.min(1.4, 0.3 + toward.length() * 0.18));
			e.setDeltaMovement(v.x, Math.max(0.1, v.y), v.z);
			e.syncVelocity = true;
			e.setAirSupply(Math.max(-19, e.getAirSupply() - 200));
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), p);
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), p);
			caught++;
		}
		// Without air, fire dies.
		BlockPos center = BlockPos.containing(heart);
		int r = (int) Math.ceil(radius);
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 2, r))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) level.removeBlock(pos, false);
		}
		level.playSound(null, center, SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 1.2f, 0.5f);
		Tasks.later(12, () -> {
			level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, heart.x, heart.y, heart.z, 1, 0, 0, 0, 0);
			level.playSound(null, center, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2f, 0.4f);
			for (LivingEntity e : Targeting.around(p, heart, 2.8, false)) if (!spared(p, e)) hurt(c, e, (5 + 2 * c.spell.tier) * power, "wind");
		});
		if (caught == 0) c.tell("The air is torn away, but nothing was caught in it.");
		return true;
	}

	// ----- sinking ground -----

	private static void groundRing(ServerLevel level, Vec3 at, double radius, BlockState as) {
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI / 12;
			BlockPos pos = BlockPos.containing(at.x + Math.cos(a) * radius, at.y - 0.5, at.z + Math.sin(a) * radius);
			BlockState ground = as != null ? as : level.getBlockState(pos);
			if (ground.isAir()) continue;
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 3, 0.3, 0.05, 0.3, 0.05);
		}
	}

	/** Every creature on the ground in the area sinks in. Returns how many were caught. */
	public static int sinkArea(Cast c, Vec3 at, double radius, int ticks, String element, BlockState as) {
		int caught = 0;
		for (LivingEntity e : Targeting.around(c.player, at, radius + 0.5, false)) {
			if (spared(c.player, e) || !e.onGround() || com.omnispell.boss.Bosses.defOf(e) != null) continue;
			if (Math.abs(e.getY() - at.y) > 2.5) continue;
			sink(c, e, ticks, element, as);
			caught++;
		}
		return caught;
	}

	private static final Map<java.util.UUID, Long> SINKING = new HashMap<>();

	/**
	 * One creature sinks into the ground up to its chest and is held there, hurting, then
	 * rises again. Mobs pass into the ground for real; a player is held at the surface
	 * instead, since a player's client will not stay inside a block.
	 */
	public static void sink(Cast c, LivingEntity e, int ticks, String element, BlockState as) {
		ServerLevel level = c.level;
		if (SINKING.containsKey(e.getUUID())) return;
		SINKING.put(e.getUUID(), Tasks.now());
		double x = e.getX(), y0 = e.getY(), z = e.getZ();
		boolean mob = e instanceof Mob;
		double depth = mob ? Math.min(1.4, e.getBbHeight() * 0.6) : 0;
		int down = 16;
		e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks + down, 6, false, false));
		for (int n = 0; n <= ticks + down; n++) {
			int beat = n;
			Tasks.later(n, () -> {
				if (!e.isAlive() || e.level() != level) return;
				double sunk = depth * Math.min(1.0, beat / (double) down);
				if (mob) {
					e.noPhysics = true;
					e.setPos(x, y0 - sunk, z);
				}
				e.setDeltaMovement(0, 0, 0);
				e.syncVelocity = true;
				if (beat % 4 == 0) {
					BlockState ground = as != null ? as : level.getBlockState(BlockPos.containing(x, y0 - 0.5, z));
					if (!ground.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, y0 + 0.1, z, 6, e.getBbWidth() * 0.5, 0.05, e.getBbWidth() * 0.5, 0.05);
				}
				if (beat % 20 == 10) {
					hurt(c, e, 1.5 + 0.5 * c.spell.tier, element);
					switch (element) {
						case "fire" -> e.igniteForSeconds(2);
						case "water" -> e.setAirSupply(Math.max(-19, e.getAirSupply() - 60));
						case "shadow" -> e.addEffect(new MobEffectInstance(MobEffects.WITHER, 30, 0), c.player);
						default -> {
						}
					}
					if (element.equals("fire")) level.sendParticles(ParticleTypes.LAVA, x, y0 + 0.2, z, 3, 0.3, 0.05, 0.3, 0.0);
				}
			});
		}
		Tasks.later(ticks + down + 1, () -> {
			SINKING.remove(e.getUUID());
			if (!e.isAlive()) return;
			if (mob) {
				e.noPhysics = false;
				e.setPos(x, y0 + 0.05, z);
			}
			e.setDeltaMovement(0, 0.2, 0);
			e.syncVelocity = true;
		});
	}

	/** Turns the surface where you look to gravel for a while, where it will not fall. Returns how many blocks changed. */
	public static int gravel(ServerLevel level, BlockPos center, int radius, int ticks) {
		List<BlockPos> changed = new ArrayList<>();
		Map<BlockPos, BlockState> before = new HashMap<>();
		BlockPos top = center;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius + 1) continue;
				// Find the surface in this column, near the aimed height.
				for (int dy = 1; dy >= -3; dy--) {
					BlockPos pos = top.offset(dx, dy, dz);
					BlockState state = level.getBlockState(pos);
					BlockState above = level.getBlockState(pos.above());
					if (state.isAir() || !(above.isAir() || above.canBeReplaced())) continue;
					if (soft(state) && !level.getBlockState(pos.below()).isAir() && level.getBlockState(pos.below()).getFluidState().isEmpty()) {
						before.put(pos.immutable(), state);
						if (above.canBeReplaced() && !above.isAir()) level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
						level.setBlock(pos, Blocks.GRAVEL.defaultBlockState(), 2);
						changed.add(pos.immutable());
					}
					break;
				}
			}
		}
		for (BlockPos pos : changed) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 4, 0.3, 0.05, 0.3, 0.05);
		Tasks.later(ticks, () -> {
			for (BlockPos pos : changed) {
				if (level.getBlockState(pos).is(Blocks.GRAVEL)) level.setBlock(pos, before.get(pos), 3);
			}
		});
		return changed.size();
	}

	private static boolean soft(BlockState state) {
		return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
				|| state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.MUD) || state.is(Blocks.CLAY)
				|| state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE)
				|| state.is(Blocks.TUFF) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL);
	}
}
