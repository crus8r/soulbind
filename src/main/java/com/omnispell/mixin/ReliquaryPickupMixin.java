package com.omnispell.mixin;

import com.omnispell.player.Reliquary;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Touching a full Reliquary: only its owner can take it, and taking it empties it at once,
 * even into a full inventory (what does not fit lands at their feet).
 */
@Mixin(ItemEntity.class)
public abstract class ReliquaryPickupMixin {
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void omnispell$reliquary(Player player, CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		ItemStack stack = self.getItem();
		if (!Reliquary.holds(stack)) return;
		ci.cancel();
		if (!(player instanceof ServerPlayer owner) || !Reliquary.ownedBy(stack, owner) || self.hasPickUpDelay() || !owner.isAlive()) return;
		ItemStack charm = stack.copy();
		self.discard();
		ItemStack empty = Reliquary.spill(owner, charm);
		if (!owner.getInventory().add(empty)) owner.drop(empty, false, net.minecraft.util.Prediction.SERVER_ONLY);
	}
}
