package com.omnispell.spell;

import com.omnispell.Omnispell;
import com.omnispell.player.Mana;
import com.omnispell.player.ManaPool;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The hidden combat toggles from Omnispell. A spell switches some of them on for
 * a while; this class remembers which are on for each player and applies the ones
 * that react to being hit. The ids and meanings are the website's.
 */
public final class Modifiers {
	public static final class Active {
		public final String id;
		public final double value;
		public long until;      // server tick
		public final String extra;

		Active(String id, double value, long until, String extra) {
			this.id = id;
			this.value = value;
			this.until = until;
			this.extra = extra;
		}
	}

	private static final Map<UUID, List<Active>> ACTIVE = new HashMap<>();
	/** How many spells in a row each player has cast (for Momentum). */
	private static final Map<UUID, Integer> STREAK = new HashMap<>();
	private static final Map<UUID, Long> LAST_CAST = new HashMap<>();
	/** Set while one of our spells is dealing damage, so the target's resistances see the spell's element. */
	private static String currentSpellElement;

	private Modifiers() {
	}

	// ----- bookkeeping -----

	/** Parses "id:value:duration[:name]" as written by the generator. */
	public static Active parse(String text, long now) {
		String[] parts = text.split(":");
		if (parts.length == 0 || parts[0].isBlank()) return null;
		double value = parts.length > 1 ? number(parts[1], 1) : 1;
		double turns = parts.length > 2 ? number(parts[2], 1) : 1;
		String extra = parts.length > 3 ? parts[3] : null;
		return new Active(parts[0].trim().toLowerCase(), value, now + Omnispell.CONFIG.turnsToTicks(Math.max(1, turns)), extra);
	}

	private static double number(String text, double fallback) {
		try {
			return Double.parseDouble(text.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public static void add(ServerPlayer player, Active modifier) {
		List<Active> list = ACTIVE.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
		// The same toggle from a new cast replaces the old one rather than stacking.
		list.removeIf(a -> a.id.equals(modifier.id));
		list.add(modifier);
	}

	public static void add(ServerPlayer player, String id, double value, int ticks) {
		add(player, new Active(id, value, Tasks.now() + ticks, null));
	}

	public static double value(LivingEntity entity, String id) {
		List<Active> list = ACTIVE.get(entity.getUUID());
		if (list == null) return 0;
		for (Active a : list) {
			if (a.id.equals(id) && a.until > Tasks.now()) return a.value;
		}
		return 0;
	}

	public static boolean has(LivingEntity entity, String id) {
		return value(entity, id) != 0;
	}

	/** Uses up a one-shot toggle. Returns its value, or 0 if it was not on. */
	public static double consume(LivingEntity entity, String id) {
		List<Active> list = ACTIVE.get(entity.getUUID());
		if (list == null) return 0;
		Iterator<Active> it = list.iterator();
		while (it.hasNext()) {
			Active a = it.next();
			if (a.id.equals(id) && a.until > Tasks.now()) {
				it.remove();
				return a.value;
			}
		}
		return 0;
	}

	public static List<Active> list(LivingEntity entity) {
		List<Active> list = ACTIVE.get(entity.getUUID());
		return list == null ? List.of() : list;
	}

	public static void clear(LivingEntity entity) {
		ACTIVE.remove(entity.getUUID());
		STREAK.remove(entity.getUUID());
	}

	// ----- momentum -----

	/** Counts casts in a row. A gap of more than three turns breaks the streak. */
	public static int noteCast(ServerPlayer player) {
		long now = Tasks.now();
		long last = LAST_CAST.getOrDefault(player.getUUID(), -100000L);
		int streak = now - last <= Omnispell.CONFIG.turnsToTicks(3) ? STREAK.getOrDefault(player.getUUID(), 0) + 1 : 0;
		STREAK.put(player.getUUID(), streak);
		LAST_CAST.put(player.getUUID(), now);
		return streak;
	}

	public static int streak(ServerPlayer player) {
		return STREAK.getOrDefault(player.getUUID(), 0);
	}

	// ----- once a second -----

	private static final List<Holder<MobEffect>> CONFUSIONS = List.of(MobEffects.BLINDNESS, MobEffects.DARKNESS, MobEffects.NAUSEA);
	private static final List<Holder<MobEffect>> STUNS = List.of(MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE, MobEffects.WEAKNESS);

	public static void everySecond(ServerPlayer player) {
		List<Active> list = ACTIVE.get(player.getUUID());
		if (list == null) return;
		long now = Tasks.now();
		list.removeIf(a -> a.until <= now);
		if (list.isEmpty()) return;

		double regen = value(player, "regeneration");
		if (regen > 0 && player.getHealth() < player.getMaxHealth()) {
			// "Heal X per turn", spread over the seconds of a turn and paid from Restoration mana.
			double perSecond = regen / Omnispell.CONFIG.turnSeconds;
			double paid = Mana.spend(player, ManaPool.RESTORATION, perSecond);
			if (paid > 0) player.heal((float) (paid * Omnispell.CONFIG.valueScale));
		}
		if (has(player, "poison_immunity")) player.removeEffect(MobEffects.POISON);
		if (has(player, "stun_immunity")) {
			for (Holder<MobEffect> effect : STUNS) {
				MobEffectInstance instance = player.getEffect(effect);
				if (instance != null && instance.getAmplifier() >= 3) player.removeEffect(effect);
			}
		}
		if (has(player, "true_sight")) {
			for (Holder<MobEffect> effect : CONFUSIONS) player.removeEffect(effect);
		}
	}

	// ----- being hit -----

	public static void withSpellElement(String element, Runnable action) {
		String before = currentSpellElement;
		currentSpellElement = element;
		try {
			action.run();
		} finally {
			currentSpellElement = before;
		}
	}

	/** Which Omnispell element a hit counts as, for resistances. */
	public static String elementOf(DamageSource source) {
		if (currentSpellElement != null) return currentSpellElement;
		if (source.is(DamageTypeTags.IS_FIRE)) return "fire";
		if (source.is(DamageTypeTags.IS_FREEZING)) return "ice";
		if (source.is(DamageTypeTags.IS_LIGHTNING)) return "light";
		if (source.is(DamageTypes.WITHER) || source.is(DamageTypes.SONIC_BOOM) || source.is(DamageTypes.DRAGON_BREATH)) return "shadow";
		if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.STING)) return "nature";
		if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) return "arcane";
		return "neutral";
	}

	/** Evasion: a chance to avoid a hit entirely, paid for with Phase mana. Returns false to cancel the damage. */
	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) return true;
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.getEntity() == null) return true;
		double evasion = value(player, "evasion");
		if (evasion > 0 && player.getRandom().nextDouble() * 100 < evasion && Mana.spend(player, ManaPool.PHASE, 1) >= 1) {
			ServerLevel level = player.level();
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
			return false;
		}
		return true;
	}

	/** Fortify, resistances and mana shield change how much a hit does. Called from the damage mixin. */
	public static float modifyIncoming(LivingEntity entity, DamageSource source, float amount) {
		if (amount > 0 && !(entity instanceof ServerPlayer)) {
			if (com.omnispell.familiar.Familiars.spared(entity, source)) return 0;
			return com.omnispell.boss.Bosses.modifyDamage(entity, source, com.omnispell.charm.Charms.outgoing(source, amount));
		}
		if (amount <= 0 || !(entity instanceof ServerPlayer player)) return amount;
		amount = com.omnispell.charm.Charms.incoming(player, source, amount);
		if (amount <= 0 || list(player).isEmpty() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
		double damage = amount;
		double scale = Omnispell.CONFIG.valueScale;

		String element = elementOf(source);
		double resist = value(player, element + "_resistance");
		if (resist != 0) damage *= Math.max(0.0, 1.0 - resist / 100.0);

		double fortify = value(player, "fortify");
		if (fortify > 0) damage = Math.max(0.5, damage - fortify * scale);

		double manaShield = value(player, "mana_shield");
		if (manaShield > 0 && damage > 0) {
			double absorb = damage * Math.min(100, manaShield) / 100.0;
			double paid = Mana.spend(player, ManaPool.BARRIER, absorb / scale) * scale;
			damage -= paid;
		}
		return (float) Math.max(0, damage);
	}

	/** Thorns, reflection and absorb-heal react after a hit lands. */
	public static void afterDamage(LivingEntity entity, DamageSource source, float dealt) {
		if (!(entity instanceof ServerPlayer player) || dealt <= 0 || list(player).isEmpty()) return;
		double scale = Omnispell.CONFIG.valueScale;
		ServerLevel level = player.level();

		double absorbHeal = value(player, "absorb_heal");
		if (absorbHeal > 0 && player.isAlive()) {
			double want = dealt * absorbHeal / 100.0;
			double paid = Mana.spend(player, ManaPool.RESTORATION, want / scale) * scale;
			if (paid > 0) player.heal((float) paid);
		}

		if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == player || source.is(DamageTypes.THORNS)) return;
		double back = 0;
		double thorns = value(player, "thorns");
		if (thorns > 0) back += thorns * scale;
		double reflect = value(player, "damage_reflection");
		if (reflect > 0) {
			double want = dealt * reflect / 100.0;
			back += Mana.spend(player, ManaPool.MIRROR, want / scale) * scale;
		}
		if (back > 0) attacker.hurtServer(level, level.damageSources().thorns(player), (float) back);
	}

	/** Last Stand: once, survive a killing blow with a sliver of health. Returns false to cancel the death. */
	public static boolean allowDeath(ServerPlayer player) {
		if (consume(player, "last_stand") == 0) return true;
		player.setHealth(1.0f);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 4));
		player.level().sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
		return false;
	}
}
