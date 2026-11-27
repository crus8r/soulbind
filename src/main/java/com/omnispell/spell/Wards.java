package com.omnispell.spell;

import com.omnispell.data.Word;
import com.omnispell.data.WordBank;
import com.omnispell.player.Mana;
import com.omnispell.player.ManaPool;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * What a defense spell looks like and does, beyond its extra hearts. Every shield raises a
 * ward you can see, shaped by the spell's element: a fire shield is a shell of flame that
 * burns what strikes it, an ice shield slows and freezes, wind turns arrows aside, shadow
 * lets blows slip past. The command adds its own habit on top: Repel throws attackers
 * back, Reflect returns their blows, Absorb drinks a hit into mana, and so on.
 */
public final class Wards {
	private Wards() {
	}

	private static final class Ward {
		final Cast cast;
		final String element;
		final String habit;
		final int tier;
		final long until;
		final int color;

		Ward(Cast cast, String element, String habit, int tier, long until, int color) {
			this.cast = cast;
			this.element = element;
			this.habit = habit;
			this.tier = tier;
			this.until = until;
			this.color = color;
		}
	}

	private static final Map<UUID, Ward> ACTIVE = new HashMap<>();

	/** The command's habit, by word first and by kind second. */
	public static String habit(Spell spell) {
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word == null || !word.type.equals("command")) continue;
			switch (word.english) {
				case "Repel", "Banish", "Dismiss", "Displace", "Throw" -> {
					return "repel";
				}
				case "Reflect", "Mislead", "Distort" -> {
					return "reflect";
				}
				case "Absorb", "Drain", "Consume", "Harbor" -> {
					return "absorb";
				}
				case "Bind", "Ensnare", "Paralyze", "Detain", "Freeze", "Petrify", "Stagnate" -> {
					return "snare";
				}
				case "Mend", "Regenerate", "Grow", "Restoration" -> {
					return "mend";
				}
				default -> {
				}
			}
			return switch (word.explore == null ? "" : word.explore) {
				case "pulse", "launch" -> "repel";
				case "channel" -> "reflect";
				case "bind" -> "snare";
				case "grow" -> "mend";
				case "scatter" -> "burst";
				case "envelop", "form" -> "bulwark";
				default -> "none";
			};
		}
		return "none";
	}

	public static String describe(Spell spell) {
		String element = AttackForms.element(spell);
		String shell = switch (element) {
			case "fire" -> "It wraps you in a shell of flame that burns whatever strikes you and keeps fire from harming you.";
			case "ice" -> "It wraps you in frost: whatever strikes you is slowed and frozen.";
			case "water" -> "It wraps you in a skin of water: arrows lose their bite, flames go out, and you can breathe under water.";
			case "wind" -> "It wraps you in whirling wind that turns arrows aside and softens falls.";
			case "storm" -> "It crackles around you, shocking whatever strikes you.";
			case "stone" -> "It hardens you like stone: blows land softer and nothing knocks you back.";
			case "nature" -> "It wraps you in living green that mends you slowly and poisons what strikes you.";
			case "light" -> "It wraps you in radiance that blinds whatever strikes you.";
			case "shadow" -> "It wraps you in shadow: some blows pass through you entirely.";
			case "void" -> "It folds space around you: sometimes a blow finds you already gone.";
			case "blood" -> "Whatever strikes you bleeds, and its blood mends you.";
			default -> "It wraps you in arcane light that turns part of every blow back on the striker.";
		};
		String extra = switch (habit(spell)) {
			case "repel" -> " Attackers are thrown back.";
			case "reflect" -> " Arrows and spells are sent back where they came from.";
			case "absorb" -> " Part of every blow it stops is drunk into Barrier mana.";
			case "snare" -> " Attackers are held fast for a moment.";
			case "mend" -> " The shield slowly grows back while it lasts.";
			case "burst" -> " When it breaks it bursts outward.";
			case "bulwark" -> " It is a thicker shield than most.";
			default -> "";
		};
		return shell + extra;
	}

	/** Raised by every shield spell, for as long as its hearts last. */
	public static void raise(Cast cast, int ticks) {
		ServerPlayer player = cast.player;
		Spell spell = cast.spell;
		String element = AttackForms.element(spell);
		int color = Conjuring.tintFor(spell);
		Ward ward = new Ward(cast, element, habit(spell), Math.max(1, Math.min(5, spell.tier)), Tasks.now() + ticks, color);
		ACTIVE.put(player.getUUID(), ward);
		if (element.equals("fire")) player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, ticks, 0, true, false));
		if (element.equals("water")) player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, ticks, 0, true, false));
		if (element.equals("stone")) {
			var kb = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
			if (kb != null) kb.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(STONE_ID, 0.8, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		}
		cast.level.playSound(null, player.blockPosition(), switch (element) {
			case "fire" -> SoundEvents.BLAZE_SHOOT;
			case "ice" -> SoundEvents.GLASS_PLACE;
			case "water" -> SoundEvents.GENERIC_SPLASH;
			case "wind" -> SoundEvents.BREEZE_IDLE_GROUND;
			default -> SoundEvents.AMETHYST_BLOCK_RESONATE;
		}, SoundSource.PLAYERS, 1.0f, 1.0f);
		shell(cast.level, player, ward, 3);
	}

	private static final net.minecraft.resources.Identifier STONE_ID = com.omnispell.Omnispell.id("stone_ward");

	public static boolean active(ServerPlayer player) {
		Ward w = ACTIVE.get(player.getUUID());
		return w != null && w.until > Tasks.now();
	}

	private static ParticleOptions particle(String element) {
		return switch (element) {
			case "fire" -> ParticleTypes.FLAME;
			case "ice" -> ParticleTypes.SNOWFLAKE;
			case "water" -> ParticleTypes.BUBBLE_POP;
			case "wind" -> ParticleTypes.CLOUD;
			case "storm" -> ParticleTypes.ELECTRIC_SPARK;
			case "stone" -> ParticleTypes.ASH;
			case "nature" -> ParticleTypes.HAPPY_VILLAGER;
			case "light" -> ParticleTypes.END_ROD;
			case "shadow" -> ParticleTypes.SMOKE;
			case "void" -> ParticleTypes.REVERSE_PORTAL;
			case "blood" -> ParticleTypes.DAMAGE_INDICATOR;
			default -> ParticleTypes.WITCH;
		};
	}

	/** The visible shell: rings of the element turning around you. */
	private static void shell(ServerLevel level, ServerPlayer player, Ward ward, int rings) {
		ParticleOptions p = particle(ward.element);
		double spin = Tasks.now() * 0.15;
		for (int r = 0; r < rings; r++) {
			double y = 0.2 + r * 0.8;
			double radius = r == 1 ? 1.0 : 0.85;
			for (int i = 0; i < 8; i++) {
				double a = spin * (r % 2 == 0 ? 1 : -1) + i * Math.PI / 4;
				level.sendParticles(p, player.getX() + Math.cos(a) * radius, player.getY() + y, player.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0);
			}
		}
		Fx.ring(level, player.position().add(0, 0.05, 0), 1.0, ward.color, null);
	}

	/** Every few ticks: draw the ward, and the steady parts of what it does. */
	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) return;
		long now = Tasks.now();
		for (Map.Entry<UUID, Ward> entry : List.copyOf(ACTIVE.entrySet())) {
			Ward ward = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || ward.until <= now || !player.isAlive()) {
				ACTIVE.remove(entry.getKey());
				if (player != null) lower(player, ward);
				continue;
			}
			if (now % 4 != 0) continue;
			ServerLevel level = player.level();
			shell(level, player, ward, 2);
			if (ward.habit.equals("burst") && player.getAbsorptionAmount() <= 0) broken(player);
			if (now % 20 == 0) {
				if (ward.element.equals("nature")) player.heal(0.5f + 0.25f * ward.tier);
				if (ward.element.equals("water")) player.clearFire();
				if (ward.habit.equals("mend")) {
					float cap = player.getMaxAbsorption();
					if (player.getAbsorptionAmount() < cap) player.setAbsorptionAmount(Math.min(cap, player.getAbsorptionAmount() + 0.5f + 0.25f * ward.tier));
				}
				if (ward.element.equals("fire")) {
					// The flame shell scorches anything hostile pressed up against you.
					for (LivingEntity e : Targeting.around(player, player.position(), 1.8, false)) {
						if (e instanceof net.minecraft.world.entity.monster.Enemy) e.igniteForSeconds(3);
					}
				}
			}
		}
	}

	private static void lower(ServerPlayer player, Ward ward) {
		var kb = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
		if (kb != null) kb.removeModifier(STONE_ID);
	}

	/** The shield's hearts ran out. A bursting ward bursts. */
	public static void broken(ServerPlayer player) {
		Ward ward = ACTIVE.get(player.getUUID());
		if (ward == null || !ward.habit.equals("burst")) return;
		ServerLevel level = player.level();
		Fx.ring(level, player.position().add(0, 0.5, 0), 3.5, ward.color, particle(ward.element));
		level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
		for (LivingEntity e : Targeting.around(player, player.position(), 3.5, false)) {
			if (e instanceof net.minecraft.world.entity.player.Player || com.omnispell.familiar.Familiars.isFamiliar(e) || Conjuring.isConjured(e)) continue;
			hurt(ward, e, 2.0 + ward.tier);
			Vec3 out = e.position().subtract(player.position()).multiply(1, 0, 1);
			if (out.lengthSqr() > 0.01) e.push(out.normalize().x, 0.4, out.normalize().z);
			e.syncVelocity = true;
		}
		ACTIVE.put(player.getUUID(), new Ward(ward.cast, ward.element, "none", ward.tier, ward.until, ward.color));
	}

	private static void hurt(Ward ward, LivingEntity target, double amount) {
		Cast c = ward.cast;
		String element = ward.element;
		float damage = (float) (amount * c.damageBonus(element));
		Modifiers.withSpellElement(element, () -> target.hurtServer(c.level, c.level.damageSources().indirectMagic(c.player, c.player), damage));
	}

	/**
	 * A blow is about to land on a warded player. Returns what is left of it. Retaliation
	 * happens here too, so it works even when the shield's hearts take the whole blow.
	 */
	public static float incoming(ServerPlayer player, DamageSource source, float amount) {
		Ward ward = ACTIVE.get(player.getUUID());
		if (ward == null || ward.until <= Tasks.now() || amount <= 0 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
		ServerLevel level = player.level();
		Entity direct = source.getDirectEntity();
		LivingEntity attacker = source.getEntity() instanceof LivingEntity l && l != player ? l : null;
		boolean projectile = direct instanceof Projectile;
		var random = player.getRandom();

		// What the element does to the blow itself.
		switch (ward.element) {
			case "fire" -> {
				if (source.is(DamageTypeTags.IS_FIRE)) return 0;
			}
			case "water" -> {
				if (projectile) amount *= 0.5f;
			}
			case "wind" -> {
				if (source.is(DamageTypeTags.IS_FALL)) amount *= 0.3f;
				if (projectile && random.nextFloat() < 0.5f + 0.06f * ward.tier) {
					direct.setDeltaMovement(direct.getDeltaMovement().scale(-0.4).add(0, 0.3, 0));
					direct.syncVelocity = true;
					level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
					return 0;
				}
			}
			case "stone" -> amount *= 0.8f;
			case "shadow" -> {
				if (random.nextFloat() < 0.18f + 0.03f * ward.tier) {
					level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
					return 0;
				}
			}
			case "void" -> {
				if (attacker != null && random.nextFloat() < 0.15f + 0.03f * ward.tier) {
					Vec3 away = player.position().subtract(attacker.position()).multiply(1, 0, 1);
					away = away.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : away.normalize();
					level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.3, 0.5, 0.3, 0.5);
					player.randomTeleport(player.getX() + away.x * 4, player.getY() + 1, player.getZ() + away.z * 4, true, state -> true);
					return 0;
				}
			}
			default -> {
			}
		}

		// What the command does.
		switch (ward.habit) {
			case "reflect" -> {
				if (projectile) {
					direct.setDeltaMovement(direct.getDeltaMovement().scale(-1.0));
					if (direct instanceof Projectile proj) proj.setOwner(player);
					direct.syncVelocity = true;
					return 0;
				}
				if (attacker != null) hurt(ward, attacker, amount * 0.35);
			}
			case "absorb" -> {
				double drink = Math.min(amount, player.getAbsorptionAmount()) * 0.5;
				if (drink > 0) {
					double[] mana = com.omnispell.player.PlayerStore.get(player).mana;
					mana[ManaPool.BARRIER.ordinal()] = Math.min(Mana.max(player, ManaPool.BARRIER), mana[ManaPool.BARRIER.ordinal()] + drink / ward.cast.scale);
				}
			}
			default -> {
			}
		}

		// What it does to whoever struck.
		if (attacker != null && !projectile && attacker.isAlive()) {
			switch (ward.element) {
				case "fire" -> {
					attacker.igniteForSeconds(3 + ward.tier);
					hurt(ward, attacker, 1.0 + 0.5 * ward.tier);
					level.sendParticles(ParticleTypes.FLAME, attacker.getX(), attacker.getY() + 1, attacker.getZ(), 15, 0.3, 0.5, 0.3, 0.05);
				}
				case "ice" -> {
					attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2), player);
					attacker.setTicksFrozen(Math.min(attacker.getTicksRequiredToFreeze() + 60, attacker.getTicksFrozen() + 80));
				}
				case "storm" -> {
					hurt(ward, attacker, 1.5 + 0.5 * ward.tier);
					Fx.line(level, player.getBoundingBox().getCenter(), attacker.getBoundingBox().getCenter(), 0xBFE6FF, ParticleTypes.ELECTRIC_SPARK, 3);
				}
				case "nature" -> attacker.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), player);
				case "light" -> attacker.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0), player);
				case "blood" -> {
					MobControl.dot(player, attacker, 1.0f + 0.3f * ward.tier, 3, "blood");
					player.heal(Math.min(3f, amount * 0.25f));
				}
				case "arcane" -> hurt(ward, attacker, amount * 0.25);
				default -> {
				}
			}
			switch (ward.habit) {
				case "repel" -> {
					Vec3 away = attacker.position().subtract(player.position()).multiply(1, 0, 1);
					away = away.lengthSqr() < 0.01 ? player.getLookAngle().multiply(1, 0, 1) : away.normalize();
					attacker.push(away.x * (1.2 + 0.15 * ward.tier), 0.45, away.z * (1.2 + 0.15 * ward.tier));
					attacker.syncVelocity = true;
					level.sendParticles(particle(ward.element), attacker.getX(), attacker.getY() + 1, attacker.getZ(), 10, 0.3, 0.4, 0.3, 0.05);
				}
				case "snare" -> attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 5), player);
				default -> {
				}
			}
		}
		return amount;
	}

	public static void clear() {
		ACTIVE.clear();
	}
}
