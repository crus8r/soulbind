package com.omnispell.mixin;

import com.omnispell.client.ClientSettings;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dark sight: a wizard's eyes make out shapes in the dark. Not the Night Vision
 * effect, only a share of it, so caves and nights are dim instead of black.
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class DarkSightMixin {
	@Inject(method = "extract", at = @At("RETURN"))
	private void omnispell$darkSight(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		float sight = ClientSettings.get().darkSight;
		if (sight <= 0 || state.nightVisionEffectIntensity >= sight) return;
		state.nightVisionEffectIntensity = sight;
		state.nightVisionColor = LightmapRenderStateExtractor.WHITE;
		state.needsUpdate = true;
	}
}
