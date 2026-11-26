package com.omnispell.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.omnispell.Omnispell;
import com.omnispell.item.OmniItems;
import com.omnispell.item.WandItem;
import com.omnispell.net.OmniNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class OmnispellClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Omnispell.id("omnispell"));
	public static final KeyMapping[] CAST = new KeyMapping[WandItem.SLOTS];
	public static KeyMapping HUD;
	public static KeyMapping BOOK;

	private static boolean sprintWasDown;
	private static int sprintTicks = 100;

	@Override
	public void onInitializeClient() {
		FamiliarRender.register();
		// Double-tap sprint while in the air with a wand: walk on air.
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;
			boolean down = client.options.keySprint.isDown();
			sprintTicks++;
			if (down && !sprintWasDown) {
				boolean wand = WandItem.isWand(client.player.getMainHandItem()) || WandItem.isWand(client.player.getOffhandItem());
				if (sprintTicks <= 7 && wand && !client.player.onGround() && client.gui.screen() == null) {
					ClientPlayNetworking.send(new OmniNet.CastSpell(-3));
					sprintTicks = 100;
				} else {
					sprintTicks = 0;
				}
			}
			sprintWasDown = down;
		});
		// Left-click with a wand in the main hand casts the selected spell (sneak: next slot) instead of punching.
		net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			if (!WandItem.isWand(player.getMainHandItem())) return false;
			if (clickCount > 0) {
				ClientPlayNetworking.send(new OmniNet.CastSpell(player.isShiftKeyDown() ? -2 : -1));
				player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			}
			return true;
		});
		// Armor shows its charm slots, and what is set in them.
		net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			int slots = com.omnispell.charm.Charms.slots(stack);
			if (slots == 0) return;
			java.util.List<com.omnispell.charm.Charms.Set> sets = com.omnispell.charm.Charms.stored(stack);
			lines.add(net.minecraft.network.chat.Component.literal("Charm slots: " + sets.size() + "/" + slots).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
			for (com.omnispell.charm.Charms.Set set : sets) {
				StringBuilder text = new StringBuilder(" \u25C6 " + set.gem() + ": ");
				boolean first = true;
				for (java.util.Map.Entry<String, Integer> e : set.charms().entrySet()) {
					if (!first) text.append(", ");
					text.append(com.omnispell.charm.Charms.ALL.get(e.getKey()).name()).append(' ').append(com.omnispell.charm.Charms.roman(e.getValue()));
					first = false;
				}
				lines.add(net.minecraft.network.chat.Component.literal(text.toString()).withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
			}
			for (int i = sets.size(); i < slots; i++) {
				lines.add(net.minecraft.network.chat.Component.literal(" \u25C7 empty").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
			}
		});
		int[] defaults = {InputConstants.KEY_Z, InputConstants.KEY_X, InputConstants.KEY_C, InputConstants.KEY_V, InputConstants.KEY_B};
		for (int i = 0; i < CAST.length; i++) {
			CAST[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.omnispell.cast_" + (i + 1), defaults[i], CATEGORY));
		}
		HUD = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.omnispell.hud", InputConstants.KEY_H, CATEGORY));
		BOOK = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.omnispell.book", InputConstants.KEY_G, CATEGORY));

		ClientPlayNetworking.registerGlobalReceiver(OmniNet.SyncState.TYPE, (payload, context) -> ClientState.readState(payload.json()));
		ClientPlayNetworking.registerGlobalReceiver(OmniNet.SyncVitals.TYPE, (payload, context) -> ClientState.readVitals(payload.json()));
		ClientPlayNetworking.registerGlobalReceiver(OmniNet.BookMessage.TYPE, (payload, context) -> ClientState.say(payload.ok(), payload.text()));
		ClientPlayNetworking.registerGlobalReceiver(OmniNet.OpenBook.TYPE, (payload, context) -> context.client().gui.setScreen(new BookScreen()));
		ClientPlayNetworking.registerGlobalReceiver(OmniNet.OpenWaypoint.TYPE, (payload, context) ->
				context.client().gui.setScreen(new WaypointScreen(payload.pairId(), payload.name(), payload.color())));
		ClientPlayNetworking.registerGlobalReceiver(OmniNet.ScreenFx.TYPE, (payload, context) -> {
			ClientState.flashKind = payload.kind();
			ClientState.flashIntensity = payload.intensity();
			ClientState.flashColor = payload.color();
			ClientState.flashAtTick = ClientState.clientTick;
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Omnispell.id("mana"), ManaHud::extract);

		ClientTickEvents.END_CLIENT_TICK.register(OmnispellClient::tick);
	}

	private static void tick(Minecraft client) {
		ClientState.clientTick++;
		if (client.player == null) return;
		ClientSettings settings = ClientSettings.get();
		while (HUD.consumeClick()) {
			settings.showMana = !settings.showMana;
			ClientSettings.save();
			client.player.sendOverlayMessage(Component.literal(settings.showMana ? "Mana shown" : "Mana hidden"));
		}
		while (BOOK.consumeClick()) {
			if (client.gui.screen() != null) continue;
			if (hasBook(client)) {
				ClientPlayNetworking.send(new OmniNet.BookAction("sync", ""));
				client.gui.setScreen(new BookScreen());
			} else {
				client.player.sendOverlayMessage(Component.literal("You need a Book of Words."));
			}
		}
		for (int i = 0; i < CAST.length; i++) {
			while (CAST[i].consumeClick()) {
				if (client.gui.screen() != null) continue;
				net.minecraft.world.item.ItemStack wand = WandItem.heldWand(client.player);
				if (wand.isEmpty()) continue;
				pressSpellKey(client, wand, i, settings);
			}
		}
	}

	/**
	 * First press: show the spell's name above the bar and make it the selected spell.
	 * Second press soon after (or the first, with double-tap switched off): cast it.
	 */
	private static void pressSpellKey(Minecraft client, net.minecraft.world.item.ItemStack wand, int slot, ClientSettings settings) {
		long since = ClientState.clientTick - ClientState.shownAtTick;
		// A held key repeats within the same tick or the next; that is not a second press.
		boolean second = ClientState.shownSlot == slot && since >= 2 && since <= settings.doubleTapTicks;
		// Pressing the key of the spell that is already selected, without it being a double-tap, puts the wand back to no spell: left-click is then the plain bolt.
		if (settings.doubleTap && !second && since >= 2 && WandItem.selected(wand) == slot && WandItem.spell(wand, slot) != null) {
			ClientPlayNetworking.send(new OmniNet.BookAction("select", "-1"));
			client.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("No spell selected: left-click throws a bolt"));
			ClientState.shownSlot = slot;          // a quick second press still casts it
			ClientState.shownAtTick = ClientState.clientTick;
			ClientState.hideName = true;
			return;
		}
		ClientState.hideName = false;
		ClientState.shownSlot = slot;
		ClientState.shownAtTick = ClientState.clientTick;
		if (WandItem.spell(wand, slot) == null) return;
		if (!settings.doubleTap || second) {
			ClientPlayNetworking.send(new OmniNet.CastSpell(slot));
			client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			// After a cast the next press starts over: name first, then cast.
			if (settings.doubleTap) ClientState.shownAtTick = ClientState.clientTick - settings.doubleTapTicks - 1 + 12;
			ClientState.castAtTick = ClientState.clientTick;
		} else {
			ClientPlayNetworking.send(new OmniNet.BookAction("select", String.valueOf(slot)));
		}
	}

	/** True if the player carries the book, loose or inside a bundle. */
	private static boolean hasBook(Minecraft client) {
		Inventory inventory = client.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			net.minecraft.world.item.ItemStack stack = inventory.getItem(i);
			if (stack.is(OmniItems.GRIMOIRE)) return true;
			net.minecraft.world.item.component.BundleContents contents = stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
			if (contents != null) {
				for (net.minecraft.world.item.ItemStackTemplate inside : contents.items()) {
					if (inside.item().value() == OmniItems.GRIMOIRE) return true;
				}
			}
		}
		return client.player.getAbilities().instabuild;
	}
}
