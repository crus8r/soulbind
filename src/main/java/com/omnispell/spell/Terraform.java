package com.omnispell.spell;

import com.omnispell.Omnispell;
import com.omnispell.boss.Lairs;
import com.omnispell.data.Word;
import com.omnispell.data.WordBank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Environment spells that shape the land: carving tunnels, blasting craters,
 * raising walls and domes, and mending ground. The stronger the spell's tier,
 * the further it reaches. They are also the only way through a lair's sealed door.
 */
public final class Terraform {
	private Terraform() {
	}

	/** The land-shaping effect that replaces one of the old combat-style exploration effects, or null. */
	public static String remap(String toggle) {
		if (toggle == null) return null;
		return switch (toggle) {
			case "projectile", "beam", "shrapnel", "construct_weapon", "disintegrate" -> "carve";
			case "area_damage", "eruption", "detonate" -> "crater";
			case "barrier_wall" -> "raise_wall";
			case "force_field", "deflect" -> "dome";
			case "armor", "heal" -> "mend_land";
			default -> null;
		};
	}

	public static boolean handles(String toggle) {
		return switch (toggle == null ? "" : toggle) {
			case "carve", "crater", "raise_wall", "dome", "mend_land" -> true;
			default -> false;
		};
	}

	public static String effectName(String toggle) {
		return switch (toggle) {
			case "carve" -> "Carve";
			case "crater" -> "Crater";
			case "raise_wall" -> "Raise Wall";
			case "dome" -> "Dome";
			default -> "Mend Land";
		};
	}

	public static String describe(String toggle, Spell spell) {
		Block block = material(spell);
		String stuff = block == null ? "hardened magic that fades after a while" : block.getName().getString().toLowerCase();
		int t = Math.max(1, Math.min(5, spell.tier));
		return switch (toggle) {
			case "carve" -> "Bores a tunnel " + carveLength(t) + " blocks long where you look. Ores are left standing in the walls. Strong enough, it opens a lair's sealed door.";
			case "crater" -> "Blasts away the ground where you look, " + (craterRadius(t) * 2 + 1) + " blocks across. Ores are left behind.";
			case "raise_wall" -> "Raises a wall of " + stuff + " in front of you, " + wallWidth(t) + " wide and " + wallHeight(t) + " high.";
			case "dome" -> "Closes a dome of " + stuff + " around you, " + (domeRadius(t) * 2 + 1) + " blocks across.";
			default -> "Mends the land where you look, " + (mendRadius(t) * 2 + 1) + " blocks across: fills water, lava and small hollows with " + stuff + " and puts out fires.";
		};
	}

	public static double cooldownSeconds(String toggle) {
		return switch (toggle) {
			case "carve" -> 6;
			case "crater", "raise_wall", "mend_land" -> 10;
			default -> 15;
		};
	}

	static int carveLength(int tier) {
		return new int[]{5, 8, 12, 16, 22}[tier - 1];
	}

	static int craterRadius(int tier) {
		return tier + 1;
	}

	static int wallWidth(int tier) {
		return 3 + 2 * tier;
	}

	static int wallHeight(int tier) {
		return 2 + tier;
	}

	static int domeRadius(int tier) {
		return 2 + tier;
	}

	static int mendRadius(int tier) {
		return 1 + 2 * tier;
	}

	/**
	 * What the spell builds with. Common stuff is real and stays; anything
	 * precious or strange (null here) is conjured as hardened magic that fades.
	 */
	public static Block material(Spell spell) {
		String english = "";
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word != null && word.type.equals("base")) english = word.english;
		}
		return switch (english) {
			case "Earth" -> Blocks.DIRT;
			case "Mud" -> Blocks.MUD;
			case "Sand" -> Blocks.SAND;
			case "Dust" -> Blocks.GRAVEL;
			case "Ice", "Frost", "Frostbite", "Sleet" -> Blocks.PACKED_ICE;
			case "Wood" -> Blocks.OAK_PLANKS;
			case "Nature", "Vine", "Verdant", "Thorn" -> Blocks.MOSS_BLOCK;
			case "Lava", "Ember", "Heat", "Heatwave", "Sulfur" -> Blocks.BASALT;
			case "Pressure", "Gravity", "Metal" -> Blocks.COBBLED_DEEPSLATE;
			default -> "stone".equals(spell.element) ? Blocks.COBBLESTONE : null;
		};
	}

	/** How hard a block this tier can remove. Stone at tier 1; obsidian only at tier 5. */
	private static float hardnessLimit(int tier) {
		return new float[]{2.0f, 3.0f, 5.0f, 30.0f, 50.0f}[tier - 1];
	}

	private static boolean removable(ServerLevel level, BlockPos pos, BlockState state, int tier) {
		if (state.isAir() || !state.getFluidState().isEmpty() && state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) return false;
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0 || hardness > hardnessLimit(tier) || state.hasBlockEntity()) return false;
		var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		if (key.getNamespace().equals(Omnispell.MOD_ID) && !key.getPath().startsWith("faewood")) return false;
		String path = key.getPath();
		return !path.endsWith("_ore") && !path.equals("ancient_debris") && !path.contains("spawner");
	}

	private static final class Job {
		final Cast c;
		final int tier;
		final Lairs.Guard guard;
		int changed;
		int sealed;

		Job(Cast c) {
			this.c = c;
			this.tier = Math.max(1, Math.min(5, c.spell.tier));
			this.guard = new Lairs.Guard(c.level);
		}

		void remove(BlockPos pos) {
			BlockState state = c.level.getBlockState(pos);
			if (!removable(c.level, pos, state, tier)) return;
			int needed = guard.tierNeeded(pos);
			if (needed > tier) {
				sealed = Math.max(sealed, needed);
				return;
			}
			c.level.destroyBlock(pos, false);
			changed++;
		}

		void place(BlockPos pos, Block real, BlockState realState) {
			BlockState existing = c.level.getBlockState(pos);
			if (!existing.isAir() && !existing.canBeReplaced()) return;
			if (!c.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(pos)).isEmpty()) return;
			if (real == null) {
				if (TempBlocks.place(c.level, pos, TempBlocks.material(c.spell.element), 600 + 300 * tier)) changed++;
			} else {
				c.level.setBlockAndUpdate(pos, realState == null ? real.defaultBlockState() : realState);
				changed++;
			}
		}
	}

	public static boolean cast(Cast c, String toggle) {
		ServerPlayer p = c.player;
		ServerLevel level = c.level;
		Job job = new Job(c);
		int tier = job.tier;
		Block block = material(c.spell);
		String fx = c.spell.code.element == null ? "arcane" : c.spell.code.element;

		switch (toggle) {
			case "carve" -> {
				Vec3 eye = p.getEyePosition();
				Vec3 look = p.getLookAngle();
				boolean level0 = Math.abs(p.getXRot()) < 30;
				int floor = p.blockPosition().getY();
				double radius = tier >= 4 ? 2.2 : 1.5;
				Set<BlockPos> seen = new HashSet<>();
				for (double d = 1; d <= carveLength(tier); d += 0.5) {
					Vec3 at = eye.add(look.scale(d));
					BlockPos center = BlockPos.containing(at);
					int r = (int) Math.ceil(radius);
					for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
						if (level0 && pos.getY() < floor) continue; // leave a floor to walk on
						if (Vec3.atCenterOf(pos).distanceToSqr(at) > radius * radius) continue;
						if (seen.add(pos.immutable())) job.remove(pos);
					}
				}
				Fx.impact(level, eye.add(look.scale(3)), fx, tier);
			}
			case "crater" -> {
				BlockHitResult hit = Targeting.block(p, 12 + 6 * tier);
				if (hit.getType() == HitResult.Type.MISS) return c.fail("There is nothing there to break.");
				BlockPos center = hit.getBlockPos();
				int r = craterRadius(tier);
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
					if (pos.distSqr(center) <= r * r + 1) job.remove(pos);
				}
				Fx.impact(level, Vec3.atCenterOf(center), fx, tier);
				level.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.1f);
			}
			case "raise_wall" -> {
				Direction facing = p.getDirection();
				Direction side = facing.getClockWise();
				BlockPos center = p.blockPosition().relative(facing, 3);
				int half = wallWidth(tier) / 2;
				for (int w = -half; w <= half; w++) {
					BlockPos foot = center.relative(side, w);
					for (int down = 0; down < 6 && level.getBlockState(foot.below()).canBeReplaced(); down++) foot = foot.below();
					for (int up = 0; up < 4 && !level.getBlockState(foot).canBeReplaced(); up++) foot = foot.above();
					for (int h = 0; h < wallHeight(tier); h++) job.place(foot.above(h), block, null);
				}
				Fx.impact(level, Vec3.atCenterOf(center.above()), fx, tier);
			}
			case "dome" -> {
				BlockPos center = p.blockPosition();
				int r = domeRadius(tier);
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, 0, -r), center.offset(r, r, r))) {
					double d = Math.sqrt(pos.distSqr(center));
					if (d <= r + 0.5 && d > r - 0.6) job.place(pos, block, null);
				}
				Fx.impact(level, p.position().add(0, 1, 0), fx, tier);
			}
			default -> {
				BlockHitResult hit = Targeting.block(p, 12 + 6 * tier);
				BlockPos center = hit.getType() == HitResult.Type.MISS ? p.blockPosition() : hit.getBlockPos();
				int r = mendRadius(tier);
				boolean earth = block == Blocks.DIRT;
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (dx * dx + dz * dz > r * r + 1) continue;
						// Work down each column from just above the centre: fill liquid and shallow hollows.
						BlockPos top = null;
						for (int dy = 3; dy >= -4; dy--) {
							BlockPos pos = center.offset(dx, dy, dz);
							BlockState state = level.getBlockState(pos);
							if (state.is(net.minecraft.tags.BlockTags.FIRE)) {
								level.removeBlock(pos, false);
								job.changed++;
								continue;
							}
							boolean liquid = !state.getFluidState().isEmpty();
							boolean hollow = state.canBeReplaced() && dy < 0 && !liquid && solidBeside(level, pos);
							if (!liquid && !hollow) continue;
							if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(pos)).isEmpty()) continue;
							if (block == null) {
								if (TempBlocks.place(level, pos, TempBlocks.material(c.spell.element), 600 + 300 * tier)) job.changed++;
							} else {
								level.setBlockAndUpdate(pos, block.defaultBlockState());
								job.changed++;
							}
							if (top == null) top = pos.immutable();
						}
						if (earth && top != null && level.getBlockState(top.above()).isAir()) {
							level.setBlockAndUpdate(top, Blocks.GRASS_BLOCK.defaultBlockState());
							if (level.getRandom().nextInt(6) == 0) level.setBlockAndUpdate(top.above(), Blocks.SHORT_GRASS.defaultBlockState());
						}
					}
				}
				Fx.impact(level, Vec3.atCenterOf(center.above()), fx, tier);
				level.playSound(null, center, SoundEvents.GRASS_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
			}
		}
		if (job.sealed > 0) {
			c.tell("The lair's seal holds. It would take an environment spell of tier " + job.sealed + " to open it; this one is tier " + tier + ".");
		}
		if (job.changed == 0) return c.fail(job.sealed > 0 ? "The seal does not give." : "The land does not answer here.");
		return true;
	}

	private static boolean solidBeside(ServerLevel level, BlockPos pos) {
		int solid = 0;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (!level.getBlockState(pos.relative(direction)).canBeReplaced()) solid++;
		}
		return solid >= 3 && !level.getBlockState(pos.below()).canBeReplaced();
	}
}
