package com.omnispell.mixin;

import com.omnispell.spell.Modifiers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lets Omnispell's defensive toggles (fortify, resistances, mana shield) change
 * how much damage a hit does, before armor and everything else is applied.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float omnispell$modifyDamage(float damage, ServerLevel level, DamageSource source) {
		return Modifiers.modifyIncoming((LivingEntity) (Object) this, source, damage);
	}
}
