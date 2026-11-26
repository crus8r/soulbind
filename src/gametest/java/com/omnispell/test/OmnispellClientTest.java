package com.omnispell.test;

import com.google.gson.JsonObject;
import com.omnispell.block.ArcaneWellBlock;
import com.omnispell.client.BookScreen;
import com.omnispell.client.ClientState;
import com.omnispell.client.OmnispellClient;
import com.omnispell.data.WordBank;
import com.omnispell.item.OmniItems;
import com.omnispell.item.WandItem;
import com.omnispell.player.ManaPool;
import com.omnispell.player.PlayerData;
import com.omnispell.player.PlayerStore;
import com.omnispell.spell.Spell;
import com.omnispell.spell.SpellGenerator;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Starts a real game, joins a new world and plays through Omnispell: starter
 * bundle, the book, crafting, casting, mana, the arcane well and death.
 * The spell generator is replaced by a stand-in that answers in the generator's
 * own format, so the test never spends AI credits.
 */
public class OmnispellClientTest implements FabricClientGameTest {
	private static void check(boolean condition, String what) {
		if (!condition) throw new AssertionError("FAILED: " + what);
		System.out.println("[omnispell-test] ok: " + what);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** Answers like the website's generate-spell function does. */
	private static CompletableFuture<JsonObject> fakeGenerator(JsonObject request) {
		String type = request.get("spellType").getAsString();
		if (request.getAsJsonArray("words").size() == 1) {
			String base = request.getAsJsonArray("words").get(0).getAsJsonObject().get("english").getAsString();
			String[] look = switch (base) { // element, tier, pattern, particles
				case "Ice" -> new String[]{"ice", "3", "beam", "snowflakes"};
				case "Shadow" -> new String[]{"shadow", "5", "vortex", "wisps"};
				case "Light" -> new String[]{"light", "4", "nova", "motes"};
				case "Nature" -> new String[]{"nature", "2", "spiral", "leaves"};
				default -> new String[]{"fire", "1", "directional", "embers"};
			};
			String one = "{\"spell\":{\"tier\":" + look[1] + ",\"description\":\"A showcase spell.\",\"effect\":\"Deals damage.\",\"school\":\"Elemental Magic\",\"is_dark_magic\":false,"
					+ "\"effect_code\":{\"type\":\"damage\",\"primary_value\":" + (Integer.parseInt(look[1]) * 10) + ",\"duration\":0,\"element\":\"" + look[0] + "\",\"status\":\"none\",\"secondary\":null,\"modifiers\":[]},"
					+ "\"visual_manifest\":{\"pattern\":\"" + look[2] + "\",\"shape\":\"bolt\",\"color1\":\"" + look[0] + "\",\"color2\":\"" + look[0] + "\",\"particles\":\"" + look[3] + "\",\"motion\":\"cascade\",\"intensity\":" + look[1] + ",\"screenEffect\":\"none\"}}}";
			return CompletableFuture.completedFuture(WordBank.GSON.fromJson(one, JsonObject.class));
		}
		String code = switch (type) {
			case "defense" -> "{\"type\":\"shield\",\"primary_value\":25,\"duration\":3,\"element\":\"arcane\",\"status\":\"none\",\"secondary\":null,\"modifiers\":[\"thorns:5:3\"]}";
			case "enchantment" -> "{\"type\":\"buff\",\"primary_value\":20,\"duration\":3,\"element\":\"light\",\"status\":\"none\",\"secondary\":\"speed_boost\",\"modifiers\":[\"evasion:25:3\",\"regeneration:6:3\"]}";
			case "conjuration" -> "{\"type\":\"summon\",\"primary_value\":12,\"duration\":3,\"element\":\"nature\",\"status\":\"none\",\"secondary\":null,\"modifiers\":[\"summoned_creature:12:3:Ember_Wolf\"]}";
			case "charm" -> "{\"type\":\"status\",\"primary_value\":0,\"duration\":2,\"element\":\"arcane\",\"status\":\"stun\",\"secondary\":null,\"modifiers\":[]}";
			default -> "{\"type\":\"damage\",\"primary_value\":30,\"duration\":0,\"element\":\"fire\",\"status\":\"none\",\"secondary\":null,\"modifiers\":[\"critical_strike:20:1\",\"bleed:4:3\"]}";
		};
		String json = "{\"spell\":{\"tier\":3,\"description\":\"A searing lance of flame that cuts through the target.\","
				+ "\"effect\":\"Deals 30 fire damage and leaves the target bleeding for 3 turns.\",\"school\":\"Elemental Magic\",\"is_dark_magic\":false,"
				+ "\"effect_code\":" + code + ","
				+ "\"visual_manifest\":{\"pattern\":\"beam\",\"shape\":\"bolt\",\"color1\":\"fire\",\"color2\":\"light\",\"particles\":\"embers\",\"motion\":\"cascade\",\"intensity\":3,\"screenEffect\":\"flash\"}},"
				+ "\"fromMemory\":false,\"isNewDiscovery\":true}";
		return CompletableFuture.completedFuture(WordBank.GSON.fromJson(json, JsonObject.class));
	}

	/** Presses a spell key twice: the first press shows the name, the second casts. */
	private static void cast(ClientGameTestContext context, int slot) {
		context.getInput().pressKey(OmnispellClient.CAST[slot]);
		context.waitTicks(3);
		context.getInput().pressKey(OmnispellClient.CAST[slot]);
	}

	private static void craftAndSave(ClientGameTestContext context, TestServerContext server, String type, String words) {
		server.runCommand("execute as @a run omnispell craft " + type + " " + words);
		server.waitFor(s -> PlayerStore.get(player(s)).pending != null, 100);
		server.runCommand("execute as @a run omnispell save");
		context.waitTicks(3);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getenv("OMNISPELL_WORLDGEN") != null) return; // the worldgen test runs alone
		var realGenerator = SpellGenerator.transport;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule spawn_mobs false");
			context.waitTicks(40);

			// ---- boss parade: spawn every boss, let it fight for a while, photograph it
			if (System.getenv("OMNISPELL_BOSSES") != null) {
				server.runCommand("effect give @a minecraft:resistance infinite 4 true");
				server.runCommand("effect give @a minecraft:regeneration infinite 4 true");
				server.runCommand("effect give @a minecraft:fire_resistance infinite 0 true");
				BlockPos[] home = new BlockPos[1];
				server.runOnServer(s -> home[0] = player(s).blockPosition());
				int n = 0;
				for (com.omnispell.boss.BossDef def : com.omnispell.boss.BossDef.ALL.values()) {
					net.minecraft.world.entity.Mob[] boss = new net.minecraft.world.entity.Mob[1];
					server.runOnServer(s -> {
						ServerPlayer p = player(s);
						ServerLevel level = p.level();
						p.teleportTo(level, home[0].getX() + 0.5, home[0].getY(), home[0].getZ() + 0.5, java.util.Set.of(), 0f, -5f, true);
						boss[0] = com.omnispell.boss.Bosses.spawn(level, Vec3.atBottomCenterOf(home[0].offset(0, 0, 9)), def);
						check(boss[0] != null, def.name + " spawned");
						String body = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(boss[0].getType()).toString();
						System.out.println("[omnispell-test] BOSS " + def.name + " wears " + body + ", health " + boss[0].getMaxHealth() + ", hostile=" + (boss[0] instanceof net.minecraft.world.entity.monster.Enemy));
					});
					context.waitTicks(40);
					context.takeScreenshot("boss-" + String.format("%02d", n++) + "-" + def.id);
					context.waitTicks(Math.max(200, def.abilities.size() * 140)); // long enough for each attack to be used once
					server.runOnServer(s -> {
						ServerPlayer p = player(s);
						check(p.isAlive(), "player survived " + def.name + " with test protection");
						if (boss[0].isAlive()) boss[0].hurtServer(p.level(), p.level().damageSources().playerAttack(p), 100000f);
						for (net.minecraft.world.entity.Entity e : p.level().getAllEntities()) {
							if (e != p && !(e instanceof net.minecraft.world.entity.item.ItemEntity) && e.distanceToSqr(p) < 60 * 60 && e instanceof net.minecraft.world.entity.LivingEntity) e.discard();
						}
					});
					context.waitTicks(10);
				}
				return;
			}

			// ---- structure building mode: build, save, screenshot, stop
			if (System.getenv("OMNISPELL_BUILD_STRUCTURES") != null) {
				server.runOnServer(s -> StructureBuilder.buildAll(player(s).level(), player(s).blockPosition()));
				context.waitTicks(20);
				server.runOnServer(s -> {
					ServerPlayer p = player(s);
					BlockPos at = p.blockPosition();
					p.teleportTo(p.level(), at.getX() + 14.5, at.getY() + 3, at.getZ() - 1.5, java.util.Set.of(), 0f, 10f, true);
				});
				server.runCommand("gamemode spectator @a");
				context.waitTicks(30);
				context.takeScreenshot("structure-shrine");
				server.runOnServer(s -> {
					ServerPlayer p = player(s);
					p.teleportTo(p.level(), p.getX() + 78, p.getY() + 14, p.getZ() - 24, java.util.Set.of(), 0f, 25f, true);
				});
				context.waitTicks(40);
				context.takeScreenshot("structure-lairs");
				server.runOnServer(s -> {
					ServerPlayer p = player(s);
					p.teleportTo(p.level(), p.getX() - 60.5 + 6, p.getY() - 14, p.getZ() + 24 + 5, java.util.Set.of(), 0f, 5f, true);
				});
				context.waitTicks(40);
				context.takeScreenshot("structure-lair-door");
				return;
			}

			// ---- optional: one real request to the Omnispell generator (set OMNISPELL_LIVE=1).
			// "Fire" alone is a spell the website already remembers, so no AI is used.
			if (System.getenv("OMNISPELL_LIVE") != null) {
				server.runCommand("execute as @a run omnispell word Fire");
				server.runCommand("execute as @a run omnispell craft attack base:Fire");
				server.waitFor(s -> PlayerStore.get(player(s)).pending != null, 1200);
				server.runOnServer(s -> {
					Spell live = PlayerStore.get(player(s)).pending;
					System.out.println("[omnispell-test] live spell: tier " + live.tier + " | " + live.description + " | " + live.effect + " | " + live.code.type + " " + live.code.primaryValue + " " + live.code.element + " " + live.code.modifiers);
					check(!live.offline, "the real generator answered the mod");
					check(live.description.length() > 10 && live.code.primaryValue > 0, "the real reply was understood");
				});
				// A formula given in OMNISPELL_LIVE_NEW ("type word,word,...") is crafted for real: this one may use the AI.
				String fresh = System.getenv("OMNISPELL_LIVE_NEW");
				if (fresh != null && !fresh.isBlank()) {
					server.runOnServer(s -> PlayerStore.get(player(s)).pending = null);
					server.runCommand("execute as @a run omnispell words all");
					server.runCommand("execute as @a run omnispell types");
					server.runCommand("execute as @a run omnispell wand oak nether_star");
					context.waitTicks(5);
					server.runCommand("execute as @a run omnispell craft " + fresh);
					server.waitFor(s -> PlayerStore.get(player(s)).pending != null, 1800);
					server.runOnServer(s -> {
						Spell made = PlayerStore.get(player(s)).pending;
						System.out.println("[omnispell-test] NEW SPELL " + made.name + " | tier " + made.tier + " | " + made.school + " | dark=" + made.dark + " | offline=" + made.offline);
						System.out.println("[omnispell-test] NEW SPELL description: " + made.description);
						System.out.println("[omnispell-test] NEW SPELL effect: " + made.effect);
						System.out.println("[omnispell-test] NEW SPELL code: " + made.code.type + " " + made.code.primaryValue + " dur " + made.code.duration + " " + made.code.element + " status " + made.code.status + " " + made.code.modifiers);
						System.out.println("[omnispell-test] NEW SPELL visual: " + WordBank.GSON.toJson(made.visual));
						check(!made.offline, "a new spell was generated by the real generator");
					});
					throw new AssertionError("STOP-AFTER-LIVE"); // nothing else needs to run for this check
				}
				server.runCommand("execute as @a run omnispell essence 40");
				server.runOnServer(s -> {
					PlayerData data = PlayerStore.get(player(s));
					data.pending = null;
					data.words.remove("base:Fire");
				});
			}
			SpellGenerator.transport = OmnispellClientTest::fakeGenerator;

			// ---- starter bundle
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				int bundles = 0;
				boolean wand = false, book = false;
				for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
					ItemStack stack = p.getInventory().getItem(i);
					if (!stack.is(Items.BUNDLE)) continue;
					bundles++;
					check(com.soulbind.SoulBind.soulBindLevel(stack) == 2, "starter bundle has Soul Bind II");
					BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
					for (ItemStack inside : contents.itemCopies().toList()) {
						if (WandItem.isWand(inside)) wand = true;
						if (inside.is(OmniItems.GRIMOIRE)) book = true;
					}
				}
				check(bundles == 1, "exactly one starter bundle (found " + bundles + ")");
				check(wand && book, "bundle holds a wand and the book");
				boolean waypoints = false;
				for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
					BundleContents contents = p.getInventory().getItem(i).get(DataComponents.BUNDLE_CONTENTS);
					if (contents == null) continue;
					for (ItemStack inside : contents.itemCopies().toList()) {
						if (inside.is(OmniItems.WAYPOINT_ITEM) && inside.getCount() == 2 && !com.omnispell.waypoint.WaypointItem.pairId(inside).isEmpty()) waypoints = true;
					}
				}
				check(waypoints, "bundle holds a bonded pair of waypoints");
				PlayerData data = PlayerStore.get(p);
				check(data.words.size() == 9, "nine starting words (have " + data.words.size() + ")");
				check(data.spellTypes.contains("attack") && data.spellTypes.contains("exploration"), "starting spell types");
			});

			// ---- set up: wand in hand, every word, plenty of essence
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getInventory().setItem(1, WandItem.create("oak", "diamond"));
				p.getInventory().setItem(2, new ItemStack(OmniItems.GRIMOIRE));
			});
			context.getInput().pressKey(options -> options.keyHotbarSlots[1]);
			server.runCommand("execute as @a run omnispell words all");
			server.runCommand("execute as @a run omnispell types");
			server.runCommand("execute as @a run omnispell essence 100");
			context.waitTicks(10);
			check(context.computeOnClient(c -> ClientState.words.size()) > 200, "client received the dictionary");

			// ---- the book
			context.getInput().pressKey(OmnispellClient.BOOK);
			context.waitForScreen(BookScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("10-book-craft-empty");
			context.runOnClient(c -> {
				BookScreen.preset("attack", List.of("base:Fire", "command:Sever", "intention:Malice"));
				c.gui.setScreen(BookScreen.onTab(1));
			});
			context.waitTicks(5);
			context.takeScreenshot("11-book-craft-filled");
			context.runOnClient(c -> c.gui.setScreen(BookScreen.onTab(0)));
			context.waitTicks(5);
			context.takeScreenshot("12-book-dictionary");
			context.runOnClient(c -> c.gui.setScreen(BookScreen.onTab(3)));
			context.waitTicks(5);
			context.takeScreenshot("13-book-guide");
			context.runOnClient(c -> {
				BookScreen.showGuide("Materials");
				c.gui.setScreen(BookScreen.onTab(3));
			});
			context.waitTicks(5);
			context.takeScreenshot("13b-book-materials");
			context.runOnClient(c -> c.gui.setScreen(BookScreen.onTab(4)));
			context.waitTicks(5);
			context.takeScreenshot("14-book-bosses");

			// ---- craft through the server, as the Craft button does
			server.runCommand("execute as @a run omnispell craft attack base:Fire,command:Sever,intention:Malice");
			server.waitFor(s -> PlayerStore.get(player(s)).pending != null, 100);
			context.runOnClient(c -> c.gui.setScreen(BookScreen.onTab(1)));
			context.waitTicks(8);
			context.takeScreenshot("15-book-new-spell");
			server.runOnServer(s -> {
				PlayerData data = PlayerStore.get(player(s));
				check(data.essence == 90, "crafting three words cost 10 essence (essence now " + data.essence + ")");
				check(data.pending.tier == 3 && data.pending.code.primaryValue == 30, "generator reply was read");
				check(data.pending.code.modifiers.size() == 2, "toggles were read");
			});
			server.runCommand("execute as @a run omnispell save");
			craftAndSave(context, server, "exploration", "base:Light,command:Envelop,intention:Concealment");
			craftAndSave(context, server, "defense", "base:Earth,command:Shield,intention:Warding");
			craftAndSave(context, server, "exploration", "base:Earth,command:Fortify,intention:Transportation");
			craftAndSave(context, server, "conjuration", "base:Nature,command:Summon,intention:Creation,attribute:Wolf");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				PlayerData data = PlayerStore.get(p);
				check(data.spells.size() == 5, "five spells in the spellbook (have " + data.spells.size() + ")");
				Spell veil = data.spells.get(1);
				check("invisibility".equals(veil.toggle), "Light + Envelop + Concealment is invisibility (got " + veil.toggle + ")");
				ItemStack wand = p.getMainHandItem();
				check(WandItem.isWand(wand), "wand is in hand");
				for (int i = 0; i < 5; i++) check(WandItem.spell(wand, i) != null, "wand slot " + (i + 1) + " filled on save");
				System.out.println("[omnispell-test] slot 4 toggle: " + WandItem.spell(wand, 3).toggle + " / slot 4 desc: " + WandItem.spell(wand, 3).description);
			});
			context.runOnClient(c -> c.gui.setScreen(BookScreen.onTab(2)));
			context.waitTicks(8);
			context.takeScreenshot("16-book-spellbook");
			context.runOnClient(c -> c.gui.setScreen(null));
			context.waitTicks(5);

			// ---- cast the attack at a zombie with the first spell key
			Zombie[] zombie = new Zombie[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				BlockPos feet = p.blockPosition();
				p.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
				Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				z.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 5.5, 180f, 0f);
				z.setNoAi(true);
				z.setPersistenceRequired();
				z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // or daylight burns it
				level.addFreshEntity(z);
				zombie[0] = z;
			});
			context.waitTicks(20);
			context.takeScreenshot("20-hud-with-wand");
			float before = server.computeOnServer(s -> zombie[0].getHealth());
			context.getInput().pressKey(OmnispellClient.CAST[0]);
			context.waitTicks(6);
			context.takeScreenshot("20b-first-press-shows-name");
			check(server.computeOnServer(s -> zombie[0].getHealth()) == before, "one press of a spell key does not cast");
			context.getInput().pressKey(OmnispellClient.CAST[0]);
			context.waitTicks(4);
			context.takeScreenshot("21-attack-cast");
			context.waitTicks(10);
			float after = server.computeOnServer(s -> zombie[0].getHealth());
			check(after < before, "attack spell hurt the zombie (" + before + " -> " + after + ")");
			check(server.computeOnServer(s -> zombie[0].getRemainingFireTicks() > 0 || !zombie[0].isAlive()), "fire spell set the zombie alight");
			check(context.computeOnClient(c -> ClientState.cooldownFraction(0)) > 0, "client shows the spell on cooldown");

			// ---- invisibility (exploration) costs Phase mana
			double phaseBefore = server.computeOnServer(s -> PlayerStore.get(player(s)).mana[ManaPool.PHASE.ordinal()]);
			cast(context, 1);
			context.waitTicks(10);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				check(p.hasEffect(MobEffects.INVISIBILITY), "invisibility spell made the caster invisible");
				check(PlayerStore.get(p).mana[ManaPool.PHASE.ordinal()] < phaseBefore, "invisibility spent Phase mana");
				p.removeEffect(MobEffects.INVISIBILITY);
			});

			// ---- shield (defense) gives absorption and spends Barrier mana
			cast(context, 2);
			context.waitTicks(10);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				check(p.getAbsorptionAmount() > 0, "shield spell gave absorption (" + p.getAbsorptionAmount() + ")");
				check(PlayerStore.get(p).mana[ManaPool.BARRIER.ordinal()] < 100, "shield spent Barrier mana");
			});
			context.takeScreenshot("22-shield");

			// ---- conjuration summons an ally that later vanishes
			cast(context, 4);
			context.waitTicks(10);
			check(server.computeOnServer(s -> player(s).level().getEntities(EntityTypes.WOLF, e -> e.entityTags().contains("omnispell_summon")).size()) == 1, "conjuration summoned one ally");
			context.takeScreenshot("23-summon");

			// ---- bridge: spans a gap with blocks that vanish again
			BlockPos[] origin = new BlockPos[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				for (var wolf : level.getEntities(EntityTypes.WOLF, e -> true)) wolf.discard();
				if (zombie[0].isAlive()) zombie[0].discard();
				BlockPos feet = p.blockPosition();
				origin[0] = feet;
				p.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
				for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -3, 1), feet.offset(2, -1, 12))) level.setBlockAndUpdate(pos.immutable(), Blocks.AIR.defaultBlockState());
			});
			context.waitTicks(10);
			cast(context, 3);
			context.waitTicks(10);
			context.takeScreenshot("22b-bridge");
			server.runOnServer(s -> {
				ServerLevel level = player(s).level();
				int placed = 0;
				for (int z = 1; z <= 12; z++) if (!level.getBlockState(origin[0].offset(0, -1, z)).isAir()) placed++;
				check(placed >= 10, "bridge spell spanned the gap (" + placed + " of 12 blocks)");
			});

			// ---- a second wand: wall, teleport, and five attack spells of different elements and tiers
			server.runOnServer(s -> player(s).getInventory().setItem(3, WandItem.create("birch", "nether_star")));
			context.getInput().pressKey(options -> options.keyHotbarSlots[3]);
			context.waitTicks(5);
			craftAndSave(context, server, "exploration", "base:Fire,command:Fortify,intention:Warding");
			craftAndSave(context, server, "exploration", "base:Fire,command:Transmute,intention:Transportation");
			server.runOnServer(s -> {
				ItemStack wand = player(s).getMainHandItem();
				check("barrier_wall".equals(WandItem.spell(wand, 0).toggle), "Fire + Fortify + Warding is a wall (got " + WandItem.spell(wand, 0).toggle + ")");
				check("teleport".equals(WandItem.spell(wand, 1).toggle), "Fire + Transmute + Transportation is a teleport (got " + WandItem.spell(wand, 1).toggle + ")");
			});
			cast(context, 0);
			context.waitTicks(10);
			context.takeScreenshot("22c-wall");
			server.runOnServer(s -> {
				ServerLevel level = player(s).level();
				int placed = 0;
				for (BlockPos pos : BlockPos.betweenClosed(origin[0].offset(-3, 0, 3), origin[0].offset(3, 2, 3))) if (!level.getBlockState(pos).isAir()) placed++;
				check(placed >= 12, "wall spell raised a wall (" + placed + " blocks)");
			});
			// Turn away from the wall just raised: a teleport stops at the first thing in the way.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.teleportTo(p.level(), origin[0].getX() + 0.5, origin[0].getY(), origin[0].getZ() + 0.5, java.util.Set.of(), 180f, 0f, true);
			});
			context.waitTicks(10);
			cast(context, 1);
			context.waitTicks(10);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				double moved = Math.sqrt(p.blockPosition().distSqr(origin[0]));
				check(moved > 20, "teleport spell moved the caster " + (int) moved + " blocks");
			});
			// conjured blocks must vanish again (bridge 20 s, wall 15 s)
			context.waitTicks(430);
			server.runOnServer(s -> {
				ServerLevel level = player(s).level();
				int left = 0;
				for (int z = 1; z <= 12; z++) if (!level.getBlockState(origin[0].offset(0, -1, z)).isAir()) left++;
				for (BlockPos pos : BlockPos.betweenClosed(origin[0].offset(-3, 0, 3), origin[0].offset(3, 2, 3))) if (!level.getBlockState(pos).isAir()) left++;
				check(left == 0, "conjured bridge and wall vanished again (" + left + " blocks left)");
			});

			// ---- particles: one attack spell per element, at tiers 1 to 5
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				ItemStack wand = p.getMainHandItem();
				for (int i = 0; i < 5; i++) WandItem.setSpell(wand, i, null);
				BlockPos feet = origin[0].offset(20, 0, 0);
				p.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, java.util.Set.of(), 0f, 5f, true);
				Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				z.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 7.5, 180f, 0f);
				z.setNoAi(true);
				z.setPersistenceRequired();
				z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);
				z.setHealth(500);
				z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				level.addFreshEntity(z);
				zombie[0] = z;
			});
			String[] bases = {"Fire", "Nature", "Ice", "Light", "Shadow"};
			for (String base : bases) craftAndSave(context, server, "attack", "base:" + base);
			context.waitTicks(10);
			for (int i = 0; i < bases.length; i++) {
				cast(context, i);
				context.waitTicks(3);
				context.takeScreenshot("27-particles-tier" + (i + 1) + "-" + bases[i].toLowerCase());
				context.waitTicks(25);
			}
			check(server.computeOnServer(s -> zombie[0].getHealth()) < 500, "showcase spells hit their target");
			server.runOnServer(s -> {
				zombie[0].discard();
				ServerPlayer p = player(s);
				p.teleportTo(p.level(), origin[0].getX() + 0.5, origin[0].getY(), origin[0].getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
				p.getInventory().setItem(3, ItemStack.EMPTY);
			});
			context.getInput().pressKey(options -> options.keyHotbarSlots[1]);
			context.waitTicks(5);

			// ---- potions: charge, liquid, ingredients, heat, bottle, drink, throw
			com.omnispell.alchemy.PotionGenerator.transport = request -> {
				boolean weapon = request.toString().contains("Void Shard");
				String code = weapon
						? "{\"type\":\"damage\",\"value\":30,\"duration\":0,\"element\":\"fire\",\"resist_type\":null,\"status\":\"none\",\"mana_type\":null}"
						: "{\"type\":\"heal\",\"value\":25,\"duration\":0,\"element\":\"nature\",\"resist_type\":null,\"status\":\"regen\",\"mana_type\":null}";
				String json = "{\"potion\":{\"name\":\"" + (weapon ? "Searing Vitriol" : "Meadow Restorative") + "\",\"description\":\"A test potion.\",\"effect\":\""
						+ (weapon ? "Deals 30 fire damage when thrown." : "Heals 25 HP.") + "\",\"tier\":3,\"type\":\"" + (weapon ? "offensive" : "healing") + "\",\"effect_code\":" + code + "}}";
				return CompletableFuture.completedFuture(WordBank.GSON.fromJson(json, JsonObject.class));
			};
			BlockPos[] vat = new BlockPos[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				vat[0] = p.blockPosition().offset(-3, 0, 2);
				level.setBlockAndUpdate(vat[0], OmniItems.ALCHEMY_CAULDRON.defaultBlockState());
				level.setBlockAndUpdate(vat[0].below(), Blocks.MAGMA_BLOCK.defaultBlockState());
				check("high".equals(com.omnispell.alchemy.AlchemyCauldronBlock.heat(level, vat[0])), "magma under a cauldron is high heat");
			});
			java.util.function.BiConsumer<net.minecraft.world.item.Item, Integer> pour = (item, count) -> server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ItemStack held = new ItemStack(item, count);
				p.setItemInHand(InteractionHand.OFF_HAND, held);
				BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(vat[0]), Direction.UP, vat[0], false);
				p.level().getBlockState(vat[0]).useItemOn(held, p.level(), p, InteractionHand.OFF_HAND, hit);
			});
			pour.accept(Items.BLAZE_POWDER, 1);
			pour.accept(Items.HONEY_BOTTLE, 1);
			pour.accept(Items.POPPY, 1);
			pour.accept(Items.GLISTERING_MELON_SLICE, 1);
			pour.accept(Items.LAPIS_LAZULI, 1);
			context.waitTicks(5);
			context.getInput().lookAt(vat[0]);
			context.waitTicks(5);
			context.takeScreenshot("28-alchemy-cauldron");
			pour.accept(Items.GLASS_BOTTLE, 3);
			server.waitFor(s -> com.omnispell.alchemy.Vats.at(player(s).level(), vat[0]).servings > 0, 200);
			pour.accept(Items.GLASS_BOTTLE, 3);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ItemStack potion = ItemStack.EMPTY;
				for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(OmniItems.BREW)) potion = p.getInventory().getItem(i);
				check(!potion.isEmpty(), "a glass bottle draws a potion from the cauldron");
				com.omnispell.alchemy.Brew brew = com.omnispell.alchemy.BrewItem.read(potion);
				check(brew != null && brew.name.equals("Meadow Restorative") && !brew.splash, "the potioneer's reply was read (" + brew.name + ")");
				check(com.omnispell.alchemy.Vats.at(p.level(), vat[0]).servings == 2, "a brew fills three bottles (two left)");
				p.setHealth(6f);
				p.setItemInHand(InteractionHand.MAIN_HAND, potion.copyWithCount(1));
				potion.getItem().use(p.level(), p, InteractionHand.MAIN_HAND);
				check(p.getHealth() >= 15.5f, "drinking the healing potion healed 10 health (now " + p.getHealth() + ")");
			});
			// a weaponized brew is thrown
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				com.omnispell.alchemy.Vats.Vat v = com.omnispell.alchemy.Vats.at(p.level(), vat[0]);
				v.base = null;
				v.items.clear();
				v.servings = 0;
				for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(OmniItems.BREW)) p.getInventory().setItem(i, ItemStack.EMPTY);
			});
			pour.accept(Items.LAVA_BUCKET, 1);
			pour.accept(Items.BLAZE_ROD, 1);
			pour.accept(Items.FERMENTED_SPIDER_EYE, 1);
			pour.accept(Items.GLASS_BOTTLE, 3);
			server.waitFor(s -> com.omnispell.alchemy.Vats.at(player(s).level(), vat[0]).servings > 0, 200);
			pour.accept(Items.GLASS_BOTTLE, 3);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				ItemStack potion = ItemStack.EMPTY;
				for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(OmniItems.BREW)) potion = p.getInventory().getItem(i);
				com.omnispell.alchemy.Brew brew = com.omnispell.alchemy.BrewItem.read(potion);
				check(brew != null && brew.splash, "a weaponized brew is a potion for throwing");
				Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				BlockPos feet = p.blockPosition();
				p.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
				z.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 5.5, 180f, 0f);
				z.setNoAi(true);
				z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				level.addFreshEntity(z);
				zombie[0] = z;
				p.setItemInHand(InteractionHand.MAIN_HAND, potion.copyWithCount(1));
			});
			context.waitTicks(10);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getMainHandItem().getItem().use(p.level(), p, InteractionHand.MAIN_HAND);
				check(zombie[0].getHealth() < 20f, "the thrown potion hurt its target (" + zombie[0].getHealth() + ")");
				zombie[0].discard();
				p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			});
			context.getInput().pressKey(options -> options.keyHotbarSlots[1]);
			server.runOnServer(s -> player(s).getInventory().setItem(1, WandItem.create("oak", "diamond")));
			context.waitTicks(5);

			// ---- arcane well: wait for the surge, then draw
			BlockPos[] well = new BlockPos[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				well[0] = p.blockPosition().offset(2, 0, 0);
				p.level().setBlockAndUpdate(well[0], OmniItems.ARCANE_WELL.defaultBlockState());
				PlayerStore.get(p).essence = 50;
			});
			Runnable useWell = () -> server.runOnServer(s -> {
				ServerPlayer p = player(s);
				BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(well[0]), Direction.UP, well[0], false);
				p.level().getBlockState(well[0]).useWithoutItem(p.level(), p, hit);
			});
			useWell.run();
			server.waitFor(s -> ArcaneWellBlock.surging(player(s)), 200);
			useWell.run();
			check(server.computeOnServer(s -> PlayerStore.get(player(s)).essence) == 60, "catching the surge gave 10 essence");
			useWell.run(); // far too early: must not give essence
			check(server.computeOnServer(s -> PlayerStore.get(player(s)).essence) == 60, "using the well too early gives nothing");
			context.takeScreenshot("24-well");

			// ---- waypoints: a crafted pair bonds, links, teleports, recolours and comes back when broken
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getInventory().setItem(4, new ItemStack(OmniItems.WAYPOINT_ITEM, 2)); // as it comes out of the crafting grid
			});
			context.waitTicks(5);
			BlockPos[] ends = new BlockPos[2];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				ItemStack pair = p.getInventory().getItem(4);
				String id = com.omnispell.waypoint.WaypointItem.pairId(pair);
				check(!id.isEmpty() && pair.getCount() == 2, "a crafted pair of waypoints bonds in the inventory");
				check(PlayerStore.get(p).waypointPairs == 2, "second pair for this player (count " + PlayerStore.get(p).waypointPairs + ")");
				ends[0] = p.blockPosition().offset(3, 0, 3);
				ends[1] = p.blockPosition().offset(40, 0, 3);
				for (BlockPos end : ends) {
					level.setBlockAndUpdate(end, OmniItems.WAYPOINT.defaultBlockState().setValue(com.omnispell.waypoint.WaypointBlock.COLOR, 1));
					check(com.omnispell.waypoint.Waypoints.place(level, end, id, 1, ""), "waypoint end registered");
				}
				p.getInventory().setItem(4, ItemStack.EMPTY);
				PlayerStore.get(p).mana[ManaPool.PHASE.ordinal()] = 5;
				BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ends[0]), Direction.UP, ends[0], false);
				level.getBlockState(ends[0]).useWithoutItem(level, p, hit);
			});
			context.waitTicks(10);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				check(p.blockPosition().closerThan(ends[1].above(), 2), "using a waypoint travels to its partner (now at " + p.blockPosition() + ")");
				check(PlayerStore.get(p).mana[ManaPool.PHASE.ordinal()] < 4.5, "travel spent Phase mana");
				String id = com.omnispell.waypoint.Waypoints.pairAt(p.level(), ends[1]);
				com.omnispell.waypoint.Waypoints.edit(s, id, "Home", 10);
				check(p.level().getBlockState(ends[0]).getValue(com.omnispell.waypoint.WaypointBlock.COLOR) == 10
						&& p.level().getBlockState(ends[1]).getValue(com.omnispell.waypoint.WaypointBlock.COLOR) == 10, "recolouring one end recolours both");
			});
			context.getInput().lookAt(ends[1]);
			context.waitTicks(10);
			context.takeScreenshot("25-waypoint");
			context.runOnClient(c -> c.gui.setScreen(new com.omnispell.client.WaypointScreen("test", "Home", 10)));
			context.waitTicks(5);
			context.takeScreenshot("26-waypoint-screen");
			context.runOnClient(c -> c.gui.setScreen(null));

			// ---- death: the soul-bound bundle and all Omnispell progress survive
			server.runOnServer(s -> player(s).getInventory().setItem(1, ItemStack.EMPTY)); // the loose wand would simply drop
			server.runCommand("kill @a");
			context.waitForScreen(net.minecraft.client.gui.screens.DeathScreen.class);
			context.waitTicks(30);
			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(40);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				int bundles = 0;
				for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
					ItemStack stack = p.getInventory().getItem(i);
					if (!stack.is(Items.BUNDLE)) continue;
					bundles++;
					check(stack.get(DataComponents.BUNDLE_CONTENTS).size() == 3, "bundle kept its three items through death");
				}
				check(bundles == 1, "one bundle after respawn, not zero and not a second one (found " + bundles + ")");
				PlayerData data = PlayerStore.get(p);
				check(data.spells.size() == 12 && data.words.size() > 200, "words and spells survive death (" + data.spells.size() + " spells)");
			});
			context.takeScreenshot("30-after-respawn");
		} finally {
			SpellGenerator.transport = realGenerator;
		}
	}
}
