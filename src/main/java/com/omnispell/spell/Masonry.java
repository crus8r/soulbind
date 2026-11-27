package com.omnispell.spell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Conjured masonry: what a conjuration with Bridge, Wall or Tower raises. Unlike the plain
 * mounds of an environment spell, it is real dressed stone in the element's own kind (stone
 * bricks, nether bricks, prismarine, quartz, blackstone, purpur...), with rails, battlements
 * and lanterns. It does not last: after a while it turns to coloured glass, so you can see it
 * going, then comes apart and is gone. Breaking it gives nothing.
 */
public final class Masonry {
	private Masonry() {
	}

	/** Which structure a conjuration's attribute word asks for, or null. */
	public static String structure(Spell spell) {
		for (String id : spell.words) {
			switch (id) {
				case "attribute:Bridge" -> {
					return "bridge";
				}
				case "attribute:Wall" -> {
					return "wall";
				}
				case "attribute:Tower" -> {
					return "tower";
				}
				default -> {
				}
			}
		}
		return construct(spell);
	}

	/**
	 * A conjuration with no creature in it (no creature or weapon word, and nothing the Oracle named)
	 * whose command closes around things builds instead: a cage on what you look at if it is meant
	 * in malice, a shelter over yourself otherwise.
	 */
	private static String construct(Spell spell) {
		if (!"conjuration".equals(spell.type) || Conjuring.namedBody(spell, null) != null) return null;
		String kind = "", intention = "";
		for (String id : spell.words) {
			com.omnispell.data.Word w = com.omnispell.data.WordBank.word(id);
			if (w == null) continue;
			if (w.type.equals("command")) kind = w.explore == null ? "" : w.explore;
			if (w.type.equals("intention")) intention = w.english;
		}
		if (!kind.equals("envelop")) return null;
		return switch (intention) {
			case "Malice", "Weakening", "Banishment", "Binding", "Nullification" -> "cage";
			default -> "shelter";
		};
	}

	private record Palette(BlockState deck, BlockState rail, BlockState light, Block glass) {
	}

	private static Palette palette(String element) {
		return switch (element) {
			case "fire" -> new Palette(Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICK_WALL.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), Blocks.STAINED_GLASS.pick(DyeColor.ORANGE));
			case "ice" -> new Palette(Blocks.PACKED_ICE.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE));
			case "water" -> new Palette(Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.PRISMARINE_WALL.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), Blocks.STAINED_GLASS.pick(DyeColor.CYAN));
			case "light" -> new Palette(Blocks.QUARTZ_BRICKS.defaultBlockState(), Blocks.END_STONE_BRICK_WALL.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), Blocks.STAINED_GLASS.pick(DyeColor.YELLOW));
			case "shadow" -> new Palette(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), Blocks.SOUL_LANTERN.defaultBlockState(), Blocks.STAINED_GLASS.pick(DyeColor.BLACK));
			case "void" -> new Palette(Blocks.PURPUR_BLOCK.defaultBlockState(), Blocks.PURPUR_PILLAR.defaultBlockState(), Blocks.END_ROD.defaultBlockState(), Blocks.STAINED_GLASS.pick(DyeColor.PURPLE));
			case "blood" -> new Palette(Blocks.RED_NETHER_BRICKS.defaultBlockState(), Blocks.RED_NETHER_BRICK_WALL.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), Blocks.STAINED_GLASS.pick(DyeColor.RED));
			case "nature" -> new Palette(Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), Blocks.STAINED_GLASS.pick(DyeColor.GREEN));
			case "wind" -> new Palette(Blocks.CALCITE.defaultBlockState(), Blocks.DIORITE_WALL.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), Blocks.STAINED_GLASS.pick(DyeColor.WHITE));
			case "storm" -> new Palette(Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE));
			case "arcane" -> new Palette(Blocks.AMETHYST_BLOCK.defaultBlockState(), Blocks.DEEPSLATE_TILE_WALL.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), Blocks.STAINED_GLASS.pick(DyeColor.MAGENTA));
			default -> new Palette(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICK_WALL.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_GRAY));
		};
	}

	/** One block of a structure: where, what it replaced, and what it is now. */
	private static final class Stone {
		final BlockPos pos;
		final BlockState before;
		BlockState now;

		Stone(BlockPos pos, BlockState before, BlockState now) {
			this.pos = pos;
			this.before = before;
			this.now = now;
		}
	}

	/** Every position that is conjured masonry right now, so breaking it can be made to give nothing. */
	private static final Set<BlockPos> STANDING = new HashSet<>();

	public static boolean isConjured(BlockPos pos) {
		return STANDING.contains(pos);
	}

	private static final class Builder {
		final ServerLevel level;
		final List<Stone> stones = new ArrayList<>();

		Builder(ServerLevel level) {
			this.level = level;
		}

		void set(BlockPos pos, BlockState state) {
			BlockPos at = pos.immutable();
			BlockState before = level.getBlockState(at);
			if (!before.canBeReplaced() || STANDING.contains(at)) return;
			if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(at)).isEmpty()) return;
			level.setBlock(at, state, 3);
			stones.add(new Stone(at, before, state));
			STANDING.add(at);
		}
	}

	/** Seconds the structure stands, before Duratus. */
	public static int seconds(Spell spell) {
		int tier = Math.max(1, Math.min(5, spell.tier));
		return "cage".equals(structure(spell)) ? 8 + 2 * tier : 40 + 8 * tier;
	}

	public static String describe(Spell spell) {
		String what = structure(spell);
		if (what == null) return "";
		int secs = (int) (seconds(spell) * (spell.hasCharge("Duratus") ? 1.6 : 1.0));
		String kind = switch (what) {
			case "bridge" -> "a dressed-stone bridge, railed and lit, across the way you face";
			case "wall" -> "a battlemented wall in front of you";
			case "cage" -> "a barred cage around the creature you look at, which is crushed and smothered inside it for as long as it holds";
			case "shelter" -> "a domed shelter over you, with a doorway the way you face";
			default -> "a tower under your feet that lifts you to its battlements";
		};
		return ("cage".equals(what) ? "It closes " : "It raises ") + kind + ", built in its element's own stone. It stands about " + secs + " seconds: near the end it turns to glass, so you can see it going, and then it is gone.";
	}

	public static boolean build(Cast cast) {
		ServerPlayer p = cast.player;
		ServerLevel level = cast.level;
		Spell spell = cast.spell;
		String what = structure(spell);
		if (what == null) return false;
		Palette pal = palette(AttackForms.element(spell));
		boolean big = spell.hasCharge("Maxima");
		int tier = Math.max(1, Math.min(5, spell.tier));
		Builder b = new Builder(level);
		Direction facing = p.getDirection();
		Direction side = facing.getClockWise();
		BlockPos feet = p.blockPosition();
		switch (what) {
			case "bridge" -> {
				int length = (int) ((12 + 4 * tier) * (big ? 1.5 : 1.0));
				int half = big ? 2 : 1;
				BlockPos deck = feet.below();
				for (int i = 1; i <= length; i++) {
					BlockPos row = deck.relative(facing, i);
					if (!level.getBlockState(row.above()).canBeReplaced() && !level.getBlockState(row.above(2)).canBeReplaced()) break; // a cliff in the way ends it
					for (int s = -half; s <= half; s++) b.set(row.relative(side, s), pal.deck);
					for (int s : new int[]{-half - 1, half + 1}) {
						b.set(row.relative(side, s), pal.deck);
						b.set(row.relative(side, s).above(), pal.rail);
						if (i % 5 == 0) b.set(row.relative(side, s).above(2), pal.light);
					}
					if (i % 6 == 3) {
						// piers down to the ground below
						for (int s : new int[]{-half - 1, half + 1}) {
							BlockPos down = row.relative(side, s).below();
							for (int d = 0; d < 12 && level.getBlockState(down).canBeReplaced(); d++, down = down.below()) b.set(down, pal.deck);
						}
					}
				}
			}
			case "wall" -> {
				int half = big ? 5 : 3;
				int height = 3 + tier / 2 + (big ? 2 : 0);
				BlockPos center = feet.relative(facing, 3);
				for (int s = -half; s <= half; s++) {
					BlockPos column = center.relative(side, s);
					BlockPos ground = column;
					for (int d = 0; d < 4 && level.getBlockState(ground.below()).canBeReplaced(); d++) ground = ground.below();
					for (int h = 0; h < height; h++) b.set(ground.above(h), pal.deck);
					if (Math.floorMod(s, 2) == 0) b.set(ground.above(height), pal.rail); // battlements
					if (s == -half || s == half) b.set(ground.above(height + 1), pal.light);
				}
			}
			case "cage" -> {
				LivingEntity target = Targeting.entity(p, cast.range);
				if (target == null || Defenses.ally(p, target)) return cast.fail("The cage needs something to close around: look at a creature.");
				if (target.getBbWidth() > 2.6 || target.getBbHeight() > 4.2 || com.omnispell.boss.Bosses.defOf(target) != null) return cast.fail("It is too great to be caged.");
				AABB box = target.getBoundingBox();
				BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
				BlockPos max = BlockPos.containing(box.maxX - 1e-4, box.maxY - 1e-4, box.maxZ - 1e-4);
				BlockState bars = Blocks.IRON_BARS.defaultBlockState();
				for (BlockPos pos : BlockPos.betweenClosed(min.offset(-1, 0, -1), max.offset(1, 2, 1))) {
					boolean wallX = pos.getX() < min.getX() || pos.getX() > max.getX();
					boolean wallZ = pos.getZ() < min.getZ() || pos.getZ() > max.getZ();
					boolean roof = pos.getY() == max.getY() + 1, crown = pos.getY() == max.getY() + 2;
					if (crown) {
						if (wallX && wallZ) b.set(pos, pal.light);
					} else if (roof) b.set(pos, pal.deck);
					else if (wallX && wallZ) b.set(pos, pal.deck); // corner posts
					else if (wallX || wallZ) b.set(pos, bars);
				}
				// the bars join up into a proper cage
				for (Stone st : b.stones) {
					if (!st.now.is(Blocks.IRON_BARS)) continue;
					BlockState joined = Block.updateFromNeighbourShapes(st.now, level, st.pos);
					level.setBlock(st.pos, joined, 2);
					st.now = joined;
				}
				target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
				target.syncVelocity = true;
				Vec3 held = target.position();
				int life = (int) (seconds(spell) * 20 * (spell.hasCharge("Duratus") ? 1.6 : 1.0));
				for (int t = 20; t < life; t += 20) {
					Tasks.later(t, () -> {
						if (!target.isAlive() || target.position().distanceToSqr(held) > 4) return; // out of the cage, out of its grip
						target.setAirSupply(Math.max(-19, target.getAirSupply() - 80));
						target.hurtServer(level, level.damageSources().indirectMagic(p, p), (float) ((1.5 + 0.5 * tier) * cast.damageBonus(spell.code.element)));
						level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 8, 0.3, 0.3, 0.3, 0.01);
					});
				}
			}
			case "shelter" -> {
				int r = big ? 4 : 3;
				for (int dx = -r; dx <= r; dx++) for (int dy = 0; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (d > r + 0.5 || d <= r - 0.5) continue;
					BlockPos pos = feet.offset(dx, dy, dz);
					// a doorway the way you face, two blocks high
					boolean door = dy <= 1 && (facing.getAxis() == Direction.Axis.X ? dz == 0 && Integer.signum(dx) == facing.getStepX() : dx == 0 && Integer.signum(dz) == facing.getStepZ());
					if (door) continue;
					boolean window = dy == 1 && (Math.abs(dx) + Math.abs(dz)) % 3 == 0;
					b.set(pos, dy == r ? pal.light : window ? pal.glass.defaultBlockState() : pal.deck);
				}
			}
			default -> { // tower
				int height = 4 + tier + (big ? 4 : 0);
				BlockPos base = feet;
				p.teleportTo(level, p.getX(), base.getY() + height + 1.05, p.getZ(), java.util.Set.of(), p.getYRot(), p.getXRot(), true);
				for (int h = 0; h < height; h++) {
					for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b.set(base.offset(dx, h, dz), pal.deck);
				}
				BlockPos top = base.above(height);
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						b.set(top.offset(dx, 0, dz), pal.deck);
						boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
						if (edge) b.set(top.offset(dx, 1, dz), pal.rail);
						if (Math.abs(dx) == 2 && Math.abs(dz) == 2) b.set(top.offset(dx, 2, dz), pal.light);
					}
				}
			}
		}
		if (b.stones.isEmpty()) return cast.fail("There is no room here to build.");
		level.playSound(null, p.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.2f, 0.7f);
		level.playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
		for (Stone s : b.stones) {
			if (level.getRandom().nextInt(4) == 0) level.sendParticles(ParticleTypes.ENCHANT, s.pos.getX() + 0.5, s.pos.getY() + 1, s.pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.3);
		}
		schedule(level, b.stones, pal.glass, (int) (seconds(spell) * 20 * (spell.hasCharge("Duratus") ? 1.6 : 1.0)));
		return true;
	}

	/** Solid for most of its life; glass for the last third; then it comes apart, a few blocks at a time. */
	private static void schedule(ServerLevel level, List<Stone> stones, Block glass, int ticks) {
		int toGlass = (int) (ticks * 0.65);
		int crumble = (int) (ticks * 0.9);
		Tasks.later(toGlass, () -> {
			for (Stone s : stones) {
				if (!level.getBlockState(s.pos).equals(s.now)) continue;
				BlockState next = glass.defaultBlockState();
				level.setBlock(s.pos, next, 2);
				s.now = next;
			}
			if (!stones.isEmpty()) level.playSound(null, stones.get(0).pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 0.6f);
		});
		List<Stone> order = new ArrayList<>(stones);
		Collections.shuffle(order, new java.util.Random(order.size() * 31L));
		int span = Math.max(1, ticks - crumble);
		for (int i = 0; i < order.size(); i++) {
			Stone s = order.get(i);
			Tasks.later(crumble + (int) ((long) i * span / order.size()), () -> remove(level, s));
		}
		Tasks.later(ticks, () -> {
			for (Stone s : stones) remove(level, s);
		});
	}

	private static void remove(ServerLevel level, Stone s) {
		if (!STANDING.remove(s.pos)) return;
		if (!level.getBlockState(s.pos).equals(s.now)) return; // someone already broke it
		level.setBlock(s.pos, s.before, 3);
		level.sendParticles(ParticleTypes.POOF, s.pos.getX() + 0.5, s.pos.getY() + 0.5, s.pos.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
	}

	/** Breaking conjured masonry gives nothing: the block simply goes. Returns false to stop the normal break. */
	public static boolean breakBlock(ServerLevel level, BlockPos pos) {
		if (!STANDING.contains(pos)) return true;
		STANDING.remove(pos);
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.01);
		return false;
	}
}
