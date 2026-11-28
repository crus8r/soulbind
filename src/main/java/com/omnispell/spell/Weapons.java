package com.omnispell.spell;

import com.omnispell.item.WandItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Attack spells shaped by a weapon word. Sword, Blade, Club and Spear turn the
 * wand into an enchanted melee weapon for a while: left-click strikes with the
 * spell's element. Arrow and Bow loose arrows of magic.
 */
public final class Weapons {
	/** A wand that has become a weapon, until a given tick. */
	private record Imbued(Spell spell, String kind, long until) {
	}

	private static final Map<UUID, Imbued> IMBUED = new HashMap<>();
	private static final Map<UUID, Long> NEXT_SWING = new HashMap<>();

	private Weapons() {
	}

	public static String kind(Spell spell) {
		for (String id : spell.words) {
			switch (id) {
				case "attribute:Sword" -> { return "sword"; }
				case "attribute:Blade/Knife" -> { return "blade"; }
				case "attribute:Club" -> { return "club"; }
				case "attribute:Spear" -> { return "spear"; }
				case "attribute:Arrow" -> { return "arrow"; }
				case "attribute:Bow" -> { return "bow"; }
				default -> {
				}
			}
		}
		return null;
	}

	/** The melee weapon word (Sword, Blade, Club, Spear), or null. */
	public static String melee(Spell spell) {
		for (String id : spell.words) {
			switch (id) {
				case "attribute:Sword" -> { return "sword"; }
				case "attribute:Blade/Knife" -> { return "blade"; }
				case "attribute:Club" -> { return "club"; }
				case "attribute:Spear" -> { return "spear"; }
				default -> {
				}
			}
		}
		return null;
	}

	/** The ranged weapon word (Bow, Arrow), or null. */
	public static String ranged(Spell spell) {
		if (spell.words.contains("attribute:Bow")) return "bow";
		if (spell.words.contains("attribute:Arrow")) return "arrow";
		return null;
	}

	/**
	 * Casts an attack spell that carries a weapon word. Returns false if it has none. With both a ranged and a
	 * melee word, the arrows fly first and then the wand becomes the weapon.
	 */
	public static boolean cast(Cast cast) {
		String kind = cast.spell.type.equals("attack") ? kind(cast.spell) : null;
		if (kind == null) return false;
		String bowKind = ranged(cast.spell), bladeKind = melee(cast.spell);
		if (bowKind != null && bladeKind != null) {
			loose(cast, bowKind);
			return imbue(cast, bladeKind);
		}
		if (kind.equals("arrow") || kind.equals("bow")) {
			loose(cast, kind);
			return false; // the arrows fly, and the spell is still cast: its own effect follows them
		}
		return imbue(cast, kind);
	}

	/** Looses the spell's magic arrows: three in a fan from a bow, one for an arrow. */
	private static void loose(Cast cast, String kind) {
		ServerPlayer p = cast.player;
		ServerLevel level = cast.level;
		int color = Fx.color(cast.spell.visual.color1);
		double websiteDamage = cast.spell.code.primaryValue > 0 ? cast.spell.code.primaryValue : cast.spell.tier * 10.0;
		float damage = (float) (websiteDamage * cast.scale * cast.damageBonus(cast.spell.code.element));
		int arrows = kind.equals("bow") ? 3 : 1;
		for (int n = 0; n < arrows; n++) {
			Arrow arrow = new Arrow(level, p, new ItemStack(Items.ARROW), null);
			float spread = kind.equals("bow") ? (n - 1) * 6f : 0f;
			arrow.shootFromRotation(p, p.getXRot(), p.getYRot() + spread, 0f, 3.2f, 0.4f);
			arrow.setBaseDamage(damage * 0.5 / 3.2 / (kind.equals("bow") ? 1.6 : 1.0)); // the spell itself follows the arrows, so they carry half; an arrow's damage grows with its speed
			arrow.setCritArrow(true);
			arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
			AttackForms.tip(arrow, AttackForms.element(cast.spell), cast.spell.tier);
			arrow.addTag("omnispell_magic_arrow");
			level.addFreshEntity(arrow);
			for (int k = 1; k <= 6; k++) {
				Vec3 at = Targeting.hand(p).add(p.getLookAngle().scale(k * 0.6));
				Fx.burst(level, at, color, null, 1);
			}
		}
		level.playSound(null, p.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 1.4f);
	}

	/** Makes the wand the melee weapon for a while. */
	private static boolean imbue(Cast cast, String kind) {
		ServerPlayer p = cast.player;
		ServerLevel level = cast.level;
		int color = Fx.color(cast.spell.visual.color1);
		int seconds = 20 + 10 * Math.max(1, Math.min(5, cast.spell.tier));
		IMBUED.put(p.getUUID(), new Imbued(cast.spell, kind, Tasks.now() + seconds * 20L));
		Fx.helix(level, p.position(), 2.0, 0.6, color, null);
		level.playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.3f);
		p.sendOverlayMessage(Component.literal("Your wand strikes like a " + kind + " of " + cast.spell.label() + " for " + seconds + " seconds. Left-click to strike."));
		return true;
	}

	/** Ends an enchanted weapon at once (tests, and anything that should break the enchantment). */
	public static void unimbue(ServerPlayer player) {
		IMBUED.remove(player.getUUID());
	}

	/** True if the player's wand is a weapon right now. */
	public static boolean imbued(ServerPlayer player) {
		Imbued imbued = IMBUED.get(player.getUUID());
		if (imbued == null) return false;
		if (imbued.until() <= Tasks.now()) {
			IMBUED.remove(player.getUUID());
			player.sendOverlayMessage(Component.literal("The enchantment leaves your wand."));
			return false;
		}
		return true;
	}

	/**
	 * A left-click with an imbued wand. A sword sweeps everything just in front;
	 * a blade is quick; a club hits hard and throws back; a spear reaches further.
	 */
	public static void swing(ServerPlayer player, ItemStack wand) {
		Imbued imbued = IMBUED.get(player.getUUID());
		if (imbued == null) return;
		long now = Tasks.now();
		if (NEXT_SWING.getOrDefault(player.getUUID(), 0L) > now) return;
		String kind = imbued.kind();
		NEXT_SWING.put(player.getUUID(), now + switch (kind) {
			case "blade" -> 7;
			case "club" -> 18;
			case "spear" -> 14;
			default -> 11;
		});
		double reach = kind.equals("spear") ? 5.0 : 3.2;
		double websiteDamage = imbued.spell().code.primaryValue > 0 ? imbued.spell().code.primaryValue : imbued.spell().tier * 10.0;
		double share = switch (kind) {
			case "blade" -> 0.45;
			case "club" -> 0.9;
			case "spear" -> 0.65;
			default -> 0.6;
		};
		Cast cast = new Cast(player, wand, imbued.spell());
		LivingEntity target = Targeting.entity(player, reach);
		ServerLevel level = player.level();
		int color = Fx.color(imbued.spell().visual.color1);
		Vec3 front = player.getEyePosition().add(player.getLookAngle().scale(Math.min(2.0, reach - 1)));
		swingShape(player, kind, color);
		level.playSound(null, player.blockPosition(), kind.equals("club") ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.1f);
		if (target == null) return;
		CombatEffects.strike(cast, target, websiteDamage * share, false);
		carry(cast, target, websiteDamage * share);
		Fx.burst(level, target.position().add(0, target.getBbHeight() * 0.5, 0), color, null, 2);
		if (kind.equals("club")) {
			Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1).normalize();
			target.push(away.x * 1.1, 0.35, away.z * 1.1);
			target.syncVelocity = true;
		}
		if (kind.equals("sword")) {
			for (LivingEntity other : Targeting.around(player, target.position(), 1.8, false)) {
				if (other != target && other.isAlive() && !com.omnispell.familiar.Familiars.isFamiliar(other)) CombatEffects.strike(cast, other, websiteDamage * share * 0.5, false);
			}
		}
		// Sparsus (or a spell meant to burst) splashes every strike over what stands close by.
		if (cast.spell.hasCharge("Sparsus") || "area".equals(cast.spell.planned("delivery"))) {
			for (LivingEntity other : Targeting.around(player, target.position(), 2.5, false)) {
				if (other == target || !other.isAlive() || Defenses.ally(player, other) || com.omnispell.familiar.Familiars.isFamiliar(other)) continue;
				CombatEffects.strike(cast, other, websiteDamage * share * 0.5, false);
				CombatEffects.riders(cast, other, 1);
			}
			Fx.ring(level, target.position().add(0, 0.3, 0), 2.5, color, null);
		}
	}

	/** The heavy forms happen at most once a second on each target; the control holds at most every four seconds. */
	private static final Map<LivingEntity, Long> FORM_READY = new java.util.WeakHashMap<>();
	private static final Map<LivingEntity, Long> HOLD_READY = new java.util.WeakHashMap<>();
	private static final java.util.Set<String> HOLDS = java.util.Set.of("stun", "charm", "fear", "confusion");

	/**
	 * What a strike of an enchanted weapon carries besides its damage: the spell's status, its riders (slow,
	 * weaken, hex) and its form (a root, a shell of ice, a slam...), as the spell itself would on a hit.
	 */
	private static void carry(Cast cast, LivingEntity target, double base) {
		if (!target.isAlive()) return;
		long now = Tasks.now();
		Spell.EffectCode code = cast.spell.code;
		String status = code.status == null ? "none" : code.status;
		if (!status.equals("none") && !status.equals("null")) {
			boolean hold = HOLDS.contains(status);
			if (!hold || HOLD_READY.getOrDefault(target, 0L) <= now) {
				CombatEffects.status(cast, target, status, 1);
				if (hold) HOLD_READY.put(target, now + 80);
			}
		}
		CombatEffects.riders(cast, target, 1);
		if (target.isAlive() && FORM_READY.getOrDefault(target, 0L) <= now) {
			FORM_READY.put(target, now + 20);
			AttackForms.apply(cast, target, base);
		}
	}

	/** While the wand is imbued, the spell's colour hangs about it in the shape of its weapon. */
	public static void aura(ServerPlayer player) {
		Imbued imbued = IMBUED.get(player.getUUID());
		if (imbued == null) return;
		ServerLevel level = player.level();
		int color = Fx.color(imbued.spell().visual.color1);
		var dust = new net.minecraft.core.particles.DustParticleOptions(color, 0.7f);
		Vec3 hand = Targeting.hand(player);
		Vec3 look = player.getLookAngle();
		double length = switch (imbued.kind()) {
			case "spear" -> 2.4;
			case "sword" -> 1.4;
			case "club" -> 0.9;
			default -> 0.8;
		};
		// A ghost of the weapon, drawn in light along the wand.
		for (double d = 0; d <= length; d += 0.2) {
			Vec3 at = hand.add(look.scale(d)).add(0, d * 0.25, 0);
			level.sendParticles(dust, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		if (imbued.kind().equals("club")) {
			Vec3 head = hand.add(look.scale(length)).add(0, length * 0.25, 0);
			level.sendParticles(dust, head.x, head.y, head.z, 4, 0.12, 0.12, 0.12, 0);
		}
	}

	/** The swing drawn in the air: an arc for a sword, a quick cross for a blade, a thrust for a spear, a burst for a club. */
	private static void swingShape(ServerPlayer player, String kind, int color) {
		ServerLevel level = player.level();
		var dust = new net.minecraft.core.particles.DustParticleOptions(color, 1.0f);
		Vec3 eye = player.getEyePosition().add(0, -0.3, 0);
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
		Vec3 side = new Vec3(-flat.z, 0, flat.x);
		switch (kind) {
			case "spear" -> {
				for (double d = 0.8; d <= 5.0; d += 0.25) {
					Vec3 at = eye.add(look.scale(d));
					level.sendParticles(dust, at.x, at.y, at.z, 1, 0, 0, 0, 0);
				}
				Vec3 tip = eye.add(look.scale(5.0));
				level.sendParticles(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 6, 0.1, 0.1, 0.1, 0.1);
			}
			case "club" -> {
				Vec3 at = eye.add(look.scale(2.2));
				level.sendParticles(dust, at.x, at.y, at.z, 24, 0.35, 0.35, 0.35, 0);
				level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			}
			case "blade" -> {
				for (int s = -1; s <= 1; s += 2) {
					for (double t = -1; t <= 1; t += 0.2) {
						Vec3 at = eye.add(flat.scale(1.6)).add(side.scale(t * 0.6 * s)).add(0, t * 0.5, 0);
						level.sendParticles(dust, at.x, at.y, at.z, 1, 0, 0, 0, 0);
					}
				}
			}
			default -> {
				for (double a = -1.0; a <= 1.0; a += 0.12) {
					Vec3 at = eye.add(flat.scale(1.8 * Math.cos(a))).add(side.scale(1.8 * Math.sin(a))).add(0, -a * 0.25, 0);
					level.sendParticles(dust, at.x, at.y, at.z, 1, 0, 0, 0, 0);
				}
			}
		}
	}

	public static void forget(ServerPlayer player) {
		IMBUED.remove(player.getUUID());
		NEXT_SWING.remove(player.getUUID());
	}
}
