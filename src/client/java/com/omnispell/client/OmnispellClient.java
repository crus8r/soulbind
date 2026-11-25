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

	@Override
	public void onInitializeClient() {
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
		while (HUD.consumeClick()) {
			ClientState.hudVisible = !ClientState.hudVisible;
			client.player.sendOverlayMessage(Component.literal(ClientState.hudVisible ? "Mana shown" : "Mana hidden"));
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
				if (client.gui.screen() == null && !WandItem.heldWand(client.player).isEmpty()) {
					ClientPlayNetworking.send(new OmniNet.CastSpell(i));
					client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				}
			}
		}
	}

	private static boolean hasBook(Minecraft client) {
		Inventory inventory = client.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(OmniItems.GRIMOIRE)) return true;
		}
		return client.player.getAbilities().instabuild;
	}
}
