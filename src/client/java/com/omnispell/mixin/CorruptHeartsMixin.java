package com.omnispell.mixin;

import com.omnispell.Omnispell;
import com.omnispell.client.ClientState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Deep in corruption, your hearts turn purple. */
@Mixin(targets = "net.minecraft.client.gui.Hud$HeartType")
public abstract class CorruptHeartsMixin {
	@Inject(method = "getSprite", at = @At("HEAD"), cancellable = true)
	private void omnispell$purple(boolean hardcore, boolean half, boolean blinking, CallbackInfoReturnable<Identifier> cir) {
		if (ClientState.corruption < 60 || !((Enum<?>) (Object) this).name().equals("NORMAL")) return;
		cir.setReturnValue(Omnispell.id("hud/heart/corrupted_" + (half ? "half" : "full") + (blinking ? "_blinking" : "")));
	}
}
