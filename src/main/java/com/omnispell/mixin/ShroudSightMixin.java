package com.omnispell.mixin;

import com.omnispell.spell.Shroud;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A fully shrouded player cannot be seen by anything hunting for a target. */
@Mixin(LivingEntity.class)
public abstract class ShroudSightMixin {
	@Inject(method = "getVisibilityPercent", at = @At("HEAD"), cancellable = true)
	private void omnispell$unseen(ServerLevel level, Entity looker, CallbackInfoReturnable<Double> cir) {
		if ((Object) this instanceof ServerPlayer player && Shroud.active(player)) cir.setReturnValue(0.0);
	}
}
