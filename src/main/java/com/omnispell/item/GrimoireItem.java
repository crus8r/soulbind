package com.omnispell.item;

import com.omnispell.net.OmniNet;
import com.omnispell.player.PlayerStore;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The book: dictionary, spell crafting, spellbook, wand loadout and guide. Your words live in you, not in the book. */
public class GrimoireItem extends Item {
	public GrimoireItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			PlayerStore.syncState(serverPlayer);
			PlayerStore.syncVitals(serverPlayer);
			ServerPlayNetworking.send(serverPlayer, new OmniNet.OpenBook(""));
		}
		return InteractionResult.SUCCESS;
	}
}
