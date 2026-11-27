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
			// The creative inventory opens, with every tab built (the Omnispell one and the search tab among them), without a crash.
			context.runOnClient(c -> c.gui.setScreen(new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(c.player, c.player.connection.enabledFeatures(), true)));
			context.waitTicks(20);
			context.takeScreenshot("05-creative-inventory");
			check(context.computeOnClient(c -> c.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen), "the creative inventory opens and stays open");
			int tabItems = context.computeOnClient(c -> {
				int n = 0;
				for (net.minecraft.world.item.CreativeModeTab tab : net.minecraft.world.item.CreativeModeTabs.allTabs()) n += tab.getDisplayItems().size();
				return n;
			});
			check(tabItems > 500, "every creative tab is filled (" + tabItems + " stacks)");
			context.runOnClient(c -> c.gui.setScreen(null));
			context.waitTicks(5);
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

			// ---- bridge: spans a gap (with real dirt, for Earth)
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
				check(left >= 10, "an Earth bridge is real dirt and stays, like the other land spells' common stuff (" + left + " blocks)");
				for (int z = 1; z <= 12; z++) for (int x = -1; x <= 1; x++) level.setBlockAndUpdate(origin[0].offset(x, -1, z), Blocks.AIR.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(origin[0].offset(-5, -6, 3), origin[0].offset(5, 4, 3))) if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.TransparentBlock) level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			});

			Zombie[] vortexed = new Zombie[1];
			BlockPos[] ruinSites = new BlockPos[2];
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

				// Void + Summon + Malice: a void rift that pulls things in, a black hole.
				PlayerStore.get(p).essence = 500;
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Void", "command:Summon", "intention:Malice"));
				com.omnispell.spell.Spell strike = PlayerStore.get(p).pending;
				check(strike != null && "black_hole".equals(strike.toggle) && strike.description.contains("black hole"), "Void + Summon + Malice opens a black hole: " + (strike == null ? "nothing" : strike.description));
				// Hardened air: solid for players, open to hostile creatures.
				{
					var air = OmniItems.HARDENED_AIR.defaultBlockState();
					Zombie wader = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
					boolean zombieThrough = air.getCollisionShape(level, b, net.minecraft.world.phys.shapes.CollisionContext.of(wader)).isEmpty();
					boolean playerSolid = !air.getCollisionShape(level, b, net.minecraft.world.phys.shapes.CollisionContext.of(p)).isEmpty();
					check(zombieThrough && playerSolid && com.omnispell.spell.TempBlocks.material("wind") == OmniItems.HARDENED_AIR, "wind builds with hardened air: a zombie wades through it, a player stands on it");
					wader.discard();
				}

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
				boolean arrowOnly = com.omnispell.spell.Weapons.cast(new com.omnispell.spell.Cast(p, wand, blade));
				check(!arrowOnly && level.getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.Arrow.class, p.getBoundingBox().inflate(6)).size() > arrowsBefore, "an Arrow attack spell looses a magic arrow, and leaves the spell itself to be cast too");
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

				// A Maxima invisibility hides armour and held things too; a plain one does not.
				PlayerStore.get(p).pending = null;
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Light", "command:Envelop", "intention:Concealment", "charge:Maxima (Amplify power)"));
				com.omnispell.spell.Spell fullVeil = PlayerStore.get(p).pending;
				PlayerStore.get(p).pending = null;
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.PHASE.ordinal()] = 5;
				check(fullVeil != null && "invisibility".equals(fullVeil.toggle) && com.omnispell.spell.ExploreEffects.cast(new com.omnispell.spell.Cast(p, wand, fullVeil))
						&& com.omnispell.spell.Shroud.active(p), "a Maxima invisibility shrouds armour and held items too");
				Zombie hunter = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				hunter.snapTo(p.getX() + 3, p.getY(), p.getZ(), 0f, 0f);
				hunter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				level.addFreshEntity(hunter);
				hunter.setTarget(p);
				com.omnispell.spell.Shroud.everySecond(level.getServer());
				check(hunter.getTarget() == null && p.getVisibilityPercent(level, hunter) == 0.0, "under full invisibility a hunting zombie loses track and cannot see you");
				hunter.discard();
				p.removeAllEffects();

				// Infinity: a moderator-only word. Not handed out by "words all", given only by the word command, then usable.
				check(!PlayerStore.get(p).words.contains("base:Infinity"), "Infinity is not among the words everyone can learn");
				level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withEntity(p).withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER), "omnispell word Infinity");
				check(PlayerStore.get(p).words.contains("base:Infinity"), "an operator can give Infinity with /omnispell word Infinity");
				PlayerStore.get(p).pending = null;
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Infinity", "command:Sever", "intention:Malice"));
				com.omnispell.spell.Spell endless = PlayerStore.get(p).pending;
				check(endless != null && endless.tier == 5, "a spell of Infinity can be crafted, and reaches as far as any can (tier " + (endless == null ? 0 : endless.tier) + ")");
				check(com.omnispell.spell.Casting.infinity(endless), "the spell is known to be one of Infinity");
				{
					double[] pools = PlayerStore.get(p).mana;
					int phase = com.omnispell.player.ManaPool.FORCE.ordinal();
					pools[phase] = 50;
					com.omnispell.player.Mana.DISCOUNT.set(com.omnispell.player.Mana.INFINITY_DISCOUNT);
					double got = com.omnispell.player.Mana.spend(p, com.omnispell.player.ManaPool.FORCE, 10);
					com.omnispell.player.Mana.DISCOUNT.set(1.0);
					check(Math.abs(got - 10) < 0.01 && Math.abs(pools[phase] - 49) < 0.01, "while an Infinity spell is cast, what it spends costs a tenth but works in full (" + pools[phase] + " left)");
					com.omnispell.spell.Spell infiniteAttack = new com.omnispell.spell.Spell();
					infiniteAttack.type = "attack";
					infiniteAttack.tier = 5;
					infiniteAttack.words.add("base:Infinity");
					check(com.omnispell.spell.Casting.manaCost(infiniteAttack) < 2, "an Infinity attack costs almost no mana (" + com.omnispell.spell.Casting.manaCost(infiniteAttack) + ")");
				}
				PlayerStore.get(p).pending = null;
				PlayerStore.get(p).words.remove("base:Infinity");

				// Nicknames and the archive.
				com.omnispell.spell.Spell carveSpell = WandItem.spell(wand, 0);
				com.omnispell.spell.SpellCrafting.rename(p, carveSpell.id + "|Earth Tunnel");
				check("Earth Tunnel".equals(PlayerStore.get(p).spell(carveSpell.id).label()) && "Earth Tunnel".equals(WandItem.spell(p.getMainHandItem(), 0).label()), "a spell can be given a nickname, and the wand shows it too");
				com.omnispell.spell.SpellCrafting.archive(p, carveSpell.id);
				check("Archive".equals(PlayerStore.get(p).spell(carveSpell.id).folder) && WandItem.spell(p.getMainHandItem(), 0) != null, "a spell can be archived and still sits on its wand");
				com.omnispell.spell.SpellCrafting.archive(p, carveSpell.id);
				com.omnispell.spell.SpellCrafting.rename(p, carveSpell.id + "|");
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
				// Command words of the same kind build different things.
				{
					String severMalice = com.omnispell.spell.Terraform.effectFor("Sever", "launch", "destruction");
					String releaseMalice = com.omnispell.spell.Terraform.effectFor("Release", "launch", "destruction");
					String envelopWard = com.omnispell.spell.Terraform.effectFor("Envelop", "envelop", "protection");
					check(!severMalice.equals(releaseMalice) && "dome".equals(envelopWard), "Sever and Release no longer build the same thing (" + severMalice + " / " + releaseMalice + "), and Envelop with Warding closes a dome");
					java.util.Set<String> starter = new java.util.HashSet<>();
					for (String c : new String[]{"Sever", "Release", "Envelop"}) {
						String kind = c.equals("Envelop") ? "envelop" : "launch";
						starter.add(com.omnispell.spell.Terraform.effectFor(c, "Malice", kind, "destruction"));
						starter.add(com.omnispell.spell.Terraform.effectFor(c, "Warding", kind, "protection"));
						starter.add(com.omnispell.spell.Terraform.effectFor(c, "Restoration", kind, "protection"));
						starter.add(com.omnispell.spell.Terraform.effectFor(c, kind, "wild"));
					}
					check(starter.size() >= 11, "the starting words make " + starter.size() + " different exploration spells: " + starter);
					for (String i : new String[]{"destruction", "protection", "wild"}) {
						for (String c : new String[]{"Elevate", "Freeze", "Fill", "Open", "Traverse", "Shield", "Grow", "Agitate"}) {
							String e = com.omnispell.spell.Terraform.effectFor(c, "form", i);
							check(e != null && (com.omnispell.spell.Terraform.handles(e) || !com.omnispell.spell.Terraform.describe(e, new com.omnispell.spell.Spell()).startsWith("Its effect")), c + " + " + i + " is a real effect (" + e + ")");
						}
					}
				}

				// Attack forms: the command decides how an attack takes hold, and ice freezes for real.
				{
					com.omnispell.spell.Spell frost = new com.omnispell.spell.Spell();
					frost.type = "attack";
					frost.tier = 3;
					frost.code.element = "ice";
					frost.words.add("command:Freeze");
					check("encase".equals(com.omnispell.spell.AttackForms.commandForm(frost)) && com.omnispell.spell.AttackForms.describe(frost).contains("block of ice"), "an ice attack with Freeze says it freezes its target in ice: " + com.omnispell.spell.AttackForms.describe(frost));
					com.omnispell.spell.Spell hurler = new com.omnispell.spell.Spell();
					hurler.type = "attack";
					hurler.code.element = "fire";
					hurler.words.add("command:Release");
					check("hurl".equals(com.omnispell.spell.AttackForms.commandForm(hurler)), "an attack with Release hurls its target into the air");
					var iceCow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
					BlockPos icePos = b.offset(6, 0, 6);
					for (BlockPos clear : BlockPos.betweenClosed(icePos.offset(-2, 0, -2), icePos.offset(2, 3, 2))) level.setBlock(clear, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
					iceCow.snapTo(icePos.getX() + 0.5, icePos.getY(), icePos.getZ() + 0.5, 0f, 0f);
					iceCow.setNoAi(true);
					level.addFreshEntity(iceCow);
					com.omnispell.spell.Cast frostCast = new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), frost);
					com.omnispell.spell.AttackForms.manifest(frostCast, iceCow, "encase", "ice", 10);
					int ice = 0;
					for (BlockPos near : BlockPos.betweenClosed(icePos.offset(-2, 0, -2), icePos.offset(2, 3, 2))) if (level.getBlockState(near).is(net.minecraft.world.level.block.Blocks.ICE)) ice++;
					check(ice >= 8 && level.getBlockState(icePos).isAir(), "the cow is frozen in a shell of " + ice + " ice blocks, with room inside");
					iceCow.discard();
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

				// Every exploration spell and cantrip shows its mana pool on the spell bar.
				{
					int pipless = 0;
					String example = "";
					for (com.omnispell.spell.Spell known : PlayerStore.get(p).spells) {
						if (known.isExploration() && !known.fizzle && com.omnispell.spell.Casting.poolOf(known) == null) {
							pipless++;
							example = known.toggle;
						}
					}
					check(pipless == 0, "every exploration spell has a mana pool pip (" + pipless + " without, e.g. " + example + ")");
				}

				// Mana potions, drunk and thrown.
				{
					com.omnispell.alchemy.Brew phaseBrew = com.omnispell.alchemy.ManaPotions.match("water", java.util.List.of("gunpowder", "glowstone_dust", "feather"));
					check(phaseBrew != null && phaseBrew.splash && "phase".equals(phaseBrew.manaType), "water, glowstone dust, a feather and gunpowder brew a splash potion of Phase mana");
					check(com.omnispell.alchemy.ManaPotions.match("water", java.util.List.of("glowstone_dust", "feather", "sugar")) == null
							&& com.omnispell.alchemy.ManaPotions.match("honey", java.util.List.of("glowstone_dust", "feather")) == null, "anything else is left to the potioneer");
					ItemStack splashPhase = com.omnispell.alchemy.BrewItem.of(phaseBrew);
					check("Splash Potion of Phase Mana".equals(splashPhase.getHoverName().getString()), "it bottles as a Splash Potion of Phase Mana");
					int phaseSlot = com.omnispell.player.ManaPool.PHASE.ordinal();
					PlayerStore.get(p).mana[phaseSlot] = 0;
					ItemStack mainKeep = p.getMainHandItem().copy();
					p.setXRot(90f); // throw it at your feet
					p.getInventory().setItem(p.getInventory().getSelectedSlot(), splashPhase.copy());
					p.getMainHandItem().use(level, p, net.minecraft.world.InteractionHand.MAIN_HAND);
					double phaseMax = com.omnispell.player.Mana.max(p, com.omnispell.player.ManaPool.PHASE);
					check(PlayerStore.get(p).mana[phaseSlot] > phaseMax * 0.3, "thrown at your feet it refills your Phase mana (" + PlayerStore.get(p).mana[phaseSlot] + " of " + phaseMax + ")");
					check(p.getCooldowns().isOnCooldown(splashPhase), "mana potions have a short cooldown");
					p.getInventory().setItem(p.getInventory().getSelectedSlot(), mainKeep);
					p.setXRot(25f);
				}

				// An arcane well comes away whole only with Silk Touch.
				{
					var wellState = com.omnispell.item.OmniItems.ARCANE_WELL.defaultBlockState();
					ItemStack plainPick = new ItemStack(Items.DIAMOND_PICKAXE);
					ItemStack silkPick = new ItemStack(Items.DIAMOND_PICKAXE);
					silkPick.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
					var plainDrops = net.minecraft.world.level.block.Block.getDrops(wellState, level, b, null, p, plainPick);
					var silkDrops = net.minecraft.world.level.block.Block.getDrops(wellState, level, b, null, p, silkPick);
					check(plainDrops.stream().noneMatch(d -> d.is(com.omnispell.item.OmniItems.ARCANE_WELL_ITEM)) && plainDrops.stream().anyMatch(d -> d.is(Items.AMETHYST_SHARD)),
							"an arcane well mined without Silk Touch shatters into amethyst");
					check(silkDrops.stream().anyMatch(d -> d.is(com.omnispell.item.OmniItems.ARCANE_WELL_ITEM)), "with Silk Touch it comes away whole");
				}

				// Air flings and the other movement spells pay Phase mana.
				{
					com.omnispell.spell.Spell fling = new com.omnispell.spell.Spell();
					fling.type = "exploration";
					fling.toggle = "bound";
					com.omnispell.spell.Spell up = new com.omnispell.spell.Spell();
					up.type = "exploration";
					up.toggle = "launch";
					check(fling.isExploration() && com.omnispell.spell.Casting.manaCost(fling) > 0 && com.omnispell.spell.Casting.poolOf(fling) == com.omnispell.player.ManaPool.PHASE
							&& com.omnispell.spell.Casting.manaCost(up) > 0 && com.omnispell.spell.Casting.poolOf(up) == com.omnispell.player.ManaPool.PHASE, "a wild bound and a launch cost Phase mana");
				}

				// The wand sprint can be switched off.
				{
					var speedAttr = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
					ItemStack keepHand = p.getMainHandItem().copy();
					p.getInventory().setItem(p.getInventory().getSelectedSlot(), WandItem.create("oak", "flint"));
					PlayerStore.get(p).mana[com.omnispell.player.ManaPool.PHASE.ordinal()] = 5;
					p.setSprinting(true);
					com.omnispell.player.Mana.wandUpkeep(p);
					boolean onBoost = speedAttr.hasModifier(com.omnispell.Omnispell.id("wand_sprint"));
					PlayerStore.get(p).noWandSprint = true;
					com.omnispell.player.Mana.wandUpkeep(p);
					boolean offBoost = speedAttr.hasModifier(com.omnispell.Omnispell.id("wand_sprint"));
					check(onBoost && !offBoost, "the wand sprint boost works, and stops when switched off in the Spellbook");
					PlayerStore.get(p).noWandSprint = false;
					p.setSprinting(false);
					com.omnispell.player.Mana.wandUpkeep(p);
					p.getInventory().setItem(p.getInventory().getSelectedSlot(), keepHand);
				}

				// New wand cores.
				{
					var cores = com.omnispell.item.WandParts.CORES;
					check(cores.size() >= 35 && cores.containsKey("enchanted_golden_apple") && cores.containsKey("wither_skeleton_skull") && cores.containsKey("beacon")
							&& cores.containsKey("heart_of_the_sea") && cores.containsKey("wither_rose"), "there are " + cores.size() + " wand cores, the apple, wither skull, beacon, heart of the sea and wither rose among them");
					check(level.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, com.omnispell.Omnispell.id("wand_oak_enchanted_golden_apple"))).isPresent(), "an apple-core wand has a recipe");
					ItemStack mainBefore = p.getMainHandItem().copy();
					int barrierSlot = com.omnispell.player.ManaPool.BARRIER.ordinal();
					p.getInventory().setItem(p.getInventory().getSelectedSlot(), WandItem.create("oak", "enchanted_golden_apple"));
					PlayerStore.get(p).mana[barrierSlot] = 80;
					p.setHealth(6f);
					float lava = com.omnispell.item.CoreAbilities.incoming(p, level.damageSources().lava(), 30f);
					check(lava <= 5f && PlayerStore.get(p).mana[barrierSlot] < 80, "with an apple-core wand, lava that would kill leaves you alive (" + lava + " of 30 taken at 6 health) and costs Barrier mana");
					PlayerStore.get(p).mana[barrierSlot] = 0;
					float unguarded = com.omnispell.item.CoreAbilities.incoming(p, level.damageSources().lava(), 30f);
					check(unguarded == 30f, "with no Barrier mana left, the apple no longer saves you");
					p.setHealth(p.getMaxHealth());
					PlayerStore.get(p).mana[barrierSlot] = 80;
					check(com.omnispell.player.Mana.max(p, com.omnispell.player.ManaPool.BARRIER) > com.omnispell.player.ManaPool.BARRIER.baseMax, "a core can deepen a mana pool");

					p.getInventory().setItem(p.getInventory().getSelectedSlot(), WandItem.create("oak", "wither_skeleton_skull"));
					var witherCow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
					witherCow.snapTo(b.getX() + 0.5, b.getY(), b.getZ() + 3.5, 0f, 0f);
					witherCow.setNoAi(true);
					level.addFreshEntity(witherCow);
					com.omnispell.item.CoreAbilities.onStrike(p, witherCow, 2f, false, true);
					check(witherCow.hasEffect(net.minecraft.world.effect.MobEffects.WITHER), "a wither-skull core withers what its spells strike");
					witherCow.discard();

					p.getInventory().setItem(p.getInventory().getSelectedSlot(), WandItem.create("oak", "beacon"));
					com.omnispell.item.CoreAbilities.everySecond(p);
					check(p.hasEffect(net.minecraft.world.effect.MobEffects.HASTE) && p.hasEffect(net.minecraft.world.effect.MobEffects.SPEED), "a beacon core gives Haste and Speed while held");
					p.removeAllEffects();

					// A wand can be named on an anvil.
					var anvil = new net.minecraft.world.inventory.AnvilMenu(77, p.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(level, b));
					anvil.getSlot(0).set(WandItem.create("oak", "flint"));
					anvil.setItemName("Brawler");
					ItemStack named = anvil.getSlot(2).getItem();
					check(WandItem.isWand(named) && "Brawler".equals(named.getHoverName().getString()), "an anvil names a wand (" + named.getHoverName().getString() + ")");
					// and puts Soul Bind on it from a book
					ItemStack soulBook = new ItemStack(Items.ENCHANTED_BOOK);
					var soulBind = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, net.minecraft.resources.Identifier.fromNamespaceAndPath("soulbind", "soul_bind")));
					soulBook.enchant(soulBind, 1);
					var bookAnvil = new net.minecraft.world.inventory.AnvilMenu(78, p.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(level, b));
					bookAnvil.getSlot(0).set(WandItem.create("oak", "flint"));
					bookAnvil.getSlot(1).set(soulBook);
					ItemStack soulWand = bookAnvil.getSlot(2).getItem();
					check(WandItem.isWand(soulWand) && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(soulBind, soulWand) >= 1, "an anvil puts Soul Bind from a book onto a wand");
					bookAnvil.getSlot(0).set(ItemStack.EMPTY);
					bookAnvil.getSlot(1).set(ItemStack.EMPTY);
					anvil.getSlot(0).set(ItemStack.EMPTY);
					p.getInventory().setItem(p.getInventory().getSelectedSlot(), mainBefore);
				}

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
				System.out.println("[omnispell-test] arcanist offers: " + keeper.getOffers().stream().map(o -> o.getResult().getHoverName().getString() + " " + o.getResult().get(DataComponents.CUSTOM_DATA)).toList());
				keeper.discard();

				// The ruined Forest Gate: it builds, and its withered gate wakes only once mended with all three.
				BlockPos ruinGate = b.offset(-40, 0, 0);
				for (BlockPos pos : BlockPos.betweenClosed(ruinGate.offset(-6, -1, -6), ruinGate.offset(6, -1, 6))) level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(ruinGate.offset(-6, 0, -6), ruinGate.offset(6, 6, 6))) level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				check(com.omnispell.world.PortalRuins.ForestGateRuin.INSTANCE.place(level, level.getChunkSource().getGenerator(), level.getRandom(), ruinGate), "a ruined Forest Gate builds");
				BlockPos withered = null;
				for (BlockPos pos : BlockPos.betweenClosed(ruinGate.offset(-6, -2, -6), ruinGate.offset(6, 4, 6))) if (level.getBlockState(pos).is(OmniItems.WITHERED_GATE)) withered = pos.immutable();
				check(withered != null, "the ruin holds a withered gate");
				var gateHit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(withered), net.minecraft.core.Direction.UP, withered, false);
				for (var mend : new net.minecraft.world.item.Item[]{Items.EMERALD, Items.ENDER_PEARL}) {
					p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(mend));
					level.getBlockState(withered).useItemOn(p.getOffhandItem(), level, p, net.minecraft.world.InteractionHand.OFF_HAND, gateHit);
				}
				check(level.getBlockState(withered).is(OmniItems.WITHERED_GATE), "two of three is not enough to wake it");
				p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.GLOW_BERRIES));
				level.getBlockState(withered).useItemOn(p.getOffhandItem(), level, p, net.minecraft.world.InteractionHand.OFF_HAND, gateHit);
				check(level.getBlockState(withered).is(OmniItems.FOREST_GATE), "with emerald, pearl and glow berries it wakes as a Forest Gate");
				p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
				boolean skyRealm = net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.Identifier.fromNamespaceAndPath("aerialhell", "stellar_portal_frame_block"));
				BlockPos skyRuin = b.offset(-40, 0, 30);
				for (BlockPos pos : BlockPos.betweenClosed(skyRuin.offset(-6, -1, -6), skyRuin.offset(6, -1, 6))) level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(skyRuin.offset(-6, 0, -6), skyRuin.offset(6, 8, 6))) level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				boolean skyBuilt = com.omnispell.world.PortalRuins.SkyPortalRuin.INSTANCE.place(level, level.getChunkSource().getGenerator(), level.getRandom(), skyRuin);
				check(skyBuilt == skyRealm, "a ruined sky portal builds when the sky realm is installed, and only then (installed: " + skyRealm + ")");
				ruinSites[0] = ruinGate;
				ruinSites[1] = skyRuin;

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
			// Rare pulling and sinking effects: quicksand, vacuum, black hole.
			BlockPos[] pit = new BlockPos[1];
			net.minecraft.world.entity.animal.cow.Cow[] sinker = new net.minecraft.world.entity.animal.cow.Cow[2];
			double[] sinkY = new double[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				PlayerStore.get(p).essence = 500;
				PlayerStore.get(p).pending = null;
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Earth", "command:Open", "intention:Malice", "charge:Maxima (Amplify power)"));
				com.omnispell.spell.Spell quick = PlayerStore.get(p).pending;
				check(quick != null && "quicksand".equals(quick.toggle) && quick.description.contains("gravel"), "Earth + Open + Malice is quicksand: " + (quick == null ? "nothing" : quick.description));
				PlayerStore.get(p).pending = null;
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Silk", "command:Open", "intention:Malice", "charge:Maxima (Amplify power)"));
				com.omnispell.spell.Spell vac = PlayerStore.get(p).pending;
				check(vac != null && "vacuum".equals(vac.toggle), "Silk + Open + Malice is a vacuum (" + (vac == null ? "nothing" : vac.toggle) + ")");
				PlayerStore.get(p).pending = null;
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Void", "command:Sever", "intention:Malice"));
				com.omnispell.spell.Spell hole = PlayerStore.get(p).pending;
				check(hole != null && "black_hole".equals(hole.toggle), "Void + Sever + Malice opens a black hole (" + (hole == null ? "nothing" : hole.toggle) + ")");
				PlayerStore.get(p).pending = null;
				check(com.omnispell.spell.Rifts.forEntry("stone-launch-destruction") == null && com.omnispell.spell.Rifts.forEntry("stone-envelop-protection") == null,
						"and the common pairings are untouched (Earth + Sever + Malice still carves)");

				// An arena of dirt, then quicksand under a cow.
				BlockPos c0 = p.blockPosition().offset(0, 0, -30);
				pit[0] = c0;
				for (BlockPos pos : BlockPos.betweenClosed(c0.offset(-6, -2, -6), c0.offset(6, -1, 6))) level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(c0.offset(-6, 0, -6), c0.offset(6, 4, 6))) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				var cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
				cow.snapTo(c0.getX() + 0.5, c0.getY(), c0.getZ() + 0.5, 0f, 0f);
				cow.setNoAi(true);
				cow.setOnGround(true);
				level.addFreshEntity(cow);
				sinker[0] = cow;
				sinkY[0] = cow.getY();
				com.omnispell.spell.Cast qc = new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), quick);
				int turned = com.omnispell.spell.Rifts.gravel(level, c0.below(), 2, 120);
				int caught = com.omnispell.spell.Rifts.sinkArea(qc, Vec3.atBottomCenterOf(c0), 2, 60, "stone", Blocks.GRAVEL.defaultBlockState());
				check(turned >= 9 && level.getBlockState(c0.below()).is(Blocks.GRAVEL) && caught == 1, "quicksand turns " + turned + " blocks of ground to gravel and catches the cow (" + caught + ")");
				// A black hole beside a second cow.
				var cow2 = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
				cow2.snapTo(c0.getX() + 5.5, c0.getY(), c0.getZ() + 0.5, 0f, 0f);
				level.addFreshEntity(cow2);
				sinker[1] = cow2;
				com.omnispell.spell.Rifts.blackHole(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), hole), Vec3.atBottomCenterOf(c0.offset(1, 0, 0)), 2, 1.0, 1.0, 0x8020C0);
				// A vacuum beside a third creature, well away from the hole.
				var gasp = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
				gasp.snapTo(c0.getX() - 20.5, c0.getY() + 1, c0.getZ() + 0.5, 0f, 0f);
				level.addFreshEntity(gasp);
				int airBefore = gasp.getAirSupply();
				com.omnispell.spell.Rifts.vacuum(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), vac), new Vec3(c0.getX() - 16.5, c0.getY(), c0.getZ() + 0.5), 2, 1.0);
				check(gasp.getAirSupply() < airBefore - 100 && gasp.getDeltaMovement().x > 0.2, "a vacuum pulls a pig toward its heart and leaves it gasping (air " + airBefore + " to " + gasp.getAirSupply() + ")");
				gasp.discard();
			});
			context.waitTicks(25);
			server.runOnServer(s -> {
				check(sinker[0].isAlive() && sinker[0].getY() < sinkY[0] - 0.4, "the cow sinks into the quicksand (down " + String.format("%.2f", sinkY[0] - sinker[0].getY()) + ")");
				double d = sinker[1].position().distanceTo(Vec3.atBottomCenterOf(pit[0].offset(1, 0, 0)));
				check(d < 3.5, "the black hole drags the other cow in (" + String.format("%.1f", d) + " blocks from its heart)");
			});
			context.waitTicks(110);
			server.runOnServer(s -> {
				ServerLevel level = player(s).level();
				check(!sinker[0].isAlive() || sinker[0].getY() >= sinkY[0] - 0.1, "the ground lets the cow go again");
				check(level.getBlockState(pit[0].below()).is(Blocks.DIRT), "and the gravel turns back to dirt");
				for (var cow : sinker) if (cow.isAlive()) cow.discard();
			});
			// Great workings: every one is cast for real beside a cow, which must come to harm.
			String[][] wonders = {
					{"inferno", "base:Fire", "command:Envelop"}, {"sky_beam", "base:Light", "command:Summon"}, {"meteor", "base:Fire", "command:Release"},
					{"solar_flare", "base:Light", "command:Agitate"}, {"geyser", "base:Water", "command:Summon"}, {"tidal_wave", "base:Water", "command:Grow"},
					{"ice_spikes", "base:Ice", "command:Grow"}, {"blizzard", "base:Ice", "command:Envelop"}, {"earthquake", "base:Earth", "command:Agitate"},
					{"bloodletting", "base:Blood", "command:Bind"}, {"tendrils", "base:Shadow", "command:Grow"}, {"chain_lightning", "base:Thunder", "command:Release"}};
			net.minecraft.world.entity.animal.cow.Cow[] marks = new net.minecraft.world.entity.animal.cow.Cow[wonders.length];
			Vec3[] homeAt = new Vec3[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				BlockPos home = p.blockPosition();
				homeAt[0] = p.position();
				// Which spells carry which: the table, the tier gate and the book.
				com.omnispell.spell.Spell low = new com.omnispell.spell.Spell();
				low.type = "exploration";
				low.words.addAll(java.util.List.of("base:Fire", "command:Grow", "intention:Malice"));
				low.tier = 2;
				com.omnispell.spell.Spell high = new com.omnispell.spell.Spell();
				high.type = "exploration";
				high.words.addAll(low.words);
				high.tier = 3;
				check(com.omnispell.spell.Wonders.extras(low).isEmpty() && com.omnispell.spell.Wonders.describeExtras(low).contains("tier 3")
						&& com.omnispell.spell.Wonders.extras(high).contains("inferno"), "Fire + Grow + Malice gains an inferno from tier 3, and the book says so below it");
				com.omnispell.spell.Spell common = new com.omnispell.spell.Spell();
				common.type = "exploration";
				common.words.addAll(java.util.List.of("base:Earth", "command:Sever", "intention:Malice"));
				common.tier = 5;
				check(com.omnispell.spell.Wonders.extras(common).isEmpty(), "a plain tunnel spell carries no extra working, even at tier 5");
				for (int i = 0; i < wonders.length; i++) {
					BlockPos spot = home.offset(-24 + (i % 4) * 16, 0, 50 + (i / 4) * 16);
					for (BlockPos pos : BlockPos.betweenClosed(spot.offset(-6, -1, -6), spot.offset(6, -1, 6))) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
					for (BlockPos pos : BlockPos.betweenClosed(spot.offset(-6, 0, -6), spot.offset(6, 6, 6))) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
					p.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
					var cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
					cow.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 4.5, 0f, 0f);
					cow.setNoAi(true);
					cow.setOnGround(true);
					level.addFreshEntity(cow);
					marks[i] = cow;
					p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
					com.omnispell.spell.Spell sp = new com.omnispell.spell.Spell();
					sp.type = "exploration";
					sp.words.addAll(java.util.List.of(wonders[i][1], wonders[i][2], "intention:Malice"));
					sp.tier = 5;
					sp.element = com.omnispell.data.WordBank.word(wonders[i][1]).explore;
					com.omnispell.spell.Cast wc = new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), sp);
					check(com.omnispell.spell.Wonders.cast(wc, wonders[i][0]), wonders[i][0] + " is cast");
				}
			});
			context.waitTicks(50);
			server.runOnServer(s -> {
				for (int i = 0; i < wonders.length; i++) {
					var cow = marks[i];
					boolean killed = cow.isRemoved() && cow.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.KILLED;
					boolean harmed = killed || (!cow.isRemoved() && (cow.getHealth() < cow.getMaxHealth() || cow.hasEffect(MobEffects.BLINDNESS)));
					check(harmed, wonders[i][0] + " reached the cow (health " + cow.getHealth() + (killed ? ", killed" : cow.isRemoved() ? ", unloaded" : "") + ")");
					if (cow.isAlive()) cow.discard();
				}
				ServerPlayer p = player(s);
				p.teleportTo(p.level(), homeAt[0].x, homeAt[0].y, homeAt[0].z, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(5);
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
			server.runOnServer(s -> player(s).teleportTo(player(s).level(), ruinSites[0].getX() + 6.5, ruinSites[0].getY() + 4, ruinSites[0].getZ() + 6.5, java.util.Set.of(), 135f, 25f, true));
			context.waitTicks(30);
			context.takeScreenshot("29c-forest-gate-ruin");
			server.runOnServer(s -> player(s).teleportTo(player(s).level(), ruinSites[1].getX() + 7.5, ruinSites[1].getY() + 3, ruinSites[1].getZ() + 0.5, java.util.Set.of(), 90f, 15f, true));
			context.waitTicks(30);
			context.takeScreenshot("29d-sky-portal-ruin");
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
				check(data.folders.size() == 1 && data.folder("Archive") != null && data.spell(id).folder == null && data.spells.size() == 15, "removing a folder returns its spells and forgets nothing; the Archive stays");
				com.omnispell.spell.SpellCrafting.deleteFolder(p, "Archive");
				check(data.folder("Archive") != null, "the Archive cannot be removed");
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
				// Other job sites nearby (the forest ruin's barrels) would compete for the villager.
				for (BlockPos pos : BlockPos.betweenClosed(b.offset(-32, -4, -32), b.offset(32, 10, 32))) {
					if (net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(level.getBlockState(pos)).isPresent()) level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				}
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
					model.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(OmniItems.WIZARD_ROBE));
					model.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(OmniItems.WIZARD_BOOTS));
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
				player(s).setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(OmniItems.WIZARD_HAT)); // undyed first
				player(s).setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(OmniItems.WIZARD_ROBE));
				player(s).setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(OmniItems.WIZARD_BOOTS));
			});
			context.runOnClient(c -> c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			context.takeScreenshot("31c-hat-third-person");
			server.runOnServer(s -> {
				for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.FEET}) {
					ItemStack piece = player(s).getItemBySlot(slot).copy();
					piece.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x5E7C16));
					player(s).setItemSlot(slot, piece);
				}
			});
			context.waitTicks(10);
			context.takeScreenshot("31d-attire-dyed");
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

			// ---- conjuration: the creature word picks the body, the base word colours it, and it casts the spell
			net.minecraft.world.entity.monster.zombie.Zombie[] conjPrey = new net.minecraft.world.entity.monster.zombie.Zombie[1];
			float[] casterHealth = new float[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				BlockPos conjAt = p.blockPosition();
				for (BlockPos pos : BlockPos.betweenClosed(conjAt.offset(-8, -1, -4), conjAt.offset(8, -1, 14))) level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(conjAt.offset(-8, 0, -4), conjAt.offset(8, 5, 14))) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				p.setHealth(p.getMaxHealth());
				casterHealth[0] = p.getHealth();
				String[][] kinds = {{"water", "", "Water Serpent", "-35"}, {"ice", "attribute:Wolf", "Frost Hound", "0"}, {"fire", "attribute:Fox", "Ember Fox", "35"}};
				String[] expect = {"serpent", "wolf", "fox"};
				for (int i = 0; i < kinds.length; i++) {
					com.omnispell.spell.Spell conj = new com.omnispell.spell.Spell();
					conj.type = "conjuration";
					conj.tier = 3;
					conj.code.element = kinds[i][0];
					if (!kinds[i][1].isEmpty()) conj.words.add(kinds[i][1]);
					conj.name = kinds[i][2];
					check(expect[i].equals(com.omnispell.spell.Conjuring.bodyFor(conj, kinds[i][2]).id()), kinds[i][2] + " takes the body of a " + expect[i] + " (" + com.omnispell.spell.Conjuring.bodyFor(conj, kinds[i][2]).id() + ")");
					p.setYRot(Float.parseFloat(kinds[i][3]));
					p.setXRot(0f);
					check(com.omnispell.spell.Conjuring.spawn(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), conj), kinds[i][2], 10, 20), kinds[i][2] + " is conjured");
				}
				p.setYRot(0f);
				var conjZombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				conjZombie.snapTo(conjAt.getX() + 0.5, conjAt.getY(), conjAt.getZ() + 9.5, 180f, 0f);
				conjZombie.setNoAi(true);
				conjZombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2000, 0));
				level.addFreshEntity(conjZombie);
				conjPrey[0] = conjZombie;
				p.hurtServer(level, level.damageSources().mobAttack(conjZombie), 0.01f); // the zombie "hit" the caster, so the creatures know their foe
				casterHealth[0] = p.getHealth();
				p.teleportTo(level, conjAt.getX() + 0.5, conjAt.getY(), conjAt.getZ() - 3.5, java.util.Set.of(), 0f, 10f, true);
			});
			context.runOnClient(c -> c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
			context.waitTicks(25);
			context.takeScreenshot("32-conjured-creatures");
			check(context.computeOnClient(c -> ClientState.conjureElements.containsValue("water") && ClientState.conjureElements.containsValue("ice") && ClientState.conjureElements.containsValue("fire")),
					"each conjured creature wears its element's markings (" + context.computeOnClient(c -> String.valueOf(ClientState.conjureElements.values())) + ")");
			context.waitTicks(80);
			server.runOnServer(s -> {
				var conjZombie = conjPrey[0];
				boolean harmed = (conjZombie.isRemoved() && conjZombie.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.KILLED) || conjZombie.getHealth() < conjZombie.getMaxHealth();
				check(harmed, "the conjured creatures fight the zombie (health " + conjZombie.getHealth() + ")");
				check(player(s).getHealth() >= casterHealth[0] - 0.01f, "and never hurt their caster");
				if (!conjZombie.isRemoved()) conjZombie.discard();
				for (var e : player(s).level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player(s).getBoundingBox().inflate(30), com.omnispell.spell.Conjuring::isConjured)) com.omnispell.spell.Summons.dismiss(e);
			});

			// ---- defense: a shield is a ward you can see, shaped by its element and command
			net.minecraft.world.entity.monster.zombie.Zombie[] striker = new net.minecraft.world.entity.monster.zombie.Zombie[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				com.omnispell.spell.Spell fireShield = new com.omnispell.spell.Spell();
				fireShield.type = "defense";
				fireShield.tier = 3;
				fireShield.code.type = "shield";
				fireShield.code.element = "fire";
				fireShield.words.addAll(java.util.List.of("base:Ember", "command:Repel", "intention:Restoration"));
				String said = com.omnispell.spell.Wards.describe(fireShield);
				check(said.contains("flame") && said.contains("thrown back"), "Ember + Repel describes a flame shell that throws attackers back: " + said);
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.BARRIER.ordinal()] = 100;
				ItemStack shieldWand = WandItem.create("oak", "flint");
				WandItem.setSpell(shieldWand, 0, fireShield);
				p.getInventory().setItem(p.getInventory().getSelectedSlot(), shieldWand);
				com.omnispell.spell.Casting.cast(p, shieldWand, 0);
				check(com.omnispell.spell.Wards.active(p) && p.getAbsorptionAmount() > 0 && p.hasEffect(MobEffects.FIRE_RESISTANCE), "casting it raises a fire ward with its hearts (" + p.getAbsorptionAmount() + " absorption)");
				var z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				z.snapTo(p.getX() + 1.2, p.getY(), p.getZ(), 90f, 0f);
				z.setNoAi(true);
				level.addFreshEntity(z);
				striker[0] = z;
				com.omnispell.spell.Modifiers.modifyIncoming(p, level.damageSources().mobAttack(z), 3f);
				check(z.isOnFire() && z.getDeltaMovement().horizontalDistance() > 0.5, "a zombie that strikes the fire ward is set alight and thrown back");
				check(com.omnispell.spell.Modifiers.modifyIncoming(p, level.damageSources().inFire(), 4f) == 0f, "and fire cannot hurt the one inside it");
			});
			context.runOnClient(c -> c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(12);
			context.takeScreenshot("33-fire-ward");
			context.runOnClient(c -> c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
			server.runOnServer(s -> {
				if (!striker[0].isRemoved()) striker[0].discard();
				// Other elements do other things: wind turns arrows aside, shadow lets blows pass.
				com.omnispell.spell.Spell windShield = new com.omnispell.spell.Spell();
				windShield.type = "defense";
				windShield.code.element = "wind";
				windShield.words.add("command:Reflect");
				check(com.omnispell.spell.Wards.describe(windShield).contains("arrows aside") && com.omnispell.spell.Wards.describe(windShield).contains("sent back"), "a wind shield with Reflect turns arrows aside and sends spells back");
			});

			// ---- ice on lava in the Nether holds only for a while: real casts, by the player standing there
			BlockPos netherLava = new BlockPos(8, 100, 8);
			Vec3[] beforeNether = new Vec3[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				beforeNether[0] = p.position();
				ServerLevel nether = s.getLevel(net.minecraft.world.level.Level.NETHER);
				for (BlockPos pos : BlockPos.betweenClosed(netherLava.offset(-8, -2, -8), netherLava.offset(8, -1, 8))) nether.setBlockAndUpdate(pos, Blocks.NETHERRACK.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(netherLava.offset(-8, 0, -8), netherLava.offset(8, 6, 8))) nether.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				// a lava pool ahead of the player, 3 x 3, one deep
				for (BlockPos pos : BlockPos.betweenClosed(netherLava.offset(-1, -1, 3), netherLava.offset(1, -1, 5))) nether.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
				p.teleportTo(nether, netherLava.getX() + 0.5, netherLava.getY(), netherLava.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
				check(com.omnispell.spell.TempBlocks.scorching(nether, netherLava) && !com.omnispell.spell.TempBlocks.scorching(s.overworld(), BlockPos.containing(beforeNether[0])), "the Nether is scorching and the Overworld is not");
			});
			context.waitTicks(20);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel nether = p.level();
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.FORCE.ordinal()] = 60;
				// The Ice cantrip (chill), looking down at the pool.
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(netherLava.offset(0, -1, 4)));
				com.omnispell.spell.Spell chill = new com.omnispell.spell.Spell();
				chill.type = "exploration";
				chill.words.add("base:Ice");
				chill.toggle = "chill";
				chill.element = "ice";
				chill.tier = 1;
				check(com.omnispell.spell.ExploreEffects.cast(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), chill)), "the Ice cantrip is cast at Nether lava");
				int ice = 0;
				for (BlockPos pos : BlockPos.betweenClosed(netherLava.offset(-1, -1, 3), netherLava.offset(1, -1, 5))) if (nether.getBlockState(pos).is(Blocks.PACKED_ICE)) ice++;
				check(ice >= 4, "it freezes the lava over (" + ice + " of 9)");
				// Ice + Freeze + Warding: solidify, around the player, on a second pool behind them.
				for (BlockPos pos : BlockPos.betweenClosed(netherLava.offset(-1, -1, -3), netherLava.offset(1, -1, -2))) nether.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
				com.omnispell.spell.Spell hold = new com.omnispell.spell.Spell();
				hold.type = "exploration";
				hold.words.addAll(java.util.List.of("base:Ice", "command:Freeze", "intention:Warding"));
				hold.toggle = "solidify";
				hold.element = "ice";
				hold.tier = 2;
				check(com.omnispell.spell.ExploreEffects.cast(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), hold)), "an ice Solidify is cast beside Nether lava");
				int held = 0;
				for (BlockPos pos : BlockPos.betweenClosed(netherLava.offset(-1, -1, -3), netherLava.offset(1, -1, -2))) if (nether.getBlockState(pos).is(Blocks.PACKED_ICE)) held++;
				check(held >= 4, "it freezes that pool too (" + held + " of 6)");
				// Ice + Envelop + Restoration (mend the land) fills a lava pool with packed ice; in the Nether that melts too.
				for (BlockPos pos : BlockPos.betweenClosed(netherLava.offset(4, -1, -1), netherLava.offset(5, -1, 1))) nether.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(netherLava.offset(4, -1, 0)));
				com.omnispell.spell.Spell mend = new com.omnispell.spell.Spell();
				mend.type = "exploration";
				mend.words.addAll(java.util.List.of("base:Ice", "command:Envelop", "intention:Restoration"));
				mend.toggle = "mend_land";
				mend.element = "ice";
				mend.tier = 1;
				com.omnispell.spell.ExploreEffects.cast(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), mend));
				int filled = 0;
				for (BlockPos pos : BlockPos.betweenClosed(netherLava.offset(4, -1, -1), netherLava.offset(5, -1, 1))) if (nether.getBlockState(pos).is(Blocks.PACKED_ICE)) filled++;
				check(filled >= 3, "mending the land with Ice fills Nether lava with packed ice (" + filled + " of 6)");
			});
			context.waitTicks(270);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel nether = p.level();
				int left = 0, lava = 0;
				for (BlockPos pos : BlockPos.betweenClosed(netherLava.offset(-1, -1, -3), netherLava.offset(5, -1, 5))) {
					if (nether.getBlockState(pos).is(Blocks.PACKED_ICE)) left++;
					if (nether.getBlockState(pos).is(Blocks.LAVA)) lava++;
				}
				check(left == 0 && lava >= 12, "after its time the ice melts back to lava (" + left + " ice left, " + lava + " lava)");
				p.teleportTo(s.overworld(), beforeNether[0].x, beforeNether[0].y, beforeNether[0].z, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(20);

			// ---- the friends' notes: costs, attribute shapes, conjured crowds and warriors, masonry, defense forms, wand enchantments, armor
			net.minecraft.world.entity.monster.zombie.Zombie[] archerPrey = new net.minecraft.world.entity.monster.zombie.Zombie[1];
			int[] crowd = new int[2];
			Vec3[] notesHome = new Vec3[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				notesHome[0] = p.position();
				ItemStack testWand = WandItem.create("oak", "flint");
				// Every exploration spell either pays as it goes or costs mana up front, and shows its pool.
				String[] toggles = {"carve", "drill", "crater", "pit", "moat", "flatten", "dry", "clear", "raise_wall", "ring", "dome", "platform", "mend_land", "calm", "attune", "growth",
						"illuminate", "prospect", "thicket", "solidify", "weather", "gather", "conjure", "path", "tree", "stairs", "pillar", "strike", "field", "launch", "survey", "echo", "augury",
						"waymark", "lull", "bound", "bridge", "projectile", "beam", "area_damage", "shrapnel", "eruption", "detonate", "disintegrate", "barrier_wall", "armor", "deflect", "anchor",
						"ward_element", "smoke_screen", "blackout", "decoy", "mirage", "phantom", "dash", "leap", "grapple", "tether", "current", "mount", "reveal", "scout", "true_sight", "track",
						"push", "polymorph", "transmute", "hearth", "stash", "rest", "cloud", "construct_weapon"};
				int free = 0;
				String freeOne = "";
				for (String t : toggles) {
					com.omnispell.spell.Spell sp = new com.omnispell.spell.Spell();
					sp.type = "exploration";
					sp.toggle = t;
					sp.tier = 2;
					if (com.omnispell.spell.Casting.manaCost(sp) <= 0 || com.omnispell.spell.Casting.poolOf(sp) == null) {
						free++;
						freeOne = t;
					}
				}
				check(free == 0, "every exploration spell that does not pay as it goes costs mana up front and shows its pool (" + free + " free, e.g. " + freeOne + ")");
				// Attribute words shape exploration spells.
				PlayerStore.get(p).essence = 500;
				PlayerStore.get(p).pending = null;
				p.getInventory().setItem(27, WandItem.create("oak", "nether_star")); // room for four words
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Earth", "command:Grow", "intention:Warding", "attribute:Bed"));
				com.omnispell.spell.Spell bed = PlayerStore.get(p).pending;
				check(bed != null && "rest".equals(bed.toggle), "Earth + Bed in an environment spell makes a place to wake (" + (bed == null ? "nothing" : bed.toggle) + ")");
				PlayerStore.get(p).pending = null;
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Water", "command:Grow", "intention:Warding", "attribute:Bed"));
				com.omnispell.spell.Spell balloon = PlayerStore.get(p).pending;
				check(balloon != null && "mine".equals(balloon.toggle) && balloon.description.contains("balloon"), "Water + Bed sets a water-balloon mine (" + (balloon == null ? "nothing" : balloon.toggle) + ")");
				PlayerStore.get(p).pending = null;
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Fire", "command:Grow", "intention:Warding", "attribute:Bridge"));
				com.omnispell.spell.Spell fuseSpell = PlayerStore.get(p).pending;
				check(fuseSpell != null && "fuse".equals(fuseSpell.toggle), "Fire + Bridge lays a fuse of mines (" + (fuseSpell == null ? "nothing" : fuseSpell.toggle) + ")");
				PlayerStore.get(p).pending = null;
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Thunder", "command:Grow", "intention:Warding", "attribute:Bridge"));
				com.omnispell.spell.Spell wire = PlayerStore.get(p).pending;
				check(wire != null && "tripwire".equals(wire.toggle), "Thunder + Bridge strings a tripwire (" + (wire == null ? "nothing" : wire.toggle) + ")");
				PlayerStore.get(p).pending = null;
				com.omnispell.spell.SpellCrafting.craft(p, "exploration", java.util.List.of("base:Earth", "command:Grow", "intention:Warding", "attribute:Bridge"));
				com.omnispell.spell.Spell span = PlayerStore.get(p).pending;
				check(span != null && "bridge".equals(span.toggle), "Bridge in an environment spell builds a bridge (" + (span == null ? "nothing" : span.toggle) + ")");
				PlayerStore.get(p).pending = null;
				var bedWord = com.omnispell.data.WordBank.word("attribute:Bed");
				var bowWord = com.omnispell.data.WordBank.word("attribute:Bow");
				var swordWord = com.omnispell.data.WordBank.word("attribute:Sword");
				check(!com.omnispell.data.WordBank.attributeFits("conjuration", bedWord) && com.omnispell.data.WordBank.attributeFits("conjuration", bowWord)
						&& com.omnispell.data.WordBank.attributeFits("conjuration", swordWord), "conjurations take creatures, weapons and masonry, not beds");

				// Conjuration: how many, how big, which warrior, what it costs.
				com.omnispell.spell.Spell pack = new com.omnispell.spell.Spell();
				pack.type = "conjuration";
				pack.tier = 2;
				pack.code.element = "fire";
				pack.words.addAll(java.util.List.of("attribute:Wolf", "charge:Sparsus (Area of effect)"));
				java.util.Random dice = new java.util.Random(7);
				int lo = 99, hi = 0;
				for (int i = 0; i < 40; i++) {
					int n = com.omnispell.spell.Conjuring.count(pack, dice);
					lo = Math.min(lo, n);
					hi = Math.max(hi, n);
				}
				check(lo == 2 && hi == 3, "Sparsus conjures two or three (" + lo + "-" + hi + ")");
				pack.words.add("charge:Maxima (Amplify power)");
				lo = 99;
				hi = 0;
				for (int i = 0; i < 60; i++) {
					int n = com.omnispell.spell.Conjuring.count(pack, dice);
					lo = Math.min(lo, n);
					hi = Math.max(hi, n);
				}
				check(lo == 3 && hi == 5, "Sparsus with Maxima conjures three to five (" + lo + "-" + hi + ")");
				check(com.omnispell.spell.Casting.manaCost(pack) > 0 && com.omnispell.spell.Casting.poolOf(pack) == com.omnispell.player.ManaPool.SPIRIT, "conjurations cost Spirit mana, and show it");
				com.omnispell.spell.Spell archerSpell = new com.omnispell.spell.Spell();
				archerSpell.type = "conjuration";
				archerSpell.tier = 3;
				archerSpell.code.element = "ice";
				archerSpell.words.add("attribute:Bow");
				check("archer".equals(com.omnispell.spell.Conjuring.bodyFor(archerSpell, "Frost Archer").id()), "Bow conjures an archer");
				com.omnispell.spell.Spell linked = new com.omnispell.spell.Spell();
				linked.type = "conjuration";
				linked.description = "A wolf of embers rises to fight. When it is destroyed, its remaining health is redirected to you, and it explodes in a shower of sparks as it dies.";
				var promised = com.omnispell.spell.Conjuring.traits(linked);
				check(promised.contains("link") && promised.contains("explode"), "a conjuration keeps the promises its description makes (" + promised + ")");

				// Conjure them for real, in a clear field away from everything else.
				BlockPos field = BlockPos.containing(notesHome[0]).offset(60, 0, 0);
				for (BlockPos pos : BlockPos.betweenClosed(field.offset(-12, -1, -12), field.offset(12, -1, 12))) level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(field.offset(-12, 0, -24), field.offset(12, 8, 12))) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				// a gap to the north for the bridge to span
				for (BlockPos pos : BlockPos.betweenClosed(field.offset(-5, -5, -22), field.offset(5, -1, -2))) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(field.offset(-5, -6, -22), field.offset(5, -6, -2))) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
				p.teleportTo(level, field.getX() + 0.5, field.getY(), field.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
				int crowdBefore = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(20), com.omnispell.spell.Conjuring::isConjured).size();
				com.omnispell.spell.Spell twoOrThree = new com.omnispell.spell.Spell();
				twoOrThree.type = "conjuration";
				twoOrThree.tier = 2;
				twoOrThree.code.element = "nature";
				twoOrThree.words.addAll(java.util.List.of("attribute:Frog", "charge:Sparsus (Area of effect)"));
				check(com.omnispell.spell.Conjuring.spawn(new com.omnispell.spell.Cast(p, testWand, twoOrThree), "Bog Frog", 8, 20), "a Sparsus conjuration is cast");
				crowd[0] = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(20), com.omnispell.spell.Conjuring::isConjured).size() - crowdBefore;
				check(crowd[0] >= 2 && crowd[0] <= 3, "and two or three frogs come (" + crowd[0] + ")");
				com.omnispell.spell.Spell giant = new com.omnispell.spell.Spell();
				giant.type = "conjuration";
				giant.tier = 2;
				giant.code.element = "stone";
				giant.words.addAll(java.util.List.of("attribute:Bear", "charge:Maxima (Amplify power)"));
				p.setYRot(90f);
				com.omnispell.spell.Conjuring.spawn(new com.omnispell.spell.Cast(p, testWand, giant), "Great Bear", 8, 20);
				var bear = level.getEntitiesOfClass(net.minecraft.world.entity.animal.polarbear.PolarBear.class, p.getBoundingBox().inflate(20), com.omnispell.spell.Conjuring::isConjured);
				check(!bear.isEmpty() && bear.get(0).getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE) > 1.6, "Maxima conjures a giant (scale " + (bear.isEmpty() ? 0 : bear.get(0).getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE)) + ")");
				// An archer, and something to shoot at.
				p.setYRot(-90f);
				check(com.omnispell.spell.Conjuring.spawn(new com.omnispell.spell.Cast(p, testWand, archerSpell), "Frost Archer", 10, 20), "an archer is conjured");
				var target = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				target.snapTo(field.getX() + 9.5, field.getY(), field.getZ() + 0.5, 90f, 0f);
				target.setNoAi(true);
				target.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2000, 0));
				level.addFreshEntity(target);
				archerPrey[0] = target;
				p.hurtServer(level, level.damageSources().mobAttack(target), 0.01f);
				for (var stray : level.getEntitiesOfClass(net.minecraft.world.entity.monster.skeleton.Stray.class, p.getBoundingBox().inflate(20), com.omnispell.spell.Conjuring::isConjured)) {
					check(stray.getMainHandItem().is(Items.BOW) && stray.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.LEATHER_HELMET), "a frost archer is a stray with a bow and a cap against the sun");
					break;
				}

				// Masonry: a conjured bridge of dressed stone.
				com.omnispell.spell.Spell stoneBridge = new com.omnispell.spell.Spell();
				stoneBridge.type = "conjuration";
				stoneBridge.tier = 1;
				stoneBridge.code.element = "stone";
				stoneBridge.words.add("attribute:Bridge");
				p.setYRot(180f); // facing north
				check(com.omnispell.spell.Conjuring.spawn(new com.omnispell.spell.Cast(p, testWand, stoneBridge), "Bridge", 8, 20), "a Bridge conjuration is cast");
				int bricks = 0;
				for (BlockPos pos : BlockPos.betweenClosed(field.offset(-3, -12, -20), field.offset(3, 3, -1))) if (level.getBlockState(pos).is(Blocks.STONE_BRICKS) || level.getBlockState(pos).is(Blocks.STONE_BRICK_WALL)) bricks++;
				check(bricks >= 30, "it raises a bridge of stone bricks and rails (" + bricks + " blocks)");
				BlockPos brick = null;
				for (BlockPos pos : BlockPos.betweenClosed(field.offset(-3, -1, -8), field.offset(3, -1, -2))) if (level.getBlockState(pos).is(Blocks.STONE_BRICKS) && com.omnispell.spell.Masonry.isConjured(pos.immutable())) {
					brick = pos.immutable();
					break;
				}
				check(brick != null && !com.omnispell.spell.Masonry.breakBlock(level, brick) && level.getBlockState(brick).isAir(), "breaking conjured masonry gives nothing");

				// Defense forms.
				var dog = EntityTypes.WOLF.create(level, EntitySpawnReason.COMMAND);
				dog.snapTo(field.getX() + 2.5, field.getY(), field.getZ() + 2.5, 0f, 0f);
				dog.tame(p);
				dog.setNoAi(true);
				level.addFreshEntity(dog);
				dog.setHealth(4f);
				com.omnispell.spell.Spell steam = new com.omnispell.spell.Spell();
				steam.id = "defense|test-steam";
				steam.type = "defense";
				steam.tier = 3;
				steam.code.type = "heal";
				steam.code.element = "water";
				steam.words.addAll(java.util.List.of("base:Steam", "command:Disperse", "intention:Warding"));
				check("aegis".equals(com.omnispell.spell.Defenses.form(steam)), "Steam + Disperse + Warding bursts outward to friends (" + com.omnispell.spell.Defenses.form(steam) + ")");
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.RESTORATION.ordinal()] = 75;
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.BARRIER.ordinal()] = 100;
				check(com.omnispell.spell.Defenses.cast(new com.omnispell.spell.Cast(p, testWand, steam), 3) && dog.getHealth() > 4f && p.hasEffect(MobEffects.WATER_BREATHING),
						"it mends the tamed wolf beside you and gives you water breathing (wolf at " + dog.getHealth() + ")");
				com.omnispell.spell.Spell line = new com.omnispell.spell.Spell();
				line.id = "defense|test-line";
				line.type = "defense";
				line.tier = 3;
				line.code.type = "shield";
				line.code.element = "stone";
				line.words.addAll(java.util.List.of("base:Earth", "command:Release", "intention:Warding"));
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, dog.getBoundingBox().getCenter());
				check("lifeline".equals(com.omnispell.spell.Defenses.form(line)) && com.omnispell.spell.Defenses.cast(new com.omnispell.spell.Cast(p, testWand, line), 3) && dog.getAbsorptionAmount() > 0,
						"Earth + Release + Warding casts a shield at the friend you look at (wolf absorption " + dog.getAbsorptionAmount() + ")");
				com.omnispell.spell.Spell cloak = new com.omnispell.spell.Spell();
				cloak.type = "defense";
				cloak.code.element = "shadow";
				cloak.words.addAll(java.util.List.of("command:Shield", "attribute:Cloak"));
				com.omnispell.spell.Spell circle = new com.omnispell.spell.Spell();
				circle.type = "defense";
				circle.code.element = "light";
				circle.words.addAll(java.util.List.of("command:Bind"));
				com.omnispell.spell.Spell statue = new com.omnispell.spell.Spell();
				statue.type = "defense";
				statue.code.element = "stone";
				statue.words.addAll(java.util.List.of("command:Fortify"));
				check("veil".equals(com.omnispell.spell.Defenses.form(cloak)) && "sanctuary".equals(com.omnispell.spell.Defenses.form(circle)) && "bulwark".equals(com.omnispell.spell.Defenses.form(statue)),
						"Cloak hides, Bind marks a sanctuary, Fortify makes you a statue");
				dog.discard();

				// Wands take the mana enchantments, and armor does what the book says.
				var wellspring = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, com.omnispell.Omnispell.id("wellspring")));
				ItemStack wellBook = new ItemStack(Items.ENCHANTED_BOOK);
				wellBook.enchant(wellspring, 2);
				var wellAnvil = new net.minecraft.world.inventory.AnvilMenu(79, p.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(level, field));
				wellAnvil.getSlot(0).set(WandItem.create("oak", "flint"));
				wellAnvil.getSlot(1).set(wellBook);
				ItemStack wellWand = wellAnvil.getSlot(2).getItem();
				check(WandItem.isWand(wellWand) && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(wellspring, wellWand) == 2, "an anvil puts Wellspring on a wand");
				wellAnvil.getSlot(0).set(ItemStack.EMPTY);
				wellAnvil.getSlot(1).set(ItemStack.EMPTY);
				ItemStack keep = p.getMainHandItem().copy();
				p.getInventory().setItem(p.getInventory().getSelectedSlot(), WandItem.create("oak", "flint"));
				double plain = com.omnispell.player.Mana.regenMultiplier(p);
				p.getInventory().setItem(p.getInventory().getSelectedSlot(), wellWand);
				double welled = com.omnispell.player.Mana.regenMultiplier(p);
				check(welled > plain + 0.25, "Wellspring on the wand in hand refills mana faster (" + plain + " to " + welled + ")");
				p.getInventory().setItem(p.getInventory().getSelectedSlot(), keep);
				var slots = new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
				var nethers = new net.minecraft.world.item.Item[]{Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
				ItemStack[] worn = new ItemStack[4];
				for (int i = 0; i < 4; i++) {
					worn[i] = p.getItemBySlot(slots[i]).copy();
					p.setItemSlot(slots[i], new ItemStack(nethers[i]));
				}
				check(com.omnispell.player.Mana.armorDamage(p) == 20, "four pieces of netherite make spells hit 20% harder, as the book says (" + com.omnispell.player.Mana.armorDamage(p) + ")");
				for (int i = 0; i < 4; i++) p.setItemSlot(slots[i], worn[i]);
			});
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				BlockPos field = BlockPos.containing(notesHome[0]).offset(60, 0, 0);
				// stand back to the south-east and look over the bridge, the archers and the giant
				p.teleportTo(p.level(), field.getX() + 6.5, field.getY() + 3, field.getZ() + 5.5, java.util.Set.of(), 150f, 22f, true);
			});
			context.waitTicks(15);
			context.takeScreenshot("34-conjured-bridge-and-warriors");
			context.waitTicks(65);
			server.runOnServer(s -> {
				var z = archerPrey[0];
				boolean shot = (z.isRemoved() && z.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.KILLED) || z.getHealth() < z.getMaxHealth();
				check(shot, "the conjured archers shoot the zombie (health " + z.getHealth() + ")");
				if (!z.isRemoved()) z.discard();
				for (var e : player(s).level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player(s).getBoundingBox().inflate(40), com.omnispell.spell.Conjuring::isConjured)) com.omnispell.spell.Summons.dismiss(e);
				player(s).teleportTo(player(s).level(), notesHome[0].x, notesHome[0].y, notesHome[0].z, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(10);

			// ---- corruption, sway, mines
			net.minecraft.world.entity.Mob[] swayed = new net.minecraft.world.entity.Mob[2];
			net.minecraft.world.entity.LivingEntity[] mineVictims = new net.minecraft.world.entity.LivingEntity[3];
			float[] dropFrom = new float[1];
			Vec3[] cHome = new Vec3[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				cHome[0] = p.position();
				ItemStack w = WandItem.create("oak", "flint");
				com.omnispell.spell.Spell dark = new com.omnispell.spell.Spell();
				dark.type = "attack";
				dark.tier = 4;
				dark.words.addAll(java.util.List.of("base:Void", "command:Sever", "intention:Malice"));
				check(dark.forbidden(), "a Void spell is forbidden magic");
				float fullHealth = p.getMaxHealth();
				for (int i = 0; i < 6; i++) com.omnispell.player.Corruption.onCast(p, dark);
				check(com.omnispell.player.Corruption.full(p) && p.getMaxHealth() <= fullHealth * 0.55f, "six forbidden casts fill the corruption, and the purple hearts fall to half (" + p.getMaxHealth() + " of " + fullHealth + ")");
				float arrowHit = com.omnispell.player.Corruption.incoming(p, level.damageSources().fall(), 10f);
				var biter = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				float handHit = com.omnispell.player.Corruption.incoming(p, level.damageSources().mobAttack(biter), 10f);
				check(arrowHit <= 5.01f && handHit == 10f, "purple hearts halve a fall (" + arrowHit + ") but not a blow by hand (" + handHit + ")");
				double[] mana = PlayerStore.get(p).mana;
				for (var pool : com.omnispell.player.ManaPool.values()) mana[pool.ordinal()] = com.omnispell.player.Mana.max(p, pool);
				mana[com.omnispell.player.ManaPool.FORCE.ordinal()] = 0;
				double barrierBefore = mana[com.omnispell.player.ManaPool.BARRIER.ordinal()];
				double found = com.omnispell.player.Corruption.overdraw(p, com.omnispell.player.ManaPool.FORCE);
				check(found > 10 && mana[com.omnispell.player.ManaPool.FORCE.ordinal()] > 10 && mana[com.omnispell.player.ManaPool.BARRIER.ordinal()] < barrierBefore,
						"full corruption tears a tenth of every other reserve to pay a forbidden spell (" + String.format("%.1f", found) + " found)");
			});
			context.runOnClient(c -> c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(30);
			context.takeScreenshot("35-full-corruption");
			context.runOnClient(c -> c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = p.level();
				PlayerStore.get(p).lastForbiddenAt = level.getServer().overworld().getGameTime() - 13000;
				com.omnispell.player.Corruption.everySecond(p);
				check(PlayerStore.get(p).corruption == 0 && !p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).hasModifier(com.omnispell.Omnispell.id("corruption_health")),
						"ten quiet minutes and the corruption is gone, hearts and all");
				p.setHealth(p.getMaxHealth());

				// Sway: Banish frightens the weak and not the strong.
				BlockPos field = BlockPos.containing(cHome[0]).offset(-60, 0, 0);
				for (BlockPos pos : BlockPos.betweenClosed(field.offset(-10, -1, -10), field.offset(10, -1, 10))) level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(field.offset(-10, 0, -10), field.offset(10, 18, 10))) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				p.teleportTo(level, field.getX() + 0.5, field.getY(), field.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
				var weak = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				weak.snapTo(field.getX() + 3.5, field.getY(), field.getZ() + 0.5, 0f, 0f);
				level.addFreshEntity(weak);
				var strong = EntityTypes.RAVAGER.create(level, EntitySpawnReason.COMMAND);
				strong.snapTo(field.getX() - 4.5, field.getY(), field.getZ() + 0.5, 0f, 0f);
				level.addFreshEntity(strong);
				swayed[0] = weak;
				swayed[1] = strong;
				com.omnispell.spell.Spell banish = new com.omnispell.spell.Spell();
				banish.type = "defense";
				banish.tier = 3;
				banish.code.type = "shield";
				banish.code.element = "shadow";
				banish.words.addAll(java.util.List.of("base:Shadow", "command:Banish", "intention:Warding"));
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.SPIRIT.ordinal()] = 60;
				check("terror".equals(com.omnispell.spell.Defenses.form(banish)) && com.omnispell.spell.Defenses.cast(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), banish), 3), "Shadow + Banish + Warding is cast");
				check(com.omnispell.spell.MobControl.held(weak, "fear") && !com.omnispell.spell.MobControl.held(strong, "fear"), "the zombie flees in fear; the ravager is too strong to sway");
				weak.discard();
				strong.discard();

				// Mines: a fire mine under a zombie, a water balloon under a falling pig, a tripwire across a zombie.
				com.omnispell.spell.Spell fireBed = new com.omnispell.spell.Spell();
				fireBed.type = "exploration";
				fireBed.toggle = "mine";
				fireBed.element = "fire";
				fireBed.tier = 3;
				fireBed.words.add("base:Fire");
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(field.offset(0, -1, 4)));
				check(com.omnispell.spell.Mines.cast(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), fireBed), "mine"), "a fire mine is set");
				var walker = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				walker.snapTo(field.getX() + 0.5, field.getY(), field.getZ() + 4.5, 0f, 0f);
				walker.setNoAi(true);
				walker.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400, 0));
				level.addFreshEntity(walker);
				mineVictims[0] = walker;
				com.omnispell.spell.Spell waterBed = new com.omnispell.spell.Spell();
				waterBed.type = "exploration";
				waterBed.toggle = "mine";
				waterBed.element = "water";
				waterBed.tier = 2;
				waterBed.words.add("base:Water");
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(field.offset(5, -1, 0)));
				com.omnispell.spell.Mines.cast(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), waterBed), "mine");
				var pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
				pig.snapTo(field.getX() + 5.5, field.getY() + 16, field.getZ() + 0.5, 0f, 0f);
				level.addFreshEntity(pig);
				mineVictims[1] = pig;
				dropFrom[0] = pig.getHealth();
				com.omnispell.spell.Spell wireSpell = new com.omnispell.spell.Spell();
				wireSpell.type = "exploration";
				wireSpell.toggle = "tripwire";
				wireSpell.element = "wind";
				wireSpell.tier = 2;
				wireSpell.words.add("base:Thunder");
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(field.offset(-8, 0, 0)));
				com.omnispell.spell.Mines.cast(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), wireSpell), "tripwire");
				var crosser = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				crosser.snapTo(field.getX() - 3.5, field.getY(), field.getZ() + 0.5, 0f, 0f);
				crosser.setNoAi(true);
				level.addFreshEntity(crosser);
				mineVictims[2] = crosser;
			});
			context.waitTicks(70);
			server.runOnServer(s -> {
				var walker = mineVictims[0];
				check(walker.isRemoved() || walker.getHealth() < walker.getMaxHealth(), "the zombie that stepped on the fire mine was blasted (health " + walker.getHealth() + ")");
				var pig = mineVictims[1];
				check(pig.isAlive() && pig.getHealth() >= dropFrom[0] - 0.01f, "a pig dropped sixteen blocks onto a water balloon lands unhurt (health " + pig.getHealth() + " of " + dropFrom[0] + ")");
				var crosser = mineVictims[2];
				check(crosser.isRemoved() || crosser.getHealth() < crosser.getMaxHealth(), "the tripwire shocks the zombie standing across it (health " + crosser.getHealth() + ")");
				for (var e : mineVictims) if (!e.isRemoved()) e.discard();
				player(s).teleportTo(player(s).level(), cHome[0].x, cHome[0].y, cHome[0].z, java.util.Set.of(), 0f, 0f, true);
				// A wand named on an anvil: its name shows above the spell bar, clear of it.
				ItemStack named = WandItem.create("oak", "flint");
				named.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Brawler's Wand"));
				player(s).getInventory().setItem(player(s).getInventory().getSelectedSlot(), named);
			});
			context.waitTicks(10);
			context.takeScreenshot("36-wand-name");

			// ---- surging walls, crumbling earth, soul links, and a tenth of the harm for other players
			net.minecraft.world.entity.Mob[] surgeMobs = new net.minecraft.world.entity.Mob[6];
			double[] surgeStart = new double[6];
			java.util.List<net.minecraft.world.entity.Mob> surgeLook = new java.util.ArrayList<>();
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel sgLevel = p.level();
				BlockPos sgArena = BlockPos.containing(cHome[0]).offset(0, 0, -90);
				for (BlockPos pos : BlockPos.betweenClosed(sgArena.offset(-12, -1, -6), sgArena.offset(12, -1, 16))) sgLevel.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(sgArena.offset(-12, 0, -6), sgArena.offset(12, 6, 16))) sgLevel.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				// a stone wall twelve blocks out: the surge carries what it catches up to it, and no further
				for (BlockPos pos : BlockPos.betweenClosed(sgArena.offset(-6, 0, 12), sgArena.offset(6, 3, 12))) sgLevel.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
				p.teleportTo(sgLevel, sgArena.getX() + 0.5, sgArena.getY(), sgArena.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(sgArena.offset(0, 1, 8)));
				// two zombies and a tamed wolf in its path, and a cow off to the side for the crumbling earth
				for (int i = 0; i < 4; i++) {
					net.minecraft.world.entity.Mob sgA = i < 2 ? EntityTypes.ZOMBIE.create(sgLevel, EntitySpawnReason.COMMAND) : i == 2 ? EntityTypes.WOLF.create(sgLevel, EntitySpawnReason.COMMAND) : EntityTypes.COW.create(sgLevel, EntitySpawnReason.COMMAND);
					sgA.snapTo(sgArena.getX() + (i == 0 ? 0.5 : i == 1 ? 2.5 : i == 2 ? -1.5 : -9.5), sgArena.getY(), sgArena.getZ() + 3.5 + (i == 3 ? 0 : i), 0f, 0f);
					// They keep their AI (a mob without it skips movement altogether, so nothing could push it), but cannot walk off.
					sgA.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SLOWNESS, 600, 9));
					sgA.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
					if (i < 2) {
						sgA.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
						sgA.setHealth(200f);
					}
					if (sgA instanceof net.minecraft.world.entity.TamableAnimal sgPet) {
						sgPet.tame(p);
						sgPet.setOrderedToSit(true);
						sgPet.setInSittingPose(true);
					}
					sgLevel.addFreshEntity(sgA);
					surgeMobs[i] = sgA;
					surgeStart[i] = sgA.getZ();
				}
				com.omnispell.spell.Spell sgSpell = new com.omnispell.spell.Spell();
				sgSpell.type = "attack";
				sgSpell.tier = 3;
				sgSpell.code.type = "damage";
				sgSpell.code.element = "water";
				sgSpell.code.primaryValue = 20;
				sgSpell.words.addAll(java.util.List.of("base:Water", "command:Throw", "attribute:Wall"));
				check(com.omnispell.data.WordBank.attributeFits("attack", com.omnispell.data.WordBank.word("attribute:Wall")) && com.omnispell.spell.Surges.wallAttack(sgSpell), "Wall fits an attack spell, and makes it a surging wall");
				check(com.omnispell.spell.CombatEffects.cast(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), sgSpell)), "a Water + Throw + Wall attack rolls out");
			});
			context.waitTicks(45);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel sgLevel = p.level();
				BlockPos sgArena = BlockPos.containing(cHome[0]).offset(0, 0, -90);
				var sgA = surgeMobs[0];
				var sgB = surgeMobs[1];
				var sgPet = surgeMobs[2];
				check(sgA.getZ() > surgeStart[0] + 4 && sgB.getZ() > surgeStart[1] + 3, "the wall of water carries both zombies along (" + String.format("%.1f", sgA.getZ() - surgeStart[0]) + " and " + String.format("%.1f", sgB.getZ() - surgeStart[1]) + " blocks)");
				check(sgA.getZ() < sgArena.getZ() + 12 && sgB.getZ() < sgArena.getZ() + 12 && sgLevel.getBlockState(sgArena.offset(0, 1, 12)).is(Blocks.STONE), "it passes through the stone wall, but they stop against it (" + String.format("%.1f", sgA.getZ() - sgArena.getZ()) + ")");
				check(sgA.getHealth() < 200f && sgB.getHealth() < 200f, "each zombie it sweeps up is struck (" + sgA.getHealth() + ", " + sgB.getHealth() + ")");
				check(Math.abs(sgPet.getZ() - surgeStart[2]) < 0.3 && sgPet.getHealth() >= sgPet.getMaxHealth(), "the tamed wolf in its path is left alone (moved " + String.format("%.2f", sgPet.getZ() - surgeStart[2]) + ")");
				sgA.discard();
				sgB.discard();
				sgPet.discard();
				// A defense: Air + Repel + Wall drives hostile creatures back and leaves the cow be.
				var sgFoe = EntityTypes.ZOMBIE.create(sgLevel, EntitySpawnReason.COMMAND);
				sgFoe.snapTo(sgArena.getX() + 0.5, sgArena.getY(), sgArena.getZ() + 3.5, 0f, 0f);
				sgFoe.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SLOWNESS, 600, 9));
				sgFoe.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
				sgLevel.addFreshEntity(sgFoe);
				var sgCow = EntityTypes.COW.create(sgLevel, EntitySpawnReason.COMMAND);
				sgCow.snapTo(sgArena.getX() + 2.5, sgArena.getY(), sgArena.getZ() + 3.5, 0f, 0f);
				sgCow.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SLOWNESS, 600, 9));
				sgLevel.addFreshEntity(sgCow);
				surgeMobs[4] = sgFoe;
				surgeMobs[5] = sgCow;
				surgeStart[4] = sgFoe.getZ();
				surgeStart[5] = sgCow.getZ();
				com.omnispell.spell.Spell sgDef = new com.omnispell.spell.Spell();
				sgDef.id = "defense|test-surge";
				sgDef.type = "defense";
				sgDef.tier = 3;
				sgDef.code.type = "shield";
				sgDef.code.element = "wind";
				sgDef.words.addAll(java.util.List.of("base:Air", "command:Repel", "attribute:Wall"));
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.BARRIER.ordinal()] = 100;
				check("surge".equals(com.omnispell.spell.Defenses.form(sgDef)) && com.omnispell.spell.Defenses.describe(sgDef).contains("wall of wind"), "Air + Repel + Wall is a surging wall: " + com.omnispell.spell.Defenses.describe(sgDef));
				check(com.omnispell.spell.Defenses.cast(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), sgDef), 3), "the surging wall of wind is cast");
				com.omnispell.spell.Spell sgWall = new com.omnispell.spell.Spell();
				sgWall.type = "defense";
				sgWall.code.element = "stone";
				sgWall.words.addAll(java.util.List.of("base:Earth", "command:Shield", "attribute:Wall"));
				check("barrier".equals(com.omnispell.spell.Defenses.form(sgWall)), "a Wall with a shielding command still raises a plain barrier");
				// Crumble: an Earth + Disintegrate attack swallows its target into the ground for a moment.
				com.omnispell.spell.Spell sgDust = new com.omnispell.spell.Spell();
				sgDust.type = "attack";
				sgDust.tier = 3;
				sgDust.code.element = "earth";
				sgDust.words.addAll(java.util.List.of("base:Earth", "command:Disintegrate", "intention:Malice"));
				check("crumble".equals(com.omnispell.spell.AttackForms.commandForm(sgDust)) && com.omnispell.spell.AttackForms.describe(sgDust).contains("sand"), "Earth + Disintegrate crumbles the ground under its target: " + com.omnispell.spell.AttackForms.describe(sgDust));
				var crumbleCow = surgeMobs[3];
				surgeStart[3] = crumbleCow.getY();
				com.omnispell.spell.AttackForms.manifest(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), sgDust), crumbleCow, "crumble", "stone", 10);
				// A tenth for other players: only attack spells, and never villagers or animals.
				net.fabricmc.fabric.api.entity.FakePlayer sgFriend = net.fabricmc.fabric.api.entity.FakePlayer.get(sgLevel);
				com.omnispell.spell.Cast sgCast = new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), sgDust);
				var sgVillager = EntityTypes.VILLAGER.create(sgLevel, EntitySpawnReason.COMMAND);
				check(Math.abs(com.omnispell.spell.CombatEffects.versusPlayers(sgCast, sgFriend) - 0.1f) < 1e-6f && com.omnispell.spell.CombatEffects.versusPlayers(sgCast, sgCow) == 1f
						&& com.omnispell.spell.CombatEffects.versusPlayers(sgCast, sgVillager) == 1f && com.omnispell.spell.CombatEffects.versusPlayers(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), sgDef), sgFriend) == 1f,
						"attack spells hurt other players at a tenth; villagers, animals and other kinds of spell are untouched");
				var sgArrow = new net.minecraft.world.entity.projectile.arrow.Arrow(sgLevel, p, new ItemStack(Items.ARROW), null);
				sgArrow.addTag("omnispell_magic_arrow");
				float sgShot = com.omnispell.spell.Modifiers.modifyIncoming(sgFriend, sgLevel.damageSources().arrow(sgArrow, p), 10f);
				check(Math.abs(sgShot - 1f) < 0.01f, "a Bow spell's arrow hurts another player at a tenth (10 became " + sgShot + ")");
				// Soul link: a share of the harm your creature deals comes back to you as health.
				p.setHealth(10f);
				com.omnispell.spell.Modifiers.add(p, "soul_link", 50, 200);
				com.omnispell.spell.Conjuring.soulLink(p, 8f);
				check(Math.abs(p.getHealth() - 14f) < 0.01f, "soul link heals you for half of what your creature dealt (10 became " + p.getHealth() + ")");
				com.omnispell.spell.Modifiers.add(p, "soul_link", 0, 1);
				p.setHealth(p.getMaxHealth());
			});
			context.waitTicks(40);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				BlockPos sgArena = BlockPos.containing(cHome[0]).offset(0, 0, -90);
				var sgFoe = surgeMobs[4];
				var sgCow = surgeMobs[5];
				check(sgFoe.getZ() > surgeStart[4] + 3, "the wall of wind drives the zombie back (" + String.format("%.1f", sgFoe.getZ() - surgeStart[4]) + " blocks)");
				check(Math.abs(sgCow.getZ() - surgeStart[5]) < 0.3, "it leaves the cow be (moved " + String.format("%.2f", sgCow.getZ() - surgeStart[5]) + ")");
				var crumbleCow = surgeMobs[3];
				check(crumbleCow.isAlive() && crumbleCow.getY() < surgeStart[3] - 0.4, "the cow sinks into the crumbling ground (down " + String.format("%.2f", surgeStart[3] - crumbleCow.getY()) + ")");
				for (var m : surgeMobs) if (m != null && !m.isRemoved()) m.discard();
				// A close look at the markings conjured creatures wear: flame, storm, frost and stone, side by side.
				ServerLevel sgLevel = p.level();
				String[][] sgLook = {{"fire", "attribute:Fox", "Ember Fox"}, {"storm", "attribute:Wolf", "Storm Hound"}, {"ice", "attribute:Bear", "Frost Bear"}, {"stone", "", "Stone Golem"}};
				net.minecraft.world.entity.EntityType<?>[] sgTypes = {EntityTypes.FOX, EntityTypes.WOLF, EntityTypes.POLAR_BEAR, EntityTypes.IRON_GOLEM};
				p.teleportTo(sgLevel, sgArena.getX() + 0.5, sgArena.getY(), sgArena.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
				for (int i = 0; i < sgLook.length; i++) {
					com.omnispell.spell.Spell sgBeast = new com.omnispell.spell.Spell();
					sgBeast.type = "conjuration";
					sgBeast.tier = 3;
					sgBeast.code.element = sgLook[i][0];
					if (!sgLook[i][1].isEmpty()) sgBeast.words.add(sgLook[i][1]);
					sgBeast.name = sgLook[i][2];
					com.omnispell.spell.Conjuring.spawn(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), sgBeast), sgLook[i][2], 10, 20);
					final int sgK = i;
					for (var sgM : sgLevel.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(16), m -> m.getType() == sgTypes[sgK] && !m.isNoAi())) {
						sgM.setNoAi(true);
						sgM.snapTo(sgArena.getX() + 0.5 - 2.85 + 1.9 * sgK, sgArena.getY(), sgArena.getZ() + 5.5, 180f, 0f);
						sgM.setYHeadRot(180f);
						surgeLook.add(sgM);
					}
				}
				check(surgeLook.size() >= 4, "four conjured creatures stand for a look at their markings (" + surgeLook.size() + ")");
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, new Vec3(sgArena.getX() + 0.5, sgArena.getY() + 0.9, sgArena.getZ() + 5.5));
			});
			context.waitTicks(12);
			context.takeScreenshot("38-element-markings");
			server.runOnServer(s -> {
				surgeLook.forEach(net.minecraft.world.entity.Entity::discard);
				player(s).teleportTo(player(s).level(), cHome[0].x, cHome[0].y, cHome[0].z, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(5);

			// ---- elements from the base word, constructs, the cleansing veil, and invisibility that shakes off pursuit
			net.minecraft.world.entity.Mob[] cageMobs = new net.minecraft.world.entity.Mob[2];
			float[] cageHealth = new float[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel cgLevel = p.level();
				BlockPos cgArena = BlockPos.containing(cHome[0]).offset(0, 0, -90);
				for (BlockPos pos : BlockPos.betweenClosed(cgArena.offset(-12, 0, -6), cgArena.offset(12, 8, 16))) cgLevel.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				p.teleportTo(cgLevel, cgArena.getX() + 0.5, cgArena.getY(), cgArena.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
				p.removeAllEffects();
				// The base word decides the element, whatever the Oracle labelled it.
				String[][] cgLabels = {{"base:Water", "ice", "water"}, {"base:Earth", "nature", "stone"}, {"base:Thunder", "arcane", "storm"}, {"base:Wave", "nature", "water"}, {"base:Memory", "arcane", "arcane"}};
				for (String[] row : cgLabels) {
					com.omnispell.spell.Spell cgSpell = new com.omnispell.spell.Spell();
					cgSpell.type = "attack";
					cgSpell.code.element = row[1];
					cgSpell.words.add(row[0]);
					check(row[2].equals(com.omnispell.spell.AttackForms.element(cgSpell)), row[0] + " labelled " + row[1] + " by the Oracle works as " + row[2] + " (" + com.omnispell.spell.AttackForms.element(cgSpell) + ")");
				}
				com.omnispell.spell.Spell cgRock = new com.omnispell.spell.Spell();
				cgRock.type = "attack";
				cgRock.tier = 4;
				cgRock.code.element = "nature";
				cgRock.words.addAll(java.util.List.of("base:Earth", "command:Envelop", "intention:Malice"));
				var cgCow = EntityTypes.COW.create(cgLevel, EntitySpawnReason.COMMAND);
				cgCow.snapTo(cgArena.getX() + 8.5, cgArena.getY(), cgArena.getZ() + 0.5, 0f, 0f);
				cgLevel.addFreshEntity(cgCow);
				for (int i = 0; i < 6; i++) {
					cgCow.setInvulnerableTime(0);
					com.omnispell.spell.CombatEffects.strike(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), cgRock), cgCow, 1, false);
				}
				check(!cgCow.hasEffect(MobEffects.POISON), "crushing rock never poisons");
				cgCow.discard();
				// Creature words no longer go in attack spells.
				check(!com.omnispell.data.WordBank.attributeFits("attack", com.omnispell.data.WordBank.word("attribute:Snake")) && com.omnispell.data.WordBank.attributeFits("conjuration", com.omnispell.data.WordBank.word("attribute:Snake")),
						"a creature word is refused in an attack spell and welcome in a conjuration");
				// Earth + Envelop + Malice, with no creature in it, conjures a cage on what you look at.
				com.omnispell.spell.Spell cgCage = new com.omnispell.spell.Spell();
				cgCage.type = "conjuration";
				cgCage.tier = 2;
				cgCage.code.element = "nature";
				cgCage.name = "Violet Enveloper";
				cgCage.description = "Summons a spiteful violet earth elemental to constrict and batter your opponent. It comes as a spirit. It bites what is close and casts the spell's magic at what is not.";
				cgCage.words.addAll(java.util.List.of("base:Earth", "command:Envelop", "intention:Malice", "attribute:Violet"));
				check("cage".equals(com.omnispell.spell.Masonry.structure(cgCage)), "Earth + Envelop + Malice conjures a cage, not a creature (" + com.omnispell.spell.Masonry.structure(cgCage) + ")");
				com.omnispell.spell.SpellCrafting.refresh(cgCage);
				check(cgCage.description.contains("barred cage") && !cgCage.description.contains("spirit"), "an older spell's description is brought up to date: " + cgCage.description);
				var cgFoe = EntityTypes.ZOMBIE.create(cgLevel, EntitySpawnReason.COMMAND);
				cgFoe.snapTo(cgArena.getX() + 0.5, cgArena.getY(), cgArena.getZ() + 5.5, 180f, 0f);
				cgFoe.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SLOWNESS, 600, 9));
				cgFoe.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
				cgLevel.addFreshEntity(cgFoe);
				cageMobs[0] = cgFoe;
				cageHealth[0] = cgFoe.getHealth();
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, cgFoe.getBoundingBox().getCenter());
				check(com.omnispell.spell.Conjuring.spawn(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), cgCage), "Violet Enveloper", 10, 20), "the cage is conjured");
				int cgBars = 0;
				for (BlockPos pos : BlockPos.betweenClosed(cgArena.offset(-2, 0, 3), cgArena.offset(2, 4, 7))) if (cgLevel.getBlockState(pos).is(Blocks.IRON_BARS)) cgBars++;
				check(cgBars >= 8, "bars close around the zombie (" + cgBars + ")");
				// With a gentler intention the same words raise a shelter instead; with a creature named, a creature.
				com.omnispell.spell.Spell cgHut = new com.omnispell.spell.Spell();
				cgHut.type = "conjuration";
				cgHut.code.element = "ice";
				cgHut.words.addAll(java.util.List.of("base:Ice", "command:Envelop", "intention:Creation"));
				com.omnispell.spell.Spell cgBeast = new com.omnispell.spell.Spell();
				cgBeast.type = "conjuration";
				cgBeast.code.element = "arcane";
				cgBeast.name = "Crimson_Stormtalon";
				cgBeast.description = "Summons a malevolent crimson thunder-beast that lacerates enemies with crackling talons.";
				cgBeast.words.addAll(java.util.List.of("base:Thunder", "command:Sever", "intention:Malice", "attribute:Red"));
				check("shelter".equals(com.omnispell.spell.Masonry.structure(cgHut)) && com.omnispell.spell.Masonry.structure(cgBeast) == null
						&& "eagle".equals(com.omnispell.spell.Conjuring.bodyFor(cgBeast, "Crimson_Stormtalon").id()) && com.omnispell.spell.Conjuring.sonic(cgBeast),
						"Ice + Envelop + Creation is a shelter; the Crimson Stormtalon is an eagle that strikes with sound (" + com.omnispell.spell.Conjuring.bodyFor(cgBeast, "Crimson_Stormtalon").id() + ")");
				com.omnispell.spell.Spell cgStone = new com.omnispell.spell.Spell();
				cgStone.type = "conjuration";
				cgStone.code.element = "nature";
				cgStone.words.addAll(java.util.List.of("base:Earth", "command:Summon", "intention:Creation"));
				var cgBody = com.omnispell.spell.Conjuring.bodyFor(cgStone, null);
				check("golem".equals(cgBody.id()) && !cgBody.flies(), "an earth elemental is a golem, and walks");
				// The cleansing veil: Light + Disenchant + Concealment strips every effect and hides the one it cleans.
				com.omnispell.spell.Spell cgClean = new com.omnispell.spell.Spell();
				cgClean.id = "defense|test-cleanse";
				cgClean.type = "defense";
				cgClean.tier = 1;
				cgClean.code.type = "shield";
				cgClean.code.element = "light";
				cgClean.words.addAll(java.util.List.of("base:Light", "command:Disenchant", "intention:Concealment"));
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, new Vec3(p.getX() - 10, p.getY() + 30, p.getZ() - 10));
				p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 400, 0));
				p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.STRENGTH, 400, 0));
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.BARRIER.ordinal()] = 100;
				check("cleanse".equals(com.omnispell.spell.Defenses.form(cgClean)) && com.omnispell.spell.Defenses.cast(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), cgClean), 2), "Light + Disenchant + Concealment is cast");
				check(!p.hasEffect(MobEffects.POISON) && !p.hasEffect(MobEffects.STRENGTH) && p.hasEffect(MobEffects.INVISIBILITY), "it strips poison and strength alike and leaves you invisible");
				// Invisible: a zombie hunting you loses you, until you strike it.
				var cgHunter = EntityTypes.ZOMBIE.create(cgLevel, EntitySpawnReason.COMMAND);
				cgHunter.snapTo(cgArena.getX() - 8.5, cgArena.getY(), cgArena.getZ() + 0.5, 0f, 0f);
				cgHunter.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SLOWNESS, 600, 9));
				cgHunter.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
				cgLevel.addFreshEntity(cgHunter);
				cgHunter.setTarget(p);
				cageMobs[1] = cgHunter;
				com.omnispell.spell.Shroud.tick(s);
				check(cgHunter.getTarget() == null && p.getVisibilityPercent(cgLevel, cgHunter) == 0.0, "the zombie hunting you loses track of you when you turn invisible");
			});
			context.waitTicks(50);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				var cgFoe = cageMobs[0];
				var cgHunter = cageMobs[1];
				check(cgFoe.isRemoved() || cgFoe.getHealth() < cageHealth[0], "the caged zombie is smothered (" + cgFoe.getHealth() + " of " + cageHealth[0] + ")");
				check(cgHunter.getTarget() == null, "and it stays lost while you are unseen");
				p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.INVISIBILITY, 200, 0));
				cgHunter.setInvulnerableTime(0);
				boolean cgHit = cgHunter.hurtServer(p.level(), p.damageSources().playerAttack(p), 4f);
				check(!com.omnispell.spell.Shroud.hiddenFrom(p, cgHunter) && com.omnispell.spell.Shroud.hiddenFrom(p, EntityTypes.ZOMBIE.create(p.level(), EntitySpawnReason.COMMAND)), "strike it while invisible and it alone knows where you are (hit " + cgHit + ", invisible " + p.hasEffect(MobEffects.INVISIBILITY) + ", alive " + cgHunter.isAlive() + ")");
				for (var m : cageMobs) if (m != null && !m.isRemoved()) m.discard();
				p.removeAllEffects();
				p.setHealth(p.getMaxHealth());
				BlockPos cgArena = BlockPos.containing(cHome[0]).offset(0, 0, -90);
				for (BlockPos pos : BlockPos.betweenClosed(cgArena.offset(-3, 0, 2), cgArena.offset(3, 5, 8))) com.omnispell.spell.Masonry.breakBlock(p.level(), pos.immutable());
				p.teleportTo(p.level(), cHome[0].x, cHome[0].y, cHome[0].z, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(5);

			// ---- the forest portal: a frame of mossy stone, woken with an ender pearl, walked through both ways
			BlockPos[] fpOrigin = new BlockPos[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel fpLevel = p.level();
				BlockPos fpAt = BlockPos.containing(cHome[0]).offset(30, 0, -90);
				for (BlockPos pos : BlockPos.betweenClosed(fpAt.offset(-4, -1, -4), fpAt.offset(5, -1, 4))) fpLevel.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(fpAt.offset(-4, 0, -4), fpAt.offset(5, 6, 4))) fpLevel.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				// four wide, five tall, no corners, of mixed mossy stone, sunk one into the floor like a Nether portal
				BlockPos fpIn = fpAt; // the inside's lower corner
				for (int a = 0; a <= 1; a++) {
					fpLevel.setBlockAndUpdate(fpIn.offset(a, -1, 0), Blocks.MOSS_BLOCK.defaultBlockState());
					fpLevel.setBlockAndUpdate(fpIn.offset(a, 3, 0), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
				}
				for (int h = 0; h <= 2; h++) {
					fpLevel.setBlockAndUpdate(fpIn.offset(-1, h, 0), Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
					fpLevel.setBlockAndUpdate(fpIn.offset(2, h, 0), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
				}
				fpOrigin[0] = fpIn;
				p.teleportTo(fpLevel, fpAt.getX() + 1.0, fpAt.getY(), fpAt.getZ() + 3.5, java.util.Set.of(), 180f, 0f, true);
				fpLevel.setBlockAndUpdate(fpIn.offset(2, 1, 0), Blocks.STONE.defaultBlockState());
				check(!com.omnispell.realm.ForestPortalBlock.light(fpLevel, fpIn.offset(-1, 1, 0)), "a frame with a plain stone in it does not wake");
				fpLevel.setBlockAndUpdate(fpIn.offset(2, 1, 0), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
				check(com.omnispell.realm.ForestPortalBlock.light(fpLevel, fpIn.offset(-1, 1, 0)), "a whole frame of mossy stone wakes");
				int fpLit = 0;
				for (int a = 0; a <= 1; a++) for (int h = 0; h <= 2; h++) if (fpLevel.getBlockState(fpIn.offset(a, h, 0)).is(OmniItems.FOREST_PORTAL)) fpLit++;
				check(fpLit == 6, "the inside fills with portal (" + fpLit + " of 6)");
			});
			context.waitTicks(10);
			context.takeScreenshot("39-forest-portal");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel fpLevel = p.level();
				BlockPos fpIn = fpOrigin[0];
				fpLevel.destroyBlock(fpIn.offset(0, 3, 0), false);
			});
			context.waitTicks(5);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel fpLevel = p.level();
				BlockPos fpIn = fpOrigin[0];
				check(fpLevel.getBlockState(fpIn).isAir() && fpLevel.getBlockState(fpIn.offset(1, 2, 0)).isAir(), "break the frame and the portal goes out");
				fpLevel.setBlockAndUpdate(fpIn.offset(0, 3, 0), Blocks.MOSS_BLOCK.defaultBlockState());
				check(com.omnispell.realm.ForestPortalBlock.light(fpLevel, fpIn.offset(0, 3, 0)), "mended, it wakes again");
				p.teleportTo(fpLevel, fpIn.getX() + 0.5, fpIn.getY(), fpIn.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(60);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				check(com.omnispell.realm.Realms.isForest(p.level()), "walking into it carries you to the Enchanted Forest");
				check(p.level().getBlockState(p.blockPosition()).is(OmniItems.FOREST_PORTAL), "a portal back stands where you arrive");
				p.teleportTo(p.level(), p.getX() + 0.0, p.getY(), p.getZ() + 3.0, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(40);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.teleportTo(p.level(), p.getX(), p.getY(), p.getZ() - 3.0, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(60);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				check(p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD && p.blockPosition().distManhattan(fpOrigin[0]) <= 3, "walking into that one brings you back to the first (" + p.level().dimension().identifier() + ")");
				p.teleportTo(s.overworld(), cHome[0].x, cHome[0].y, cHome[0].z, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(20);

			// ---- the Pet Shell: holds one tamed animal, whole, and lets it out again
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel psLevel = p.level();
				BlockPos psAt = p.blockPosition();
				ItemStack psKept = p.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).copy();
				p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(OmniItems.PET_SHELL));
				var psWild = EntityTypes.WOLF.create(psLevel, EntitySpawnReason.COMMAND);
				psWild.snapTo(psAt.getX() + 2.5, psAt.getY(), psAt.getZ() + 0.5, 0f, 0f);
				psLevel.addFreshEntity(psWild);
				com.omnispell.item.PetShellItem.capture(p, net.minecraft.world.InteractionHand.MAIN_HAND, psWild);
				check(psWild.isAlive() && !com.omnispell.item.PetShellItem.full(p.getMainHandItem()), "a wild wolf will not go into a Pet Shell");
				var psCow = EntityTypes.COW.create(psLevel, EntitySpawnReason.COMMAND);
				check(!com.omnispell.item.PetShellItem.tamedBy(p, psCow), "nor will a cow");
				psWild.tame(p);
				psWild.setCustomName(net.minecraft.network.chat.Component.literal("Biscuit"));
				psWild.setHealth(11f);
				java.util.UUID psId = psWild.getUUID();
				com.omnispell.item.PetShellItem.capture(p, net.minecraft.world.InteractionHand.MAIN_HAND, psWild);
				ItemStack psShell = p.getMainHandItem();
				check(psWild.isRemoved() && com.omnispell.item.PetShellItem.full(psShell) && psShell.getHoverName().getString().equals("Shell of Biscuit"), "a tamed wolf goes in, and the shell takes its name (" + psShell.getHoverName().getString() + ")");
				var psOther = EntityTypes.CAT.create(psLevel, EntitySpawnReason.COMMAND);
				psOther.snapTo(psAt.getX() + 2.5, psAt.getY(), psAt.getZ() + 2.5, 0f, 0f);
				psLevel.addFreshEntity(psOther);
				psOther.tame(p);
				com.omnispell.item.PetShellItem.capture(p, net.minecraft.world.InteractionHand.MAIN_HAND, psOther);
				check(psOther.isAlive(), "a full shell takes no second pet");
				psOther.discard();
				var psHit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(psAt.below()).add(2, 0.5, 0), net.minecraft.core.Direction.UP, psAt.below().offset(2, 0, 0), false);
				psShell.useOn(new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, psHit));
				var psBack = psLevel.getEntity(psId);
				check(psBack instanceof net.minecraft.world.entity.animal.wolf.Wolf w && w.isTame() && w.isOwnedBy(p) && Math.abs(w.getHealth() - 11f) < 0.01f && "Biscuit".equals(w.getName().getString()),
						"let out, it is the same wolf: tame, yours, named, at the health it had");
				check(!com.omnispell.item.PetShellItem.full(p.getMainHandItem()) && p.getMainHandItem().is(OmniItems.PET_SHELL) && p.getMainHandItem().getHoverName().getString().equals("Pet Shell"), "and the shell is empty and plain again");
				if (psBack != null) psBack.discard();
				p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, psKept);
			});
			context.waitTicks(5);

			// ---- conjured masonry fades completely: deck, rails (walls that reshape to join their neighbours) and lanterns
			BlockPos[] fadeAt = new BlockPos[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel fdLevel = p.level();
				BlockPos fdAt = BlockPos.containing(cHome[0]).offset(-30, 0, -90);
				for (BlockPos pos : BlockPos.betweenClosed(fdAt.offset(-4, -3, -24), fdAt.offset(4, -3, 2))) fdLevel.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(fdAt.offset(-4, -2, -24), fdAt.offset(4, 6, 2))) fdLevel.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				fdLevel.setBlockAndUpdate(fdAt.below(), Blocks.STONE.defaultBlockState());
				p.teleportTo(fdLevel, fdAt.getX() + 0.5, fdAt.getY(), fdAt.getZ() + 0.5, java.util.Set.of(), 180f, 0f, true);
				com.omnispell.spell.Spell fdBridge = new com.omnispell.spell.Spell();
				fdBridge.type = "conjuration";
				fdBridge.tier = 1;
				fdBridge.code.element = "stone";
				fdBridge.words.add("attribute:Bridge");
				com.omnispell.spell.Masonry.lifeOverride = 60;
				check(com.omnispell.spell.Conjuring.spawn(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), fdBridge), "Bridge", 8, 20), "a short-lived bridge is conjured");
				com.omnispell.spell.Masonry.lifeOverride = 0;
				int fdWalls = 0;
				for (BlockPos pos : BlockPos.betweenClosed(fdAt.offset(-4, -2, -24), fdAt.offset(4, 4, 0))) if (fdLevel.getBlockState(pos).is(Blocks.STONE_BRICK_WALL)) fdWalls++;
				check(fdWalls >= 10, "it has railings of stone brick wall (" + fdWalls + ")");
				fadeAt[0] = fdAt;
			});
			context.waitTicks(80);
			server.runOnServer(s -> {
				ServerLevel fdLevel = player(s).level();
				BlockPos fdAt = fadeAt[0];
				int fdLeft = 0;
				for (BlockPos pos : BlockPos.betweenClosed(fdAt.offset(-4, -2, -24), fdAt.offset(4, 4, 0))) {
					var st = fdLevel.getBlockState(pos);
					if (st.is(Blocks.STONE_BRICK_WALL) || st.is(Blocks.STONE_BRICKS) || st.is(Blocks.LANTERN) || st.getBlock() instanceof net.minecraft.world.level.block.StainedGlassBlock) fdLeft++;
				}
				check(fdLeft == 0, "when its time is up, all of it is gone, railings and lanterns too (" + fdLeft + " left)");
				// A conjured thunderbird is a parrot underneath, but it never settles on its caster's shoulder.
				ServerPlayer p = player(s);
				com.omnispell.spell.Spell fdBird = new com.omnispell.spell.Spell();
				fdBird.type = "conjuration";
				fdBird.tier = 3;
				fdBird.code.element = "arcane";
				fdBird.name = "Crimson_Stormtalon";
				fdBird.words.addAll(java.util.List.of("base:Thunder", "command:Sever", "intention:Malice", "attribute:Red"));
				com.omnispell.spell.Conjuring.spawn(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), fdBird), "Crimson_Stormtalon", 10, 20);
				var fdParrots = fdLevel.getEntitiesOfClass(net.minecraft.world.entity.animal.parrot.Parrot.class, p.getBoundingBox().inflate(16));
				check(!fdParrots.isEmpty() && fdParrots.stream().allMatch(bird -> ((com.omnispell.mixin.MobGoalsAccessor) bird).omnispell$goalSelector().getAvailableGoals().stream()
						.noneMatch(g -> g.getGoal() instanceof net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal)), "a conjured bird never perches on its caster's shoulder (" + fdParrots.size() + " birds)");
				fdParrots.forEach(net.minecraft.world.entity.Entity::discard);
				player(s).teleportTo(fdLevel, cHome[0].x, cHome[0].y, cHome[0].z, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(5);

			// ---- a jockey: a weapon word with a creature word calls the warrior riding the creature
			java.util.List<net.minecraft.world.entity.Entity> jkMobs = new java.util.ArrayList<>();
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel jkLevel = p.level();
				BlockPos jkAt = BlockPos.containing(cHome[0]).offset(-30, 0, -90);
				for (BlockPos pos : BlockPos.betweenClosed(jkAt.offset(-5, -1, -8), jkAt.offset(5, -1, 3))) jkLevel.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(jkAt.offset(-5, 0, -8), jkAt.offset(5, 5, 3))) jkLevel.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				p.teleportTo(jkLevel, jkAt.getX() + 0.5, jkAt.getY(), jkAt.getZ() + 0.5, java.util.Set.of(), 180f, 10f, true);
				com.omnispell.spell.Spell jkSpell = new com.omnispell.spell.Spell();
				jkSpell.type = "conjuration";
				jkSpell.tier = 3;
				jkSpell.code.element = "shadow";
				jkSpell.words.addAll(java.util.List.of("base:Shadow", "command:Summon", "intention:Malice", "attribute:Cat", "attribute:Bow"));
				com.omnispell.spell.Spell jkPlain = new com.omnispell.spell.Spell();
				jkPlain.type = "conjuration";
				jkPlain.tier = 3;
				jkPlain.words.addAll(java.util.List.of("base:Shadow", "command:Summon", "intention:Malice", "attribute:Bow"));
				check(com.omnispell.spell.Conjuring.mountFor(jkSpell) != null && com.omnispell.spell.Casting.manaCost(jkSpell) > com.omnispell.spell.Casting.manaCost(jkPlain) * 1.4,
						"Cat + Bow is a jockey, and costs more than an archer alone (" + com.omnispell.spell.Casting.manaCost(jkSpell) + " vs " + com.omnispell.spell.Casting.manaCost(jkPlain) + ")");
				check(com.omnispell.spell.Conjuring.describe(jkSpell, "Shadow Rider").contains("riding"), "the book says it rides: " + com.omnispell.spell.Conjuring.describe(jkSpell, "Shadow Rider"));
				// A flyer cannot carry a warrior: Bird + Bow is a bird that looses arrows, Snake + Sword a serpent with claws.
				com.omnispell.spell.Spell jkHawk = new com.omnispell.spell.Spell();
				jkHawk.type = "conjuration";
				jkHawk.tier = 3;
				jkHawk.words.addAll(java.util.List.of("base:Air", "command:Summon", "intention:Malice", "attribute:Bird", "attribute:Bow"));
				com.omnispell.spell.Spell jkWyrm = new com.omnispell.spell.Spell();
				jkWyrm.type = "conjuration";
				jkWyrm.tier = 3;
				jkWyrm.words.addAll(java.util.List.of("base:Water", "command:Summon", "intention:Malice", "attribute:Snake", "attribute:Sword"));
				check("bird".equals(com.omnispell.spell.Conjuring.bodyFor(jkHawk, null).id()) && "bow".equals(com.omnispell.spell.Conjuring.gift(jkHawk)) && com.omnispell.spell.Conjuring.mountFor(jkHawk) == null
						&& "serpent".equals(com.omnispell.spell.Conjuring.bodyFor(jkWyrm, null).id()) && "sword".equals(com.omnispell.spell.Conjuring.gift(jkWyrm))
						&& com.omnispell.spell.Conjuring.describe(jkWyrm, "Tide Wyrm").contains("claws"),
						"Bird + Bow is a bird that looses arrows; Snake + Sword a serpent with slashing claws: " + com.omnispell.spell.Conjuring.describe(jkWyrm, "Tide Wyrm"));
				check(com.omnispell.spell.Conjuring.spawn(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), jkSpell), "Shadow Rider", 10, 20), "the jockey is conjured");
				var jkRiders = jkLevel.getEntitiesOfClass(net.minecraft.world.entity.monster.skeleton.AbstractSkeleton.class, p.getBoundingBox().inflate(10));
				check(!jkRiders.isEmpty() && jkRiders.get(0).getVehicle() instanceof net.minecraft.world.entity.animal.feline.Ocelot,
						"an archer comes riding a great cat (" + (jkRiders.isEmpty() ? "no archer" : String.valueOf(jkRiders.get(0).getVehicle())) + ")");
				for (var r : jkRiders) {
					jkMobs.add(r);
					if (r.getVehicle() != null) jkMobs.add(r.getVehicle());
				}
			});
			context.waitTicks(15);
			context.takeScreenshot("40-jockey");
			server.runOnServer(s -> {
				for (var e : jkMobs) if (!e.isRemoved()) e.discard();
				player(s).teleportTo(player(s).level(), cHome[0].x, cHome[0].y, cHome[0].z, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(5);

			// ---- a boss walled off underground cannot reach a player standing above it
			net.minecraft.world.entity.Mob[] bsBoss = new net.minecraft.world.entity.Mob[1];
			float[] bsHealth = new float[1];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel bsLevel = p.level();
				BlockPos bsAt = BlockPos.containing(cHome[0]).offset(60, 0, -90);
				for (BlockPos pos : BlockPos.betweenClosed(bsAt.offset(-6, -12, -6), bsAt.offset(6, -1, 6))) bsLevel.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(bsAt.offset(-6, 0, -6), bsAt.offset(6, 5, 6))) bsLevel.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(bsAt.offset(-2, -9, -2), bsAt.offset(2, -6, 2))) bsLevel.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); // a sealed chamber below
				p.teleportTo(bsLevel, bsAt.getX() + 0.5, bsAt.getY(), bsAt.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
				p.setHealth(p.getMaxHealth());
				p.removeAllEffects();
				bsBoss[0] = com.omnispell.boss.Bosses.spawn(bsLevel, Vec3.atBottomCenterOf(bsAt.offset(0, -9, 0)), com.omnispell.boss.BossDef.ALL.get("frostweaver_arachne"));
				bsBoss[0].setNoAi(true);
				bsHealth[0] = p.getHealth();
			});
			context.waitTicks(400);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				check(p.getHealth() >= bsHealth[0] && bsBoss[0].getTarget() == null && !p.hasEffect(MobEffects.SLOWNESS),
						"a boss sealed underground leaves the player above alone (health " + p.getHealth() + ", target " + bsBoss[0].getTarget() + ")");
				bsBoss[0].discard();
				p.teleportTo(p.level(), cHome[0].x, cHome[0].y, cHome[0].z, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(5);

			// ---- a colour word conjures a being of that colour; the shelter's doorway is hardened air you crouch through
			java.util.List<net.minecraft.world.entity.Entity> hkMobs = new java.util.ArrayList<>();
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel hkLevel = p.level();
				BlockPos hkAt = BlockPos.containing(cHome[0]).offset(-60, 0, -90);
				for (BlockPos pos : BlockPos.betweenClosed(hkAt.offset(-6, -1, -8), hkAt.offset(6, -1, 4))) hkLevel.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
				for (BlockPos pos : BlockPos.betweenClosed(hkAt.offset(-6, 0, -8), hkAt.offset(6, 6, 4))) hkLevel.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				p.teleportTo(hkLevel, hkAt.getX() + 0.5, hkAt.getY(), hkAt.getZ() + 0.5, java.util.Set.of(), 180f, 10f, true);
				// Space + Shield + Warding + Yellow + Maxima: a giant yellow hollow knight, not a shelter.
				com.omnispell.spell.Spell hkSpell = new com.omnispell.spell.Spell();
				hkSpell.type = "conjuration";
				hkSpell.tier = 4;
				hkSpell.code.element = "void";
				hkSpell.name = "Yellow Guardian";
				hkSpell.description = "Summons a radiant yellow guardian that shields you from harm.";
				hkSpell.words.addAll(java.util.List.of("base:Space", "command:Shield", "intention:Warding", "attribute:Yellow", "charge:Maxima (Amplify power)"));
				check(com.omnispell.spell.Masonry.structure(hkSpell) == null && "hollow_knight".equals(com.omnispell.spell.Conjuring.bodyFor(hkSpell, "Yellow Guardian").id()),
						"Space + Shield + Warding + Yellow is a hollow knight, not a shelter (" + com.omnispell.spell.Masonry.structure(hkSpell) + ")");
				com.omnispell.spell.Spell hkSprite = new com.omnispell.spell.Spell();
				hkSprite.type = "conjuration";
				hkSprite.code.element = "fire";
				hkSprite.words.addAll(java.util.List.of("base:Fire", "command:Summon", "intention:Malice", "attribute:Blue"));
				check("spirit".equals(com.omnispell.spell.Conjuring.bodyFor(hkSprite, null).id()) && com.omnispell.spell.Conjuring.describe(hkSprite, "Blue Wisp").contains("sprite of blue"),
						"a colour word with no creature calls a featureless sprite of that colour: " + com.omnispell.spell.Conjuring.describe(hkSprite, "Blue Wisp"));
				com.omnispell.spell.Spell hkPlain = new com.omnispell.spell.Spell();
				hkPlain.type = "conjuration";
				hkPlain.tier = 4;
				hkPlain.words.addAll(java.util.List.of("base:Light", "command:Shield", "intention:Warding", "attribute:Yellow", "charge:Maxima (Amplify power)"));
				check(com.omnispell.spell.Masonry.alsoShelter(hkSpell) && !com.omnispell.spell.Masonry.alsoShelter(hkPlain), "Space, a mighty base word, raises the shelter as well; Light does not");
				check(com.omnispell.spell.Conjuring.spawn(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), hkSpell), "Yellow Guardian", 10, 20), "the hollow knight is conjured");
				int hkWalls = 0;
				for (BlockPos pos : BlockPos.betweenClosed(hkAt.offset(-5, 0, -5), hkAt.offset(5, 5, 5))) if (com.omnispell.spell.Masonry.isConjured(pos.immutable())) hkWalls++;
				check(hkWalls >= 40, "and a shelter rises around the caster with it (" + hkWalls + " blocks)");
				for (BlockPos pos : BlockPos.betweenClosed(hkAt.offset(-5, -1, -5), hkAt.offset(5, 5, 5))) com.omnispell.spell.Masonry.breakBlock(hkLevel, pos.immutable());
				var hkKnights = hkLevel.getEntitiesOfClass(net.minecraft.world.entity.monster.skeleton.AbstractSkeleton.class, p.getBoundingBox().inflate(14),
						x -> x.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE));
				var hkKnight = hkKnights.isEmpty() ? null : hkKnights.get(0);
				check(hkKnight != null && hkKnight.hasEffect(MobEffects.INVISIBILITY) && hkKnight.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND).is(Items.SHIELD)
						&& hkKnight.getMainHandItem().is(Items.IRON_SWORD) && !com.omnispell.spell.Masonry.isConjured(hkKnight.blockPosition()) && !com.omnispell.spell.Masonry.isConjured(hkKnight.blockPosition().above(3)),
						"it is an unseen body in coloured armour, sword and shield in hand, standing guard outside the shelter"
						+ (hkKnight == null ? " (no knight found among " + hkLevel.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(14)).size() + " creatures)"
						: " (invisible " + hkKnight.hasEffect(MobEffects.INVISIBILITY) + ", offhand " + hkKnight.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND) + ", main " + hkKnight.getMainHandItem() + ", at " + hkKnight.blockPosition() + ")"));
				if (hkKnight != null) {
					hkKnight.setNoAi(true);
					hkKnight.snapTo(hkAt.getX() + 0.5, hkAt.getY(), hkAt.getZ() - 4.5, 0f, 0f);
					hkMobs.add(hkKnight);
				}
				// Hardened air: solid to a player, open to one crouching, still a floor for one standing on it.
				BlockPos hkAir = hkAt.offset(3, 0, 0);
				hkLevel.setBlockAndUpdate(hkAir, OmniItems.HARDENED_AIR.defaultBlockState());
				var hkShape = OmniItems.HARDENED_AIR.defaultBlockState();
				boolean solidUpright = !hkShape.getCollisionShape(hkLevel, hkAir, net.minecraft.world.phys.shapes.CollisionContext.of(p)).isEmpty();
				p.setShiftKeyDown(true);
				boolean openCrouched = hkShape.getCollisionShape(hkLevel, hkAir, net.minecraft.world.phys.shapes.CollisionContext.of(p)).isEmpty();
				p.teleportTo(hkLevel, hkAir.getX() + 0.5, hkAir.getY() + 1.0, hkAir.getZ() + 0.5, java.util.Set.of(), 180f, 10f, true);
				boolean floorCrouched = !hkShape.getCollisionShape(hkLevel, hkAir, net.minecraft.world.phys.shapes.CollisionContext.of(p)).isEmpty();
				p.setShiftKeyDown(false);
				check(solidUpright && openCrouched && floorCrouched, "hardened air is solid, but crouch and you walk through it; standing on it you stay up (" + solidUpright + ", " + openCrouched + ", " + floorCrouched + ")");
				hkLevel.setBlockAndUpdate(hkAir, Blocks.AIR.defaultBlockState());
				p.teleportTo(hkLevel, hkAt.getX() + 0.5, hkAt.getY(), hkAt.getZ() + 0.5, java.util.Set.of(), 180f, 10f, true);
				// A shelter with no colour word: its doorway is hardened air.
				com.omnispell.spell.Spell hkHut = new com.omnispell.spell.Spell();
				hkHut.type = "conjuration";
				hkHut.code.element = "ice";
				hkHut.words.addAll(java.util.List.of("base:Ice", "command:Shield", "intention:Warding"));
				check("shelter".equals(com.omnispell.spell.Masonry.structure(hkHut)) && com.omnispell.spell.Conjuring.spawn(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), hkHut), "Ice Hut", 10, 20), "Ice + Shield + Warding raises a shelter");
				int hkDoor = 0;
				for (BlockPos pos : BlockPos.betweenClosed(hkAt.offset(-4, 0, -4), hkAt.offset(4, 2, 4))) if (hkLevel.getBlockState(pos).is(OmniItems.HARDENED_AIR)) hkDoor++;
				check(hkDoor >= 2, "its doorway is hardened air (" + hkDoor + " blocks)");
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, new Vec3(hkAt.getX() + 0.5, hkAt.getY() + 2.0, hkAt.getZ() - 4.5));
			});
			context.waitTicks(15);
			context.takeScreenshot("41-hollow-knight");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				for (var e : hkMobs) if (!e.isRemoved()) e.discard();
				BlockPos hkAt = BlockPos.containing(cHome[0]).offset(-60, 0, -90);
				for (BlockPos pos : BlockPos.betweenClosed(hkAt.offset(-5, -1, -5), hkAt.offset(5, 5, 5))) com.omnispell.spell.Masonry.breakBlock(p.level(), pos.immutable());
				p.teleportTo(p.level(), cHome[0].x, cHome[0].y, cHome[0].z, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(5);

			// ---- bridges do what their spells claim, and a bridge that claims nothing stays plain
			net.minecraft.world.entity.Mob[] brFoes = new net.minecraft.world.entity.Mob[2];
			BlockPos[] brAt = new BlockPos[4];
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel brLevel = p.level();
				java.util.function.Function<String[], com.omnispell.spell.Spell> brMake = parts -> {
					com.omnispell.spell.Spell made = new com.omnispell.spell.Spell();
					made.type = "conjuration";
					made.tier = 2;
					made.code.type = "summon";
					made.code.primaryValue = 20;
					made.code.duration = 3;
					made.code.element = parts[0];
					made.words.addAll(java.util.List.of(parts[1].split(",")));
					made.description = parts[2];
					made.effect = parts[3];
					if (!parts[4].isEmpty()) made.code.modifiers.addAll(java.util.List.of(parts[4].split(",")));
					return made;
				};
				// The friend's bridges, word for word as the Oracle made them.
				com.omnispell.spell.Spell brGate = brMake.apply(new String[]{"neutral", "base:Metal,command:Shield,intention:Warding,attribute:Bridge",
						"Summons a fortified metallic construct that strikes foes and shields you from harm.",
						"Materializes an Iron Bulwark for 3 turns that attacks the enemy for 15 damage each turn. While active, the metallic ward grants 6 fortify, reducing incoming damage from all enemy attacks.",
						"summoned_creature:15:3:Iron_Bulwark,fortify:6:3"});
				com.omnispell.spell.Spell brSpike = brMake.apply(new String[]{"neutral", "base:Metal,command:Fracture,intention:Binding,attribute:Bridge",
						"Summons a jagged construct of fractured iron shards bound into an arching span that attacks foes and reinforces your defense.",
						"Conjures an Iron Span-Construct for 3 turns that strikes for 20 neutral damage each turn. While active, the magnetic binding also fortifies you, reducing incoming damage by 6.",
						"summoned_creature:20:3:Iron Span-Construct,fortify:6:3"});
				com.omnispell.spell.Spell brMoss = brMake.apply(new String[]{"nature", "base:Nature,command:Solidify,intention:Soothing,attribute:Bridge",
						"Summons a living bridge-golem of solidified timber and moss that strikes foes while soothing your wounds.",
						"Materializes a Mossbridge Golem that deals 15 nature damage per turn for 3 turns. Its soothing connection heals you for 30% of all damage it inflicts.",
						"summoned_creature:15:3:Mossbridge Golem,soul_link:30:3"});
				com.omnispell.spell.Spell brPlain = brMake.apply(new String[]{"earth", "base:Earth,command:Summon,intention:Creation,attribute:Bridge",
						"Conjures a sturdy stone bridge across the gap.", "", ""});
				var tGate = com.omnispell.spell.Masonry.bridgeToggles(brGate);
				var tSpike = com.omnispell.spell.Masonry.bridgeToggles(brSpike);
				var tMoss = com.omnispell.spell.Masonry.bridgeToggles(brMoss);
				var tPlain = com.omnispell.spell.Masonry.bridgeToggles(brPlain);
				check(tGate.contains("spikes") && tGate.contains("gated") && !tGate.contains("snare"), "Metal + Shield + Warding + Bridge strikes and protects: " + tGate);
				check(tSpike.contains("spikes") && tSpike.contains("snare") && !tSpike.contains("gated"), "Metal + Fracture + Binding + Bridge strikes and binds: " + tSpike);
				check(tMoss.contains("spikes") && tMoss.contains("mend") && !tMoss.contains("gated"), "Nature + Solidify + Soothing + Bridge strikes and soothes: " + tMoss);
				check(tPlain.isEmpty(), "a bridge that claims nothing is a plain bridge: " + tPlain);
				check(com.omnispell.spell.Conjuring.describe(brGate, "Iron Bulwark").contains("gate at each end") && com.omnispell.spell.Conjuring.describe(brGate, "Iron Bulwark").contains("Spikes of iron"),
						"the book says what the bridge does: " + com.omnispell.spell.Conjuring.describe(brGate, "Iron Bulwark"));
				// Build all four over pits, facing north.
				com.omnispell.spell.Spell[] brSpells = {brGate, brSpike, brMoss, brPlain};
				com.omnispell.spell.Masonry.lifeOverride = 400;
				for (int i = 0; i < 4; i++) {
					BlockPos brHere = BlockPos.containing(cHome[0]).offset(90 + 12 * i, 0, -90); // close together, so all stay loaded around the player
					for (BlockPos pos : BlockPos.betweenClosed(brHere.offset(-4, -8, -40), brHere.offset(4, 7, -1))) brLevel.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
					for (BlockPos pos : BlockPos.betweenClosed(brHere.offset(-3, -1, 0), brHere.offset(3, -1, 3))) brLevel.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
					for (BlockPos pos : BlockPos.betweenClosed(brHere.offset(-3, 0, 0), brHere.offset(3, 5, 3))) brLevel.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
					p.teleportTo(brLevel, brHere.getX() + 0.5, brHere.getY(), brHere.getZ() + 0.5, java.util.Set.of(), 180f, 0f, true);
					check(com.omnispell.spell.Conjuring.spawn(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), brSpells[i]), "Bridge " + i, 15, 3), "bridge " + i + " is conjured");
					brAt[i] = brHere;
				}
				com.omnispell.spell.Masonry.lifeOverride = 0;
				int brGates = 0;
				for (BlockPos pos : BlockPos.betweenClosed(brAt[0].offset(-3, -1, -40), brAt[0].offset(3, 1, -1))) if (brLevel.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) brGates++;
				check(brGates >= 6 && !brLevel.getBlockState(brAt[0].offset(0, 2, -5)).isAir(), "the protecting bridge is a roofed tunnel with gates at both ends (" + brGates + " gates)");
				// A zombie on the striking-binding bridge, and one on the plain bridge.
				for (int k = 0; k < 2; k++) {
					var foe = EntityTypes.ZOMBIE.create(brLevel, EntitySpawnReason.COMMAND);
					BlockPos on = brAt[k == 0 ? 1 : 3].offset(0, 0, -8);
					foe.snapTo(on.getX() + 0.5, on.getY(), on.getZ() + 0.5, 0f, 0f);
					foe.setNoAi(true);
					foe.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
					brLevel.addFreshEntity(foe);
					brFoes[k] = foe;
				}
				// The caster waits on the platform of the soothing bridge, between the two zombies.
				p.removeAllEffects();
				p.teleportTo(brLevel, brAt[2].getX() + 0.5, brAt[2].getY(), brAt[2].getZ() + 1.5, java.util.Set.of(), 180f, 0f, true);
			});
			context.waitTicks(45);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				var spiked = brFoes[0];
				var plain = brFoes[1];
				check(spiked.getHealth() < spiked.getMaxHealth() && spiked.hasEffect(MobEffects.SLOWNESS) && spiked.getEffect(MobEffects.SLOWNESS).getAmplifier() >= 3,
						"a zombie on the jagged bridge is spiked and held fast (" + spiked.getHealth() + " of " + spiked.getMaxHealth() + ")");
				check(plain.getHealth() >= plain.getMaxHealth() && !plain.hasEffect(MobEffects.SLOWNESS), "one on the plain bridge is left alone");
				check(!p.hasEffect(MobEffects.REGENERATION), "off the soothing bridge, you do not mend");
				p.teleportTo(p.level(), brAt[2].getX() + 0.5, brAt[2].getY(), brAt[2].getZ() - 5.5, java.util.Set.of(), 180f, 0f, true);
			});
			context.waitTicks(20);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				var spiked = brFoes[0];
				var plain = brFoes[1];
				check(p.hasEffect(MobEffects.REGENERATION), "standing on the soothing bridge, you mend");
				spiked.discard();
				plain.discard();
				p.teleportTo(p.level(), brAt[0].getX() + 0.5, brAt[0].getY(), brAt[0].getZ() + 2.5, java.util.Set.of(), 180f, 5f, true);
			});
			context.waitTicks(15);
			context.takeScreenshot("42-gated-bridge");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				for (BlockPos brHere : brAt) for (BlockPos pos : BlockPos.betweenClosed(brHere.offset(-4, -14, -40), brHere.offset(4, 6, 0))) com.omnispell.spell.Masonry.breakBlock(p.level(), pos.immutable());
				p.removeAllEffects();
				p.teleportTo(p.level(), cHome[0].x, cHome[0].y, cHome[0].z, java.util.Set.of(), 0f, 0f, true);
			});
			context.waitTicks(5);

			// ---- a creature word in a defense gives the creature's traits, never a living creature
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel asLevel = p.level();
				p.removeAllEffects();
				java.util.function.Function<String, com.omnispell.spell.Spell> asMake = creatureWord -> {
					com.omnispell.spell.Spell made = new com.omnispell.spell.Spell();
					made.id = "defense|test-aspect-" + creatureWord;
					made.type = "defense";
					made.tier = 3;
					made.code.type = "shield";
					made.code.element = "nature";
					made.words.addAll(java.util.List.of("base:Nature", "command:Shield", "intention:Warding", "attribute:" + creatureWord));
					return made;
				};
				int asBefore = asLevel.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(16), x -> !(x instanceof ServerPlayer)).size();
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.BARRIER.ordinal()] = 100;
				com.omnispell.spell.Spell asWolf = asMake.apply("Wolf");
				check("aspect".equals(com.omnispell.spell.Defenses.form(asWolf)) && com.omnispell.spell.Defenses.describe(asWolf).contains("wolf's traits"), "Wolf in a defense gives the wolf's traits: " + com.omnispell.spell.Defenses.describe(asWolf));
				check(com.omnispell.spell.Defenses.cast(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), asWolf), 3), "the wolf defense is cast");
				int asAfter = asLevel.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(16), x -> !(x instanceof ServerPlayer)).size();
				check(asAfter <= asBefore && p.hasEffect(MobEffects.SPEED) && p.hasEffect(MobEffects.STRENGTH), "it calls nothing living, and you run fast and hit hard (" + asBefore + " creatures before, " + asAfter + " after)");
				// The cat lands on its feet; the snake poisons what strikes it.
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.BARRIER.ordinal()] = 100;
				com.omnispell.spell.Defenses.cast(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), asMake.apply("Cat")), 3);
				check(com.omnispell.spell.Modifiers.modifyIncoming(p, p.damageSources().fall(), 10f) == 0f, "with the cat's traits a fall does nothing");
				PlayerStore.get(p).mana[com.omnispell.player.ManaPool.BARRIER.ordinal()] = 100;
				com.omnispell.spell.Defenses.cast(new com.omnispell.spell.Cast(p, WandItem.create("oak", "flint"), asMake.apply("Snake")), 3);
				var asBiter = EntityTypes.ZOMBIE.create(asLevel, EntitySpawnReason.COMMAND);
				asBiter.snapTo(p.getX() + 1.5, p.getY(), p.getZ(), 0f, 0f);
				asBiter.setNoAi(true);
				asLevel.addFreshEntity(asBiter);
				p.setInvulnerableTime(0);
				p.hurtServer(asLevel, asLevel.damageSources().mobAttack(asBiter), 1f);
				check(asBiter.hasEffect(MobEffects.POISON), "with the snake's traits, whatever strikes you is poisoned");
				asBiter.discard();
				p.removeAllEffects();
				for (String gone : new String[]{"wolf", "cat", "snake"}) com.omnispell.spell.Modifiers.add(p, "aspect_" + gone, 0, 1);
				p.setHealth(p.getMaxHealth());
			});
			context.waitTicks(5);

			// ---- toggles that had no code: a counter-spell ward and an arcane anchor; reflection behind a barrier
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel tgLevel = p.level();
				p.removeAllEffects();
				p.setHealth(p.getMaxHealth());
				var tgFoe = EntityTypes.ZOMBIE.create(tgLevel, EntitySpawnReason.COMMAND);
				tgFoe.snapTo(p.getX() + 2, p.getY(), p.getZ(), 0f, 0f);
				tgFoe.setNoAi(true);
				tgFoe.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
				tgFoe.setHealth(200f);
				tgLevel.addFreshEntity(tgFoe);
				var tgArcher = EntityTypes.SKELETON.create(tgLevel, EntitySpawnReason.COMMAND);
				tgArcher.snapTo(p.getX() - 2, p.getY(), p.getZ(), 0f, 0f);
				tgArcher.setNoAi(true);
				tgLevel.addFreshEntity(tgArcher);
				// Space + Reflect + Warding: half of every blow goes back, even while the barrier catches it.
				com.omnispell.spell.Modifiers.add(p, "damage_reflection", 50, 200);
				p.setAbsorptionAmount(10f);
				p.setInvulnerableTime(0);
				p.hurtServer(tgLevel, tgLevel.damageSources().mobAttack(tgFoe), 4f);
				check(tgFoe.getHealth() <= 198.1f, "with the barrier up, half the blow goes back to the striker (zombie at " + tgFoe.getHealth() + ")");
				com.omnispell.spell.Modifiers.add(p, "damage_reflection", 0, 1);
				// The counter-spell ward unravels a monster's arrow or blast, never a plain blow.
				com.omnispell.spell.Modifiers.add(p, "counter_spell", 100, 200);
				float tgArrow = com.omnispell.spell.Modifiers.modifyIncoming(p, tgLevel.damageSources().mobProjectile(tgArcher, tgArcher), 6f);
				float tgBlow = com.omnispell.spell.Modifiers.modifyIncoming(p, tgLevel.damageSources().mobAttack(tgFoe), 6f);
				check(tgArrow == 0f && tgBlow > 0f, "a counter-spell ward unravels a skeleton's arrow but not a zombie's blow (" + tgArrow + ", " + tgBlow + ")");
				check(com.omnispell.spell.Modifiers.wardUnravels(tgLevel, java.util.List.of(p)), "and it can unravel a boss's special attack");
				com.omnispell.spell.Modifiers.add(p, "counter_spell", 0, 1);
				// Mud + Fill + Binding: an arcane anchor cuts a turn off each harmful effect that lands, and leaves the good ones be.
				com.omnispell.spell.Modifiers.add(p, "arcane_anchor", 1, 200);
				p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 300, 0));
				p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SPEED, 300, 0));
				com.omnispell.spell.Modifiers.everySecond(p);
				com.omnispell.spell.Modifiers.everySecond(p);
				check(p.getEffect(MobEffects.POISON) != null && p.getEffect(MobEffects.POISON).getDuration() <= 200 && p.getEffect(MobEffects.POISON).getDuration() > 150
						&& p.getEffect(MobEffects.SPEED).getDuration() > 250,
						"an arcane anchor cuts a turn off poison, once, and leaves speed alone (poison " + (p.getEffect(MobEffects.POISON) == null ? 0 : p.getEffect(MobEffects.POISON).getDuration()) + ")");
				com.omnispell.spell.Modifiers.add(p, "arcane_anchor", 0, 1);
				tgFoe.discard();
				tgArcher.discard();
				p.removeAllEffects();
				p.setAbsorptionAmount(0f);
				p.setHealth(p.getMaxHealth());
			});
			context.waitTicks(5);

			// ---- death: the soul-bound bundle and all Omnispell progress survive
			Vec3[] deathAt = new Vec3[1];
			server.runOnServer(s -> {
				for (int i = 0; i < 9; i++) if (WandItem.isWand(player(s).getInventory().getItem(i))) player(s).getInventory().setItem(i, ItemStack.EMPTY); // loose wands would simply drop
				player(s).getInventory().setItem(8, new ItemStack(OmniItems.GRIMOIRE)); // a loose book must not
				// A totem wand on the hotbar saves you once, then recharges for ten minutes of world time.
				ServerPlayer saved = player(s);
				saved.getInventory().setItem(7, WandItem.create("oak", "totem_of_undying"));
				PlayerStore.get(saved).totemReadyAt = 0;
				check(!com.omnispell.spell.Modifiers.allowDeath(saved), "a totem wand on the hotbar saves you from death");
				check(com.omnispell.spell.Modifiers.allowDeath(saved), "and not again until it has recharged");
				PlayerStore.get(saved).totemReadyAt = saved.level().getServer().overworld().getGameTime() - 1;
				check(!com.omnispell.spell.Modifiers.allowDeath(saved), "once recharged it saves you again");
				saved.getInventory().setItem(7, ItemStack.EMPTY);
				saved.removeAllEffects();
				saved.setHealth(saved.getMaxHealth());
				saved.getInventory().setItem(20, WandItem.create("oak", "totem_of_undying")); // off the hotbar it does not save you, but it is never lost
				player(s).setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(OmniItems.WIZARD_ROBE)); // nor wizard's attire
				player(s).setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(OmniItems.WIZARD_BOOTS));
				// A Reliquary, and loose things for it to catch: iron, dirt and a plain bundle.
				check(s.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, com.omnispell.Omnispell.id("reliquary"))).isPresent(), "a bundle and a totem make a Reliquary");
				saved.getInventory().setItem(21, new ItemStack(OmniItems.RELIQUARY));
				saved.getInventory().setItem(22, new ItemStack(Items.IRON_INGOT, 16));
				saved.getInventory().setItem(23, new ItemStack(Items.DIRT, 32));
				ItemStack plainBundle = new ItemStack(Items.BUNDLE);
				saved.getInventory().setItem(24, plainBundle);
				saved.giveExperienceLevels(10);
				deathAt[0] = saved.position();
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
				check("totem_of_undying".equals(WandItem.core(p.getInventory().getItem(20))), "a totem-core wand is kept through death");
				check(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(OmniItems.WIZARD_ROBE) && p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(OmniItems.WIZARD_BOOTS), "the robe and boots are kept through death");
				PlayerData data = PlayerStore.get(p);
				check(data.spells.size() == 15 && data.words.size() > 200, "words and spells survive death (" + data.spells.size() + " spells)");

				// The Reliquary caught what fell, and only that.
				ServerLevel level = p.level();
				var near = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(deathAt[0], deathAt[0]).inflate(8), e -> true);
				net.minecraft.world.entity.item.ItemEntity token = null;
				int loose = 0;
				StringBuilder looseNames = new StringBuilder();
				for (var e : near) {
					if (com.omnispell.player.Reliquary.holds(e.getItem())) token = e;
					else if (e.getAge() < 400) { // fell at the death, not left from an earlier test here
						loose++;
						looseNames.append(e.getItem()).append(' ');
					}
				}
				check(token != null && loose == 0, "a Reliquary lies where you fell, and nothing else is scattered (" + loose + " loose: " + looseNames + ")");
				var held = token.getItem().getOrDefault(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList();
				boolean iron = false, dirt = false, plainBundle = false, keptThing = false;
				for (ItemStack thing : held) {
					if (thing.is(Items.IRON_INGOT) && thing.getCount() == 16) iron = true;
					if (thing.is(Items.DIRT) && thing.getCount() == 32) dirt = true;
					if (thing.is(Items.BUNDLE)) plainBundle = true;
					boolean totemWand = WandItem.isWand(thing) && "totem_of_undying".equals(WandItem.core(thing));
					if (thing.is(OmniItems.GRIMOIRE) || totemWand || thing.is(OmniItems.WIZARD_ROBE) || thing.is(OmniItems.WIZARD_BOOTS)) keptThing = true;
				}
				check(iron && dirt && plainBundle && !keptThing, "it holds the iron, the dirt and the plain bundle, and none of what Soul Bind keeps (" + held + ")");
				check(token.getItem().getDamageValue() == 1 && token.getItem().has(DataComponents.DAMAGE_RESISTANT) && token.isInvulnerable(), "one use spent; it cannot burn or be blasted");
				check(level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, new net.minecraft.world.phys.AABB(deathAt[0], deathAt[0]).inflate(8), e -> true).isEmpty(), "no experience orbs fall: half the experience is in the Reliquary");
				check(data.reliquaries.size() == 1, "the Reliquary is remembered, for the diamond on the experience bar");
				deathAt[0] = token.position();
				// stand a little way off, facing roughly toward it, for a look at the diamond on the experience bar
				p.teleportTo(level, deathAt[0].x + 10, deathAt[0].y, deathAt[0].z + 3, java.util.Set.of(), 110f, 0f, true);
				com.omnispell.player.Reliquary.sendTarget(p);
			});
			context.waitTicks(10);
			context.takeScreenshot("37-reliquary-diamond");
			server.runOnServer(s -> player(s).teleportTo(player(s).level(), deathAt[0].x, deathAt[0].y, deathAt[0].z, java.util.Set.of(), 0f, 0f, true));
			context.waitTicks(30);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				int iron = 0, dirt = 0;
				ItemStack reliquary = ItemStack.EMPTY;
				for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
					ItemStack stack = p.getInventory().getItem(i);
					if (stack.is(Items.IRON_INGOT)) iron += stack.getCount();
					if (stack.is(Items.DIRT)) dirt += stack.getCount();
					if (stack.is(OmniItems.RELIQUARY)) reliquary = stack;
				}
				check(iron == 16 && dirt == 32, "picking it up gives everything back (" + iron + " iron, " + dirt + " dirt)");
				check(!reliquary.isEmpty() && !com.omnispell.player.Reliquary.holds(reliquary) && reliquary.getDamageValue() == 1, "and the empty Reliquary comes back to your inventory, five uses left");
				check(p.totalExperience > 0 || p.experienceLevel > 0, "with half the lost experience (level " + p.experienceLevel + ")");
				check(PlayerStore.get(p).reliquaries.isEmpty(), "and it is no longer pointed at");
			});
			context.takeScreenshot("30-after-respawn");
		} finally {
			SpellGenerator.transport = realGenerator;
		}
	}
}
