package com.omnispell.mixin;

import com.mojang.serialization.DataResult;
import com.omnispell.item.OmniItems;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A bundle weighs an item by how small its stack is, so anything that does not
 * stack fills a bundle by itself. The wand, the book and the other single
 * Omnispell things take one of the 64 places instead.
 */
@Mixin(BundleContents.class)
public abstract class BundleWeightMixin {
	@Inject(method = "getWeight", at = @At("HEAD"), cancellable = true)
	private static void omnispell$lightKit(ItemInstance item, CallbackInfoReturnable<DataResult<Fraction>> cir) {
		if (item.is(OmniItems.WAND) || item.is(OmniItems.GRIMOIRE) || item.is(OmniItems.WAYPOINT_ITEM) || item.is(OmniItems.ARCANE_FOCUS)
				|| item.is(OmniItems.FAMILIAR) || item.is(OmniItems.FAMILIAR_EGG)) {
			cir.setReturnValue(DataResult.success(Fraction.getFraction(1, 64)));
		}
	}
}
