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

	/** Casts an attack spell that carries a weapon word. Returns false if it has none. */
	public static boolean cast(Cast cast) {
		String kind = cast.spell.type.equals("attack") ? kind(cast.spell) : null;
		if (kind == null) return false;
		ServerPlayer p = cast.player;
		ServerLevel level = cast.level;
		int color = Fx.color(cast.spell.visual.color1);
		if (kind.equals("arrow") || kind.equals("bow")) {
			double websiteDamage = cast.spell.code.primaryValue > 0 ? cast.spell.code.primaryValue : cast.spell.tier * 10.0;
			float damage = (float) (websiteDamage * cast.scale * cast.damageBonus(cast.spell.code.element));
			int arrows = kind.equals("bow") ? 3 : 1;
			for (int n = 0; n < arrows; n++) {
				Arrow arrow = new Arrow(level, p, new ItemStack(Items.ARROW), null);
				float spread = kind.equals("bow") ? (n - 1) * 6f : 0f;
				arrow.shootFromRotation(p, p.getXRot(), p.getYRot() + spread, 0f, 3.2f, 0.4f);
				arrow.setBaseDamage(damage / 3.2 / (kind.equals("bow") ? 1.6 : 1.0)); // an arrow's damage grows with its speed
				arrow.setCritArrow(true);
				arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
				if ("fire".equals(cast.spell.code.element)) arrow.igniteForSeconds(100);
				arrow.addTag("omnispell_magic_arrow");
				level.addFreshEntity(arrow);
				for (int k = 1; k <= 6; k++) {
					Vec3 at = Targeting.hand(p).add(p.getLookAngle().scale(k * 0.6));
					Fx.burst(level, at, color, null, 1);
				}
			}
			level.playSound(null, p.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 1.4f);
			return true;
		}
		int seconds = 20 + 10 * Math.max(1, Math.min(5, cast.spell.tier));
		IMBUED.put(p.getUUID(), new Imbued(cast.spell, kind, Tasks.now() + seconds * 20L));
		Fx.helix(level, p.position(), 2.0, 0.6, color, null);
		level.playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.3f);
		p.sendOverlayMessage(Component.literal("Your wand becomes a " + (kind.equals("blade") ? "blade" : kind) + " of " + cast.spell.label() + " for " + seconds + " seconds. Left-click to strike."));
		return true;
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
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y - 0.3, front.z, 1, 0, 0, 0, 0);
		level.playSound(null, player.blockPosition(), kind.equals("club") ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.1f);
		if (target == null) return;
		CombatEffects.strike(cast, target, websiteDamage * share, false);
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
	}

	public static void forget(ServerPlayer player) {
		IMBUED.remove(player.getUUID());
		NEXT_SWING.remove(player.getUUID());
	}
}
