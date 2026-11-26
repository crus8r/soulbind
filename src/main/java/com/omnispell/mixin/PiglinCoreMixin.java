package com.omnispell.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Piglins take a player holding a piglin-head wand for one of their own, as if they wore gold. */
@Mixin(PiglinAi.class)
public abstract class PiglinCoreMixin {
	@Inject(method = "isWearingSafeArmor", at = @At("HEAD"), cancellable = true)
	private static void omnispell$piglinCore(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player player && com.omnispell.item.CoreAbilities.holding(player, "piglin")) cir.setReturnValue(true);
	}
}
