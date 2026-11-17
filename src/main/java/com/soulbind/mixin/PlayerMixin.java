package com.soulbind.mixin;

import com.soulbind.SoulBind;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla drops up to 7 XP points per level as orbs when a player dies, which at
 * low levels is nearly everything they had. XP saved by Soul Bind armor is taken
 * out of that pile first, so the saved XP is real and the orbs hold only the rest.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

	@Inject(method = "getBaseExperienceReward", at = @At("RETURN"), cancellable = true)
	private void soulbind$shrinkOrbs(CallbackInfoReturnable<Integer> cir) {
		int orbs = cir.getReturnValue();
		if (orbs <= 0) return;
		Player self = (Player) (Object) this;
		int kept = SoulBind.keptXp(self);
		if (kept <= 0) return;
		int left = Math.max(0, SoulBind.totalXp(self) - kept);
		cir.setReturnValue(Math.min(orbs, left));
	}
}
