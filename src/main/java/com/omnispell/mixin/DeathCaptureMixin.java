package com.omnispell.mixin;

import com.omnispell.player.Reliquary;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Around a dying player's things falling: whatever actually falls (after Soul Bind has set aside
 * everything it keeps) is held back, so a Reliquary among it can gather the rest up.
 */
@Mixin(Player.class)
public abstract class DeathCaptureMixin {
	@Inject(method = "dropEquipment", at = @At("HEAD"))
	private void omnispell$beginCatch(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && player.isDeadOrDying()) Reliquary.begin(player);
	}

	@Inject(method = "dropEquipment", at = @At("RETURN"))
	private void omnispell$endCatch(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) Reliquary.end(player);
	}
}
