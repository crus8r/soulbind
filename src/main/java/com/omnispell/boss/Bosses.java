package com.omnispell.boss;

import com.omnispell.Omnispell;
import com.omnispell.item.WordScrollItem;
import com.omnispell.player.ManaPool;
import com.omnispell.player.PlayerData;
import com.omnispell.player.PlayerStore;
import com.omnispell.spell.Casting;
import com.omnispell.spell.Fx;
import com.omnispell.spell.Modifiers;
import com.omnispell.spell.Tasks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Spawns bosses, runs their abilities and boss bars, and hands out their rewards. */
public final class Bosses {
	private static final String TAG_PREFIX = "omnispell_boss.";

	private static final class Active {
		final BossDef def;
		final Mob entity;
		final ServerBossEvent bar;
		long nextAbility;
		int abilityIndex;
		boolean split;
		long reflectUntil;

		Active(BossDef def, Mob entity) {
			this.def = def;
			this.entity = entity;
			this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(def.name), barColor(def.element), BossEvent.BossBarOverlay.PROGRESS);
			this.nextAbility = Tasks.now() + 100;
		}
	}

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();

	private Bosses() {
	}

	private static BossEvent.BossBarColor barColor(String element) {
		return switch (element) {
			case "fire" -> BossEvent.BossBarColor.RED;
			case "ice" -> BossEvent.BossBarColor.BLUE;
			case "nature" -> BossEvent.BossBarColor.GREEN;
			case "light" -> BossEvent.BossBarColor.YELLOW;
			case "shadow" -> BossEvent.BossBarColor.PURPLE;
			default -> BossEvent.BossBarColor.PINK;
		};
	}

	public static Mob spawn(ServerLevel level, Vec3 at, BossDef def) {
		Mob mob = def.body.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (mob == null) return null;
		mob.snapTo(at.x, at.y, at.z, 0, 0);
		set(mob.getAttribute(Attributes.SCALE), def.scale);
		set(mob.getAttribute(Attributes.MAX_HEALTH), def.health());
		set(mob.getAttribute(Attributes.ATTACK_DAMAGE), def.damage());
		set(mob.getAttribute(Attributes.FOLLOW_RANGE), 40);
		set(mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE), 0.8);
		mob.setHealth(mob.getMaxHealth());
		mob.setCustomName(Component.literal(def.name).withStyle(ChatFormatting.GOLD));
		mob.setCustomNameVisible(true);
		mob.setPersistenceRequired();
		mob.addTag(TAG_PREFIX + def.id);
		mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		level.addFreshEntity(mob);
		ACTIVE.put(mob.getUUID(), new Active(def, mob));
		Fx.impact(level, at, def.element, 5);
		level.playSound(null, BlockPos.containing(at), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.7f, 1.3f);
		return mob;
	}

	private static void set(AttributeInstance attribute, double value) {
		if (attribute != null) attribute.setBaseValue(value);
	}

	/** The boss this entity is, if it is one. Also re-adopts a boss that was saved with the world. */
	public static BossDef defOf(Entity entity) {
		Active active = ACTIVE.get(entity.getUUID());
		if (active != null) return active.def;
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(TAG_PREFIX)) return BossDef.ALL.get(tag.substring(TAG_PREFIX.length()));
		}
		return null;
	}

	public static void onEntityLoad(Entity entity) {
		if (ACTIVE.containsKey(entity.getUUID()) || !(entity instanceof Mob mob)) return;
		BossDef def = defOf(entity);
		if (def != null) ACTIVE.put(entity.getUUID(), new Active(def, mob));
	}

	/** Bosses take more from the element they are weak to and less from their own. */
	public static float modifyDamage(LivingEntity entity, DamageSource source, float amount) {
		Active active = ACTIVE.get(entity.getUUID());
		if (active == null) return amount;
		String element = Modifiers.elementOf(source);
		if (active.reflectUntil > Tasks.now() && source.getEntity() instanceof ServerPlayer attacker && attacker != entity) {
			ServerLevel level = attacker.level();
			attacker.hurtServer(level, level.damageSources().thorns(entity), amount * 0.5f);
			return amount * 0.25f;
		}
		if (element.equals(active.def.weakness)) return amount * 1.5f;
		if (element.equals(active.def.element)) return amount * 0.5f;
		return amount;
	}

	public static void afterDamage(LivingEntity entity, DamageSource source, float dealt) {
		Active active = ACTIVE.get(entity.getUUID());
		if (active == null || dealt <= 0) return;
		if (active.def.abilities.contains("thorns") && source.getDirectEntity() instanceof LivingEntity attacker && attacker != entity) {
			ServerLevel level = (ServerLevel) entity.level();
			attacker.hurtServer(level, level.damageSources().thorns(entity), Math.max(1f, dealt * 0.2f));
		}
	}

	/** Every 10 ticks. */
	public static void tick() {
		long now = Tasks.now();
		Iterator<Active> it = ACTIVE.values().iterator();
		List<Runnable> after = new ArrayList<>();
		while (it.hasNext()) {
			Active boss = it.next();
			Mob mob = boss.entity;
			if (mob.isRemoved() || !mob.isAlive()) {
				boss.bar.removeAllPlayers();
				it.remove();
				continue;
			}
			ServerLevel level = (ServerLevel) mob.level();
			boss.bar.setProgress(Math.max(0f, Math.min(1f, mob.getHealth() / mob.getMaxHealth())));
			List<ServerPlayer> near = new ArrayList<>();
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(mob) < 48 * 48) {
					near.add(player);
					boss.bar.addPlayer(player);
				} else {
					boss.bar.removePlayer(player);
				}
			}
			if (near.isEmpty()) continue;
			if (mob.getTarget() == null) {
				for (ServerPlayer player : near) {
					if (!player.getAbilities().invulnerable && player.distanceToSqr(mob) < 30 * 30) {
						mob.setTarget(player);
						break;
					}
				}
			}
			if (boss.def.abilities.contains("split") && !boss.split && mob.getHealth() < mob.getMaxHealth() * 0.5f) {
				boss.split = true;
				after.add(() -> split(level, boss));
			}
			if (now >= boss.nextAbility && !boss.def.abilities.isEmpty()) {
				boss.nextAbility = now + Math.max(70, 160 - boss.def.tier * 12);
				String ability = boss.def.abilities.get(boss.abilityIndex++ % boss.def.abilities.size());
				after.add(() -> use(level, boss, ability, near));
			}
		}
		for (Runnable r : after) r.run();
	}

	private static void split(ServerLevel level, Active boss) {
		for (int i = 0; i < 2; i++) {
			Mob minion = boss.def.body.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (minion == null) continue;
			Vec3 at = boss.entity.position().add(i == 0 ? 1.5 : -1.5, 0, 0);
			minion.snapTo(at.x, at.y, at.z, 0, 0);
			set(minion.getAttribute(Attributes.SCALE), boss.def.scale * 0.6);
			set(minion.getAttribute(Attributes.MAX_HEALTH), boss.def.health() * 0.15);
			set(minion.getAttribute(Attributes.ATTACK_DAMAGE), boss.def.damage() * 0.5);
			minion.setHealth(minion.getMaxHealth());
			minion.setCustomName(Component.literal("Spawn of " + boss.def.name));
			level.addFreshEntity(minion);
			minion.setTarget(boss.entity.getTarget());
		}
		Fx.impact(level, boss.entity.position(), boss.def.element, 4);
	}

	private static void use(ServerLevel level, Active boss, String ability, List<ServerPlayer> near) {
		Mob mob = boss.entity;
		int tier = Math.min(6, boss.def.tier);
		Vec3 at = mob.position();
		switch (ability) {
			case "poison_cloud" -> {
				Fx.ring(level, at, 6, Fx.color("poison"), ParticleTypes.SNEEZE);
				Fx.impact(level, at, "poison", 4);
				for (ServerPlayer p : near) {
					if (p.distanceToSqr(mob) > 49) continue;
					p.addEffect(new MobEffectInstance(MobEffects.POISON, 100 + tier * 20, tier >= 4 ? 1 : 0), mob);
					p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), mob);
				}
			}
			case "regen_shield" -> {
				set(mob.getAttribute(Attributes.MAX_ABSORPTION), 30);
				mob.setAbsorptionAmount(30);
				Fx.helix(level, at, 3, 1.2, Fx.color("nature"), ParticleTypes.HAPPY_VILLAGER);
			}
			case "fire_nova" -> {
				Fx.ring(level, at, 2.5, Fx.color("fire"), ParticleTypes.FLAME);
				Tasks.later(15, () -> {
					if (!mob.isAlive()) return;
					Vec3 now = mob.position();
					Fx.ring(level, now, 5.5, Fx.color("fire"), ParticleTypes.FLAME);
					Fx.impact(level, now, "fire", 4);
					for (ServerPlayer p : level.players()) {
						if (p.distanceToSqr(mob) > 36) continue;
						p.hurtServer(level, level.damageSources().mobAttack(mob), (float) (boss.def.damage() * 0.8));
						p.igniteForSeconds(4);
					}
				});
			}
			case "web" -> {
				for (ServerPlayer p : near) {
					if (p.distanceToSqr(mob) > 20 * 20) continue;
					p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 3), mob);
					Fx.impact(level, p.position(), "ice", 3);
					com.omnispell.spell.TempBlocks.place(level, p.blockPosition(), net.minecraft.world.level.block.Blocks.COBWEB, 80);
				}
			}
			case "lightning" -> {
				for (ServerPlayer p : near) {
					if (p.distanceToSqr(mob) > 24 * 24) continue;
					Vec3 strike = p.position();
					Fx.ring(level, strike, 1.5, Fx.color("light"), ParticleTypes.ELECTRIC_SPARK);
					Tasks.later(20, () -> {
						Fx.line(level, strike.add(0, 16, 0), strike, Fx.color("light"), ParticleTypes.ELECTRIC_SPARK, 5);
						level.playSound(null, BlockPos.containing(strike), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.0f, 1.0f);
						if (p.position().distanceToSqr(strike) < 6.25) {
							p.hurtServer(level, level.damageSources().lightningBolt(), (float) boss.def.damage());
							// The storm drains mana as well as health.
							double[] mana = PlayerStore.get(p).mana;
							for (ManaPool pool : ManaPool.values()) mana[pool.ordinal()] *= 0.75;
						}
					});
				}
			}
			case "silence" -> {
				Fx.ring(level, at, 8, Fx.color("shadow"), ParticleTypes.SCULK_SOUL);
				for (ServerPlayer p : near) {
					if (p.distanceToSqr(mob) > 100) continue;
					Casting.lengthenCooldowns(p, 100);
					p.sendOverlayMessage(Component.literal("Silenced: your spells are slow to return.").withStyle(ChatFormatting.DARK_PURPLE));
				}
			}
			case "reflect" -> {
				boss.reflectUntil = Tasks.now() + 80;
				mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, false));
				Fx.helix(level, at, 3, 1.4, Fx.color("light"), ParticleTypes.END_ROD);
				for (ServerPlayer p : near) p.sendOverlayMessage(Component.literal(boss.def.name + " shines like a mirror. Hold your fire.").withStyle(ChatFormatting.YELLOW));
			}
			case "exhaust" -> {
				Fx.ring(level, at, 9, Fx.color("dark"), ParticleTypes.SMOKE);
				for (ServerPlayer p : near) {
					if (p.distanceToSqr(mob) > 144) continue;
					p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0), mob);
					p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 160, 2), mob);
					p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 160, 1), mob);
				}
			}
			case "time" -> {
				LivingEntity target = mob.getTarget();
				if (target == null) return;
				Vec3 behind = target.position().subtract(target.getLookAngle().multiply(2.0, 0, 2.0));
				Fx.impact(level, at, "arcane", 3);
				mob.teleportTo(behind.x, target.getY(), behind.z);
				Fx.impact(level, mob.position(), "arcane", 3);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2), mob);
				mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 1));
			}
			default -> {
			}
		}
	}

	/** Rewards and the record in the book. Called when any living thing dies. */
	public static void onDeath(LivingEntity entity, DamageSource source) {
		BossDef def = defOf(entity);
		if (def == null) return;
		Active active = ACTIVE.remove(entity.getUUID());
		if (active != null) active.bar.removeAllPlayers();
		ServerLevel level = (ServerLevel) entity.level();
		int tier = def.tier;
		drop(entity, WordScrollItem.ofRarity(Math.min(5, 2 + tier / 2)));
		if (tier >= 2) drop(entity, WordScrollItem.ofRarity(Math.min(5, 1 + tier / 2)));
		if (tier >= 7) {
			drop(entity, new ItemStack(Items.NETHER_STAR));
			for (int i = 0; i < 3; i++) drop(entity, WordScrollItem.ofRarity(5));
		}
		net.minecraft.world.entity.ExperienceOrb.award(level, entity.position(), 30 + tier * 40);
		Fx.impact(level, entity.position(), def.element, 5);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(entity) > 64 * 64) continue;
			PlayerData data = PlayerStore.get(player);
			if (data.bosses.add(def.id)) {
				player.sendSystemMessage(Component.literal(def.name + " is defeated. " + def.lesson).withStyle(ChatFormatting.GOLD));
				// Each first victory teaches the next kind of spell, in the website's order.
				for (String type : new String[]{"enchantment", "illusion", "charm", "conjuration"}) {
					if (tier >= 1 && data.spellTypes.add(type)) {
						player.sendSystemMessage(Component.literal("You can now craft " + type + " spells.").withStyle(ChatFormatting.LIGHT_PURPLE));
						break;
					}
				}
				PlayerStore.save(player);
				PlayerStore.syncState(player);
			}
		}
	}

	private static void drop(LivingEntity from, ItemStack stack) {
		ItemEntity item = new ItemEntity(from.level(), from.getX(), from.getY() + 0.5, from.getZ(), stack);
		item.setDefaultPickUpDelay();
		item.setGlowingTag(true);
		from.level().addFreshEntity(item);
	}

	public static void clear() {
		for (Active active : ACTIVE.values()) active.bar.removeAllPlayers();
		ACTIVE.clear();
	}
}
