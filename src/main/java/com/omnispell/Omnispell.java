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

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** What a new player finds in their Soul Bind bundle: a starter wand and the book. */
	public static List<ItemStack> starterKit() {
		List<ItemStack> kit = new java.util.ArrayList<>();
		kit.add(WandItem.create("oak", "flint"));
		kit.add(new ItemStack(OmniItems.GRIMOIRE));
		return kit;
	}

	@Override
	public void onInitialize() {
		CONFIG = OmniConfig.load();
		WordBank.load();
		OmniItems.register();
		PlayerStore.register();
		OmniNet.register();
		OmniLoot.register();
		OmniCommands.register();
		registerMessages();
		registerEvents();
		LOG.info("Omnispell ready: {} words of power", WordBank.obtainableCount());
	}

	private static void registerMessages() {
		ServerPlayNetworking.registerGlobalReceiver(OmniNet.CastSpell.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			ItemStack wand = WandItem.heldWand(player);
			if (wand.isEmpty()) return;
			int slot = payload.slot() < 0 ? WandItem.selected(wand) : Math.min(WandItem.SLOTS - 1, payload.slot());
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
				case "select" -> {
					ItemStack wand = WandItem.heldWand(player);
					try {
						if (!wand.isEmpty()) WandItem.select(wand, Integer.parseInt(payload.arg()));
					} catch (NumberFormatException ignored) {
					}
					PlayerStore.syncVitals(player);
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

	private static void registerEvents() {
		ServerPlayerEvents.JOIN.register(player -> {
			PlayerStore.get(player);
			PlayerStore.save(player);
			PlayerStore.syncState(player);
			PlayerStore.syncVitals(player);
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			PlayerStore.unload(player);
			Casting.forget(player);
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
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> Modifiers.afterDamage(entity, source, damageTaken));

		// A conjured creature left behind by a crash or restart is removed when its chunk loads.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (Summons.isStray(entity)) Tasks.later(1, entity::discard);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Tasks.flush(); // conjured blocks and creatures vanish before the world is saved
			for (ServerPlayer player : server.getPlayerList().getPlayers()) PlayerStore.save(player);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Tasks.tick();
			int tick = server.getTickCount();
			if (tick % 10 == 0) MobControl.tick();
			if (tick % 20 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					Mana.regenerate(player);
					Modifiers.everySecond(player);
				}
			}
			if (tick % 5 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) PlayerStore.syncVitals(player);
			}
			if (tick % 600 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) PlayerStore.save(player);
			}
		});
	}
}
