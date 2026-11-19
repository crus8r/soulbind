package com.soulbind.mixin;

import com.soulbind.SoulBind;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.DigDurabilityEnchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets Unbreaking go on bundles, shulker boxes and ender chests in an anvil. */
@Mixin(DigDurabilityEnchantment.class)
public abstract class DigDurabilityEnchantmentMixin {

	@Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
	private void soulbind$containers(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (SoulBind.isContainer(stack)) cir.setReturnValue(true);
	}
}
