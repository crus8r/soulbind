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

	private static final String[] COMMANDS = {"launch", "envelop", "scatter", "form", "bind", "summon", "channel", "shift", "pulse", "grow"};
	private static final String[] INTENTIONS = {"destruction", "protection", "discovery", "concealment", "manipulation", "traversal"};
	/** What each kind of command does with each kind of intention. Rows follow COMMANDS, columns INTENTIONS. */
	private static final String[][] TABLE = {
			/* launch  */ {"carve", "platform", "reveal", "smoke_screen", "push", "grapple"},
			/* envelop */ {"dry", "mend_land", "true_sight", "invisibility", "solidify", "phase"},
			/* scatter */ {"crater", "calm", "illuminate", "decoy", "weather", "teleport"},
			/* form    */ {"pit", "raise_wall", "prospect", "mirage", "golem", "bridge"},
			/* bind    */ {"moat", "anchor", "track", "silent_step", "gather", "tether"},
			/* summon  */ {"strike", "guardian", "scout", "phantom", "conjure", "mount"},
			/* channel */ {"drill", "attune", "prospect", "camouflage", "current", "stairs"},
			/* shift   */ {"transmute", "ring", "scout", "fade", "path", "pillar"},
			/* pulse   */ {"flatten", "dome", "reveal", "blackout", "field", "dash"},
			/* grow    */ {"clear", "growth", "illuminate", "thicket", "tree", "leap"},
	};

	/** The effect of an environment spell: the command's kind and the intention's kind decide it. */
	public static String effectFor(String command, String intention) {
		for (int c = 0; c < COMMANDS.length; c++) {
			if (!COMMANDS[c].equals(command)) continue;
			for (int n = 0; n < INTENTIONS.length; n++) if (INTENTIONS[n].equals(intention)) return TABLE[c][n];
		}
		return null;
	}

	public static boolean handles(String toggle) {
		return switch (toggle == null ? "" : toggle) {
			case "carve", "drill", "crater", "pit", "moat", "flatten", "dry", "clear", "raise_wall", "ring", "dome", "platform", "mend_land",
					"calm", "attune", "growth", "illuminate", "prospect", "thicket", "solidify", "weather", "gather", "conjure", "path", "tree",
					"stairs", "pillar", "strike", "field" -> true;
			default -> false;
		};
	}

	public static String effectName(String toggle) {
		StringBuilder name = new StringBuilder();
		for (String part : toggle.replace("raise_wall", "wall").replace("mend_land", "mend").split("_")) {
			if (!name.isEmpty()) name.append(' ');
			name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return name.toString();
	}

	public static String describe(String toggle, Spell spell) {
		Block block = material(spell);
		String stuff = block == null ? "hardened magic that fades after a while" : block.getName().getString().toLowerCase();
		int t = Math.max(1, Math.min(5, spell.tier));
		return switch (toggle) {
			case "carve" -> "Bores a tunnel " + carveLength(t) + " blocks long where you look. Ores are left standing in the walls. Strong enough, it opens a lair's sealed door.";
			case "drill" -> "Drills a narrow shaft " + carveLength(t) * 2 + " blocks long where you look, one block wide. Opens a sealed lair door if strong enough.";
			case "crater" -> "Blasts away the ground where you look, " + (craterRadius(t) * 2 + 1) + " blocks across. Ores are left behind.";
			case "pit" -> "Opens a pit " + (2 + t * 2) + " blocks deep under whatever you look at.";
			case "moat" -> "Digs a trench in a ring around you, " + (3 + t) + " blocks out.";
			case "flatten" -> "Levels the land around you to the height you stand at, " + (mendRadius(t) * 2 + 1) + " blocks across: cuts what is above, fills what is below with " + stuff + ".";
			case "dry" -> "Drives off water, lava, snow and fire, " + (mendRadius(t) * 2 + 1) + " blocks across.";
			case "clear" -> "Withers leaves, plants, webs and snow away, " + (mendRadius(t) * 2 + 3) + " blocks across.";
			case "raise_wall" -> "Raises a wall of " + stuff + " in front of you, " + wallWidth(t) + " wide and " + wallHeight(t) + " high.";
			case "ring" -> "Raises a ring wall of " + stuff + " around you, " + (domeRadius(t) * 2 + 1) + " blocks across and " + wallHeight(t) + " high.";
			case "dome" -> "Closes a dome of " + stuff + " around you, " + (domeRadius(t) * 2 + 1) + " blocks across.";
			case "platform" -> "Lays a platform of " + stuff + " under your feet, " + (t * 2 + 1) + " blocks across. Cast it mid-fall to catch yourself.";
			case "mend_land" -> "Mends the land where you look, " + (mendRadius(t) * 2 + 1) + " blocks across: fills water, lava and small hollows with " + stuff + " and puts out fires.";
			case "calm" -> "Stills the weather and puts out every fire within " + (8 * t) + " blocks.";
			case "attune" -> "Attunes you to your element for " + (t + 1) + " minutes: " + attunement(spell.element) + ".";
			case "growth" -> "Quickens everything growing within " + (mendRadius(t) + 1) + " blocks: crops ripen, saplings shoot up, grass and flowers spread.";
			case "illuminate" -> "Hangs " + (4 + t * 4) + " lasting lights in the air around you, out to " + (6 + t * 4) + " blocks.";
			case "prospect" -> "Shows the ores hidden within " + (6 + t * 4) + " blocks and tells you what they are.";
			case "thicket" -> "Grows a thicket of leaves around you to hide in, " + (domeRadius(t) * 2 + 1) + " blocks across.";
			case "solidify" -> "Hardens liquid within " + mendRadius(t) + " blocks: water to ice, lava to stone.";
			case "weather" -> "Changes the weather: brings rain if the sky is clear, clears it if it is raining." + (t >= 4 ? " At this tier it can call a thunderstorm." : "");
			case "gather" -> "Draws every loose item and experience orb within " + (8 * t) + " blocks to you.";
			case "conjure" -> "Calls " + conjured(spell.element, stuff) + " into being where you look.";
			case "path" -> "Lays a path of " + stuff + " along the ground ahead of you, " + (carveLength(t) * 2) + " blocks long.";
			case "tree" -> "Raises a full-grown tree where you look.";
			case "stairs" -> "Builds a stair of " + stuff + " climbing " + (carveLength(t) + 2) + " blocks the way you face.";
			case "strike" -> "Calls " + bolt(spell) + " down where you look. It strikes everything within " + (2 + t) + " blocks and leaves its element's mark on the ground.";
			case "field" -> "Lays a field of " + (spell.element == null ? "magic" : spell.element) + " where you look, " + ((2 + t) * 2 + 1) + " blocks across, for " + (6 + 3 * t) + " seconds: " + fieldEffect(spell.element) + ".";
			case "pillar" -> "Lifts you on a pillar of " + stuff + ", " + (2 + t * 3) + " blocks high.";
			default -> OLD.getOrDefault(toggle, "Its effect is its own.");
		};
	}

	/** Short accounts of the effects that are not about blocks. */
	private static final java.util.Map<String, String> OLD = java.util.Map.ofEntries(
			java.util.Map.entry("reveal", "Makes every living thing nearby glow through walls."),
			java.util.Map.entry("scout", "Makes every living thing in a wide area glow through walls."),
			java.util.Map.entry("true_sight", "Lets you see in the dark and see through illusions, and reveals what is near."),
			java.util.Map.entry("track", "Points the way to the nearest creature of note."),
			java.util.Map.entry("smoke_screen", "Throws up a cloud that blinds whatever is inside it."),
			java.util.Map.entry("blackout", "Blinds everything around you for a moment."),
			java.util.Map.entry("invisibility", "Hides you from sight."),
			java.util.Map.entry("camouflage", "Blends you into your surroundings for a long while."),
			java.util.Map.entry("fade", "Fades you from sight briefly."),
			java.util.Map.entry("silent_step", "Muffles you: creatures notice you far less."),
			java.util.Map.entry("decoy", "Leaves a false you behind for creatures to chase."),
			java.util.Map.entry("mirage", "Raises an illusion that draws creatures to it."),
			java.util.Map.entry("phantom", "Calls a phantom shape that draws creatures to it."),
			java.util.Map.entry("push", "Shoves away whatever you look at."),
			java.util.Map.entry("stun", "Stuns the creatures around your target."),
			java.util.Map.entry("trap", "Sets a hidden snare where you look."),
			java.util.Map.entry("anchor", "Roots you in place: nothing can knock you back."),
			java.util.Map.entry("golem", "Shapes a golem that fights for you for a while."),
			java.util.Map.entry("guardian", "Calls a guardian to stand watch beside you."),
			java.util.Map.entry("transmute", "Changes the blocks you look at into your element's kin."),
			java.util.Map.entry("current", "A current carries you swiftly the way you face."),
			java.util.Map.entry("grapple", "Pulls you to the point you look at."),
			java.util.Map.entry("tether", "Pulls you to the point you look at."),
			java.util.Map.entry("phase", "Lets you step through a wall."),
			java.util.Map.entry("teleport", "Carries you instantly to where you look."),
			java.util.Map.entry("bridge", "Spans the gap ahead with a bridge that fades."),
			java.util.Map.entry("mount", "Calls a steed for a while."),
			java.util.Map.entry("dash", "A burst of speed forward."),
			java.util.Map.entry("leap", "A great leap, and a soft landing."));

	/** Every element leaves its own mark on whatever an environment spell touches. */
	public static String mark(String element) {
		return switch (element == null ? "" : element) {
			case "fire" -> "Fire scorches: flames are left behind, sand fuses to glass, ice and snow melt, creatures burn";
			case "ice" -> "Ice freezes: water turns to ice, snow settles, creatures are slowed";
			case "water" -> "Water floods: hollows fill with water, fires go out, creatures are soaked and pushed";
			case "wind" -> "Wind scatters: creatures and loose items are thrown outward";
			case "light" -> "Light lingers: lasting lights are left behind, creatures glow and the undead burn";
			case "shadow" -> "Shadow smothers: torches go out and creatures are blinded";
			case "void" -> "Void pulls: creatures are dragged to the centre and lifted";
			case "blood" -> "Blood feeds: creatures wither and you are healed a little for each";
			default -> "Stone weighs: creatures are slowed by the falling rubble";
		};
	}

	static String fieldEffect(String element) {
		return switch (element == null ? "" : element) {
			case "fire" -> "whatever stands in it burns";
			case "ice" -> "whatever stands in it is frozen slow, and water in it turns to ice";
			case "water" -> "it puts out fire, waters crops and drags at whatever stands in it";
			case "wind" -> "an updraft lifts whatever enters, you included";
			case "light" -> "it is bright as day, creatures glow and the undead burn";
			case "shadow" -> "whatever stands in it is blinded";
			case "void" -> "it drags everything towards its centre";
			case "blood" -> "whatever stands in it withers";
			default -> "the ground shakes and whatever stands in it can barely move";
		};
	}

	private static boolean stormWord(Spell spell) {
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word != null && word.type.equals("base") && (word.english.equals("Lightning") || word.english.equals("Thunder") || word.english.equals("Energy"))) return true;
		}
		return false;
	}

	static String bolt(Spell spell) {
		if (stormWord(spell)) return "true lightning";
		return switch (spell.element == null ? "" : spell.element) {
			case "fire" -> "a pillar of flame";
			case "ice" -> "a lance of frost";
			case "water" -> "a column of water";
			case "wind" -> "a green-white bolt";
			case "light" -> "a golden bolt";
			case "shadow" -> "a bolt of violet shadow";
			case "void" -> "purple lightning";
			case "blood" -> "a red bolt";
			default -> "a hammer-blow of stone";
		};
	}

	static String attunement(String element) {
		return switch (element == null ? "" : element) {
			case "water" -> "you breathe water and swim like a dolphin";
			case "fire" -> "fire and lava cannot burn you";
			case "ice" -> "neither fire nor deep water can harm you";
			case "wind" -> "you fall slowly and jump high";
			case "light" -> "you see in the dark and glow to your friends";
			case "shadow" -> "you see in the dark and creatures notice you less";
			case "void" -> "you fall slowly and never hunger";
			case "blood" -> "your wounds close by themselves";
			default -> "you dig faster and shrug off blows";
		};
	}

	static String conjured(String element, String stuff) {
		return switch (element == null ? "" : element) {
			case "water" -> "a spring of water";
			case "fire" -> "a ring of fire";
			case "ice" -> "a drift of snow";
			default -> "a mound of " + stuff;
		};
	}

	public static double cooldownSeconds(String toggle) {
		return switch (toggle) {
			case "carve", "drill", "platform", "pillar", "gather", "path", "stairs" -> 6;
			case "attune", "weather", "calm", "dome", "ring", "thicket" -> 20;
			default -> 10;
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
		final java.util.List<BlockPos> touched = new java.util.ArrayList<>();

		void touch(BlockPos pos) {
			if (touched.size() < 600) touched.add(pos.immutable());
		}

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
			touch(pos);
			changed++;
		}

		/** Places a block even where the caster stands above it. */
		void placeUnder(BlockPos pos, Block real) {
			BlockState existing = c.level.getBlockState(pos);
			if (!existing.isAir() && !existing.canBeReplaced()) return;
			if (real == null) {
				if (TempBlocks.place(c.level, pos, TempBlocks.material(c.spell.element), 600 + 300 * tier)) changed++;
			} else {
				c.level.setBlockAndUpdate(pos, real.defaultBlockState());
				changed++;
			}
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
			case "strike" -> {
				BlockHitResult hit = Targeting.block(p, 16 + 8 * tier);
				if (hit.getType() == HitResult.Type.MISS) return c.fail("There is nothing there to strike.");
				BlockPos ground = hit.getBlockPos();
				Vec3 at = Vec3.atCenterOf(ground).add(0, 0.5, 0);
				String element = c.spell.element == null ? "stone" : c.spell.element;
				WordBank.Flavor flavor = WordBank.flavor(element);
				int color = Fx.color(flavor == null ? "arcane" : flavor.color);
				var extra = Fx.themed(flavor == null ? "sparks" : flavor.particle);
				if (stormWord(c.spell)) {
					net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
					if (bolt != null) {
						bolt.snapTo(at.x, at.y, at.z, 0, 0);
						bolt.setVisualOnly(true);
						level.addFreshEntity(bolt);
					}
				} else {
					// A jagged bolt in the element's own colour.
					Vec3 from = at.add(level.getRandom().nextDouble() * 4 - 2, 24, level.getRandom().nextDouble() * 4 - 2);
					Vec3 last = from;
					for (int n = 1; n <= 8; n++) {
						Vec3 next = n == 8 ? at : from.add(at.subtract(from).scale(n / 8.0)).add(level.getRandom().nextDouble() * 2 - 1, 0, level.getRandom().nextDouble() * 2 - 1);
						Fx.line(level, last, next, color, n % 2 == 0 ? extra : null, 3);
						last = next;
					}
					level.playSound(null, ground, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2f, element.equals("void") || element.equals("shadow") ? 0.6f : 1.2f);
				}
				Fx.burst(level, at, color, extra, 3 + tier);
				Fx.ring(level, at, 2 + tier, color, extra);
				int r = 2 + tier;
				float damage = 4 + 2 * tier;
				for (net.minecraft.world.entity.LivingEntity e : Targeting.around(p, at, r, false)) {
					e.hurtServer(level, level.damageSources().indirectMagic(p, p), damage);
				}
				for (BlockPos pos : BlockPos.betweenClosed(ground.offset(-r, -1, -r), ground.offset(r, 2, r))) {
					if (pos.distSqr(ground) <= r * r && level.getBlockState(pos).canBeReplaced() && !level.getBlockState(pos.below()).canBeReplaced()) job.touch(pos);
				}
				job.changed++;
			}
			case "field" -> {
				BlockHitResult hit = Targeting.block(p, 12 + 6 * tier);
				BlockPos ground = hit.getType() == HitResult.Type.MISS ? p.blockPosition() : hit.getBlockPos().relative(hit.getDirection());
				Vec3 at = Vec3.atBottomCenterOf(ground);
				String element = c.spell.element == null ? "stone" : c.spell.element;
				WordBank.Flavor flavor = WordBank.flavor(element);
				int color = Fx.color(flavor == null ? "arcane" : flavor.color);
				var extra = Fx.themed(flavor == null ? "sparks" : flavor.particle);
				int r = 2 + tier;
				int seconds = 6 + 3 * tier;
				for (int n = 0; n < seconds * 2; n++) {
					int beat = n;
					Tasks.later(n * 10, () -> {
						if (!p.isAlive() || p.level() != level) return;
						Fx.ring(level, at.add(0, 0.2, 0), r, color, beat % 2 == 0 ? extra : null);
						if (beat % 4 == 0) Fx.rain(level, at.add(0, 3, 0), r * 0.8, color, extra);
						creatures(level, p, at, r + 0.5, element, tier, true);
						if (beat % 2 == 0) {
							java.util.List<BlockPos> spots = new java.util.ArrayList<>();
							for (int k = 0; k < 4 + tier * 2; k++) {
								BlockPos pos = ground.offset(level.getRandom().nextInt(r * 2 + 1) - r, level.getRandom().nextInt(3) - 1, level.getRandom().nextInt(r * 2 + 1) - r);
								if (level.getBlockState(pos).canBeReplaced() && !level.getBlockState(pos.below()).canBeReplaced()) spots.add(pos);
							}
							if (!element.equals("fire")) ground(level, spots, element, tier, "field"); // a burning field only burns creatures, or it would burn the forest down
						}
					});
				}
				job.changed++;
			}
			case "drill" -> {
				Vec3 eye = p.getEyePosition();
				Vec3 look = p.getLookAngle();
				Set<BlockPos> seen = new HashSet<>();
				for (double d = 1; d <= carveLength(tier) * 2; d += 0.4) {
					BlockPos pos = BlockPos.containing(eye.add(look.scale(d)));
					if (seen.add(pos)) job.remove(pos);
					if (Math.abs(p.getXRot()) < 60 && seen.add(pos.below())) job.remove(pos.below());
				}
				Fx.impact(level, eye.add(look.scale(3)), fx, tier);
			}
			case "pit" -> {
				BlockHitResult hit = Targeting.block(p, 12 + 6 * tier);
				if (hit.getType() == HitResult.Type.MISS) return c.fail("There is no ground there to open.");
				BlockPos top = hit.getBlockPos();
				int half = tier >= 3 ? 1 : 0;
				for (BlockPos pos : BlockPos.betweenClosed(top.offset(-half - 1, -(2 + tier * 2) + 1, -half - 1), top.offset(half, 0, half))) job.remove(pos);
				Fx.impact(level, Vec3.atCenterOf(top), fx, tier);
			}
			case "moat" -> {
				BlockPos center = p.blockPosition();
				int r = 3 + tier;
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r - 1, -2 - tier / 2, -r - 1), center.offset(r + 1, 1, r + 1))) {
					double d = Math.sqrt((pos.getX() - center.getX()) * (pos.getX() - center.getX()) + (pos.getZ() - center.getZ()) * (pos.getZ() - center.getZ()));
					if (d >= r - 0.5 && d < r + 1.3) job.remove(pos);
				}
				Fx.impact(level, p.position(), fx, tier);
			}
			case "flatten" -> {
				BlockPos center = p.blockPosition();
				int r = mendRadius(tier);
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (dx * dx + dz * dz > r * r + 1) continue;
						for (int dy = 0; dy <= 5; dy++) job.remove(center.offset(dx, dy, dz));
						for (int dy = -1; dy >= -3; dy--) job.place(center.offset(dx, dy, dz), block, null);
					}
				}
				Fx.impact(level, p.position(), fx, tier);
			}
			case "dry", "clear", "solidify" -> {
				BlockPos center = p.blockPosition();
				int r = toggle.equals("clear") ? mendRadius(tier) + 1 : mendRadius(tier);
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
					if (pos.distSqr(center) > r * r + 1) continue;
					BlockState state = level.getBlockState(pos);
					if (state.isAir()) continue;
					boolean water = state.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
					boolean lava = state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
					boolean liquidBlock = state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock;
					if (toggle.equals("solidify")) {
						if (!liquidBlock || !state.getFluidState().isSource()) continue;
						level.setBlockAndUpdate(pos, (water ? Blocks.ICE : Blocks.STONE).defaultBlockState());
						job.changed++;
					} else if (toggle.equals("dry")) {
						boolean snow = state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW);
						if (liquidBlock || snow || state.is(net.minecraft.tags.BlockTags.FIRE)) {
							level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
							job.changed++;
						} else if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
								&& state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
							level.setBlockAndUpdate(pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false));
							job.changed++;
						}
					} else {
						boolean plant = state.is(net.minecraft.tags.BlockTags.LEAVES) || state.canBeReplaced() && !liquidBlock || state.is(Blocks.COBWEB)
								|| state.is(net.minecraft.tags.BlockTags.FLOWERS) || state.is(Blocks.SNOW) || state.is(Blocks.VINE) || state.is(net.minecraft.tags.BlockTags.SAPLINGS);
						if (plant && job.guard.tierNeeded(pos) <= tier) {
							level.destroyBlock(pos, false);
							job.changed++;
						}
					}
				}
				Fx.impact(level, p.position().add(0, 1, 0), fx, tier);
			}
			case "ring", "thicket" -> {
				BlockPos center = p.blockPosition();
				int r = domeRadius(tier);
				Block wall = toggle.equals("thicket") ? Blocks.OAK_LEAVES : block;
				BlockState state = toggle.equals("thicket")
						? Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true) : null;
				int height = toggle.equals("thicket") ? 3 : wallHeight(tier);
				for (int dx = -r - 1; dx <= r + 1; dx++) {
					for (int dz = -r - 1; dz <= r + 1; dz++) {
						double d = Math.sqrt(dx * dx + dz * dz);
						if (d > r + 0.5 || d <= r - 0.6) continue;
						BlockPos foot = center.offset(dx, 0, dz);
						for (int down = 0; down < 5 && level.getBlockState(foot.below()).canBeReplaced(); down++) foot = foot.below();
						for (int up = 0; up < 4 && !level.getBlockState(foot).canBeReplaced(); up++) foot = foot.above();
						for (int h = 0; h < height; h++) job.place(foot.above(h), wall, state);
					}
				}
				Fx.impact(level, p.position().add(0, 1, 0), fx, tier);
			}
			case "platform" -> {
				BlockPos center = p.blockPosition().below();
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-tier, 0, -tier), center.offset(tier, 0, tier))) job.placeUnder(pos, block);
				p.fallDistance = 0;
				p.setDeltaMovement(p.getDeltaMovement().multiply(1, 0, 1));
				p.syncVelocity = true;
				Fx.impact(level, p.position(), fx, tier);
			}
			case "pillar" -> {
				BlockPos foot = p.blockPosition();
				int height = 2 + tier * 3, raised = 0;
				for (int h = 0; h < height; h++) {
					BlockState above = level.getBlockState(foot.above(h + 2));
					if (!above.isAir() && !above.canBeReplaced()) break;
					job.placeUnder(foot.above(h), block);
					raised++;
				}
				if (raised > 0) p.teleportTo(p.getX(), foot.getY() + raised + 0.05, p.getZ());
				p.fallDistance = 0;
				Fx.impact(level, p.position(), fx, tier);
			}
			case "stairs" -> {
				Direction facing = p.getDirection();
				Direction side = facing.getClockWise();
				BlockPos foot = p.blockPosition().relative(facing);
				for (int n = 0; n < carveLength(tier) + 2; n++) {
					BlockPos step = foot.relative(facing, n).above(n);
					for (int w = -1; w <= 1; w++) {
						job.place(step.relative(side, w), block, null);
						job.remove(step.relative(side, w).above());
						job.remove(step.relative(side, w).above(2));
						job.remove(step.relative(side, w).above(3));
					}
				}
				Fx.impact(level, Vec3.atCenterOf(foot.above()), fx, tier);
			}
			case "path" -> {
				Direction facing = p.getDirection();
				Direction side = facing.getClockWise();
				BlockPos at = p.blockPosition().below();
				for (int n = 1; n <= carveLength(tier) * 2; n++) {
					BlockPos next = at.relative(facing);
					if (!level.getBlockState(next.above()).canBeReplaced()) next = next.above();
					else if (level.getBlockState(next).canBeReplaced() && !level.getBlockState(next.below()).canBeReplaced()) next = next.below();
					at = next;
					for (int w = -1; w <= 1; w++) {
						BlockPos pos = at.relative(side, w);
						BlockState existing = level.getBlockState(pos);
						if (existing.hasBlockEntity() || existing.getDestroySpeed(level, pos) < 0 || job.guard.tierNeeded(pos) > 0) continue;
						if (block == null) {
							if (existing.canBeReplaced() && TempBlocks.place(level, pos, TempBlocks.material(c.spell.element), 600 + 300 * tier)) job.changed++;
						} else {
							level.setBlockAndUpdate(pos, (block == Blocks.DIRT ? Blocks.DIRT_PATH : block).defaultBlockState());
							job.changed++;
						}
					}
				}
				Fx.impact(level, p.position(), fx, tier);
			}
			case "conjure" -> {
				BlockHitResult hit = Targeting.block(p, 12 + 6 * tier);
				if (hit.getType() == HitResult.Type.MISS) return c.fail("There is nowhere there to set it down.");
				BlockPos at = hit.getBlockPos().relative(hit.getDirection());
				String element = c.spell.element == null ? "" : c.spell.element;
				if (element.equals("water")) {
					job.place(at, Blocks.WATER, null);
				} else if (element.equals("fire")) {
					for (BlockPos pos : BlockPos.betweenClosed(at.offset(-tier, 0, -tier), at.offset(tier, 0, tier))) {
						double d = Math.sqrt(pos.distSqr(at));
						if (d > tier - 0.5 && d <= tier + 0.5 && !level.getBlockState(pos.below()).canBeReplaced()) job.place(pos, Blocks.FIRE, null);
					}
				} else if (element.equals("ice")) {
					for (BlockPos pos : BlockPos.betweenClosed(at.offset(-tier - 1, -1, -tier - 1), at.offset(tier + 1, 1, tier + 1))) {
						if (!level.getBlockState(pos.below()).canBeReplaced() && level.getBlockState(pos.below()).isCollisionShapeFullBlock(level, pos.below())) job.place(pos, Blocks.SNOW, null);
					}
				} else {
					int r = (tier + 1) / 2;
					for (BlockPos pos : BlockPos.betweenClosed(at.offset(-r, 0, -r), at.offset(r, r, r))) {
						if (Math.abs(pos.getX() - at.getX()) + Math.abs(pos.getZ() - at.getZ()) + (pos.getY() - at.getY()) <= r) job.place(pos, block, null);
					}
				}
				Fx.impact(level, Vec3.atCenterOf(at), fx, tier);
			}
			case "tree" -> {
				BlockHitResult hit = Targeting.block(p, 12 + 6 * tier);
				if (hit.getType() == HitResult.Type.MISS) return c.fail("There is no ground there for roots.");
				BlockPos ground = hit.getBlockPos();
				BlockState soil = level.getBlockState(ground);
				if (!soil.is(net.minecraft.tags.BlockTags.DIRT)) return c.fail("A tree needs earth to stand in.");
				Block[] saplings = {Blocks.OAK_SAPLING, Blocks.BIRCH_SAPLING, Blocks.SPRUCE_SAPLING, Blocks.CHERRY_SAPLING, Blocks.ACACIA_SAPLING};
				Block sapling = saplings[Math.floorMod(c.spell.id == null ? 0 : c.spell.id.hashCode(), saplings.length)];
				BlockPos at = ground.above();
				if (!level.getBlockState(at).canBeReplaced()) return c.fail("There is no room there.");
				level.setBlockAndUpdate(at, sapling.defaultBlockState());
				for (int tries = 0; tries < 40 && level.getBlockState(at).getBlock() instanceof net.minecraft.world.level.block.SaplingBlock grower; tries++) {
					grower.advanceTree(level, at, level.getBlockState(at), level.getRandom());
				}
				if (level.getBlockState(at).getBlock() instanceof net.minecraft.world.level.block.SaplingBlock) {
					level.removeBlock(at, false);
					return c.fail("It will not take root here. It needs open sky above.");
				}
				job.changed++;
				Fx.impact(level, Vec3.atCenterOf(at), "nature", tier);
			}
			case "growth" -> {
				BlockPos center = p.blockPosition();
				int r = mendRadius(tier) + 1;
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 3, r))) {
					if (pos.distSqr(center) > r * r + 1) continue;
					BlockState state = level.getBlockState(pos);
					if (state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock plant && !(state.getBlock() instanceof net.minecraft.world.level.block.GrassBlock)) {
						for (int n = 0; n < 1 + tier && level.getBlockState(pos).getBlock() == state.getBlock(); n++) {
							BlockState now = level.getBlockState(pos);
							if (!plant.isValidBonemealTarget(level, pos, now, net.minecraft.world.level.block.BonemealSource.INTERACTION) || !plant.isBonemealSuccess(level, level.getRandom(), pos, now, net.minecraft.world.level.block.BonemealSource.INTERACTION)) break;
							plant.performBonemeal(level, level.getRandom(), pos.immutable(), now, net.minecraft.world.level.block.BonemealSource.INTERACTION);
							job.changed++;
						}
					} else if (state.is(Blocks.DIRT) && level.getBlockState(pos.above()).isAir()) {
						level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
						job.changed++;
					} else if (state.is(Blocks.GRASS_BLOCK) && level.getBlockState(pos.above()).isAir() && level.getRandom().nextInt(5) == 0) {
						Block[] grown = {Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.DANDELION, Blocks.POPPY, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER};
						level.setBlockAndUpdate(pos.above(), grown[level.getRandom().nextInt(grown.length)].defaultBlockState());
						job.changed++;
					}
				}
				Fx.impact(level, p.position().add(0, 1, 0), "nature", tier);
				level.playSound(null, center, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
			}
			case "illuminate" -> {
				BlockPos center = p.blockPosition().above();
				int count = 4 + tier * 4, reach = 6 + tier * 4;
				for (int n = 0; n < count * 6 && job.changed < count; n++) {
					BlockPos pos = center.offset(level.getRandom().nextInt(reach * 2 + 1) - reach, level.getRandom().nextInt(5) - 1, level.getRandom().nextInt(reach * 2 + 1) - reach);
					if (!level.getBlockState(pos).isAir() || level.getMaxLocalRawBrightness(pos) > 9 && n < count * 3) continue;
					level.setBlockAndUpdate(pos, Blocks.LIGHT.defaultBlockState());
					level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.01);
					job.changed++;
				}
				level.setBlockAndUpdate(center, level.getBlockState(center).isAir() ? Blocks.LIGHT.defaultBlockState() : level.getBlockState(center));
				job.changed++;
				level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);
			}
			case "prospect" -> {
				BlockPos center = p.blockPosition();
				int r = 6 + tier * 4;
				java.util.Map<String, Integer> found = new java.util.TreeMap<>();
				int shown = 0;
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
					BlockState state = level.getBlockState(pos);
					if (state.isAir()) continue;
					String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
					if (!path.endsWith("_ore") && !path.equals("ancient_debris")) continue;
					found.merge(state.getBlock().getName().getString(), 1, Integer::sum);
					if (shown++ < 60) {
						net.minecraft.world.entity.monster.Shulker marker = net.minecraft.world.entity.EntityTypes.SHULKER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
						if (marker != null) {
							marker.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
							marker.setNoAi(true);
							marker.setInvisible(true);
							marker.setPermanentlyInvulnerable(true);
							marker.setSilent(true);
							marker.setGlowingTag(true);
							marker.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 400, 0, false, false));
							Summons.track(marker, 200 + 60 * tier);
							level.addFreshEntity(marker);
						}
					}
				}
				if (found.isEmpty()) c.tell("No ore lies within " + r + " blocks.");
				else {
					StringBuilder text = new StringBuilder("Ore nearby: ");
					found.forEach((name, n) -> text.append(n).append(' ').append(name).append(", "));
					c.tell(text.substring(0, text.length() - 2) + ".");
				}
				job.changed++;
				Fx.impact(level, p.position().add(0, 1, 0), fx, tier);
			}
			case "gather" -> {
				double r = 8 * tier;
				for (net.minecraft.world.entity.Entity e : level.getEntities(p, p.getBoundingBox().inflate(r),
						x -> x instanceof net.minecraft.world.entity.item.ItemEntity || x instanceof net.minecraft.world.entity.ExperienceOrb)) {
					e.teleportTo(p.getX(), p.getY() + 0.3, p.getZ());
					if (e instanceof net.minecraft.world.entity.item.ItemEntity item) item.setNoPickUpDelay();
					job.changed++;
				}
				if (job.changed == 0) return c.fail("Nothing lies loose nearby.");
				Fx.impact(level, p.position().add(0, 1, 0), fx, tier);
			}
			case "calm", "weather" -> {
				if (toggle.equals("calm")) {
					level.getServer().setWeatherParameters(24000, 0, false, false);
					BlockPos center = p.blockPosition();
					int r = 8 * tier;
					for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -4, -r), center.offset(r, 6, r))) {
						if (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE)) level.removeBlock(pos, false);
					}
					c.tell("The sky settles.");
				} else if (level.isRaining()) {
					level.getServer().setWeatherParameters(12000, 0, false, false);
					c.tell("The clouds part.");
				} else {
					boolean thunder = tier >= 4;
					level.getServer().setWeatherParameters(0, 6000, true, thunder);
					c.tell(thunder ? "A storm answers." : "Rain answers.");
				}
				job.changed++;
				Fx.impact(level, p.position().add(0, 2, 0), fx, tier);
			}
			case "attune" -> {
				int ticks = (tier + 1) * 1200;
				String element = c.spell.element == null ? "" : c.spell.element;
				java.util.List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> effects = switch (element) {
					case "water" -> java.util.List.of(net.minecraft.world.effect.MobEffects.WATER_BREATHING, net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE);
					case "fire" -> java.util.List.of(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
					case "ice" -> java.util.List.of(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, net.minecraft.world.effect.MobEffects.WATER_BREATHING);
					case "wind" -> java.util.List.of(net.minecraft.world.effect.MobEffects.SLOW_FALLING, net.minecraft.world.effect.MobEffects.JUMP_BOOST);
					case "light" -> java.util.List.of(net.minecraft.world.effect.MobEffects.NIGHT_VISION, net.minecraft.world.effect.MobEffects.GLOWING);
					case "shadow" -> java.util.List.of(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
					case "void" -> java.util.List.of(net.minecraft.world.effect.MobEffects.SLOW_FALLING, net.minecraft.world.effect.MobEffects.SATURATION);
					case "blood" -> java.util.List.of(net.minecraft.world.effect.MobEffects.REGENERATION);
					default -> java.util.List.of(net.minecraft.world.effect.MobEffects.HASTE, net.minecraft.world.effect.MobEffects.RESISTANCE);
				};
				for (var effect : effects) {
					boolean brief = effect == net.minecraft.world.effect.MobEffects.SATURATION || effect == net.minecraft.world.effect.MobEffects.REGENERATION;
					p.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, brief && effect == net.minecraft.world.effect.MobEffects.SATURATION ? 20 : ticks, 0, false, false, true));
				}
				if (element.equals("shadow")) Modifiers.add(p, "silent_step", 1, ticks);
				job.changed++;
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
		if (!job.touched.isEmpty()) {
			String element = c.spell.element == null ? "stone" : c.spell.element;
			ground(level, job.touched, element, tier, toggle);
			if (!toggle.equals("strike")) {
				BlockPos mid = job.touched.get(job.touched.size() / 2);
				creatures(level, p, Vec3.atCenterOf(mid), 3 + tier, element, tier, false);
			}
		}
		if (job.sealed > 0) {
			c.tell("The lair's seal holds. It would take an environment spell of tier " + job.sealed + " to open it; this one is tier " + tier + ".");
		}
		if (job.changed == 0) return c.fail(job.sealed > 0 ? "The seal does not give." : "The land does not answer here.");
		return true;
	}

	/** The element's mark on the ground a spell has touched. */
	private static void ground(ServerLevel level, java.util.List<BlockPos> touched, String element, int tier, String toggle) {
		var random = level.getRandom();
		boolean hollow = toggle.equals("pit") || toggle.equals("moat") || toggle.equals("crater");
		WordBank.Flavor flavor = WordBank.flavor(element);
		int color = Fx.color(flavor == null ? "arcane" : flavor.color);
		int shown = 0;
		for (BlockPos pos : touched) {
			BlockState here = level.getBlockState(pos);
			BlockState below = level.getBlockState(pos.below());
			boolean floor = here.isAir() && !below.canBeReplaced() && below.isCollisionShapeFullBlock(level, pos.below());
			if (shown < 14 && random.nextInt(4) == 0) {
				Fx.burst(level, Vec3.atCenterOf(pos), color, null, 1);
				shown++;
			}
			switch (element) {
				case "fire" -> {
					if (floor && random.nextInt(6) == 0) level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
					for (Direction d : Direction.values()) {
						BlockPos near = pos.relative(d);
						BlockState state = level.getBlockState(near);
						if (state.is(Blocks.SAND) && random.nextInt(3) == 0) level.setBlockAndUpdate(near, Blocks.GLASS.defaultBlockState());
						else if (state.is(Blocks.ICE) || state.is(Blocks.SNOW_BLOCK)) level.setBlockAndUpdate(near, Blocks.WATER.defaultBlockState());
						else if (state.is(Blocks.SNOW)) level.removeBlock(near, false);
						else if (state.is(Blocks.COBBLESTONE) && random.nextInt(3) == 0) level.setBlockAndUpdate(near, Blocks.STONE.defaultBlockState());
					}
				}
				case "ice" -> {
					if (floor && random.nextInt(2) == 0) level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState());
					for (Direction d : Direction.values()) {
						BlockPos near = pos.relative(d);
						BlockState state = level.getBlockState(near);
						if (state.is(Blocks.WATER) && state.getFluidState().isSource()) level.setBlockAndUpdate(near, Blocks.ICE.defaultBlockState());
						else if (state.is(net.minecraft.tags.BlockTags.FIRE)) level.removeBlock(near, false);
					}
				}
				case "water" -> {
					if (floor && (hollow || random.nextInt(12) == 0)) level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
					for (Direction d : Direction.values()) {
						BlockPos near = pos.relative(d);
						BlockState state = level.getBlockState(near);
						if (state.is(net.minecraft.tags.BlockTags.FIRE)) level.removeBlock(near, false);
						else if (state.is(Blocks.FARMLAND)) level.setBlockAndUpdate(near, state.setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7));
					}
				}
				case "light" -> {
					if (here.isAir() && Math.floorMod(pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 13, 23) == 0) level.setBlockAndUpdate(pos, Blocks.LIGHT.defaultBlockState());
				}
				case "shadow" -> {
					for (Direction d : Direction.values()) {
						BlockPos near = pos.relative(d);
						BlockState state = level.getBlockState(near);
						if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LIGHT)) level.removeBlock(near, false);
					}
				}
				default -> {
				}
			}
		}
	}

	/** The element's mark on the creatures caught in a spell. */
	private static void creatures(ServerLevel level, ServerPlayer caster, Vec3 center, double radius, String element, int tier, boolean field) {
		int ticks = 40 + 20 * tier;
		for (net.minecraft.world.entity.LivingEntity e : Targeting.around(caster, center, radius, false)) {
			if (com.omnispell.familiar.Familiars.isFamiliar(e)) continue;
			Vec3 away = e.position().subtract(center);
			switch (element) {
				case "fire" -> e.igniteForSeconds(2 + tier);
				case "ice" -> {
					e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, ticks, 1 + tier / 2));
					e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 60, e.getTicksFrozen() + 80));
				}
				case "water" -> {
					e.clearFire();
					e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, ticks, 0));
					if (!field && away.lengthSqr() > 0.01) e.push(away.normalize().x * 0.6, 0.2, away.normalize().z * 0.6);
				}
				case "wind" -> {
					if (field) e.push(0, 0.45, 0);
					else if (away.lengthSqr() > 0.01) e.push(away.normalize().x * (0.8 + 0.2 * tier), 0.5, away.normalize().z * (0.8 + 0.2 * tier));
				}
				case "light" -> {
					e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, ticks * 3, 0));
					if (e.is(net.minecraft.tags.EntityTypeTags.UNDEAD)) e.igniteForSeconds(3 + tier);
				}
				case "shadow" -> {
					e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, ticks, 0));
					if (e instanceof net.minecraft.world.entity.Mob mob) mob.setTarget(null);
				}
				case "void" -> {
					if (away.lengthSqr() > 0.3) e.push(-away.normalize().x * 0.5, field ? 0.1 : 0.4, -away.normalize().z * 0.5);
				}
				case "blood" -> {
					e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, ticks, 0));
					if (caster.isAlive()) caster.heal(field ? 0.25f : 1.0f);
				}
				default -> e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, ticks, field ? 3 : 1));
			}
			e.syncVelocity = true;
		}
		if (field && element.equals("wind") && caster.position().distanceToSqr(center) <= radius * radius) {
			caster.push(0, 0.45, 0);
			caster.fallDistance = 0;
			caster.syncVelocity = true;
		}
	}

	private static boolean solidBeside(ServerLevel level, BlockPos pos) {
		int solid = 0;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (!level.getBlockState(pos.relative(direction)).canBeReplaced()) solid++;
		}
		return solid >= 3 && !level.getBlockState(pos.below()).canBeReplaced();
	}
}
