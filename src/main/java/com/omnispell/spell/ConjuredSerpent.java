package com.omnispell.spell;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * A conjured serpent: a long, hovering body that swims through the air the way a
 * water snake swims through a river. It has no goals of its own; Conjuring moves
 * it and decides what it bites.
 */
public class ConjuredSerpent extends PathfinderMob {
	public ConjuredSerpent(EntityType<? extends ConjuredSerpent> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.FOLLOW_RANGE, 32);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
		return false;
	}
}
