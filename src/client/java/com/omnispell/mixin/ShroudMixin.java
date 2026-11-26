package com.omnispell.mixin;

import com.omnispell.client.ClientState;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A fully shrouded player shows nothing at all: no armour, no hat or robe, nothing in the hands. */
@Mixin(HumanoidMobRenderer.class)
public abstract class ShroudMixin {
	@Inject(method = "extractHumanoidRenderState", at = @At("RETURN"))
	private static void omnispell$shroud(LivingEntity entity, HumanoidRenderState state, float partialTick, ItemModelResolver resolver, CallbackInfo ci) {
		if (!ClientState.isShrouded(entity.getId())) return;
		state.headEquipment = ItemStack.EMPTY;
		state.chestEquipment = ItemStack.EMPTY;
		state.legsEquipment = ItemStack.EMPTY;
		state.feetEquipment = ItemStack.EMPTY;
		state.headItem.clear();
		state.rightHandItemState.clear();
		state.leftHandItemState.clear();
		state.rightHandItemStack = ItemStack.EMPTY;
		state.leftHandItemStack = ItemStack.EMPTY;
	}
}
