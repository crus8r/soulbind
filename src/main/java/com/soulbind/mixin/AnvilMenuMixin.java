package com.soulbind.mixin;

import com.soulbind.SoulBind;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla anvils assume enchanted books never stack. Soul Bind books do, so this
 * makes the anvil use exactly one book from each side. It also caps tools,
 * weapons and armor at Soul Bind III.
 * Anvil slots: 0 = left input, 1 = right input, 2 = result.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

	@Unique
	private boolean soulbind$trimmedResult = false;
	@Unique
	private ItemStack soulbind$leftoverLeft = ItemStack.EMPTY;
	@Unique
	private ItemStack soulbind$leftoverRight = ItemStack.EMPTY;

	@Inject(method = "createResult", at = @At("RETURN"))
	private void soulbind$oneBookOut(CallbackInfo ci) {
		AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
		ItemStack left = self.getSlot(0).getItem();
		ItemStack result = self.getSlot(2).getItem();
		soulbind$trimmedResult = false;
		if (!result.isEmpty() && !result.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) {
			SoulBind.capLevel(result, SoulBind.MAX_GEAR_LEVEL);
		}
		if (left.getCount() > 1 && SoulBind.soulBindLevel(left) > 0 && !result.isEmpty()) {
			result.setCount(1);
			soulbind$trimmedResult = true;
		}
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void soulbind$rememberStacks(CallbackInfo ci) {
		AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
		ItemStack left = self.getSlot(0).getItem();
		ItemStack right = self.getSlot(1).getItem();
		soulbind$leftoverLeft = ItemStack.EMPTY;
		soulbind$leftoverRight = ItemStack.EMPTY;
		if (soulbind$trimmedResult && left.getCount() > 1) {
			soulbind$leftoverLeft = left.copyWithCount(left.getCount() - 1);
		}
		if (right.getCount() > 1 && SoulBind.soulBindLevel(right) > 0) {
			soulbind$leftoverRight = right.copyWithCount(right.getCount() - 1);
		}
	}

	@Inject(method = "onTake", at = @At("RETURN"))
	private void soulbind$returnRest(CallbackInfo ci) {
		AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
		ItemStack left = soulbind$leftoverLeft;
		ItemStack right = soulbind$leftoverRight;
		soulbind$leftoverLeft = ItemStack.EMPTY;
		soulbind$leftoverRight = ItemStack.EMPTY;
		soulbind$trimmedResult = false;
		if (!right.isEmpty()) self.getSlot(1).set(right);
		if (!left.isEmpty()) self.getSlot(0).set(left);
	}
}
