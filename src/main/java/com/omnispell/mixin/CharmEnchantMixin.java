package com.omnispell.mixin;

import com.omnispell.charm.Charms;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Gems take charms at the enchanting table, but only when a Runic Bookshelf
 * stands beside it. Without one the table offers a gem nothing.
 */
@Mixin(EnchantmentMenu.class)
public abstract class CharmEnchantMixin {
	@Shadow @Final private Container enchantSlots;
	@Shadow @Final private ContainerLevelAccess access;
	@Shadow @Final private RandomSource random;
	@Shadow @Final private DataSlot enchantmentSeed;
	@Shadow @Final public int[] costs;
	@Shadow @Final public int[] enchantClue;
	@Shadow @Final public int[] levelClue;

	@Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
	private void omnispell$gemNeedsRunicShelf(Container container, CallbackInfo ci) {
		if (container != enchantSlots || !Charms.isGem(container.getItem(0))) return;
		if (access.evaluate(Charms::runicShelfNear, true)) return;
		for (int i = 0; i < 3; i++) {
			costs[i] = 0;
			enchantClue[i] = -1;
			levelClue[i] = -1;
		}
		((AbstractContainerMenu) (Object) this).broadcastChanges();
		ci.cancel();
	}

	@Inject(method = "getEnchantmentList", at = @At("HEAD"), cancellable = true)
	private void omnispell$charmsForGems(RegistryAccess registries, ItemStack stack, int slot, int cost, CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
		if (!Charms.isGem(stack)) return;
		random.setSeed(enchantmentSeed.get() + slot);
		cir.setReturnValue(Charms.roll(registries, random, stack, cost));
	}
}
