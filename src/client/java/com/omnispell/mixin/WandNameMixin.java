package com.omnispell.mixin;

import com.omnispell.item.WandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A wand's name is drawn by the spell bar, above it, so the usual item name does not run into it. */
@Mixin(Hud.class)
public abstract class WandNameMixin {
	@Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
	private void omnispell$wandName(GuiGraphicsExtractor g, CallbackInfo ci) {
		var player = Minecraft.getInstance().player;
		if (player != null && WandItem.isWand(player.getMainHandItem())) ci.cancel();
	}
}
