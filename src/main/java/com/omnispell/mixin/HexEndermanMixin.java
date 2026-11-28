package com.omnispell.mixin;

import com.omnispell.spell.Hex;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A hexed enderman cannot teleport. */
@Mixin(Enderman.class)
public abstract class HexEndermanMixin {
	@Inject(method = "teleport(DDD)Z", at = @At("HEAD"), cancellable = true)
	private void omnispell$hexed(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (Hex.hexed((Enderman) (Object) this)) cir.setReturnValue(false);
	}
}
