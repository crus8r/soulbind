package com.omnispell.mixin;

import com.omnispell.spell.Hex;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A hexed shulker cannot teleport away. */
@Mixin(Shulker.class)
public abstract class HexShulkerMixin {
	@Inject(method = "teleportSomewhere", at = @At("HEAD"), cancellable = true)
	private void omnispell$hexed(CallbackInfoReturnable<Boolean> cir) {
		if (Hex.hexed((Shulker) (Object) this)) cir.setReturnValue(false);
	}
}
