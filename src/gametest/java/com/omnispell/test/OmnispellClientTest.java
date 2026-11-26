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
	private static TestServerContext SERVER;

	private static ItemStack charmGem(ServerLevel level, String charm, int strength) {
		ItemStack gem = new ItemStack(Items.AMETHYST_SHARD);
		var holder = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
				.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, com.omnispell.Omnispell.id(charm)));
		gem.enchant(holder.orElseThrow(), strength);
		return gem;
	}

	private static void cast(ClientGameTestContext context, int slot) {
		// Attack spells cost Force mana; the play-through is not about running dry.
		if (SERVER != null) SERVER.runOnServer(s -> {
			PlayerStore.get(player(s)).mana[com.omnispell.player.ManaPool.FORCE.ordinal()] = 60;
			PlayerStore.get(player(s)).mana[com.omnispell.player.ManaPool.SPIRIT.ordinal()] = 60;
		});
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
			SERVER = server;
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
					var weight = contents.weight().getOrThrow();
					check(weight.compareTo(org.apache.commons.lang3.math.Fraction.getFraction(4, 64)) == 0, "the starter kit takes 4 of the bundle's 64 places (takes " + weight + ")");
					for (ItemStack inside : contents.itemCopies().toList()) {
						if (WandItem.isWand(inside)) {
							wand = true;
							check(com.soulbind.SoulBind.soulBindLevel(inside) == 2, "the starter wand is soul-bound like its bundle");
						}
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
				check(data.words.size() == 11, "eleven starting words, Maxima and Minima among them (have " + data.words.size() + ")");
				check(data.spellTypes.contains("attack") && data.spellTypes.contains("exploration"), "starting spell types");
			});

			// ---- creative mode opens the whole dictionary without changing what was earned
			server.runCommand("gamemode creative @a");
			context.waitTicks(30);
			check(context.computeOnClient(c -> ClientState.words.size()) > 200, "creative mode shows every word");
			check(server.computeOnServer(s -> PlayerStore.get(player(s)).words.size()) == 11, "creative mode does not change the words actually known");
			server.runCommand("gamemode survival @a");
			context.waitTicks(30);
			check(context.computeOnClient(c -> ClientState.words.size()) == 11, "back in survival only the earned words show");

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
			context.runOnClient(c -> c.gui.setScreen(BookScreen.onTab(5)));
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
			// A plain left click on a tab must work.
			double[] at = context.computeOnClient(c -> {
				var screen = c.gui.screen();
				int pw = Math.min(400, screen.width - 20), ph = Math.min(236, screen.height - 20);
				int px = (screen.width - pw) / 2, py = (screen.height - ph) / 2;
				double scale = c.getWindow().getGuiScale();
				return new double[]{(px + 6 + 3 * 58 + 27) * scale, (py + 14) * scale};
			});
			context.getInput().setCursorPos(at[0], at[1]);
			context.waitTicks(3);
			context.getInput().pressMouse(0);
			context.waitTicks(3);
			check(context.computeOnClient(c -> BookScreen.currentTab()) == 3, "a left click on the Guide tab opens it (tab is " + context.computeOnClient(c -> BookScreen.currentTab()) + ")");
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
			craftAndSave(context, server, "exploration", "base:Fire,command:Scatter,intention:Transportation");
			server.runOnServer(s -> {
				ItemStack wand = player(s).getMainHandItem();
				check("raise_wall".equals(WandItem.spell(wand, 0).toggle), "Fire + Fortify + Warding is a wall (got " + WandItem.spell(wand, 0).toggle + ")");
				check("teleport".equals(WandItem.spell(wand, 1).toggle), "Fire + Scatter + Transportation is a teleport (got " + WandItem.spell(wand, 1).toggle + ")");
			});
			cast(context, 0);
			context.waitTicks(10);
			context.takeScreenshot("22c-wall");
			server.runOnServer(s -> {
				ServerLevel level = player(s).level();
				int placed = 0;
				for (BlockPos pos : BlockPos.betweenClosed(origin[0].offset(-3, -6, 3), origin[0].offset(3, 3, 3))) if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.TransparentBlock) placed++;
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
				check(left == 0, "the conjured bridge vanished again (" + left + " blocks left)");
				for (BlockPos pos : BlockPos.betweenClosed(origin[0].offset(-5, -6, 3), origin[0].offset(5, 4, 3))) if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.TransparentBlock) level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			});

			Zombie[] vortexed = new Zombie[1];
			// ---- land shaping: carve a tunnel, mend water into grass, raise a lasting wall; then the wand's plain bolt
			BlockPos[] site = new BlockPos[1];
			server.runOnServer(s -> player(s).getInventory().setItem(4, WandItem.create("oak", "nether_star")));
			context.getInput().pressKey(options -> options.keyHotbarSlots[4]);
			context.waitTicks(5);
			server.runCommand("execute as @a run omnispell essence 200");
			craftAndSave(context, server, "exploration", "base:Earth,command:Sever,intention:Malice");
			craftAndSave(context, server, "exploration", "base:Earth,command:Envelop,intention:Restoration");
			craftAndSave(context, server, "exploration", "base:Earth,command:Fortify,intention:Warding");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				ItemStack wand = p.getMainHandItem();
				check("carve".equals(WandItem.spell(wand, 0).toggle), "Earth + Sever + Malice carves (got " + WandItem.spell(wand, 0).toggle + ")");
				check("mend_land".equals(WandItem.spell(wand, 1).toggle), "Earth + Envelop + Restoration mends the land (got " + WandItem.spell(wand, 1).toggle + ")");
				check("raise_wall".equals(WandItem.spell(wand, 2).toggle), "Earth + Fortify + Warding raises a wall");
				BlockPos b = origin[0].offset(40, 0, 0);
				site[0] = b;
				var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
				for (BlockPos pos : BlockPos.betweenClosed(b.offset(-9, 0, -9), b.offset(9, 8, 9))) level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(b.offset(-9, -2, -9), b.offset(9, -1, 9))) level.setBlockAndUpdate(pos, stone);
				for (BlockPos pos : BlockPos.betweenClosed(b.offset(-3, 0, 2), b.offset(3, 4, 8))) level.setBlockAndUpdate(pos, stone);
				level.setBlockAndUpdate(b.offset(0, 1, 4), net.minecraft.world.level.block.Blocks.DIAMOND_ORE.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(b.offset(-2, -1, -5), b.offset(2, -1, -2))) level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
				p.teleportTo(level, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(10);
			cast(context, 0);
			context.waitTicks(10);
			context.takeScreenshot("22d-carve");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				BlockPos b = site[0];
				check(level.getBlockState(b.offset(0, 1, 3)).isAir() && level.getBlockState(b.offset(0, 0, 5)).isAir(), "the carve spell bored a tunnel into the stone");
				check(level.getBlockState(b.offset(0, 1, 4)).is(net.minecraft.world.level.block.Blocks.DIAMOND_ORE), "carving leaves ore standing");
				check(!level.getBlockState(b.offset(0, -1, 4)).isAir(), "carving level leaves the floor");
				check(!level.getBlockState(b.offset(0, 1, 8)).isAir(), "a tier 1 tunnel stops after five blocks");
				p.teleportTo(level, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, java.util.Set.of(), 180f, 40f, true);
			});
			context.waitTicks(10);
			cast(context, 1);
			context.waitTicks(10);
			context.takeScreenshot("22e-mend");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				BlockPos b = site[0];
				int water = 0, grass = 0;
				for (BlockPos pos : BlockPos.betweenClosed(b.offset(-2, -1, -5), b.offset(2, -1, -2))) {
					if (!level.getFluidState(pos).isEmpty()) water++;
					if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) grass++;
				}
				check(water <= 4 && grass >= 12, "mending covered the water with grass (" + grass + " grass, " + water + " water left)");
				p.teleportTo(level, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, java.util.Set.of(), 90f, 0f, true);
			});
			context.waitTicks(10);
			cast(context, 2);
			context.waitTicks(10);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				BlockPos b = site[0];
				int dirt = 0;
				for (BlockPos pos : BlockPos.betweenClosed(b.offset(-3, 0, -3), b.offset(-3, 3, 3))) if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.DIRT)) dirt++;
				check(dirt >= 12, "an Earth wall is real dirt (" + dirt + " blocks)");

				// The wand's left-click with an empty slot: a plain bolt.
				ItemStack wand = p.getMainHandItem();
				WandItem.select(wand, 4);
				Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				z.snapTo(b.getX() + 0.5, b.getY(), b.getZ() - 5.5, 0f, 0f);
				z.setNoAi(true);
				z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				level.addFreshEntity(z);
				p.teleportTo(level, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, java.util.Set.of(), 180f, 0f, true);
				p.setYRot(180f);
				p.setXRot(0f);
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.FORCE.ordinal()] = 6;
				com.omnispell.spell.Casting.primary(p, wand);
				check(z.getHealth() < z.getMaxHealth(), "left-click with an empty slot throws an arcane bolt (zombie health " + z.getHealth() + ")");
				check(Math.abs(PlayerStore.get(p).mana[com.omnispell.player.ManaPool.FORCE.ordinal()] - 1) < 0.01, "a bolt costs 5 Force mana");
				z.discard();

				// A coloured strike: Void + Summon + Malice calls purple lightning on what you look at.
				PlayerStore.get(p).essence = 500;
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Void", "command:Summon", "intention:Malice"));
				com.omnispell.spell.Spell strike = PlayerStore.get(p).pending;
				check(strike != null && "strike".equals(strike.toggle) && strike.description.contains("purple lightning"), "Void + Summon + Malice is a strike of purple lightning: " + (strike == null ? "nothing" : strike.description));
				Zombie z2 = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				z2.snapTo(b.getX() + 0.5, b.getY(), b.getZ() - 6.5, 0f, 0f);
				z2.setNoAi(true);
				z2.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				level.addFreshEntity(z2);
				p.setXRot(25f);
				check(com.omnispell.spell.ExploreEffects.cast(new com.omnispell.spell.Cast(p, wand, strike)) && z2.getHealth() < z2.getMaxHealth(), "the strike hit what stood there (zombie health " + z2.getHealth() + ")");
				z2.discard();

				// Pillar: Earth + Ascend + Transportation lifts the caster.
				PlayerStore.get(p).pending = null;
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Earth", "command:Ascend", "intention:Transportation"));
				com.omnispell.spell.Spell pillar = PlayerStore.get(p).pending;
				double yBefore = p.getY();
				check(pillar != null && "pillar".equals(pillar.toggle) && com.omnispell.spell.ExploreEffects.cast(new com.omnispell.spell.Cast(p, wand, pillar)), "Earth + Ascend + Transportation is a pillar");
				check(p.getY() >= yBefore + 4.9 && level.getBlockState(b).is(net.minecraft.world.level.block.Blocks.DIRT), "the pillar lifted the caster five blocks on dirt (rose " + (p.getY() - yBefore) + ")");
				for (int h = 0; h < 6; h++) level.setBlockAndUpdate(b.above(h), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				p.teleportTo(level, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, java.util.Set.of(), 180f, 0f, true);
				PlayerStore.get(p).pending = null;

				// Launch: Air + Envelop + Liberation throws the caster upward.
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Air", "command:Envelop", "intention:Liberation"));
				com.omnispell.spell.Spell launch = PlayerStore.get(p).pending;
				check(launch != null && "phase".equals(launch.toggle), "Air + Envelop + Liberation is phase (got " + (launch == null ? "nothing" : launch.toggle) + ")");
				check(com.omnispell.spell.ExploreEffects.cast(new com.omnispell.spell.Cast(p, wand, launch)) && p.getDeltaMovement().y > 1.2, "with no wall in front, phase throws the caster up instead (upward speed " + p.getDeltaMovement().y + ")");
				p.setDeltaMovement(0, 0, 0);
				p.removeAllEffects();
				PlayerStore.get(p).pending = null;
				// No intention: the command let loose. Air + Envelop is a wild bound.
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Air", "command:Envelop"));
				com.omnispell.spell.Spell bound = PlayerStore.get(p).pending;
				check(bound != null && "bound".equals(bound.toggle) && !bound.fizzle, "Air + Envelop with no intention is a wild bound (got " + (bound == null ? "nothing" : bound.toggle) + ")");
				check(com.omnispell.spell.ExploreEffects.cast(new com.omnispell.spell.Cast(p, wand, bound)) && p.getDeltaMovement().y > 0.8 && p.getDeltaMovement().horizontalDistance() > 0.8, "the bound flings the caster up and forward");
				p.setDeltaMovement(0, 0, 0);
				p.removeAllEffects();
				PlayerStore.get(p).pending = null;
				// A colour attribute tints the magic an environment spell builds with.
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Fire", "command:Fortify", "intention:Warding", "attribute:Blue"));
				com.omnispell.spell.Spell blueWall = PlayerStore.get(p).pending;
				check(blueWall != null && "raise_wall".equals(blueWall.toggle) && com.omnispell.spell.Terraform.magicBlock(blueWall) == net.minecraft.world.level.block.Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.BLUE), "a Blue attribute makes a fire wall of blue magic");
				PlayerStore.get(p).pending = null;

				// Cantrips: a base word alone.
				for (var pool : com.omnispell.player.ManaPool.values()) PlayerStore.get(p).mana[pool.ordinal()] = pool.baseMax;
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Fire"));
				com.omnispell.spell.Spell kindle = PlayerStore.get(p).pending;
				check(kindle != null && "kindle".equals(kindle.toggle) && kindle.name.endsWith("se"), "Fire alone is the Kindle cantrip, named " + (kindle == null ? "nothing" : kindle.name));
				PlayerStore.get(p).pending = null;
				var rawBeefItem = new net.minecraft.world.entity.item.ItemEntity(level, b.getX() + 0.5, b.getY() + 0.2, b.getZ() - 2.5, new ItemStack(Items.BEEF, 3));
				rawBeefItem.setDeltaMovement(0, 0, 0);
				level.addFreshEntity(rawBeefItem);
				p.setXRot(32f);
				check(com.omnispell.spell.ExploreEffects.cast(new com.omnispell.spell.Cast(p, wand, kindle)) && rawBeefItem.getItem().is(Items.COOKED_BEEF), "Kindle cooks loose food where it lands (now " + rawBeefItem.getItem() + ")");
				rawBeefItem.discard();
				check(com.omnispell.spell.Terraform.heated(Items.RAW_IRON) == Items.IRON_INGOT, "and it smelts raw ore");
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Earth"));
				com.omnispell.spell.Spell clod = PlayerStore.get(p).pending;
				PlayerStore.get(p).pending = null;
				p.setXRot(89f);
				double clodY = p.getY();
				check(clod != null && "clod".equals(clod.toggle) && com.omnispell.spell.ExploreEffects.cast(new com.omnispell.spell.Cast(p, wand, clod)) && p.getY() > clodY + 0.9
						&& level.getBlockState(b).is(net.minecraft.world.level.block.Blocks.DIRT), "Earth alone sets a dirt block underfoot and lifts the caster (rose " + (p.getY() - clodY) + ")");
				level.setBlockAndUpdate(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				p.teleportTo(level, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, java.util.Set.of(), 180f, 0f, true);
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Air"));
				com.omnispell.spell.Spell gust = PlayerStore.get(p).pending;
				PlayerStore.get(p).pending = null;
				check(gust != null && "gust".equals(gust.toggle) && com.omnispell.spell.ExploreEffects.cast(new com.omnispell.spell.Cast(p, wand, gust)) && p.getDeltaMovement().y > 0.6, "Air alone is a gust: a jump on demand");
				p.setDeltaMovement(0, 0, 0);
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Water"));
				check(PlayerStore.get(p).pending != null && "nourish".equals(PlayerStore.get(p).pending.toggle), "Water alone is Nourish");
				PlayerStore.get(p).pending = null;
				// A vortex takes hold of a creature.
				Zombie spun = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				spun.snapTo(b.getX() + 3.5, b.getY(), b.getZ() - 3.5, 0f, 0f);
				spun.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				level.addFreshEntity(spun);
				com.omnispell.spell.Terraform.vortex(level, p, spun, 3);
				check(spun.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING), "a vortex takes hold of its target");
				vortexed[0] = spun;

				// Weapon words: a Sword attack spell makes the wand a melee weapon; Arrow looses an arrow.
				check(!com.omnispell.data.WordBank.attributeFits("defense", com.omnispell.data.WordBank.word("attribute:Sword"))
						&& com.omnispell.data.WordBank.attributeFits("attack", com.omnispell.data.WordBank.word("attribute:Sword"))
						&& com.omnispell.data.WordBank.attributeFits("exploration", com.omnispell.data.WordBank.word("attribute:Tower")), "attribute words belong to their own kinds of spell");
				com.omnispell.spell.Spell blade = new com.omnispell.spell.Spell();
				blade.id = "test-blade";
				blade.name = "Test Blade";
				blade.type = "attack";
				blade.tier = 2;
				blade.words = new java.util.ArrayList<>(java.util.List.of("base:Fire", "attribute:Sword"));
				blade.code.primaryValue = 20;
				blade.code.element = "fire";
				check(com.omnispell.spell.Weapons.cast(new com.omnispell.spell.Cast(p, wand, blade)) && com.omnispell.spell.Weapons.imbued(p), "a Sword attack spell makes the wand a weapon");
				Zombie dummy = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				dummy.snapTo(b.getX() + 0.5, b.getY(), b.getZ() - 2.0, 0f, 0f);
				dummy.setNoAi(true);
				dummy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				level.addFreshEntity(dummy);
				p.teleportTo(level, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, java.util.Set.of(), 180f, 10f, true);
				p.setYRot(180f);
				p.setXRot(10f);
				com.omnispell.spell.Casting.primary(p, wand);
				check(dummy.getHealth() < dummy.getMaxHealth(), "a left-click with the imbued wand strikes in melee (zombie health " + dummy.getHealth() + ")");
				dummy.discard();
				com.omnispell.spell.Weapons.forget(p);
				blade.words.set(1, "attribute:Arrow");
				int arrowsBefore = level.getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.Arrow.class, p.getBoundingBox().inflate(6)).size();
				check(com.omnispell.spell.Weapons.cast(new com.omnispell.spell.Cast(p, wand, blade))
						&& level.getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.Arrow.class, p.getBoundingBox().inflate(6)).size() > arrowsBefore, "an Arrow attack spell looses a magic arrow");
				// The Wizard's Hat: holds 64 of anything, beds and totems too, and three stacks of sixteen are one pile.
				ItemStack hat = new ItemStack(OmniItems.WIZARD_HAT);
				ItemStack beds = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("red_bed")), 1);
				ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING, 1);
				ItemStack pearls = new ItemStack(Items.ENDER_PEARL, 16);
				com.omnispell.item.WizardHatItem.insert(hat, beds);
				com.omnispell.item.WizardHatItem.insert(hat, totem);
				for (int k = 0; k < 3; k++) com.omnispell.item.WizardHatItem.insert(hat, new ItemStack(Items.ENDER_PEARL, 16));
				check(com.omnispell.item.WizardHatItem.count(hat) == 50 && beds.isEmpty() && totem.isEmpty(), "the hat holds beds, totems and pearls (" + com.omnispell.item.WizardHatItem.count(hat) + " things)");
				check(com.omnispell.item.WizardHatItem.insert(hat, new ItemStack(Items.DIRT, 64)) == 14, "and stops at 64");
				check(com.omnispell.charm.Charms.slots(hat) == 2 && com.omnispell.charm.Charms.isGem(new ItemStack(Items.TOTEM_OF_UNDYING)), "the hat takes two charms, and a totem can be charmed");
				check(com.omnispell.item.WandParts.CORES.containsKey("totem_of_undying"), "a totem can be a wand core");
				int tableBound = 0, plain = 0;
				for (int k = 0; k < 40; k++) {
					ItemStack tableWand = WandItem.create("oak", "enchanting_table");
					WandItem.rollEnchantments(tableWand, level);
					if (com.soulbind.SoulBind.soulBindLevel(tableWand) > 0) tableBound++;
					if (tableWand.getEnchantments().isEmpty()) plain++;
				}
				check(plain == 0 && tableBound > 8 && tableBound < 32, "an enchanting-table core always brings an enchantment, Soul Bind about half the time (" + tableBound + " of 40)");

				// Nicknames and the archive.
				com.omnispell.spell.Spell carveSpell = WandItem.spell(wand, 0);
				com.omnispell.spell.SpellCrafting.rename(p, carveSpell.id + "|Earth Tunnel");
				check("Earth Tunnel".equals(PlayerStore.get(p).spell(carveSpell.id).label()) && "Earth Tunnel".equals(WandItem.spell(p.getMainHandItem(), 0).label()), "a spell can be given a nickname, and the wand shows it too");
				com.omnispell.spell.SpellCrafting.archive(p, carveSpell.id);
				check("Archive".equals(PlayerStore.get(p).spell(carveSpell.id).folder) && WandItem.spell(p.getMainHandItem(), 0) != null, "a spell can be archived and still sits on its wand");
				com.omnispell.spell.SpellCrafting.archive(p, carveSpell.id);
				com.omnispell.spell.SpellCrafting.rename(p, carveSpell.id + "|");
				PlayerStore.get(p).folders.clear();
				check(PlayerStore.get(p).spell(carveSpell.id).folder == null && carveSpell.name.equals(PlayerStore.get(p).spell(carveSpell.id).label()), "and restored, and un-named");
				wand = p.getMainHandItem();

				// Fire magic cooks; Sparsus exists and is rare.
				check(com.omnispell.OmniLoot.cooked(Items.BEEF) == Items.COOKED_BEEF && com.omnispell.OmniLoot.cooked(Items.SALMON) == Items.COOKED_SALMON
						&& com.omnispell.OmniLoot.cooked(Items.POTATO) == Items.BAKED_POTATO && com.omnispell.OmniLoot.cooked(Items.BONE) == null, "raw food has a cooked form and other drops do not");
				var sparsus = com.omnispell.data.WordBank.word("charge:Sparsus (Area of effect)");
				check(sparsus != null && sparsus.rarity == 4, "Sparsus is a rare charge word");
				// A cow killed by a fire spell drops cooked beef.
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Fire", "command:Summon", "intention:Malice"));
				com.omnispell.spell.Spell fireStrike = PlayerStore.get(p).pending;
				PlayerStore.get(p).pending = null;
				var cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
				cow.snapTo(b.getX() + 0.5, b.getY(), b.getZ() - 3.5, 0f, 0f);
				cow.setNoAi(true);
				cow.setHealth(1f);
				level.addFreshEntity(cow);
				p.setXRot(25f);
				ItemStack fireWand = WandItem.create("oak", "nether_star");
				WandItem.setSpell(fireWand, 0, fireStrike);
				p.getInventory().setItem(6, fireWand);
				com.omnispell.spell.Casting.cast(p, fireWand, 0);
				p.getInventory().setItem(6, ItemStack.EMPTY);
				boolean cookedBeef = false, rawBeef = false;
				for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(cow.blockPosition()).inflate(4))) {
					if (item.getItem().is(Items.COOKED_BEEF)) cookedBeef = true;
					if (item.getItem().is(Items.BEEF)) rawBeef = true;
					item.discard();
				}
				check(fireStrike != null && "strike".equals(fireStrike.toggle) && !cow.isAlive(), "the fire strike killed the cow");
				check(!rawBeef, "a cow killed by fire magic drops no raw beef" + (cookedBeef ? " (cooked beef dropped)" : " (it dropped no beef this time)"));
				p.setXRot(0f);
				// No two pairings of command and intention give the same effect.
				java.util.Set<String> effects = new java.util.HashSet<>();
				for (String command : new String[]{"launch", "envelop", "scatter", "form", "bind", "summon", "channel", "shift", "pulse", "grow"}) {
					for (String intention : new String[]{"destruction", "protection", "discovery", "concealment", "manipulation", "traversal"}) {
						check(effects.add(com.omnispell.spell.Terraform.effectFor(command, intention)), command + " + " + intention + " has an effect of its own (" + com.omnispell.spell.Terraform.effectFor(command, intention) + ")");
					}
				}
				// Deselecting: with no spell selected, the wand reports none and left-click is the bolt.
				WandItem.select(wand, -1);
				check(WandItem.selected(wand) == -1, "a wand can have no spell selected");
				WandItem.select(wand, 4);

				// The wand's ward: holding right-click turns 70% of a hit aside for Barrier mana.
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.BARRIER.ordinal()] = 50;
				p.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
				check(WandItem.warding(p), "holding the wand up raises its ward");
				float warded = com.omnispell.spell.Modifiers.modifyIncoming(p, p.damageSources().generic(), 10f);
				check(Math.abs(warded - 5f) < 0.01f, "the ward turns half of a hit aside (10 became " + warded + ")");
				p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(OmniItems.WIZARD_ROBE));
				p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(OmniItems.WIZARD_BOOTS));
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.BARRIER.ordinal()] = 50;
				float robed = com.omnispell.spell.Modifiers.modifyIncoming(p, p.damageSources().generic(), 10f);
				check(robed < 0.01f, "in a wizard's robe the ward stops the whole blow (10 became " + robed + ")");
				float fall = com.omnispell.spell.Modifiers.modifyIncoming(p, p.damageSources().fall(), 6f);
				check(fall < 0.01f, "in wizard's boots the ward holds off a fall");
				check(com.omnispell.charm.Charms.slots(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)) == 2 && com.omnispell.charm.Charms.slots(wand) == 3, "attire holds two charms, a wand three");
				ItemStack eloquent = com.omnispell.charm.Charms.merge(WandItem.create("oak", "flint"), charmGem(level, "eloquence", 2));
				ItemStack mainWand = p.getMainHandItem();
				ItemStack[] kept = new ItemStack[p.getInventory().getContainerSize()];
				for (int k = 0; k < kept.length; k++) {
					kept[k] = p.getInventory().getItem(k);
					if (WandItem.isWand(kept[k])) p.getInventory().setItem(k, ItemStack.EMPTY); // only flint wands about, for a clean count
				}
				p.getInventory().setItem(p.getInventory().getSelectedSlot(), WandItem.create("oak", "flint"));
				int budgetBefore = WandItem.bestWordBudget(p);
				p.getInventory().setItem(p.getInventory().getSelectedSlot(), eloquent);
				check(budgetBefore == 3 && WandItem.bestWordBudget(p) == 5, "an Eloquence II charm on the wand in hand lends two more words (" + budgetBefore + " -> " + WandItem.bestWordBudget(p) + ")");
				for (int k = 0; k < kept.length; k++) p.getInventory().setItem(k, kept[k]);
				com.omnispell.player.Mana.wandUpkeep(p);
				check(PlayerStore.get(p).mana[com.omnispell.player.ManaPool.BARRIER.ordinal()] < 41, "the ward costs Barrier mana to hold");
				p.stopUsingItem();

				// Attack spells are paid for in Force mana.
				com.omnispell.spell.Spell attack = null;
				for (com.omnispell.spell.Spell known : PlayerStore.get(p).spells) if ("attack".equals(known.type)) attack = known;
				check(attack != null && com.omnispell.spell.Casting.manaCost(attack) == 4 + 3 * attack.tier, "an attack spell has a Force cost");
				ItemStack costWand = WandItem.create("oak", "nether_star");
				WandItem.setSpell(costWand, 0, attack);
				p.getInventory().setItem(6, costWand);
				var attackPool = com.omnispell.spell.Casting.poolOf(attack);
				PlayerStore.get(p).mana[attackPool.ordinal()] = 60;
				com.omnispell.spell.Casting.cast(p, costWand, 0);
				double left = PlayerStore.get(p).mana[attackPool.ordinal()];
				check(Math.abs(left - (60 - com.omnispell.spell.Casting.manaCost(attack))) < 0.01, "casting it spent " + (60 - left) + " " + attackPool.label + " mana");
				com.omnispell.spell.Spell probe = new com.omnispell.spell.Spell();
				probe.type = "attack";
				probe.code.element = "fire";
				boolean fireIsForce = com.omnispell.spell.Casting.poolOf(probe) == com.omnispell.player.ManaPool.FORCE;
				probe.code.element = "shadow";
				check(fireIsForce && com.omnispell.spell.Casting.poolOf(probe) == com.omnispell.player.ManaPool.SPIRIT, "fire attacks draw on Force and shadow attacks on Spirit");
				p.getInventory().setItem(6, ItemStack.EMPTY);

				// Villagers sell scrolls: the trades are in the game's trade lists.
				var trades = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TRADE);
				check(trades.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.VILLAGER_TRADE, com.omnispell.Omnispell.id("librarian/1/scroll_common"))).isPresent()
						&& trades.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.VILLAGER_TRADE, com.omnispell.Omnispell.id("librarian/5/scroll_ancient"))).isPresent(), "librarians have word scroll trades");
				var librarianOne = trades.get(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.VILLAGER_TRADE, net.minecraft.resources.Identifier.withDefaultNamespace("librarian/level_1")));
				check(librarianOne.isPresent() && librarianOne.get().stream().anyMatch(h -> h.getRegisteredName().equals("omnispell:librarian/1/scroll_common")), "a novice librarian can offer a common scroll");

				// The Arcanist: a profession of its own, with scroll trades at every level.
				var professions = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION);
				var arcanist = professions.get(com.omnispell.world.Arcanist.PROFESSION);
				check(arcanist.isPresent(), "the Arcanist profession exists");
				var tradeSets = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.TRADE_SET);
				boolean allLevels = true;
				for (int lv = 1; lv <= 5; lv++) allLevels &= tradeSets.get(arcanist.get().value().getTrades(lv)).isPresent();
				check(allLevels, "the Arcanist has trades for all five levels");
				var keeper = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
				keeper.snapTo(b.getX() + 4.5, b.getY(), b.getZ() - 6.5, 0f, 0f);
				keeper.setNoAi(true);
				level.addFreshEntity(keeper);
				keeper.setVillagerData(keeper.getVillagerData().withProfession(arcanist.get()));
				int offers = keeper.getOffers().size();
				boolean sellsScroll = keeper.getOffers().stream().anyMatch(o -> o.getResult().is(OmniItems.WORD_SCROLL) || o.getResult().is(OmniItems.ARCANE_POTION) || o.getResult().is(Items.EMERALD));
				check(offers >= 2 && sellsScroll, "a villager made an Arcanist has offers (" + offers + ")");
				keeper.discard();

				// The forest's ruin and nest build, and their chests hold what they should.
				BlockPos ruinAt = b.offset(0, 0, 30);
				for (BlockPos pos : BlockPos.betweenClosed(ruinAt.offset(-8, -1, -8), ruinAt.offset(24, -1, 8))) level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
				check(com.omnispell.world.ForestFeatures.Ruin.INSTANCE.place(level, level.getChunkSource().getGenerator(), level.getRandom(), ruinAt), "a forest ruin builds");
				check(com.omnispell.world.ForestFeatures.Nest.INSTANCE.place(level, level.getChunkSource().getGenerator(), level.getRandom(), ruinAt.offset(16, 0, 0)), "a familiar nest builds");
				int chests = 0, eggs = 0, pillars = 0;
				for (BlockPos pos : BlockPos.betweenClosed(ruinAt.offset(-8, -1, -8), ruinAt.offset(24, 8, 8))) {
					if (level.getBlockState(pos).is(OmniItems.FAEWOOD_LOG)) pillars++;
					if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity box) {
						chests++;
						box.unpackLootTable(p);
						for (int i = 0; i < box.getContainerSize(); i++) if (com.omnispell.familiar.Familiars.isEgg(box.getItem(i))) eggs++;
					}
				}
				check(chests == 2 && pillars >= 12, "the ruin has its pillars and both have their boxes (" + chests + " boxes, " + pillars + " logs)");
				check(eggs >= 1, "the nest holds a familiar egg (" + eggs + ")");

				// Shift-click in the inventory sends a wand to the off hand, as it does a shield.
				p.getInventory().setItem(6, WandItem.create("birch", "flint"));
				p.inventoryMenu.quickMoveStack(p, 36 + 6);
				check(WandItem.isWand(p.getOffhandItem()), "shift-clicking a wand in the inventory puts it in the off hand");
				p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
				p.getInventory().setItem(6, ItemStack.EMPTY);

				// Arcane Focus: 40% of a hit is paid in Barrier mana.
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.BARRIER.ordinal()] = 50;
				p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(OmniItems.ARCANE_FOCUS));
				float hit = com.omnispell.spell.Modifiers.modifyIncoming(p, p.damageSources().generic(), 10f);
				check(Math.abs(hit - 6f) < 0.01f, "an Arcane Focus turns 40% of a hit into spent mana (10 became " + hit + ")");
				p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
				for (BlockPos pos : BlockPos.betweenClosed(b.offset(-3, 0, -3), b.offset(-3, 4, 3))) level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			});

			context.waitTicks(16);
			server.runOnServer(s -> {
				check(vortexed[0].getY() > site[0].getY() + 1.0, "the vortex lifts it off the ground (" + (vortexed[0].getY() - site[0].getY()) + " blocks up)");
				vortexed[0].discard();
			});
			// Sneak + right-click passes the wand to the other hand; a bolt with no mana left does nothing.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.FORCE.ordinal()] = 0;
				p.setXRot(-60f);
			});
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(8);
			context.getInput().releaseKey(options -> options.keyShift);
			context.waitTicks(3);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				check(WandItem.isWand(p.getOffhandItem()) && p.getMainHandItem().isEmpty(), "sneak + right-click passes the wand to the off hand");
				ItemStack wand = p.getOffhandItem();
				p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
				p.getInventory().setItem(4, wand);

				// Air-walk: hardened air underfoot for as long as Phase mana lasts.
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.PHASE.ordinal()] = 5;
				p.teleportTo(p.level(), site[0].getX() + 0.5, site[0].getY() + 12, site[0].getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(9);
			server.runOnServer(s -> com.omnispell.player.AirWalk.toggle(player(s)));
			context.waitTicks(30);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				check(p.getY() > site[0].getY() + 5, "air-walking holds the caster up (height above ground " + (p.getY() - site[0].getY()) + ", walking " + com.omnispell.player.AirWalk.isWalking(p) + ", wand " + !WandItem.heldWand(p).isEmpty() + ", sneaking " + p.isShiftKeyDown() + ", phase " + PlayerStore.get(p).mana[com.omnispell.player.ManaPool.PHASE.ordinal()] + ", gamemode " + p.gameMode.getGameModeForPlayer() + ")");
				check(PlayerStore.get(p).mana[com.omnispell.player.ManaPool.PHASE.ordinal()] < 4, "air-walking drains Phase mana quickly");
			});
			context.waitTicks(100);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				check(p.getY() < site[0].getY() + 2, "when the mana is gone the caster comes down");
				p.setHealth(p.getMaxHealth());
			});
			context.getInput().pressKey(options -> options.keyHotbarSlots[3]);
			context.waitTicks(5);
			server.runCommand("execute as @a run omnispell essence 200");

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
			// sludge: a liquid and nothing but lapis
			java.util.function.Consumer<String> emptyVat = why -> server.runOnServer(s -> {
				ServerPlayer p = player(s);
				com.omnispell.alchemy.Vats.Vat v = com.omnispell.alchemy.Vats.at(p.level(), vat[0]);
				v.base = null;
				v.items.clear();
				v.servings = 0;
				v.charges = 8;
				for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(OmniItems.BREW)) p.getInventory().setItem(i, ItemStack.EMPTY);
			});
			emptyVat.accept("sludge");
			pour.accept(Items.MILK_BUCKET, 1);
			pour.accept(Items.LAPIS_LAZULI, 1);
			pour.accept(Items.LAPIS_LAZULI, 1);
			pour.accept(Items.GLASS_BOTTLE, 3);
			pour.accept(Items.GLASS_BOTTLE, 3);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ItemStack potion = ItemStack.EMPTY;
				for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(OmniItems.BREW)) potion = p.getInventory().getItem(i);
				com.omnispell.alchemy.Brew brew = potion.isEmpty() ? null : com.omnispell.alchemy.BrewItem.read(potion);
				check(brew != null && brew.name.equals("Sludge"), "milk and nothing but lapis makes sludge");
			});
			// boiling over: lava, potent mismatched ingredients, flora with fauna, no honeycomb
			emptyVat.accept("volatile");
			pour.accept(Items.LAVA_BUCKET, 1);
			pour.accept(Items.WITHER_ROSE, 1);
			pour.accept(Items.GHAST_TEAR, 1);
			pour.accept(Items.ECHO_SHARD, 1);
			server.runOnServer(s -> player(s).setHealth(20f));
			pour.accept(Items.GLASS_BOTTLE, 3);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				com.omnispell.alchemy.Vats.Vat v = com.omnispell.alchemy.Vats.at(p.level(), vat[0]);
				check(v.base == null && v.servings == 0, "a volatile brew with no harmonizer boils over and is lost");
				check(p.getHealth() < 20f, "boiling over burns the brewer (" + p.getHealth() + ")");
				p.setHealth(20f);
				p.clearFire();
			});
			// the same brew with honeycomb holds together
			emptyVat.accept("harmonized");
			pour.accept(Items.LAVA_BUCKET, 1);
			pour.accept(Items.WITHER_ROSE, 1);
			pour.accept(Items.GHAST_TEAR, 1);
			pour.accept(Items.ECHO_SHARD, 1);
			pour.accept(Items.HONEYCOMB, 1);
			pour.accept(Items.GLASS_BOTTLE, 3);
			server.waitFor(s -> com.omnispell.alchemy.Vats.at(player(s).level(), vat[0]).servings > 0, 200);
			check(server.computeOnServer(s -> com.omnispell.alchemy.Vats.at(player(s).level(), vat[0]).servings) == 3, "honeycomb steadies the same brew");

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

			// ---- charms: a gem only takes a charm beside a runic bookshelf, and an anvil sets it into armor
			server.runCommand("xp set @a 60 levels");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				BlockPos table = p.blockPosition().offset(6, 0, -6);
				for (BlockPos clear : BlockPos.betweenClosed(table.offset(-2, 0, -2), table.offset(2, 2, 2))) level.setBlockAndUpdate(clear, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(table, net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE.defaultBlockState());
				var access = net.minecraft.world.inventory.ContainerLevelAccess.create(level, table);
				var menu = new net.minecraft.world.inventory.EnchantmentMenu(90, p.getInventory(), access);
				menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 16));
				menu.getSlot(0).set(new ItemStack(Items.DIAMOND));
				check(menu.costs[0] == 0 && menu.costs[2] == 0, "without a runic bookshelf the table offers a gem nothing");
				menu.getSlot(0).set(ItemStack.EMPTY);
				for (int dx = -2; dx <= 2; dx++) {
					level.setBlockAndUpdate(table.offset(dx, 0, -2), net.minecraft.world.level.block.Blocks.BOOKSHELF.defaultBlockState());
					level.setBlockAndUpdate(table.offset(dx, 1, -2), net.minecraft.world.level.block.Blocks.BOOKSHELF.defaultBlockState());
					level.setBlockAndUpdate(table.offset(dx, 0, 2), net.minecraft.world.level.block.Blocks.BOOKSHELF.defaultBlockState());
				}
				level.setBlockAndUpdate(table.offset(2, 0, 0), com.omnispell.item.OmniItems.RUNIC_BOOKSHELF.defaultBlockState());
				int doubles = 0;
				ItemStack charmed = ItemStack.EMPTY;
				for (int n = 0; n < 12; n++) {
					menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 16));
					p.setExperienceLevels(60);
					menu.getSlot(0).set(new ItemStack(Items.DIAMOND));
					check(menu.costs[2] > 0, "with a runic bookshelf the table offers the gem a charm (cost " + menu.costs[2] + ")");
					check(menu.clickMenuButton(p, 2), "the charm can be taken (levels " + p.experienceLevel + ", cost " + menu.costs[2] + ", lapis " + menu.getSlot(1).getItem() + ", clue " + menu.enchantClue[2] + ")");
					charmed = menu.getSlot(0).getItem().copy();
					var charms = com.omnispell.charm.Charms.charmsOn(charmed);
					check(!charms.isEmpty(), "the diamond came out charmed: " + charms);
					if (charms.size() > 1) doubles++;
					menu.getSlot(0).set(ItemStack.EMPTY);
				}
				check(doubles >= 2, "diamonds often take more than one charm (" + doubles + " of 12 did)");
				menu.getSlot(0).set(new ItemStack(Items.IRON_PICKAXE));
				check(menu.costs[2] > 0, "ordinary enchanting still works beside a runic bookshelf");
				menu.getSlot(0).set(ItemStack.EMPTY);
				menu.getSlot(1).set(ItemStack.EMPTY);

				var anvil = new net.minecraft.world.inventory.AnvilMenu(91, p.getInventory(), access);
				anvil.getSlot(0).set(new ItemStack(Items.IRON_CHESTPLATE));
				anvil.getSlot(1).set(charmed);
				ItemStack worn = anvil.getSlot(2).getItem().copy();
				check(com.omnispell.charm.Charms.stored(worn).size() == 1 && anvil.getCost() > 0, "the anvil sets the charm into armor (cost " + anvil.getCost() + ")");
				check(com.omnispell.charm.Charms.slots(worn) == 1, "plain armor has one charm slot");
				anvil.getSlot(0).set(ItemStack.EMPTY);
				anvil.getSlot(1).set(ItemStack.EMPTY);

				// A known charm, to check its effect: Vigor II is two more hearts.
				ItemStack vigorGem = new ItemStack(Items.EMERALD);
				var vigor = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
						.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, com.omnispell.Omnispell.id("vigor")));
				check(vigor.isPresent(), "charm enchantments are registered");
				vigorGem.enchant(vigor.get(), 2);
				ItemStack helmet = com.omnispell.charm.Charms.merge(new ItemStack(Items.IRON_HELMET), vigorGem);
				ItemStack second = com.omnispell.charm.Charms.merge(helmet, charmed);
				check(com.omnispell.charm.Charms.stored(second).size() == 1, "a full piece pushes out its oldest charm");
				p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helmet);
				com.omnispell.charm.Charms.everySecond(p);
				check(p.getMaxHealth() == 24.0f, "Vigor II gives two more hearts (max health " + p.getMaxHealth() + ")");
				p.getInventory().setItem(7, worn);
			});
			context.waitTicks(30);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
				com.omnispell.charm.Charms.everySecond(p);
				check(p.getMaxHealth() == 20.0f, "taking the armor off takes the charm away");
			});

			// ---- familiars: an egg's leanings, soaking, hatching, fighting and pocketing
			Zombie[] prey = new Zombie[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				var F = com.omnispell.familiar.Familiars.Stat.POWER;
				ItemStack egg = com.omnispell.familiar.Familiars.egg("phoenix", F, com.omnispell.familiar.Familiars.Stat.SPIRIT);
				check(egg.getHoverName().getString().equals("Strong Phoenix Egg"), "egg is named for its leaning: " + egg.getHoverName().getString());
				check(java.util.Arrays.equals(com.omnispell.familiar.Familiars.eggStats(egg), new int[]{4, 1, 1, 4}), "egg stats " + java.util.Arrays.toString(com.omnispell.familiar.Familiars.eggStats(egg)));
				check(java.util.Arrays.equals(com.omnispell.familiar.Familiars.hatchStats(egg), new int[]{14, 1, 1, 10}), "an unsoaked egg splits 60/40: " + java.util.Arrays.toString(com.omnispell.familiar.Familiars.hatchStats(egg)));
				check(com.omnispell.familiar.Familiars.egg("griffin", null, null).getHoverName().getString().equals("Blank Griffin Egg"), "blank eggs are named blank");
				boolean[] changed = {false};
				com.omnispell.familiar.Familiars.soak(egg, com.omnispell.familiar.Familiars.Stat.VITALITY, 1000, changed);
				check(changed[0] && com.omnispell.familiar.Familiars.eggStats(egg)[1] == 3, "a soak adds two to its stat");
				changed[0] = false;
				com.omnispell.familiar.Familiars.soak(egg, com.omnispell.familiar.Familiars.Stat.VITALITY, 2000, changed);
				check(!changed[0], "the shell needs time between soaks");
				com.omnispell.familiar.Familiars.soak(egg, com.omnispell.familiar.Familiars.Stat.POWER, 8000, changed);
				check(changed[0], "after five minutes it soaks again");
				int[] hatch = com.omnispell.familiar.Familiars.hatchStats(egg);
				check(java.util.Arrays.equals(hatch, new int[]{18, 3, 1, 4}), "a soaked egg puts the rest into its highest stat: " + java.util.Arrays.toString(hatch));

				BlockPos feet = p.blockPosition();
				var mob = com.omnispell.familiar.Familiars.release(p, com.omnispell.familiar.Familiars.SPECIES.get("phoenix"), hatch, "", net.minecraft.world.phys.Vec3.atBottomCenterOf(feet.offset(1, 0, 1)));
				check(mob != null && com.omnispell.familiar.Familiars.familiarOf(p) == mob, "the familiar hatched as " + (mob == null ? "nothing" : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())));
				check(mob.getMaxHealth() == 25.0f, "Vitality 3 gives 25 health (has " + mob.getMaxHealth() + ")");
				check(com.omnispell.familiar.Familiars.spiritPercent(p) == 6.0, "Spirit 4 refills mana 6% faster");
				Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				z.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 4.5, 180f, 0f);
				z.setNoAi(true);
				z.setPersistenceRequired();
				z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				level.addFreshEntity(z);
				p.setLastHurtMob(z);
				prey[0] = z;
			});
			context.waitTicks(100);
			context.takeScreenshot("27-familiar");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				check(!prey[0].isAlive() || prey[0].getHealth() < prey[0].getMaxHealth(), "the familiar fought what its owner fought (zombie health " + prey[0].getHealth() + ")");
				prey[0].discard();
				var mob = com.omnispell.familiar.Familiars.familiarOf(p);
				check(mob != null && mob.isAlive(), "the familiar is still with its owner");
				float healthBefore = mob.getHealth();
				mob.hurtServer(p.level(), p.damageSources().playerAttack(p), 5f);
				check(mob.getHealth() == healthBefore, "its owner cannot hurt it");
				com.omnispell.familiar.Familiars.pocket(p, mob, 0);
				boolean carried = false;
				for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
					ItemStack stack = p.getInventory().getItem(i);
					if (stack.is(com.omnispell.item.OmniItems.FAMILIAR)) {
						carried = java.util.Arrays.equals(com.omnispell.familiar.Familiars.pocketStats(stack), new int[]{18, 3, 1, 4});
						p.getInventory().setItem(i, ItemStack.EMPTY);
					}
				}
				check(carried && com.omnispell.familiar.Familiars.familiarOf(p) == null, "the familiar goes into a pocket with its stats intact");
				p.getInventory().setItem(7, ItemStack.EMPTY);
			});
			// Every kind of familiar, side by side, in its own body and skin.
			java.util.List<net.minecraft.world.entity.Entity> parade = new java.util.ArrayList<>();
			server.runCommand("time set noon");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				BlockPos b = site[0];
				int n = 0;
				for (var species : com.omnispell.familiar.Familiars.SPECIES.values()) {
					var body = com.omnispell.familiar.Familiars.TYPE.create(level, EntitySpawnReason.COMMAND);
					check(body != null, "a " + species.name() + " body can be made");
					body.setSpecies(species.id());
					body.setNoAi(true);
					body.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE).setBaseValue(species.scale());
					body.snapTo(b.getX() - 6.5 + n * 1.9, b.getY(), b.getZ() - 7.5, 20f, 0f);
					body.setYBodyRot(20f);
					body.setYHeadRot(20f);
					level.addFreshEntity(body);
					parade.add(body);
					n++;
				}
				p.teleportTo(level, b.getX() + 0.5, b.getY(), b.getZ() - 1.5, java.util.Set.of(), 180f, 12f, true);
			});
			context.waitTicks(30);
			context.takeScreenshot("29-familiar-kinds");
			server.runCommand("time set midnight");
			context.waitTicks(40);
			context.takeScreenshot("29b-dark-sight-and-coords");
			server.runCommand("time set noon");
			context.waitTicks(10);
			server.runOnServer(s -> parade.forEach(net.minecraft.world.entity.Entity::discard));

			context.runOnClient(c -> c.gui.setScreen(BookScreen.onTab(4)));
			context.waitTicks(8);
			context.takeScreenshot("28-book-lore");
			context.runOnClient(c -> c.gui.setScreen(null));
			context.waitTicks(5);

			// ---- spell folders: make one, move a spell in, hide and show it, rename it, remove it
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				PlayerData data = PlayerStore.get(p);
				String id = data.spells.get(0).id;
				com.omnispell.spell.SpellCrafting.newFolder(p, "Earth Work");
				check(data.folder("Earth Work") != null && !data.folder("Earth Work").shown, "a folder can be made, hidden from the main list at first");
				com.omnispell.spell.SpellCrafting.move(p, id + "|Earth Work");
				check("Earth Work".equals(data.spell(id).folder), "a spell can be moved into a folder");
				com.omnispell.spell.SpellCrafting.toggleFolder(p, "Earth Work");
				check(data.folder("Earth Work").shown, "a folder can be shown in the main list");
				com.omnispell.spell.SpellCrafting.renameFolder(p, "Earth Work|Builders");
				check(data.folder("Builders") != null && "Builders".equals(data.spell(id).folder), "renaming a folder takes its spells with it");
				com.omnispell.spell.SpellCrafting.deleteFolder(p, "Builders");
				check(data.folders.isEmpty() && data.spell(id).folder == null && data.spells.size() == 15, "removing a folder returns its spells and forgets nothing");
			});

			// ---- the Arcanist: an unemployed villager walks to an Arcane Well and takes up the trade
			net.minecraft.world.entity.npc.villager.Villager[] seeker = new net.minecraft.world.entity.npc.villager.Villager[1];
			java.util.List<net.minecraft.world.entity.Entity> hatWearers = new java.util.ArrayList<>();
			server.runCommand("time set noon");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				BlockPos b = site[0].offset(0, 0, 40);
				for (BlockPos pos : BlockPos.betweenClosed(b.offset(-8, -1, -8), b.offset(8, -1, 8))) level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(b.offset(-8, 0, -8), b.offset(8, 4, 8))) level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				BlockPos villageWell = com.omnispell.world.Arcanist.placeWell(level, b);
				check(villageWell != null && level.getBlockState(villageWell).is(OmniItems.ARCANE_WELL), "a village well finds a place in the open");
				var villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
				villager.snapTo(b.getX() + 5.5, b.getY(), b.getZ() + 5.5, 0f, 0f);
				villager.setPersistenceRequired();
				level.addFreshEntity(villager);
				seeker[0] = villager;
			});
			for (int wait = 0; wait < 60; wait++) {
				context.waitTicks(40);
				if (server.computeOnServer(s -> !seeker[0].getVillagerData().profession().is(net.minecraft.world.entity.npc.villager.VillagerProfession.NONE))) break;
			}
			server.runOnServer(s -> {
				check(seeker[0].getVillagerData().profession().is(com.omnispell.world.Arcanist.PROFESSION), "an unemployed villager takes up the Arcane Well and becomes an Arcanist (is " + seeker[0].getVillagerData().profession().getRegisteredName() + ")");
				player(s).teleportTo(player(s).level(), seeker[0].getX() + 3, seeker[0].getY(), seeker[0].getZ() + 3, java.util.Set.of(), 135f, 10f, true);
			});
			server.runOnServer(s -> {
				ItemStack hat = new ItemStack(OmniItems.WIZARD_HAT);
				hat.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x3A2A8C));
				player(s).getInventory().setItem(5, hat.copy());
				for (int k = 0; k < 2; k++) {
					Zombie model = EntityTypes.ZOMBIE.create(player(s).level(), EntitySpawnReason.COMMAND);
					model.snapTo(seeker[0].getX() + 1.6 - k * 3.4, seeker[0].getY(), seeker[0].getZ() - 1.0, 150f + k * 60f, 0f);
					model.setNoAi(true);
					model.setYHeadRot(150f + k * 60f);
					ItemStack worn = hat.copy();
					if (k == 1) worn.remove(DataComponents.DYED_COLOR);
					model.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, worn);
					player(s).level().addFreshEntity(model);
					hatWearers.add(model);
				}
			});
			context.waitTicks(20);
			context.takeScreenshot("31-arcanist");
			server.runOnServer(s -> {
				ItemStack green = new ItemStack(OmniItems.WIZARD_HAT);
				green.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x5E7C16));
				player(s).setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, green);
			});
			context.waitTicks(5);
			context.runOnClient(c -> c.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(c.player)));
			context.waitTicks(10);
			context.takeScreenshot("31b-hat-in-inventory");
			context.runOnClient(c -> c.gui.setScreen(null));
			server.runOnServer(s -> {
				ItemStack robe = new ItemStack(OmniItems.WIZARD_ROBE);
				robe.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x5E7C16));
				ItemStack boots = new ItemStack(OmniItems.WIZARD_BOOTS);
				boots.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x5E7C16));
				player(s).setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, robe);
				player(s).setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, boots);
			});
			context.runOnClient(c -> c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			context.takeScreenshot("31c-hat-third-person");
			context.runOnClient(c -> c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
			server.runOnServer(s -> {
				player(s).setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
				player(s).setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
				player(s).setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, ItemStack.EMPTY);
			});
			server.runOnServer(s -> {
				seeker[0].discard();
				hatWearers.forEach(net.minecraft.world.entity.Entity::discard);
			});

			// ---- death: the soul-bound bundle and all Omnispell progress survive
			server.runOnServer(s -> {
				for (int i = 0; i < 9; i++) if (WandItem.isWand(player(s).getInventory().getItem(i))) player(s).getInventory().setItem(i, ItemStack.EMPTY); // loose wands would simply drop
				player(s).getInventory().setItem(8, new ItemStack(OmniItems.GRIMOIRE)); // a loose book must not
				player(s).getInventory().setItem(7, WandItem.create("oak", "totem_of_undying")); // nor a totem wand
				player(s).setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(OmniItems.WIZARD_ROBE)); // nor wizard's attire
				player(s).setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(OmniItems.WIZARD_BOOTS));
			});
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
				check(p.getInventory().getItem(8).is(OmniItems.GRIMOIRE), "a loose Book of Words is kept through death");
				check("totem_of_undying".equals(WandItem.core(p.getInventory().getItem(7))), "a totem-core wand is kept through death");
				check(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(OmniItems.WIZARD_ROBE) && p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(OmniItems.WIZARD_BOOTS), "the robe and boots are kept through death");
				PlayerData data = PlayerStore.get(p);
				check(data.spells.size() == 15 && data.words.size() > 200, "words and spells survive death (" + data.spells.size() + " spells)");
			});
			context.takeScreenshot("30-after-respawn");
		} finally {
			SpellGenerator.transport = realGenerator;
		}
	}
}
