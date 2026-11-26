package com.omnispell.item;

import com.omnispell.player.Mana;
import com.omnispell.player.ManaPool;
import com.omnispell.player.PlayerStore;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** The powers of wand cores that do more than add numbers. */
public final class CoreAbilities {
	private static final Identifier HEAVY_ID = com.omnispell.Omnispell.id("heavy_core");

	private CoreAbilities() {
	}

	/** The ability of the wand in hand, or null. */
	public static String held(Player player) {
		ItemStack wand = WandItem.heldWand(player);
		return wand.isEmpty() ? null : WandParts.core(WandItem.core(wand)).ability;
	}

	public static boolean holding(Player player, String ability) {
		return ability.equals(held(player));
	}

	/** True if a wand with this ability is in either hand or on the hotbar. */
	public static boolean carrying(Player player, String ability) {
		if (has(player.getOffhandItem(), ability)) return true;
		for (int i = 0; i < 9; i++) if (has(player.getInventory().getItem(i), ability)) return true;
		return false;
	}

	private static boolean has(ItemStack stack, String ability) {
		return WandItem.isWand(stack) && ability.equals(WandParts.core(WandItem.core(stack)).ability);
	}

	private static void keep(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
		MobEffectInstance now = player.getEffect(effect);
		if (now != null && (now.getAmplifier() > amplifier || now.getDuration() > 60)) return;
		player.addEffect(new MobEffectInstance(effect, 50, amplifier, true, false, true));
	}

	/** Every tick: the falling check has to be quick. */
	public static void tick(ServerPlayer player) {
		if (player.fallDistance > 2.5 && !player.onGround() && holding(player, "phantom")) keep(player, MobEffects.SLOW_FALLING, 0);
	}

	/** Once a second: the steady powers of a held core. */
	public static void everySecond(ServerPlayer player) {
		String ability = held(player);
		AttributeInstance kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kb != null) {
			if ("heavy".equals(ability)) kb.addOrUpdateTransientModifier(new AttributeModifier(HEAVY_ID, 0.8, AttributeModifier.Operation.ADD_VALUE));
			else kb.removeModifier(HEAVY_ID);
		}
		if (ability == null) return;
		switch (ability) {
			case "beacon" -> {
				keep(player, MobEffects.HASTE, 0);
				keep(player, MobEffects.SPEED, 0);
			}
			case "sea" -> {
				if (player.isInWater()) keep(player, MobEffects.CONDUIT_POWER, 0);
			}
			case "rabbit" -> keep(player, MobEffects.JUMP_BOOST, 0);
			case "wither_rose" -> player.removeEffect(MobEffects.WITHER);
			default -> {
			}
		}
	}

	/** A spell hit a creature: the core leaves its mark. */
	public static void onStrike(ServerPlayer player, LivingEntity target, float dealt, boolean crit, boolean first) {
		String ability = held(player);
		if (ability == null || !target.isAlive()) return;
		ServerLevel level = player.level();
		switch (ability) {
			case "wither" -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1), player);
			case "wither_rose" -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0), player);
			case "shulker" -> {
				if (first) target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 25, 0), player);
			}
			case "heavy" -> {
				if (!first) return;
				Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1);
				if (away.lengthSqr() < 0.0001) away = player.getLookAngle().multiply(1, 0, 1);
				away = away.normalize().scale(1.6);
				target.push(away.x, 0.45, away.z);
				target.syncVelocity = true;
			}
			case "creeper" -> {
				if (!crit || !first) return;
				Vec3 at = target.position();
				level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
				level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.2f);
				for (LivingEntity other : com.omnispell.spell.Targeting.around(player, at, 3.5, false)) {
					if (other == target) continue;
					other.hurtServer(level, level.damageSources().explosion(player, player), Math.max(1f, dealt * 0.4f));
				}
			}
			default -> {
			}
		}
	}

	/** Something the player held a wand against died. */
	public static void onKill(ServerPlayer player) {
		String ability = held(player);
		if ("zombie".equals(ability)) {
			player.getFoodData().eat(3, 0.4f);
			player.heal(2f);
		} else if ("sculk".equals(ability)) {
			refill(player, ManaPool.SPIRIT, 5);
			refill(player, ManaPool.FORCE, 5);
			player.level().sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
		}
	}

	private static void refill(ServerPlayer player, ManaPool pool, double amount) {
		double[] mana = PlayerStore.get(player).mana;
		mana[pool.ordinal()] = Math.min(Mana.max(player, pool), mana[pool.ordinal()] + amount);
	}

	private static boolean projectile(DamageSource source) {
		return source.is(DamageTypeTags.IS_PROJECTILE);
	}

	/** Incoming harm to a player, before the ward and charms see it. */
	public static float incoming(ServerPlayer player, DamageSource source, float amount) {
		if (amount <= 0 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
		String ability = held(player);
		if (projectile(source)) {
			if ("ender".equals(ability) && player.getRandom().nextFloat() < 0.35f) {
				player.level().sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.6, 0.3, 0.3);
				player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.4f);
				return 0;
			}
			if ("skeleton".equals(ability)) amount *= 0.75f;
		}
		// The enchanted apple: fire, lava and arrows only wear you down, like poison, while Barrier lasts.
		boolean burning = source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR);
		if ((burning || projectile(source)) && carrying(player, "apple") && Mana.available(player, ManaPool.BARRIER) > 0) {
			float half = amount * 0.5f;
			double paid = player.getAbilities().instabuild ? half : Mana.spend(player, ManaPool.BARRIER, half * 3.0) / 3.0;
			amount -= (float) paid;
			if (Mana.available(player, ManaPool.BARRIER) > 0 || paid >= half) {
				float floor = player.getHealth() + player.getAbsorptionAmount() - 1.0f;
				amount = Math.max(0, Math.min(amount, floor));
			}
		}
		return amount;
	}
}
