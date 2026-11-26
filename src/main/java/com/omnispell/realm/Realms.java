package com.omnispell.realm;

import com.omnispell.Omnispell;
import com.omnispell.item.OmniItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The realms beyond the overworld and what Omnispell asks of them. The Enchanted
 * Forest is Omnispell's own: a high-tier place where everything hostile is
 * stronger, and the only source of faewood, fae dust and familiar eggs.
 */
public final class Realms {
	public static final ResourceKey<Level> FOREST = ResourceKey.create(Registries.DIMENSION, Omnispell.id("enchanted_forest"));
	private static final Identifier EMPOWERED = Omnispell.id("forest_empowered");
	private static final String EMPOWERED_TAG = "omnispell_forest_empowered";

	private Realms() {
	}

	public static boolean isForest(Level level) {
		return level.dimension() == FOREST;
	}

	/** How many steps rarer a scroll is for where it drops: the Nether +1, the Forest and other mods' realms +2, the End +2. */
	public static int rarityBonus(Level level) {
		ResourceKey<Level> key = level.dimension();
		if (key == Level.OVERWORLD) return 0;
		if (key == Level.NETHER) return 1;
		return 2;
	}

	/** Hostile creatures in the forest have half again the health and damage of their kind elsewhere. */
	public static void onEntityLoad(Entity entity, ServerLevel level) {
		if (!isForest(level) || !(entity instanceof Mob mob) || !(entity instanceof Enemy)) return;
		if (mob.entityTags().contains(EMPOWERED_TAG)) return;
		mob.addTag(EMPOWERED_TAG);
		boost(mob.getAttribute(Attributes.MAX_HEALTH), 0.5);
		boost(mob.getAttribute(Attributes.ATTACK_DAMAGE), 0.5);
		boost(mob.getAttribute(Attributes.FOLLOW_RANGE), 0.25);
		mob.setHealth(mob.getMaxHealth());
	}

	private static void boost(AttributeInstance attribute, double fraction) {
		if (attribute != null && !attribute.hasModifier(EMPOWERED)) {
			attribute.addPermanentModifier(new AttributeModifier(EMPOWERED, fraction, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/** Anything hostile slain by a player in the forest may leave fae dust. */
	public static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer) || !(entity instanceof Enemy) || !isForest(entity.level())) return;
		if (entity.getRandom().nextFloat() < 0.05f) {
			ItemEntity egg = new ItemEntity(entity.level(), entity.getX(), entity.getY() + 0.5, entity.getZ(), com.omnispell.familiar.Familiars.randomEgg(entity.getRandom()));
			egg.setDefaultPickUpDelay();
			entity.level().addFreshEntity(egg);
		}
		if (entity.getRandom().nextFloat() < 0.35f) {
			ItemEntity item = new ItemEntity(entity.level(), entity.getX(), entity.getY() + 0.5, entity.getZ(), new ItemStack(OmniItems.FAE_DUST, 1 + entity.getRandom().nextInt(2)));
			item.setDefaultPickUpDelay();
			entity.level().addFreshEntity(item);
		}
	}
}
