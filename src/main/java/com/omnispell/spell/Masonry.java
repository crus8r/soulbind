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
		// A creature or weapon word calls something living, before any masonry: a Bridge and a Horse is a horse.
		if ("conjuration".equals(spell.type) && livingWord(spell)) return null;
		// Something no one can stand on (lightning, light, wind...) the Oracle may read as a trail instead of a bridge.
		if ("conjuration".equals(spell.type) && "trail".equals(spell.planned("form"))) return "trail";
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
		if (!"conjuration".equals(spell.type)) return null;
		// The Oracle's plan, unless a creature or weapon word asks for something living.
		String planned = spell.planned("form");
		if (planned != null && !livingWord(spell)) {
			if (planned.equals("cage") || planned.equals("shelter")) return planned;
			if (planned.equals("creature")) return null;
		}
		if (Conjuring.namedBody(spell, null) != null) return null;
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
			// A colour word with a gentler intention calls a being of that colour (a hollow knight, a sprite) instead.
			default -> Conjuring.colourWord(spell) != null ? null : "shelter";
		};
	}

	private static boolean livingWord(Spell spell) {
		for (String id : spell.words) {
			com.omnispell.data.Word w = com.omnispell.data.WordBank.word(id);
			if (w != null && w.type.equals("attribute") && (Conjuring.creatureWord(w.english) || java.util.Set.of("Bow", "Arrow", "Sword", "Blade/Knife", "Club", "Spear").contains(w.english))) return true;
		}
		return false;
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
		final ServerPlayer owner;
		final List<Stone> stones = new ArrayList<>();

		Builder(ServerLevel level, ServerPlayer owner) {
			this.level = level;
			this.owner = owner;
		}

		void set(BlockPos pos, BlockState state) {
			BlockPos at = pos.immutable();
			BlockState before = level.getBlockState(at);
			if (!before.canBeReplaced() || STANDING.contains(at)) return;
			List<LivingEntity> there = level.getEntitiesOfClass(LivingEntity.class, new AABB(at), LivingEntity::isAlive);
			for (LivingEntity e : there) if (e == owner || Defenses.ally(owner, e)) return; // never built into a friend
			level.setBlock(at, state, 3);
			stones.add(new Stone(at, before, state));
			STANDING.add(at);
			for (LivingEntity e : there) liftOnto(level, e); // a foe in the way ends up standing on it
		}
	}

	/**
	 * Something caught where masonry rises is lifted onto it: to the first place above where it fits, up to six
	 * blocks. Only if there is no room at all does it stay where it is (and the stone closes on it).
	 */
	public static void liftOnto(ServerLevel level, LivingEntity e) {
		if (!e.isAlive()) return;
		AABB box = e.getBoundingBox();
		double base = Math.floor(box.minY);
		for (int dy = 1; dy <= 6; dy++) {
			double y = base + dy;
			AABB moved = box.move(0, y - box.minY, 0);
			if (!level.noCollision(e, moved)) continue;
			e.teleportTo(e.getX(), y, e.getZ());
			e.setDeltaMovement(0, 0, 0);
			e.syncVelocity = true;
			return;
		}
	}

	// ------------------------------------------------------------------ what a bridge can do

	/**
	 * What a conjured bridge does besides carry you. Each comes only when the spell's own words, or what the
	 * Oracle wrote it does, call for it; a bridge that claims nothing is a plain bridge.
	 * <ul>
	 * <li>spikes: it strikes (strikes, attacks, batters, jagged, shards, thorns...): spikes erupt under foes on or against it</li>
	 * <li>gated: it protects (Shield, Warding, shields, protects, bulwark...): a covered tunnel with gates foes cannot open</li>
	 * <li>snare: it binds (Binding, Bind, bound, ensnares...): foes on it can barely move</li>
	 * <li>mend: it heals (Restoration, Soothing, heals, soothes, vigor...): friends on it mend</li>
	 * <li>swift: it hastens (Accelerate, swift, speed...): friends run along it</li>
	 * <li>repel: it throws (Repel, Throw, hurls, knocks...): foes are flung off its edges</li>
	 * <li>veil: it hides (Concealment, conceals, unseen...): friends crossing it are invisible</li>
	 * <li>feather: it cushions (floats, glides, feather...): friends who fall from it drift down</li>
	 * <li>glow: it reveals (Insight, Illuminate, radiant, reveals...): foes near it glow</li>
	 * <li>turrets: it shoots (shoots, bolts, arrows, volley...): its lamps fire at foes within ten blocks</li>
	 * <li>collapse: it gives way (collapses, crumbles, plunges...): its deck drops out under foes, then closes</li>
	 * </ul>
	 */
	public static final Set<String> BRIDGE_TOGGLES = Set.of("spikes", "gated", "snare", "mend", "swift", "repel", "veil", "feather", "glow", "turrets", "collapse");

	public static Set<String> bridgeToggles(Spell spell) {
		Set<String> on = new java.util.LinkedHashSet<>();
		if (spell == null || !"conjuration".equals(spell.type)) return on;
		String command = "", intention = "";
		for (String id : spell.words) {
			com.omnispell.data.Word w = com.omnispell.data.WordBank.word(id);
			if (w == null) continue;
			if (w.type.equals("command")) command = w.english;
			if (w.type.equals("intention")) intention = w.english;
		}
		String claim = (SpellCrafting.oracleText(spell) + " " + (spell.effect == null ? "" : spell.effect) + " "
				+ (spell.code == null ? "" : String.join(" ", spell.code.modifiers))).toLowerCase(java.util.Locale.ROOT);
		if (says(claim, "strike", "attack", "batter", "pummel", "lacerat", "impal", "jagged", "shard", "spike", "thorn", "barb", "pierc", "rend", "smash", "lash", "maul")
				|| java.util.Set.of("Pierce", "Fracture", "Sever", "Cleave", "Crush", "Assault").contains(command) || intention.equals("Malice")) on.add("spikes");
		if (says(claim, "shield", "protect", "shelter", "bulwark", "guard", "safe", "sanctuary", "fortress", "fortified", "ward ", "wards ")
				|| java.util.Set.of("Shield", "Protect", "Cover", "Harbor", "Fortify", "Seal", "Close").contains(command)
				|| java.util.Set.of("Warding", "Fortitude", "Preservation").contains(intention)) on.add("gated");
		if (says(claim, "bind", "bound ", "binding", "snare", "ensnar", "entangl", "fetter", "shackl", "chained", "trap", "rooted", "roots ")
				|| java.util.Set.of("Bind", "Ensnare", "Detain", "Paralyze").contains(command) || intention.equals("Binding")) on.add("snare");
		if (says(claim, "heal ", "heals", "healing", "healed", "sooth", "mend", "restor", "regenerat", "vigor", "rejuvenat")
				|| java.util.Set.of("Mend", "Regenerate", "Soothe", "Calm").contains(command) || java.util.Set.of("Restoration", "Soothing").contains(intention)) on.add("mend");
		if (says(claim, "swift", "speed", "haste", "hasten", "rush", "fleet")
				|| java.util.Set.of("Accelerate", "Mobilize", "Move", "Traverse").contains(command)) on.add("swift");
		if (says(claim, "repel", "hurl", "fling", "knock", "throws", "sweeps")
				|| java.util.Set.of("Repel", "Throw", "Banish", "Dismiss", "Displace").contains(command)) on.add("repel");
		if (says(claim, "conceal", "hidden", "hide", "unseen", "invisib", "cloak", "veil")
				|| java.util.Set.of("Conceal", "Obscure").contains(command) || intention.equals("Concealment")) on.add("veil");
		if (says(claim, "float", "feather", "glide", "drift", "levitat", "weightless", "cushion")) on.add("feather");
		if (says(claim, "illuminat", "radian", "glow", "luminous", "reveal", "beacon")
				|| java.util.Set.of("Illuminate", "Reveal", "Expose", "Enlighten").contains(command) || intention.equals("Insight")) on.add("glow");
		if (says(claim, "shoot", "shoots", "bolts", "arrow", "volley", "barrage", "turret", "fires at", "fires bolts", "chain_lightning")) on.add("turrets");
		if (says(claim, "collaps", "crumbl", "gives way", "give way", "plunge", "pitfall", "swallow")
				|| java.util.Set.of("Disintegrate", "Erode").contains(command)) on.add("collapse");
		// What the Oracle planned for it, on top of what its words and text call for.
		if (spell.plan != null && spell.plan.extras != null) {
			for (String extra : spell.plan.extras) if (BRIDGE_TOGGLES.contains(extra)) on.add(extra);
		}
		return on;
	}

	/** Does the text use one of these word beginnings as the start of a word? */
	private static boolean says(String text, String... stems) {
		for (String stem : stems) {
			int i = text.indexOf(stem);
			while (i >= 0) {
				if (i == 0 || !Character.isLetter(text.charAt(i - 1))) return true;
				i = text.indexOf(stem, i + 1);
			}
		}
		return false;
	}

	private static Block gateFor(String element) {
		return switch (element) {
			case "fire", "blood" -> Blocks.CRIMSON_FENCE_GATE;
			case "void", "arcane" -> Blocks.WARPED_FENCE_GATE;
			case "shadow" -> Blocks.DARK_OAK_FENCE_GATE;
			case "water" -> Blocks.MANGROVE_FENCE_GATE;
			case "nature" -> Blocks.JUNGLE_FENCE_GATE;
			case "light" -> Blocks.BIRCH_FENCE_GATE;
			case "storm", "wind" -> Blocks.PALE_OAK_FENCE_GATE;
			default -> Blocks.SPRUCE_FENCE_GATE;
		};
	}

	private static BlockState tunnelLight(String element) {
		return switch (element) {
			case "fire", "blood" -> Blocks.SHROOMLIGHT.defaultBlockState();
			case "ice", "water" -> Blocks.SEA_LANTERN.defaultBlockState();
			case "nature" -> Blocks.VERDANT_FROGLIGHT.defaultBlockState();
			case "void", "arcane", "shadow" -> Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
			default -> Blocks.OCHRE_FROGLIGHT.defaultBlockState();
		};
	}

	/** A bridge at work: what it does to whoever is on it, near it, or falling from it, every quarter second while it stands. */
	public static final class BridgeWork {
		final Cast cast;
		final ServerLevel level;
		final ServerPlayer owner;
		final Set<String> toggles;
		final Direction facing, side;
		final Set<BlockPos> deck = new HashSet<>();
		final Set<BlockPos> all = new HashSet<>();
		final List<BlockPos> posts = new ArrayList<>();
		final java.util.Map<String, Long> ready = new java.util.HashMap<>();
		final String element;
		final double base;
		BlockState spikeLook;
		AABB box;

		BridgeWork(Cast cast, Set<String> toggles, Direction facing, Direction side) {
			this.cast = cast;
			this.level = cast.level;
			this.owner = cast.player;
			this.toggles = toggles;
			this.facing = facing;
			this.side = side;
			this.element = AttackForms.element(cast.spell);
			this.base = strength(cast.spell);
		}

		/** The spell's own number for what it strikes for each turn: the summoned creature's, or its primary value. */
		static double strength(Spell spell) {
			for (String text : spell.code.modifiers) {
				String[] parts = text.split(":");
				if (parts.length > 1 && parts[0].trim().equals("summoned_creature")) {
					try {
						return Double.parseDouble(parts[1].trim());
					} catch (NumberFormatException ignored) {
					}
				}
			}
			return spell.code.primaryValue > 0 ? spell.code.primaryValue : Math.max(1, spell.tier) * 6.0;
		}

		static boolean metal(Spell spell) {
			return spell.words.contains("base:Metal") || spell.words.contains("base:Magnetism") || spell.words.contains("base:Mercury");
		}

		static String describe(Spell spell, Set<String> toggles) {
			StringBuilder sb = new StringBuilder();
			String element = AttackForms.element(spell);
			String spikes = metal(spell) ? "Spikes of iron" : switch (element) {
				case "nature" -> "Thorned roots";
				case "ice" -> "Spikes of ice";
				case "fire" -> "Jets of flame";
				case "storm" -> "Forks of lightning";
				case "void" -> "Shards of the void";
				case "light" -> "Lances of light";
				case "shadow" -> "Blades of shadow";
				default -> "Spikes of stone";
			};
			if (toggles.contains("spikes")) sb.append(' ').append(spikes).append(" erupt from it under any hostile creature that sets foot on it or presses against it, about once a second.");
			if (toggles.contains("turrets")) sb.append(" Its lamps loose bolts of its magic at hostile creatures within ten blocks.");
			if (toggles.contains("snare")) sb.append(" Hostile creatures on it are held fast and can barely move.");
			if (toggles.contains("repel")) sb.append(" It flings hostile creatures off its sides.");
			if (toggles.contains("collapse")) sb.append(" Its deck gives way under hostile creatures, dropping them through, and closes again behind them.");
			if (toggles.contains("glow")) sb.append(" Hostile creatures near it glow, so nothing creeps up unseen.");
			if (toggles.contains("mend")) sb.append(" You and your friends mend while you stand on it.");
			if (toggles.contains("swift")) sb.append(" You and your friends run swiftly along it.");
			if (toggles.contains("veil")) sb.append(" Friends crossing it are hidden from sight.");
			if (toggles.contains("feather")) sb.append(" Anyone friendly who falls from it drifts gently down.");
			return sb.toString();
		}

		void start(int life, BlockState deckLook) {
			spikeLook = metal(cast.spell) ? Blocks.IRON_BLOCK.defaultBlockState() : switch (element) {
				case "nature" -> Blocks.OAK_LOG.defaultBlockState();
				case "ice" -> Blocks.PACKED_ICE.defaultBlockState();
				case "fire" -> Blocks.MAGMA_BLOCK.defaultBlockState();
				default -> deckLook;
			};
			double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
			for (BlockPos q : all) {
				minX = Math.min(minX, q.getX());
				minY = Math.min(minY, q.getY());
				minZ = Math.min(minZ, q.getZ());
				maxX = Math.max(maxX, q.getX() + 1);
				maxY = Math.max(maxY, q.getY() + 1);
				maxZ = Math.max(maxZ, q.getZ() + 1);
			}
			if (all.isEmpty()) return;
			box = new AABB(minX, minY, minZ, maxX, maxY + 3, maxZ);
			for (int t = 5; t < life * 0.9; t += 5) {
				int at = t;
				Tasks.later(t, () -> pulse(at));
			}
		}

		private boolean ready(net.minecraft.world.entity.Entity e, String what, int every) {
			String key = e.getUUID() + what;
			long now = level.getGameTime();
			Long next = ready.get(key);
			if (next != null && next > now) return false;
			ready.put(key, now + every);
			return true;
		}

		private boolean friend(LivingEntity e) {
			return e == owner || Defenses.ally(owner, e);
		}

		private boolean foe(LivingEntity e) {
			if (friend(e)) return false;
			if (e instanceof net.minecraft.world.entity.monster.Enemy) return true;
			return e instanceof net.minecraft.world.entity.Mob m && m.getTarget() != null && friend(m.getTarget());
		}

		private boolean onDeck(LivingEntity e) {
			BlockPos under = BlockPos.containing(e.getX(), e.getY() - 0.3, e.getZ());
			return deck.contains(under) || deck.contains(under.below());
		}

		private boolean touching(LivingEntity e) {
			AABB near = e.getBoundingBox().inflate(0.8);
			for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(near.minX, near.minY, near.minZ), BlockPos.containing(near.maxX, near.maxY, near.maxZ))) {
				if (all.contains(q)) return true;
			}
			return false;
		}

		private float hurt(LivingEntity e, double amount) {
			float before = e.getHealth();
			float dealt = (float) (amount * cast.scale * cast.damageBonus(cast.spell.code.element));
			Modifiers.withSpellElement(element, () -> e.hurtServer(level, level.damageSources().indirectMagic(owner, owner), dealt));
			float done = Math.max(0, before - e.getHealth());
			if (done > 0 && owner.isAlive()) Conjuring.soulLink(owner, done);
			return done;
		}

		void pulse(int t) {
			if (box == null) return;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box.inflate(1.5, 1, 1.5), LivingEntity::isAlive)) {
				boolean on = onDeck(e);
				if (foe(e)) {
					if (!on && !touching(e)) continue;
					if (toggles.contains("spikes") && ready(e, "spike", 20)) spike(e);
					if (toggles.contains("snare")) e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 30, 3), owner);
					if (toggles.contains("repel") && ready(e, "repel", 30)) fling(e);
					if (toggles.contains("collapse") && on && ready(e, "drop", 80)) drop(e);
				} else if (friend(e) && on) {
					if (toggles.contains("mend")) keep(e, net.minecraft.world.effect.MobEffects.REGENERATION, 60, 0);
					if (toggles.contains("swift")) keep(e, net.minecraft.world.effect.MobEffects.SPEED, 30, 1);
					if (toggles.contains("veil")) keep(e, net.minecraft.world.effect.MobEffects.INVISIBILITY, 30, 0);
				}
			}
			if (toggles.contains("feather") && t % 10 == 0) {
				AABB below = new AABB(box.minX - 3, box.minY - 40, box.minZ - 3, box.maxX + 3, box.minY + 1, box.maxZ + 3);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, below, x -> x.isAlive() && friend(x) && !x.onGround() && x.getDeltaMovement().y < -0.4)) {
					e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 80, 0));
				}
			}
			if (toggles.contains("glow") && t % 20 == 0) {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box.inflate(8, 4, 8), x -> x.isAlive() && foe(x))) {
					e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 60, 0), owner);
				}
			}
			if (toggles.contains("turrets") && t % 30 == 0) {
				int fired = 0;
				for (BlockPos post : posts) {
					if (fired >= 6 || !level.getBlockState(post).is(Blocks.LANTERN) && level.getBlockState(post).isAir()) continue;
					Vec3 from = Vec3.atCenterOf(post).add(0, 0.6, 0);
					LivingEntity mark = null;
					double best = 10 * 10;
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(post).inflate(10), x -> x.isAlive() && foe(x))) {
						double d = e.distanceToSqr(from);
						if (d >= best) continue;
						var hit = level.clip(new net.minecraft.world.level.ClipContext(from, e.getBoundingBox().getCenter(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
								net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
						if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS && hit.getLocation().distanceToSqr(e.getBoundingBox().getCenter()) > 1.0) continue;
						best = d;
						mark = e;
					}
					if (mark == null) continue;
					Fx.line(level, from, mark.getBoundingBox().getCenter(), Conjuring.tintFor(cast.spell), null, 2);
					level.playSound(null, post, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.4f, 1.6f);
					hurt(mark, base * 0.4);
					fired++;
				}
			}
		}

		private void keep(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int ticks, int amplifier) {
			var have = e.getEffect(effect);
			if (have == null || have.getDuration() < ticks / 3) e.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, ticks, amplifier, false, true));
		}

		private void spike(LivingEntity e) {
			Vec3 at = e.position();
			var shard = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, spikeLook);
			// three spikes thrust up around it
			for (int k = 0; k < 3; k++) {
				double a = k * Math.PI * 2 / 3 + level.getRandom().nextDouble();
				double r = e.getBbWidth() * 0.5;
				for (int h = 0; h < 6; h++) {
					double y = at.y + h * 0.22;
					double inward = r * (1 - h / 6.0);
					level.sendParticles(shard, at.x + Math.cos(a) * inward, y, at.z + Math.sin(a) * inward, 2, 0.02, 0.02, 0.02, 0.0);
				}
			}
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.6, at.z, 8, 0.3, 0.3, 0.3, 0.2);
			level.playSound(null, e.blockPosition(), metal(cast.spell) ? SoundEvents.CHAIN_HIT : SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.BLOCKS, 1.0f, 0.8f);
			hurt(e, base * 0.6);
			switch (element) {
				case "fire" -> e.igniteForSeconds(2);
				case "ice" -> e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 40, 1), owner);
				case "storm" -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5, at.z, 12, 0.3, 0.5, 0.3, 0.1);
				case "light" -> e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 60, 0), owner);
				default -> {
				}
			}
		}

		private void fling(LivingEntity e) {
			Vec3 push;
			if (toggles.contains("gated")) {
				// inside a tunnel, out by the nearer end
				Vec3 along = new Vec3(facing.getStepX(), 0, facing.getStepZ());
				double from = e.position().subtract(owner.position()).dot(along);
				push = along.scale(from < 6 ? -1 : 1);
			} else {
				Vec3 across = new Vec3(side.getStepX(), 0, side.getStepZ());
				Vec3 centre = Vec3.atCenterOf(deck.iterator().next());
				double which = e.position().subtract(centre).dot(across);
				push = across.scale(which >= 0 ? 1 : -1);
			}
			e.setDeltaMovement(push.x * 1.0, 0.5, push.z * 1.0);
			e.syncVelocity = true;
			level.sendParticles(ParticleTypes.GUST, e.getX(), e.getY() + 0.5, e.getZ(), 1, 0, 0, 0, 0);
		}

		private void drop(LivingEntity e) {
			BlockPos under = BlockPos.containing(e.getX(), e.getY() - 0.3, e.getZ());
			if (!deck.contains(under)) under = under.below();
			if (!deck.contains(under)) return;
			for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, new AABB(under).inflate(2.5), x -> x.isAlive() && friend(x))) return; // never under a friend
			BlockState was = level.getBlockState(under);
			if (was.isAir()) return;
			BlockPos hole = under.immutable();
			level.setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, was), hole.getX() + 0.5, hole.getY() + 0.5, hole.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.1);
			level.playSound(null, hole, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
			Tasks.later(40, () -> {
				if (!STANDING.contains(hole) || !level.getBlockState(hole).isAir()) return;
				level.setBlock(hole, was, 3);
				for (LivingEntity caught : level.getEntitiesOfClass(LivingEntity.class, new AABB(hole), LivingEntity::isAlive)) liftOnto(level, caught); // it rises back up under them
			});
		}
	}

	/** For tests only: when above zero, how many ticks everything conjured stands, in place of its usual life. */
	public static int lifeOverride = 0;

	/** Seconds the structure stands, before Duratus. */
	public static int seconds(Spell spell) {
		int tier = Math.max(1, Math.min(5, spell.tier));
		if (alsoShelter(spell)) return 20 + 4 * tier; // the bulwark stands as long as its warden, more or less
		return "cage".equals(structure(spell)) ? 8 + 2 * tier : 40 + 8 * tier;
	}

	public static String describe(Spell spell) {
		String what = structure(spell);
		if (what == null) return "";
		if (what.equals("trail")) return Trails.describe(spell);
		int secs = (int) (seconds(spell) * (spell.hasCharge("Duratus") ? 1.6 : 1.0));
		String kind = switch (what) {
			case "bridge" -> bridgeToggles(spell).contains("gated") ? "a covered bridge across the way you face, walled, roofed and windowed, with a gate at each end that you can open and hostile creatures cannot" : "a dressed-stone bridge, railed and lit, across the way you face";
			case "wall" -> "a battlemented wall in front of you";
			case "cage" -> "a barred cage around the creature you look at, which is crushed and smothered inside it for as long as it holds";
			case "shelter" -> "a domed shelter over you, with a doorway of hardened air the way you face: crouch to pass through it; hostile creatures can only wade through slowly";
			default -> "a tower under your feet that lifts you to its battlements";
		};
		String text = ("cage".equals(what) ? "It closes " : "It raises ") + kind + ", built in its element's own stone. It stands about " + secs + " seconds: near the end it turns to glass, so you can see it going, and then it is gone.";
		if ("bridge".equals(what)) text += BridgeWork.describe(spell, bridgeToggles(spell));
		return text;
	}

	/**
	 * Only a mighty base word (Space, Time, Void, Ether, Mythic: tier 5 or more) carries power enough for a
	 * guarding conjuration of a colour to raise the shelter around you as well as its hollow knight: a
	 * bulwark and its warden. Every other conjuration makes one thing.
	 */
	public static boolean alsoShelter(Spell spell) {
		if (!"conjuration".equals(spell.type) || Conjuring.colourWord(spell) == null || !Conjuring.guarding(spell)) return false;
		String kind = "", intention = "";
		boolean mighty = false;
		for (String id : spell.words) {
			com.omnispell.data.Word w = com.omnispell.data.WordBank.word(id);
			if (w == null) continue;
			if (w.type.equals("base") && w.tierOr(1) >= 5) mighty = true;
			if (w.type.equals("command")) kind = w.explore == null ? "" : w.explore;
			if (w.type.equals("intention")) intention = w.english;
		}
		return mighty && kind.equals("envelop") && !java.util.Set.of("Malice", "Weakening", "Banishment", "Binding", "Nullification").contains(intention);
	}

	public static boolean build(Cast cast) {
		return build(cast, structure(cast.spell));
	}

	public static boolean build(Cast cast, String what) {
		ServerPlayer p = cast.player;
		ServerLevel level = cast.level;
		Spell spell = cast.spell;
		if (what == null) return false;
		if (what.equals("trail")) return Trails.lay(cast);
		Palette pal = palette(AttackForms.element(spell));
		boolean big = spell.hasCharge("Maxima");
		int tier = Math.max(1, Math.min(5, spell.tier));
		Builder b = new Builder(level, p);
		Direction facing = p.getDirection();
		Direction side = facing.getClockWise();
		BlockPos feet = p.blockPosition();
		BridgeWork work = null;
		switch (what) {
			case "bridge" -> {
				int length = (int) ((12 + 4 * tier) * (big ? 1.5 : 1.0));
				int half = big ? 2 : 1;
				BlockPos deck = feet.below();
				Set<String> toggles = bridgeToggles(spell);
				boolean gated = toggles.contains("gated");
				String element = AttackForms.element(spell);
				work = new BridgeWork(cast, toggles, facing, side);
				int last = 0;
				for (int i = 1; i <= length; i++) {
					BlockPos row = deck.relative(facing, i);
					if (!level.getBlockState(row.above()).canBeReplaced() && !level.getBlockState(row.above(2)).canBeReplaced()) break; // a cliff in the way ends it
					last = i;
					for (int s = -half; s <= half; s++) {
						b.set(row.relative(side, s), pal.deck);
						work.deck.add(row.relative(side, s).immutable());
					}
					for (int s : new int[]{-half - 1, half + 1}) {
						b.set(row.relative(side, s), pal.deck);
						if (gated) {
							// a tunnel: walls two high with a window every third block
							b.set(row.relative(side, s).above(), pal.deck);
							b.set(row.relative(side, s).above(2), i % 3 == 0 ? pal.glass.defaultBlockState() : pal.deck);
							if (i % 5 == 0) {
								b.set(row.relative(side, s).above(4), pal.light);
								work.posts.add(row.relative(side, s).above(4).immutable());
							}
						} else {
							b.set(row.relative(side, s).above(), pal.rail);
							if (i % 5 == 0) {
								b.set(row.relative(side, s).above(2), pal.light);
								work.posts.add(row.relative(side, s).above(2).immutable());
							}
						}
					}
					if (gated) {
						// and a roof, lit from within
						for (int s = -half - 1; s <= half + 1; s++) b.set(row.relative(side, s).above(3), s == 0 && i % 4 == 2 ? tunnelLight(element) : pal.deck);
					}
					if (i % 6 == 3) {
						// piers down to the ground below
						for (int s : new int[]{-half - 1, half + 1}) {
							BlockPos down = row.relative(side, s).below();
							for (int d = 0; d < 12 && level.getBlockState(down).canBeReplaced(); d++, down = down.below()) b.set(down, pal.deck);
						}
					}
				}
				if (gated && last > 0) {
					// a gate across each end: you open it, hostile creatures cannot
					BlockState gate = gateFor(element).defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, facing);
					for (int end : new int[]{1, last}) {
						for (int s = -half; s <= half; s++) b.set(deck.relative(facing, end).relative(side, s).above(), gate);
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
					if (door) {
						// The doorway is hardened air: friends pass by crouching, enemies wade through it slowly.
						b.set(pos, com.omnispell.item.OmniItems.HARDENED_AIR.defaultBlockState());
						continue;
					}
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
		int life = lifeOverride > 0 ? lifeOverride : (int) (seconds(spell) * 20 * (spell.hasCharge("Duratus") ? 1.6 : 1.0));
		schedule(level, b.stones, pal.glass, life);
		if (work != null && !work.toggles.isEmpty()) {
			for (Stone s : b.stones) work.all.add(s.pos);
			work.start(life, pal.deck);
		}
		return true;
	}

	/** Solid for most of its life; glass for the last third; then it comes apart, a few blocks at a time. */
	private static void schedule(ServerLevel level, List<Stone> stones, Block glass, int ticks) {
		int toGlass = (int) (ticks * 0.65);
		int crumble = (int) (ticks * 0.9);
		Tasks.later(toGlass, () -> {
			for (Stone s : stones) {
				if (s.now.is(com.omnispell.item.OmniItems.HARDENED_AIR)) continue; // the doorway stays open air to the end
				if (s.now.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) continue; // a gate stays a gate, or no one could get out
				if (!level.getBlockState(s.pos).is(s.now.getBlock())) continue; // walls and bars reshape to join their neighbours, so match the block, not its exact shape
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
		if (!level.getBlockState(s.pos).is(s.now.getBlock())) return; // someone already broke it
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
