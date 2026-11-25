package com.omnispell.item;

import com.omnispell.Omnispell;
import com.omnispell.player.PlayerData;
import com.omnispell.player.PlayerStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Bottled arcane essence, filled at an arcane well. Drinking it restores essence anywhere. */
public class ArcanePotionItem extends Item {
	public ArcanePotionItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		PlayerData data = PlayerStore.get(serverPlayer);
		int cap = Omnispell.CONFIG.essenceCap;
		if (data.essence >= cap) {
			serverPlayer.sendOverlayMessage(Component.literal("Your essence is already full."));
			return InteractionResult.FAIL;
		}
		data.essence = Math.min(cap, data.essence + Omnispell.CONFIG.essencePerPotion);
		ItemStack stack = player.getItemInHand(hand);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
			ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
			if (!player.getInventory().add(bottle)) player.drop(bottle, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 1.0f, 1.3f);
		serverPlayer.sendOverlayMessage(Component.literal("Essence " + data.essence + " / " + cap).withStyle(ChatFormatting.AQUA));
		PlayerStore.save(serverPlayer);
		PlayerStore.syncVitals(serverPlayer);
		return InteractionResult.SUCCESS;
	}
}
