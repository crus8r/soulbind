package com.omnispell.spell;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * A hex: what an attack of Nullification (or one that unravels magic) leaves on a monster it hits. For a while
 * the monster cannot use its special attacks: it looses no arrows, fireballs, potions, tridents or bullets, calls
 * no fangs and no vexes, does not explode, cannot teleport, and what it does manage to land of that kind does no
 * harm. It can still bite and claw. A boss is hexed for a third as long.
 */
public final class Hex {
	private Hex() {
	}

	private static final Map<Entity, Long> HEXED = new WeakHashMap<>();

	/** Does this attack hex what it hits? An unravelling toggle on it, or words that undo magic. */
	public static boolean hexes(Spell spell) {
		if (spell == null || !"attack".equals(spell.type)) return false;
		for (String m : spell.code.modifiers) if (m.toLowerCase(java.util.Locale.ROOT).startsWith("counter_spell")) return true;
		return spell.words.contains("intention:Nullification") || spell.words.contains("command:Silence") || spell.words.contains("command:Disenchant") || spell.words.contains("command:Nullify");
	}

	/** Seconds a hex holds, by tier (a boss: a third of it). */
	public static int seconds(Spell spell) {
		return 3 + Math.max(1, Math.min(5, spell.tier));
	}

	public static void hex(Cast cast, LivingEntity target) {
		if (target == null || !target.isAlive() || target instanceof Player) return;
		int ticks = seconds(cast.spell) * 20;
		if (com.omnispell.boss.Bosses.defOf(target) != null) ticks /= 3;
		long until = Tasks.now() + ticks;
		boolean fresh = !hexed(target);
		HEXED.merge(target, until, Math::max);
		if (fresh && target.level() instanceof ServerLevel level) {
			Fx.ring(level, target.position().add(0, target.getBbHeight() + 0.3, 0), 0.6, Fx.color("arcane"), ParticleTypes.ENCHANT);
			level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
		}
	}

	public static boolean hexed(Entity entity) {
		if (entity == null) return false;
		Long until = HEXED.get(entity);
		return until != null && until > Tasks.now();
	}

	/** A projectile, fang or vex sent by a hexed monster never comes into the world. */
	public static boolean forbids(Entity spawned) {
		if (HEXED.isEmpty() || spawned == null) return false;
		Entity owner = null;
		if (spawned instanceof net.minecraft.world.entity.projectile.Projectile projectile) owner = projectile.getOwner();
		else if (spawned instanceof net.minecraft.world.entity.projectile.EvokerFangs fangs) owner = fangs.getOwner();
		else if (spawned instanceof net.minecraft.world.entity.monster.Vex vex) owner = vex.getOwner();
		if (owner == null || owner instanceof Player || !hexed(owner)) return false;
		if (spawned.level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.SMOKE, spawned.getX(), spawned.getY(), spawned.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
		return true;
	}

	/** What a hexed monster's special attack lands does no harm (a warden's boom, a breath, a blast). */
	public static boolean blocks(DamageSource source) {
		return !HEXED.isEmpty() && hexed(source.getEntity()) && Modifiers.specialAttack(source);
	}

	/** Every tick: a hexed creeper cannot swell, and every hexed monster shows its hex now and then. */
	public static void tick(MinecraftServer server) {
		if (HEXED.isEmpty()) return;
		long now = Tasks.now();
		var it = HEXED.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			Entity entity = e.getKey();
			if (entity == null || !entity.isAlive() || e.getValue() <= now) {
				it.remove();
				continue;
			}
			if (entity instanceof net.minecraft.world.entity.monster.Creeper creeper) creeper.setSwellDir(-1);
			if (now % 10 == 0 && entity.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.WITCH, entity.getX(), entity.getY() + entity.getBbHeight() + 0.2, entity.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
			}
		}
	}

	public static void clear() {
		HEXED.clear();
	}
}
