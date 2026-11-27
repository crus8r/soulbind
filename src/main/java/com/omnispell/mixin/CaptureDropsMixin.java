package com.omnispell.mixin;

import com.omnispell.player.Reliquary;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While a dying player's things fall, each item is held back instead of landing in the world. */
@Mixin(ServerLevel.class)
public abstract class CaptureDropsMixin {
	@Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
	private void omnispell$catch(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof ItemEntity item && Reliquary.capturing()) {
			Reliquary.captured(item);
			cir.setReturnValue(true);
		}
	}
}
