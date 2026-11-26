package com.omnispell.familiar;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A familiar's own body. One creature with one skeleton; which parts show and
 * which skin it wears depend on its species. Where it goes and what it fights
 * is decided by Familiars, not by goals of its own.
 */
public class FamiliarEntity extends PathfinderMob {
	private static final EntityDataAccessor<String> SPECIES = SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.STRING);

	public FamiliarEntity(EntityType<? extends FamiliarEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return createMobAttributes().add(Attributes.MAX_HEALTH, 16).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 32);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SPECIES, "griffin");
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	public String species() {
		return entityData.get(SPECIES);
	}

	public void setSpecies(String species) {
		entityData.set(SPECIES, species);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}
}
