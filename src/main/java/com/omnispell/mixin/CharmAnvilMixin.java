package com.omnispell.mixin;

import com.omnispell.charm.Charms;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Armor plus an enchanted gem on an anvil sets the gem's charm into the armor. One gem is used. */
@Mixin(AnvilMenu.class)
public abstract class CharmAnvilMixin {
	@Shadow @Final private DataSlot cost;
	@Shadow private int repairItemCountCost;

	@Inject(method = "createResult", at = @At("RETURN"))
	private void omnispell$setCharm(CallbackInfo ci) {
		AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
		ItemStack armor = self.getSlot(0).getItem();
		ItemStack gem = self.getSlot(1).getItem();
		ItemStack merged = Charms.merge(armor, gem);
		if (merged.isEmpty()) return;
		self.getSlot(2).set(merged);
		cost.set(Charms.mergeCost(gem));
		repairItemCountCost = 1;
		self.broadcastChanges();
	}
}
