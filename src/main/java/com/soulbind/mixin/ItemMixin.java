package com.soulbind.mixin;

import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In 1.21.1 the bundle is only usable in worlds created with the "Bundle"
 * experiment. Telling the game it needs no experiment turns it on everywhere,
 * using the normal vanilla item, look and behaviour.
 */
@Mixin(Item.class)
public abstract class ItemMixin {

	@Inject(method = "requiredFeatures", at = @At("HEAD"), cancellable = true)
	private void soulbind$bundleAlwaysOn(CallbackInfoReturnable<FeatureFlagSet> cir) {
		if ((Object) this instanceof BundleItem) {
			cir.setReturnValue(FeatureFlagSet.of());
		}
	}
}
