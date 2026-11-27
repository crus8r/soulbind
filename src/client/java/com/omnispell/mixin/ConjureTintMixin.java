package com.omnispell.mixin;

import com.omnispell.client.ClientState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A conjured creature takes on the colour of the magic that made it. */
@Mixin(LivingEntityRenderer.class)
public abstract class ConjureTintMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
	private void omnispell$noteTint(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		Integer tint = ClientState.conjureTints.get(entity.getId());
		if (tint != null) ClientState.tintedStates.put(state, tint);
		else ClientState.tintedStates.remove(state);
	}

	@Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true)
	private void omnispell$tint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
		Integer tint = ClientState.tintedStates.get(state);
		if (tint != null) cir.setReturnValue(tint);
	}
}
