package com.omnispell.mixin;

import com.omnispell.spell.Hex;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A hexed monster's arrows, fireballs, potions, fangs and vexes never come into the world. */
@Mixin(ServerLevel.class)
public abstract class HexSpawnMixin {
	@Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
	private void omnispell$hexed(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (Hex.forbids(entity)) cir.setReturnValue(false);
	}
}
