package com.omnispell;

import com.omnispell.data.WordBank;
import com.omnispell.item.OmniItems;
import com.omnispell.item.WandItem;
import com.omnispell.net.OmniNet;
import com.omnispell.player.Mana;
import com.omnispell.player.PlayerStore;
import com.omnispell.spell.Casting;
import com.omnispell.spell.MobControl;
import com.omnispell.spell.Modifiers;
import com.omnispell.spell.SpellCrafting;
import com.omnispell.spell.Summons;
import com.omnispell.spell.Tasks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class Omnispell implements ModInitializer {
	public static final String MOD_ID = "omnispell";
	public static final Logger LOG = LoggerFactory.getLogger("Omnispell");
	public static OmniConfig CONFIG = new OmniConfig();

	private static final java.util.Map<java.util.UUID, Boolean> CREATIVE_SEEN = new java.util.HashMap<>();

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** What a new player finds in their Soul Bind bundle: a starter wand, the book and one pair of waypoints. */
	public static List<ItemStack> starterKit(ServerPlayer player) {
		List<ItemStack> kit = new java.util.ArrayList<>();
		kit.add(WandItem.create("oak", "flint"));
		kit.add(new ItemStack(OmniItems.GRIMOIRE));
		kit.add(com.omnispell.waypoint.WaypointItem.newPair(player));
		return kit;
	}

	@Override
	public void onInitialize() {
		CONFIG = OmniConfig.load();
		com.omnispell.spell.SpellGenerator.purgeLegacyCache();
		WordBank.load();
		com.omnispell.alchemy.Alchemy.load();
		com.omnispell.alchemy.Vats.register();
		OmniItems.register();
		com.omnispell.charm.Charms.register();
		com.omnispell.familiar.Familiars.register();
		com.omnispell.spell.Conjuring.register();
		// Conjured masonry gives nothing when broken: the block simply goes.
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				!(level instanceof net.minecraft.server.level.ServerLevel server) || com.omnispell.spell.Masonry.breakBlock(server, pos));
		com.omnispell.boss.Lairs.register();
		com.omnispell.world.Cauldrons.register();
		com.omnispell.world.ForestFeatures.register();
		com.omnispell.world.Arcanist.register();
		com.omnispell.world.PortalRuins.register();
		PlayerStore.register();
		com.omnispell.waypoint.Waypoints.register();
		OmniNet.register();
		OmniLoot.register();
		OmniCommands.register();
		registerMessages();
		registerWaypointMessages();
		registerRazza();
		registerEvents();
		LOG.info("Omnispell ready: {} words of power", WordBank.obtainableCount());
	}

	private static void registerMessages() {
		ServerPlayNetworking.registerGlobalReceiver(OmniNet.CastSpell.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			ItemStack wand = WandItem.heldWand(player);
			if (wand.isEmpty()) return;
			if (payload.slot() == -3) {
				com.omnispell.player.AirWalk.toggle(player);
				return;
			}
			if (payload.slot() == -2) {
				WandItem.cycle(player, wand);
				return;
			}
			if (payload.slot() == -1) {
				Casting.primary(player, wand);
				return;
			}
			int slot = Math.min(WandItem.SLOTS - 1, payload.slot());
			if (payload.slot() >= 0 && slot != WandItem.selected(wand)) WandItem.select(wand, slot);
			Casting.cast(player, wand, slot);
		});
		ServerPlayNetworking.registerGlobalReceiver(OmniNet.CraftSpell.TYPE, (payload, context) -> {
			List<String> words = payload.words().isEmpty() ? List.of() : Arrays.asList(payload.words().split(","));
			SpellCrafting.craft(context.player(), payload.spellType(), words);
		});
		ServerPlayNetworking.registerGlobalReceiver(OmniNet.BookAction.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			switch (payload.action()) {
				case "save" -> SpellCrafting.savePending(player);
				case "discard" -> SpellCrafting.discardPending(player);
				case "forget" -> SpellCrafting.forget(player, payload.arg());
				case "equip" -> SpellCrafting.equip(player, payload.arg());
				case "rename" -> SpellCrafting.rename(player, payload.arg());
				case "archive" -> SpellCrafting.archive(player, payload.arg());
				case "move" -> SpellCrafting.move(player, payload.arg());
				case "folder_new" -> SpellCrafting.newFolder(player, payload.arg());
				case "folder_rename" -> SpellCrafting.renameFolder(player, payload.arg());
				case "folder_toggle" -> SpellCrafting.toggleFolder(player, payload.arg());
				case "folder_delete" -> SpellCrafting.deleteFolder(player, payload.arg());
				case "select" -> {
					ItemStack wand = WandItem.heldWand(player);
					try {
						if (!wand.isEmpty()) WandItem.select(wand, Integer.parseInt(payload.arg()));
					} catch (NumberFormatException ignored) {
					}
					PlayerStore.syncVitals(player);
				}
				case "wand_sprint" -> {
					com.omnispell.player.PlayerData data = PlayerStore.get(player);
					data.noWandSprint = !"on".equals(payload.arg());
					PlayerStore.save(player);
					PlayerStore.syncState(player);
				}
				case "sync" -> {
					PlayerStore.syncState(player);
					PlayerStore.syncVitals(player);
				}
				default -> {
				}
			}
		});
	}

	private static void registerWaypointMessages() {
		ServerPlayNetworking.registerGlobalReceiver(OmniNet.EditWaypoint.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			com.omnispell.waypoint.Waypoints.Pair pair = com.omnispell.waypoint.Waypoints.pair(context.server(), payload.pairId());
			if (pair == null) return;
			// Only someone standing at one of the pair's ends may change it.
			boolean near = false;
			for (com.omnispell.waypoint.Waypoints.End end : pair.ends) {
				if (end.is(player.level(), end.pos()) && end.pos().closerToCenterThan(player.position(), 8)) near = true;
			}
			if (!near) return;
			com.omnispell.waypoint.Waypoints.edit(context.server(), payload.pairId(), payload.name(), payload.color());
		});
	}

	/**
	 * Razza is summoned at the dragon's egg: once the Ender Dragon is dead, use a
	 * wand on the egg. The egg is not used up.
	 */
	private static void registerRazza() {
		// A Pet Shell takes in a tamed animal before the animal's own right-click (sitting, mounting) can answer.
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				player.isSpectator() ? net.minecraft.world.InteractionResult.PASS : com.omnispell.item.PetShellItem.capture(player, hand, entity));
		// An ender pearl on a whole frame of mossy stone wakes a portal to the Enchanted Forest.
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
			if (!held.is(net.minecraft.world.item.Items.ENDER_PEARL) || !com.omnispell.realm.ForestPortalBlock.frame(level.getBlockState(hit.getBlockPos()))) return net.minecraft.world.InteractionResult.PASS;
			if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return net.minecraft.world.InteractionResult.PASS;
			if (server.dimension() != net.minecraft.world.level.Level.OVERWORLD && server.dimension() != com.omnispell.realm.Realms.FOREST) return net.minecraft.world.InteractionResult.PASS;
			if (!com.omnispell.realm.ForestPortalBlock.light(server, hit.getBlockPos())) return net.minecraft.world.InteractionResult.PASS;
			if (!player.getAbilities().instabuild) held.shrink(1);
			return net.minecraft.world.InteractionResult.SUCCESS;
		});
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.getBlockState(hit.getBlockPos()).is(net.minecraft.world.level.block.Blocks.DRAGON_EGG)) return net.minecraft.world.InteractionResult.PASS;
			if (!WandItem.isWand(player.getItemInHand(hand))) return net.minecraft.world.InteractionResult.PASS;
			if (!(player instanceof ServerPlayer serverPlayer)) return net.minecraft.world.InteractionResult.SUCCESS;
			net.minecraft.server.level.ServerLevel serverLevel = serverPlayer.level();
			if (!PlayerStore.get(serverPlayer).bosses.contains("ender_dragon")) {
				serverPlayer.sendOverlayMessage(net.minecraft.network.chat.Component.literal("The egg is silent. Its mother still lives in your memory."));
				return net.minecraft.world.InteractionResult.SUCCESS;
			}
			for (net.minecraft.world.entity.Entity e : serverLevel.getAllEntities()) {
				com.omnispell.boss.BossDef def = com.omnispell.boss.Bosses.defOf(e);
				if (def != null && def.id.equals("razza") && e.isAlive()) {
					serverPlayer.sendOverlayMessage(net.minecraft.network.chat.Component.literal("He is already here."));
					return net.minecraft.world.InteractionResult.SUCCESS;
				}
			}
			net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atBottomCenterOf(hit.getBlockPos().above(2));
			com.omnispell.boss.Bosses.spawn(serverLevel, at, com.omnispell.boss.BossDef.ALL.get("razza"));
			for (ServerPlayer other : serverLevel.players()) {
				other.sendSystemMessage(net.minecraft.network.chat.Component.literal("The seals break. Razza, the Unbound, has been called to account.").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
			}
			return net.minecraft.world.InteractionResult.SUCCESS;
		});
	}

	private static void registerEvents() {
		ServerPlayerEvents.JOIN.register(player -> {
			PlayerStore.get(player);
			PlayerStore.save(player);
			PlayerStore.syncState(player);
			PlayerStore.syncVitals(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			com.omnispell.player.Reliquary.respawned(newPlayer);
			com.omnispell.player.Corruption.respawned(newPlayer);
			com.omnispell.player.Reliquary.sendTarget(newPlayer);
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			PlayerStore.unload(player);
			Casting.forget(player);
			com.omnispell.player.AirWalk.forget(player);
			com.omnispell.spell.Weapons.forget(player);
			Modifiers.clear(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			Modifiers.clear(newPlayer);
			PlayerStore.save(newPlayer);
			PlayerStore.syncState(newPlayer);
			PlayerStore.syncVitals(newPlayer);
		});
		ServerPlayerEvents.ALLOW_DEATH.register((player, source, amount) -> Modifiers.allowDeath(player));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Modifiers::allowDamage);
		// Friends do not hurt friends: no player's spell, bolt or blade harms another player unless the server's settings allow it.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				CONFIG.friendlyFire || !(entity instanceof ServerPlayer victim) || !(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			Modifiers.afterDamage(entity, source, damageTaken);
			com.omnispell.spell.Shroud.struck(entity, source);
			com.omnispell.boss.Bosses.afterDamage(entity, source, damageTaken);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(com.omnispell.boss.Bosses::onDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(com.omnispell.realm.Realms::onDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(com.omnispell.familiar.Familiars::onDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && killer != entity) com.omnispell.item.CoreAbilities.onKill(killer);
		});

		// A conjured creature left behind by a crash or restart is removed when its chunk loads.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (Summons.isStray(entity)) Tasks.later(1, entity::discard);
			com.omnispell.boss.Bosses.onEntityLoad(entity);
			com.omnispell.realm.Realms.onEntityLoad(entity, level);
			com.omnispell.familiar.Familiars.onEntityLoad(entity);
			com.omnispell.world.Arcanist.onEntityLoad(entity, level);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			com.omnispell.spell.Trails.clear();
			Tasks.flush(); // conjured blocks and creatures vanish before the world is saved
			com.omnispell.waypoint.Waypoints.unload();
			com.omnispell.boss.Bosses.clear();
			com.omnispell.familiar.Familiars.clear();
			com.omnispell.alchemy.Vats.unload();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) PlayerStore.save(player);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Tasks.tick();
			com.omnispell.player.AirWalk.tick(server);
			com.omnispell.item.Attire.tick(server);
			int tick = server.getTickCount();
			com.omnispell.spell.Conjuring.tick(server);
			com.omnispell.spell.Trails.tick(server);
			com.omnispell.spell.Wards.tick(server);
			com.omnispell.player.Reliquary.tick(server);
			if (server.getTickCount() % 2 == 0) com.omnispell.spell.Mines.tick(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) com.omnispell.item.CoreAbilities.tick(player);
			if (tick % 10 == 0) {
				MobControl.tick();
				com.omnispell.boss.Bosses.tick();
				com.omnispell.familiar.Familiars.tick(server);
				for (ServerPlayer player : server.getPlayerList().getPlayers()) Mana.wandUpkeep(player);
			}
			if (tick % 20 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					Mana.regenerate(player);
					com.omnispell.boss.BossAltarBlock.wakeNear(player);
					Modifiers.everySecond(player);
					com.omnispell.charm.Charms.everySecond(player);
					Modifiers.totemCheck(player);
					com.omnispell.item.CoreAbilities.everySecond(player);
					com.omnispell.player.Corruption.everySecond(player);
				}
				com.omnispell.spell.Shroud.everySecond(server);
			}
			if (tick % 10 == 0) com.omnispell.spell.Shroud.tick(server);
			if (tick % 5 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) PlayerStore.syncVitals(player);
			}
			if (tick % 20 == 0) {
				// Switching into or out of creative changes what the book shows.
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					boolean creative = player.getAbilities().instabuild;
					if (CREATIVE_SEEN.getOrDefault(player.getUUID(), false) != creative) {
						CREATIVE_SEEN.put(player.getUUID(), creative);
						PlayerStore.syncState(player);
					}
				}
			}
			if (tick % 600 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) PlayerStore.save(player);
			}
		});
	}
}
