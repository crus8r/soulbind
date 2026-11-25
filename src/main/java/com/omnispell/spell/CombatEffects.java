package com.omnispell.spell;

import com.omnispell.Omnispell;
import com.omnispell.player.Mana;
import com.omnispell.player.ManaPool;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Carries out a combat spell exactly as the generator described it: the effect
 * type, the numbers, the status and the toggles all come from the spell's effect
 * code. Only the translation from an arena turn to Minecraft is decided here.
 */
public final class CombatEffects {
	private static final net.minecraft.resources.Identifier SHIELD_ID = Omnispell.id("spell_shield");

	private CombatEffects() {
	}

	public static boolean cast(Cast cast) {
		Spell spell = cast.spell;
		Spell.EffectCode code = spell.code;
		ServerPlayer player = cast.player;
		long now = Tasks.now();

		// Switch the spell's toggles on first, so this very cast benefits from them, as in the arena.
		String summon = null;
		for (String text : code.modifiers) {
			Modifiers.Active modifier = Modifiers.parse(text, now);
			if (modifier == null) continue;
			switch (modifier.id) {
				case "summoned_creature" -> summon = text;
				case "cleanse" -> cleanse(player);
				case "fortune" -> player.addEffect(new MobEffectInstance(MobEffects.LUCK, (int) (modifier.until - now), modifier.value >= 30 ? 1 : 0));
				case "fire_resistance" -> {
					if (modifier.value >= 50) player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, (int) (modifier.until - now), 0));
					Modifiers.add(player, modifier);
				}
				default -> Modifiers.add(player, modifier);
			}
		}

		int durationTurns = Math.max(code.duration, 0) + (int) Modifiers.value(player, "debuff_extend") * 0;
		Vec3 hand = Targeting.hand(player);
		boolean did;
		switch (code.type) {
			case "heal" -> did = heal(cast, code.primaryValue);
			case "shield" -> did = shield(cast, code.primaryValue, Math.max(1, durationTurns));
			case "buff", "utility" -> {
				did = buff(cast, Math.max(2, durationTurns));
				Fx.play(cast.level, spell.visual, hand, player.position());
			}
			case "summon" -> did = true; // the creature itself comes from the summoned_creature toggle below
			case "debuff", "status" -> did = afflict(cast, Math.max(1, durationTurns));
			default -> did = attack(cast);
		}

		if (summon != null || code.type.equals("summon")) did |= summon(cast, summon);

		// A spell of another type can still carry a secondary boost or a status.
		if (did && !code.type.equals("buff") && !code.type.equals("utility")) applySecondary(cast, Math.max(2, durationTurns));
		if (did) Fx.sound(cast.level, player.position(), code.element, 0.9f);
		return did;
	}

	// ----- attack -----

	private static boolean attack(Cast cast) {
		ServerPlayer player = cast.player;
		Spell.EffectCode code = cast.spell.code;
		LivingEntity target = Targeting.entity(player, cast.range);
		Vec3 hand = Targeting.hand(player);
		if (target == null) {
			Vec3 end = Targeting.point(player, cast.range);
			Fx.play(cast.level, cast.spell.visual, hand, end);
			cast.cooldownFactor = 0.5; // a miss costs less
			return true;
		}
		Fx.play(cast.level, cast.spell.visual, hand, target.getBoundingBox().getCenter());
		double base = code.primaryValue > 0 ? code.primaryValue : cast.spell.tier * 10.0;
		strike(cast, target, base, true);
		if (!code.status.equals("none") && !code.status.equals("null") && target.isAlive()) {
			status(cast, target, code.status, Math.max(1, code.duration));
		}
		return true;
	}

	/** Deals a spell's damage to one target, with every offensive toggle the caster has switched on. */
	static void strike(Cast cast, LivingEntity target, double websiteDamage, boolean mayRepeat) {
		ServerPlayer player = cast.player;
		String element = cast.spell.code.element;
		double damage = websiteDamage;

		damage *= 1.0 + Modifiers.value(player, "damage_amplify") / 100.0;
		damage *= 1.0 + Modifiers.value(player, "elemental_surge") / 100.0;
		if (player.getHealth() < player.getMaxHealth() * 0.3) damage *= 1.0 + Modifiers.value(player, "berserker") / 100.0;
		if (target.getHealth() < target.getMaxHealth() * 0.3) damage *= 1.0 + Modifiers.value(player, "execute") / 100.0;
		damage *= 1.0 + Modifiers.streak(player) * Modifiers.value(player, "momentum") / 100.0;
		if (mayRepeat) damage *= 1.0 + Modifiers.consume(player, "overcharge") / 100.0;
		// Armor pierce ignores flat defense on the website; spell damage already ignores armor here, so it is bonus damage.
		damage += Modifiers.value(player, "armor_pierce");
		if (target.getAbsorptionAmount() > 0) damage *= 1.0 + Modifiers.value(player, "phantom_strike") / 200.0;

		double gambit = mayRepeat ? Modifiers.value(player, "gambit") : 0;
		if (gambit > 0 && player.getHealth() > 2) {
			float sacrifice = (float) Math.min(player.getHealth() - 1, player.getHealth() * gambit / 100.0);
			player.hurtServer(cast.level, cast.level.damageSources().magic(), sacrifice);
			damage += sacrifice / cast.scale;
		}

		double critChance = Modifiers.value(player, "critical_strike") + cast.core.crit;
		boolean crit = critChance > 0 && player.getRandom().nextDouble() * 100 < critChance;
		if (crit) damage *= 2.0;

		float amount = (float) (damage * cast.scale * cast.damageBonus(element));
		float before = target.getHealth();
		Modifiers.withSpellElement(element, () ->
				target.hurtServer(cast.level, cast.level.damageSources().indirectMagic(player, player), amount));
		float dealt = Math.max(0, before - target.getHealth());
		if (crit) cast.level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 12, 0.3, 0.3, 0.3, 0.3);

		elementRider(cast, target, element);

		double siphon = Modifiers.value(player, "life_siphon");
		if (siphon > 0 && dealt > 0) {
			double paid = Mana.spend(player, ManaPool.RESTORATION, dealt * siphon / 100.0 / cast.scale) * cast.scale;
			if (paid > 0) player.heal((float) paid);
		}
		double bleed = Modifiers.value(player, "bleed");
		if (bleed > 0 && target.isAlive()) MobControl.dot(player, target, (float) (bleed * cast.scale), 3, "blood");
		if (Modifiers.has(player, "spell_vampirism") && dealt > 0) Casting.shortenRandomCooldown(player, cast.turns(1));

		if (!mayRepeat) return;
		double chain = Modifiers.value(player, "chain_lightning");
		if (chain > 0) {
			int jumps = 0;
			Vec3 from = target.getBoundingBox().getCenter();
			for (LivingEntity other : Targeting.around(player, target.position(), 6, false)) {
				if (other == target || jumps >= 3) continue;
				Fx.line(cast.level, from, other.getBoundingBox().getCenter(), Fx.color(cast.spell.visual.color1), ParticleTypes.ELECTRIC_SPARK, 2);
				strike(cast, other, websiteDamage * chain / 100.0, false);
				jumps++;
			}
		}
		double multi = Modifiers.value(player, "multi_strike");
		if (multi > 0 && target.isAlive() && player.getRandom().nextDouble() * 100 < multi) {
			target.setInvulnerableTime(0);
			strike(cast, target, websiteDamage, false);
		}
		double echo = Modifiers.value(player, "spell_echo");
		if (echo > 0 && target.isAlive() && player.getRandom().nextDouble() * 100 < echo) {
			Tasks.later(8, () -> {
				if (!target.isAlive()) return;
				Fx.play(cast.level, cast.spell.visual, Targeting.hand(player), target.getBoundingBox().getCenter());
				target.setInvulnerableTime(0);
				strike(cast, target, websiteDamage * 0.5, false);
			});
		}
	}

	private static void elementRider(Cast cast, LivingEntity target, String element) {
		int tier = cast.spell.tier;
		switch (element == null ? "" : element) {
			case "fire" -> target.igniteForSeconds(2 + tier);
			case "ice" -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40 + tier * 20, tier >= 4 ? 1 : 0), cast.player);
			case "light" -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0), cast.player);
			case "nature" -> {
				if (tier >= 3) target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), cast.player);
			}
			case "shadow" -> {
				if (target instanceof ServerPlayer) target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), cast.player);
			}
			default -> {
			}
		}
	}

	// ----- statuses -----

	/** Applies one of the generator's statuses for a number of turns. Control statuses are paid for with Control mana. */
	static void status(Cast cast, LivingEntity target, String status, int turns) {
		ServerPlayer player = cast.player;
		double wanted = turns + Modifiers.value(player, "debuff_extend");
		switch (status) {
			case "poison" -> target.addEffect(new MobEffectInstance(MobEffects.POISON, cast.turns(wanted), cast.spell.tier >= 4 ? 1 : 0), player);
			case "distracted" -> MobControl.apply(player, target, "distracted", cast.turns(wanted), null);
			case "stun", "charm", "fear", "confusion" -> {
				if (MobControl.immune(target)) {
					cast.tell(target.getName().getString() + " resists.");
					return;
				}
				double paid = Mana.spend(player, ManaPool.CONTROL, wanted);
				if (paid < 0.5) {
					cast.tell("Your Control mana is spent.");
					return;
				}
				MobControl.apply(player, target, status, cast.turns(paid), null);
			}
			default -> {
			}
		}
	}

	private static boolean afflict(Cast cast, int turns) {
		ServerPlayer player = cast.player;
		Spell.EffectCode code = cast.spell.code;
		LivingEntity target = Targeting.entity(player, cast.range);
		Vec3 hand = Targeting.hand(player);
		if (target == null) {
			Fx.play(cast.level, cast.spell.visual, hand, Targeting.point(player, cast.range));
			cast.cooldownFactor = 0.5;
			return true;
		}
		Fx.play(cast.level, cast.spell.visual, hand, target.getBoundingBox().getCenter());
		boolean hasStatus = !code.status.equals("none") && !code.status.equals("null");
		if (hasStatus) {
			status(cast, target, code.status, turns);
		} else {
			// A plain debuff: the target is weakened and slowed for the duration.
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, cast.turns(turns), cast.spell.tier >= 4 ? 1 : 0), player);
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, cast.turns(turns), 0), player);
		}
		// Offensive spell types that debuff still sting a little, scaled well below a true attack.
		if (cast.spell.type.equals("attack") && code.primaryValue > 0) strike(cast, target, code.primaryValue, true);
		return true;
	}

	// ----- self effects -----

	private static boolean heal(Cast cast, double websiteAmount) {
		ServerPlayer player = cast.player;
		double want = websiteAmount > 0 ? websiteAmount : cast.spell.tier * 8.0 + 10;
		double paid = Mana.spend(player, ManaPool.RESTORATION, want);
		if (paid < 1) return cast.fail("Your Restoration mana is spent.");
		player.heal((float) (paid * cast.scale * cast.mendingBonus()));
		Fx.play(cast.level, cast.spell.visual, Targeting.hand(player), player.position());
		cast.level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.6, player.getZ(), 4, 0.4, 0.3, 0.4, 0.0);
		return true;
	}

	private static boolean shield(Cast cast, double websiteAmount, int turns) {
		ServerPlayer player = cast.player;
		double want = websiteAmount > 0 ? websiteAmount : cast.spell.tier * 5.0 + 10;
		want *= 1.0 + Modifiers.value(player, "shield_overcharge") / 100.0;
		double paid = Mana.spend(player, ManaPool.BARRIER, want);
		if (paid < 1) return cast.fail("Your Barrier mana is spent.");
		giveShield(cast, (float) (paid * cast.scale * cast.mendingBonus()), cast.turns(turns));
		Fx.play(cast.level, cast.spell.visual, Targeting.hand(player), player.position());
		return true;
	}

	/** A shield is absorption health that lasts for a set time. A new shield replaces a weaker one. */
	static void giveShield(Cast cast, float amount, int ticks) {
		ServerPlayer player = cast.player;
		AttributeInstance max = player.getAttribute(Attributes.MAX_ABSORPTION);
		if (max == null) return;
		float total = Math.max(amount, player.getAbsorptionAmount());
		max.addOrUpdateTransientModifier(new AttributeModifier(SHIELD_ID, total, AttributeModifier.Operation.ADD_VALUE));
		player.setAbsorptionAmount(total);
		long token = Tasks.now() + ticks;
		SHIELD_UNTIL.put(player.getUUID(), token);
		Tasks.later(ticks, () -> {
			if (SHIELD_UNTIL.getOrDefault(player.getUUID(), 0L) != token) return; // a newer shield took over
			SHIELD_UNTIL.remove(player.getUUID());
			AttributeInstance instance = player.getAttribute(Attributes.MAX_ABSORPTION);
			if (instance != null) instance.removeModifier(SHIELD_ID);
			player.setAbsorptionAmount(Math.min(player.getAbsorptionAmount(), player.getMaxAbsorption()));
		});
	}

	private static final java.util.Map<java.util.UUID, Long> SHIELD_UNTIL = new java.util.HashMap<>();

	private static boolean buff(Cast cast, int turns) {
		ServerPlayer player = cast.player;
		Spell.EffectCode code = cast.spell.code;
		boolean any = applySecondary(cast, turns) || !code.modifiers.isEmpty();
		if (!any) {
			// A buff with nothing else attached: the generator's number becomes a damage boost.
			double percent = code.primaryValue > 0 ? code.primaryValue : cast.spell.tier * 10.0;
			Modifiers.add(player, "damage_amplify", percent, cast.turns(turns));
		}
		if (!code.status.equals("none") && !code.status.equals("null")) {
			LivingEntity target = Targeting.entity(player, cast.range);
			if (target != null) status(cast, target, code.status, turns);
		}
		return true;
	}

	private static boolean applySecondary(Cast cast, int turns) {
		String secondary = cast.spell.code.secondary;
		if (secondary == null || secondary.isBlank() || secondary.equals("none")) return false;
		ServerPlayer player = cast.player;
		int ticks = cast.turns(turns);
		boolean strong = cast.spell.tier >= 4;
		switch (secondary) {
			case "resistance_fire" -> {
				player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, ticks, 0));
				Modifiers.add(player, "fire_resistance", 50, ticks);
			}
			case "resistance_ice" -> Modifiers.add(player, "ice_resistance", 50, ticks);
			case "resistance_shadow" -> Modifiers.add(player, "shadow_resistance", 50, ticks);
			case "attack_boost" -> {
				player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, strong ? 1 : 0));
				Modifiers.add(player, "damage_amplify", Math.max(Modifiers.value(player, "damage_amplify"), 10 + cast.spell.tier * 5), ticks);
			}
			case "defense_boost" -> player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks, strong ? 1 : 0));
			case "speed_boost" -> player.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, strong ? 1 : 0));
			default -> {
				return false;
			}
		}
		return true;
	}

	private static void cleanse(ServerPlayer player) {
		List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> remove = new ArrayList<>();
		for (MobEffectInstance instance : player.getActiveEffects()) {
			if (!instance.getEffect().value().isBeneficial()) remove.add(instance.getEffect());
		}
		for (var effect : remove) player.removeEffect(effect);
		player.clearFire();
		player.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
	}

	// ----- conjuration -----

	/**
	 * Conjures an ally that fights beside the caster for a few turns, then despawns.
	 * {@code text} is the generator's "summoned_creature:DAMAGE:DURATION:NAME" toggle.
	 */
	private static boolean summon(Cast cast, String text) {
		ServerPlayer player = cast.player;
		double damage = cast.spell.code.primaryValue > 0 ? cast.spell.code.primaryValue : cast.spell.tier * 6.0;
		double turns = Math.max(3, cast.spell.code.duration);
		String name = "Conjured Spirit";
		if (text != null) {
			String[] parts = text.split(":");
			try {
				if (parts.length > 1) damage = Double.parseDouble(parts[1].trim());
				if (parts.length > 2) turns = Double.parseDouble(parts[2].trim());
			} catch (NumberFormatException ignored) {
			}
			if (parts.length > 3 && !parts[3].isBlank()) name = parts[3].trim().replace('_', ' ');
		}
		Wolf wolf = EntityTypes.WOLF.create(cast.level, EntitySpawnReason.MOB_SUMMONED);
		if (wolf == null) return cast.fail("The conjuration finds no purchase here.");
		Vec3 at = player.position().add(player.getLookAngle().multiply(1.5, 0, 1.5));
		wolf.snapTo(at.x, player.getY(), at.z, player.getYRot(), 0);
		wolf.tame(player);
		wolf.setCustomName(Component.literal(name));
		wolf.setCustomNameVisible(true);
		wolf.addTag(Summons.TAG);
		AttributeInstance attack = wolf.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) attack.setBaseValue(Math.max(2.0, damage * cast.scale * cast.damageBonus(cast.spell.code.element)));
		AttributeInstance health = wolf.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) health.setBaseValue(12 + cast.spell.tier * 6);
		wolf.setHealth(wolf.getMaxHealth());
		wolf.addEffect(new MobEffectInstance(MobEffects.GLOWING, cast.turns(turns), 0, false, false));
		cast.level.addFreshEntity(wolf);
		Summons.track(wolf, cast.turns(turns));
		Fx.play(cast.level, cast.spell.visual, Targeting.hand(player), wolf.position());
		LivingEntity target = Targeting.entity(player, cast.range);
		if (target != null && target != wolf) wolf.setTarget(target);
		return true;
	}
}
