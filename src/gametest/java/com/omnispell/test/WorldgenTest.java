package com.omnispell.test;

import com.omnispell.Omnispell;
import com.omnispell.boss.BossDef;
import com.omnispell.boss.Bosses;
import com.omnispell.item.OmniItems;
import com.omnispell.player.PlayerStore;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Makes a normal (not flat) world and checks that Omnispell's structures really
 * generate in it: an arcane shrine with its well, and a boss lair whose seal
 * wakes a boss. Run with OMNISPELL_WORLDGEN=1.
 */
public class WorldgenTest implements FabricClientGameTest {
	private static void check(boolean condition, String what) {
		if (!condition) throw new AssertionError("FAILED: " + what);
		System.out.println("[omnispell-test] ok: " + what);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static BlockPos locate(ServerLevel level, String name, BlockPos from) {
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, Omnispell.id(name));
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		return level.findNearestMapStructure(HolderSet.direct(holder), from, 100, false);
	}

	/** Crafts nothing, but walks through the Forest Gate both ways and looks at what is on the other side. */
	private static void forest(ClientGameTestContext context, TestServerContext server) {
		BlockPos[] gate = new BlockPos[1];
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			check(s.getLevel(com.omnispell.realm.Realms.FOREST) != null, "the Enchanted Forest exists as a realm");
			gate[0] = p.blockPosition().offset(2, 0, 0);
			level.setBlockAndUpdate(gate[0], OmniItems.FOREST_GATE.defaultBlockState());
			net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(gate[0]), net.minecraft.core.Direction.UP, gate[0], false);
			level.getBlockState(gate[0]).useWithoutItem(level, p, hit);
		});
		context.waitTicks(120);
		java.util.Set<String> biomes = new java.util.TreeSet<>();
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			check(com.omnispell.realm.Realms.isForest(level), "using the gate carries the player into the forest");
			check(level.getBlockState(p.blockPosition().below()).is(OmniItems.FOREST_GATE), "a return gate stands where the player arrives");
			int logs = 0;
			for (int x = -160; x <= 160; x += 16) {
				for (int z = -160; z <= 160; z += 16) biomes.add(level.getBiome(p.blockPosition().offset(x, 0, z)).getRegisteredName());
			}
			for (BlockPos pos : BlockPos.betweenClosed(p.blockPosition().offset(-40, -10, -40), p.blockPosition().offset(40, 40, 40))) {
				if (level.getBlockState(pos).is(OmniItems.FAEWOOD_LOG)) logs++;
			}
			System.out.println("[omnispell-test] forest biomes near arrival: " + biomes + ", faewood logs within 40 blocks: " + logs);
			p.teleportTo(level, p.getX(), p.getY() + 18, p.getZ(), java.util.Set.of(), 0f, 25f, true);
		});
		server.runCommand("gamemode spectator @a");
		context.waitTicks(80);
		context.takeScreenshot("50-forest-arrival");
		// Look around from four more places to see the biomes.
		int[][] hops = {{300, 0}, {0, 300}, {-300, 0}, {600, 600}};
		int n = 1;
		for (int[] hop : hops) {
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				int x = gate[0].getX() + hop[0], z = gate[0].getZ() + hop[1];
				level.getChunk(x >> 4, z >> 4);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				biomes.add(level.getBiome(new BlockPos(x, y, z)).getRegisteredName());
				p.teleportTo(level, x + 0.5, y + 14, z + 0.5, java.util.Set.of(), 0f, 25f, true);
			});
			context.waitTicks(90);
			context.takeScreenshot("51-forest-view-" + (n++));
		}
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			System.out.println("[omnispell-test] forest biomes seen: " + biomes);
			check(biomes.stream().allMatch(b -> b.startsWith("omnispell:")), "the forest is made only of its own biomes");
			System.out.println("[omnispell-test] forest biomes found near arrival: " + biomes.size() + " (depends on the seed)");
			int boosted = 0, hostile = 0;
			for (Entity e : level.getAllEntities()) {
				if (e instanceof net.minecraft.world.entity.monster.Enemy) {
					hostile++;
					if (e.entityTags().contains("omnispell_forest_empowered")) boosted++;
				}
			}
			System.out.println("[omnispell-test] forest monsters loaded: " + hostile + ", empowered: " + boosted);
			check(hostile == boosted, "every hostile creature in the forest is empowered");
		});
		// And back again through the arrival gate.
		server.runCommand("gamemode creative @a");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			BlockPos back = null;
			level.getChunk(gate[0].getX() >> 4, gate[0].getZ() >> 4);
			for (BlockPos pos : BlockPos.betweenClosed(gate[0].getX() - 3, level.getMinY(), gate[0].getZ() - 3, gate[0].getX() + 3, level.getMaxY() - 1, gate[0].getZ() + 3)) {
				if (level.getBlockState(pos).is(OmniItems.FOREST_GATE)) back = pos.immutable();
			}
			check(back != null, "the arrival gate is still there");
			net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(back), net.minecraft.core.Direction.UP, back, false);
			level.getBlockState(back).useWithoutItem(level, p, hit);
		});
		context.waitTicks(60);
		server.runOnServer(s -> check(player(s).level().dimension() == net.minecraft.world.level.Level.OVERWORLD
				&& player(s).blockPosition().closerThan(gate[0], 4), "using a gate in the forest returns to the overworld gate"));
	}

	/** Visits several generated shrines and lairs and reports how each one sits in the terrain. */
	private static void survey(ClientGameTestContext context, TestServerContext server) {
		int[][] origins = {{0, 0}, {900, 0}, {0, 900}, {-900, 0}, {0, -900}, {900, 900}, {-900, -900}, {1800, 0}};
		int shot = 0;
		for (String kind : new String[]{"arcane_shrine", "boss_lair"}) {
			int good = 0, total = 0;
			for (int[] o : origins) {
				BlockPos[] found = new BlockPos[1];
				server.runOnServer(s -> {
					ServerLevel level = player(s).level();
					BlockPos from = new BlockPos(o[0], 64, o[1]);
					if (kind.equals("arcane_shrine")) found[0] = locate(level, kind, from);
					else {
						double best = Double.MAX_VALUE;
						for (int t = 0; t < 7; t++) {
							BlockPos pos = locate(level, kind + "_" + t, from);
							if (pos != null && pos.distSqr(from) < best) {
								best = pos.distSqr(from);
								found[0] = pos;
							}
						}
					}
					if (found[0] != null) player(s).teleportTo(level, found[0].getX() + 0.5, 220, found[0].getZ() + 0.5, java.util.Set.of(), 0f, 90f, true);
				});
				if (found[0] == null) continue;
				context.waitTicks(80);
				boolean[] ok = new boolean[1];
				server.runOnServer(s -> {
					ServerPlayer p = player(s);
					ServerLevel level = p.level();
					BlockPos core = null;
					for (BlockPos pos : BlockPos.betweenClosed(found[0].offset(-24, -64, -24), found[0].offset(24, 200, 24))) {
						if (level.getBlockState(pos).is(kind.equals("arcane_shrine") ? OmniItems.ARCANE_WELL : OmniItems.BOSS_ALTAR)) core = pos.immutable();
					}
					if (core == null) {
						System.out.println("[omnispell-test] survey " + kind + " at " + found[0] + ": NOT FOUND");
						return;
					}
					String biome = level.getBiome(core).getRegisteredName();
					int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, core.getX() + 8, core.getZ() + 8);
					boolean water = false;
					int buried = 0;
					for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
						BlockPos out = core.relative(d, kind.equals("arcane_shrine") ? 5 : 8);
						if (!level.getFluidState(out).isEmpty() || !level.getFluidState(out.above()).isEmpty()) water = true;
						if (!level.getBlockState(out.above()).isAir() && !level.getBlockState(out.above(2)).isAir()) buried++;
					}
					boolean sky = kind.equals("arcane_shrine") ? level.canSeeSky(core.above()) : true;
					ok[0] = !water && buried <= 1 && sky;
					System.out.println("[omnispell-test] survey " + kind + " core " + core + " biome " + biome + " water=" + water + " buriedSides=" + buried + " sky=" + sky + (ok[0] ? "  GOOD" : "  BAD"));
					p.teleportTo(level, core.getX() + 0.5, core.getY() + 14, core.getZ() - 14.5, java.util.Set.of(), 0f, 40f, true);
				});
				total++;
				if (ok[0]) good++;
				if (shot < 16) {
					context.waitTicks(30);
					context.takeScreenshot("survey-" + kind + "-" + (shot++));
				}
			}
			System.out.println("[omnispell-test] survey " + kind + ": " + good + " of " + total + " sit well");
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getenv("OMNISPELL_WORLDGEN") == null) return;
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).create()) {
			TestServerContext server = world.getServer();
			server.runCommand("gamemode " + ("survey".equals(System.getenv("OMNISPELL_WORLDGEN")) ? "spectator" : "creative") + " @a");
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			context.waitTicks(40);

			if ("forest".equals(System.getenv("OMNISPELL_WORLDGEN"))) {
				forest(context, server);
				return;
			}
			if ("survey".equals(System.getenv("OMNISPELL_WORLDGEN"))) {
				survey(context, server);
				return;
			}

			// ---- arcane shrine
			BlockPos[] found = new BlockPos[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				found[0] = locate(p.level(), "arcane_shrine", p.blockPosition());
				check(found[0] != null, "an arcane shrine generates in a normal world");
				System.out.println("[omnispell-test] shrine at " + found[0] + ", " + (int) Math.sqrt(found[0].distSqr(p.blockPosition())) + " blocks from spawn");
				p.teleportTo(p.level(), found[0].getX() + 4.5, 200, found[0].getZ() - 6.5, java.util.Set.of(), 0f, 20f, true);
			});
			context.waitTicks(400);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				BlockPos well = null;
				for (BlockPos pos : BlockPos.betweenClosed(found[0].offset(-20, -64, -20), found[0].offset(20, 319, 20))) {
					if (level.getBlockState(pos).is(OmniItems.ARCANE_WELL)) well = pos.immutable();
				}
				check(well != null, "the generated shrine contains an arcane well");
				System.out.println("[omnispell-test] well at " + well);
				p.teleportTo(level, well.getX() + 0.5, well.getY() + 4, well.getZ() - 9.5, java.util.Set.of(), 0f, 20f, true);
			});
			context.waitTicks(60);
			context.takeScreenshot("40-generated-shrine");

			// ---- boss lair (whichever tier is nearest)
			int[] tier = {-1};
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				double best = Double.MAX_VALUE;
				for (int t = 0; t < 7; t++) {
					BlockPos pos = locate(p.level(), "boss_lair_" + t, p.blockPosition());
					if (pos == null) continue;
					double d = pos.distSqr(p.blockPosition());
					System.out.println("[omnispell-test] nearest " + BossDef.TIER_NAMES[t] + " lair: " + (int) Math.sqrt(d) + " blocks away");
					if (d < best) {
						best = d;
						found[0] = pos;
						tier[0] = t;
					}
				}
				check(tier[0] >= 0, "a boss lair generates in a normal world");
				p.teleportTo(p.level(), found[0].getX() + 6.5, 200, found[0].getZ() - 7.5, java.util.Set.of(), 0f, 20f, true);
			});
			context.waitTicks(100);
			BlockPos[] altar = new BlockPos[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				for (BlockPos pos : BlockPos.betweenClosed(found[0].offset(-24, -64, -24), found[0].offset(24, 200, 24))) {
					if (level.getBlockState(pos).is(OmniItems.BOSS_ALTAR)) altar[0] = pos.immutable();
				}
				check(altar[0] != null, "the generated lair contains its seal");
				// The lair is placed with a random rotation: find which side the door is on.
				net.minecraft.core.Direction doorSide = null;
				for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
					if (level.getBlockState(altar[0].relative(d, 6)).is(BossDef.door(tier[0]))) doorSide = d;
				}
				check(doorSide != null, "the lair's door is made of " + BossDef.TIER_NAMES[tier[0]]);
				BlockPos door = altar[0].relative(doorSide, 6);
				int needed = new com.omnispell.boss.Lairs.Guard(level).tierNeeded(door);
				check(needed == com.omnispell.boss.Lairs.spellTierFor(tier[0]), "the sealed door needs an environment spell of tier " + needed);
				p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				check(!p.gameMode.destroyBlock(door) && !level.getBlockState(door).isAir(), "a sealed lair cannot be broken into by hand");
				p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
				BlockPos view = altar[0].relative(doorSide, 12).above(2);
				p.teleportTo(level, view.getX() + 0.5, view.getY(), view.getZ() + 0.5, java.util.Set.of(), doorSide.getOpposite().toYRot(), 10f, true);
			});
			context.waitTicks(60);
			context.takeScreenshot("41-generated-lair");

			// ---- step inside: the boss wakes
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a minecraft:resistance 600 4 true");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.teleportTo(p.level(), altar[0].getX() + 0.5, altar[0].getY(), altar[0].getZ() - 3.5, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(50);
			LivingEntity[] boss = new LivingEntity[1];
			server.runOnServer(s -> {
				ServerLevel level = player(s).level();
				for (Entity e : level.getAllEntities()) {
					if (e instanceof LivingEntity living && Bosses.defOf(e) != null) boss[0] = living;
				}
				check(boss[0] != null, "entering the lair wakes its boss");
				BossDef def = Bosses.defOf(boss[0]);
				System.out.println("[omnispell-test] boss: " + def.name + " (" + BossDef.TIER_NAMES[def.tier] + "), health " + boss[0].getHealth());
				check(def.tier == tier[0] && boss[0].getMaxHealth() == (float) def.health(), "the boss matches the door's tier");
			});
			context.waitTicks(20);
			context.takeScreenshot("42-boss-awake");
			context.waitTicks(200); // let it use an ability or two
			context.takeScreenshot("43-boss-fight");

			// ---- defeat it: reward and record
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				BossDef def = Bosses.defOf(boss[0]);
				// Some bodies shrug off the first blows (a wildfire's shields), so strike until it falls.
				for (int blow = 0; blow < 12 && boss[0].isAlive(); blow++) {
					boss[0].setInvulnerableTime(0);
					boss[0].hurtServer(level, level.damageSources().playerAttack(p), 100000f);
				}
				check(!boss[0].isAlive(), "the boss can be killed");
				check(PlayerStore.get(p).bosses.contains(def.id), "the victory is recorded in the book");
				int scrolls = 0;
				for (Entity e : level.getAllEntities()) {
					if (e instanceof ItemEntity item && item.getItem().is(OmniItems.WORD_SCROLL)) scrolls++;
				}
				check(scrolls >= 1, "the boss dropped a word scroll (" + scrolls + ")");
			});
			context.waitTicks(20);
			context.runOnClient(c -> c.gui.setScreen(com.omnispell.client.BookScreen.onTab(4)));
			context.waitTicks(8);
			context.takeScreenshot("44-book-bosses");
		}
	}
}
