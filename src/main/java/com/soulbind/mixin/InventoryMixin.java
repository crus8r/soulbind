package com.soulbind.mixin;

import com.soulbind.SoulBind;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Inventory.dropAll() is what scatters a player's items on death.
 * We lift soul-bound items out just before it runs and put them back in the
 * same slots just after, so they never hit the ground.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {

	@Unique
	private final Map<Integer, ItemStack> soulbind$kept = new HashMap<>();

	@Inject(method = "dropAll", at = @At("HEAD"))
	private void soulbind$stash(CallbackInfo ci) {
		Inventory self = (Inventory) (Object) this;
		soulbind$kept.clear();
		for (int i = 0; i < self.getContainerSize(); i++) {
			ItemStack stack = self.getItem(i);
			if (stack.is(com.omnispell.item.OmniItems.GRIMOIRE) || stack.is(com.omnispell.item.OmniItems.WIZARD_HAT) || SoulBind.payDeathCost(stack)) {
				soulbind$kept.put(i, stack);
				self.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	@Inject(method = "dropAll", at = @At("RETURN"))
	private void soulbind$restore(CallbackInfo ci) {
		Inventory self = (Inventory) (Object) this;
		for (Map.Entry<Integer, ItemStack> entry : soulbind$kept.entrySet()) {
			self.setItem(entry.getKey(), entry.getValue());
		}
		soulbind$kept.clear();
	}
}
