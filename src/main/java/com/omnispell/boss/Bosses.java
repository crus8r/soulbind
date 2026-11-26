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
		/** Some pack creatures bring a boss bar of their own; ours would be a second one. */
		boolean ownBar;
		boolean split;
		long reflectUntil;
		long nextStrike;
		float healthFiveSecondsAgo;
		long healthStampedAt;

		Active(BossDef def, Mob entity) {
			this.def = def;
			this.entity = entity;
			this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(def.name), barColor(def.element), BossEvent.BossBarOverlay.PROGRESS);
			this.nextAbility = Tasks.now() + 100;
			String body = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
			this.ownBar = body.equals("aerialhell:lunatic_priest") || body.equals("aerialhell:chained_god");
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

	/** Makes the first of the boss's bodies that exists here. Bodies from mods that are not installed are skipped. */
	private static Mob body(ServerLevel level, BossDef def, double[] scaleOut) {
		for (BossDef.Body body : def.bodies) {
			var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.Identifier.parse(body.entity()));
			if (type.isEmpty()) continue;
			try {
				if (type.get().create(level, EntitySpawnReason.MOB_SUMMONED) instanceof Mob mob) {
					scaleOut[0] = body.scale();
					return mob;
				}
			} catch (Exception e) {
				Omnispell.LOG.warn("Boss body {} could not be made; trying the next", body.entity(), e);
			}
		}
		return null;
	}

	public static Mob spawn(ServerLevel level, Vec3 at, BossDef def) {
		double[] scale = {1.0};
		Mob mob = body(level, def, scale);
		if (mob == null) return null;
		mob.snapTo(at.x, at.y, at.z, 0, 0);
		set(mob.getAttribute(Attributes.SCALE), scale[0]);
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
				// Some borrowed bodies die in their own way, without the usual death being announced. A boss that is dead is defeated all the same.
				boolean dead = mob.getHealth() <= 0 || mob.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.KILLED;
				if (dead && !mob.entityTags().contains(REWARDED)) after.add(() -> defeated(mob, boss.def));
				continue;
			}
			ServerLevel level = (ServerLevel) mob.level();
			boss.bar.setProgress(Math.max(0f, Math.min(1f, mob.getHealth() / mob.getMaxHealth())));
			List<ServerPlayer> near = new ArrayList<>();
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(mob) < 48 * 48) {
					near.add(player);
					if (!boss.ownBar) boss.bar.addPlayer(player);
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
			// Whatever body a boss wears, standing next to it hurts: gentle creatures get teeth this way.
			if (now >= boss.nextStrike) {
				double reach = 2.2 + mob.getBbWidth();
				for (ServerPlayer player : near) {
					if (player.getAbilities().invulnerable || player.distanceToSqr(mob) > reach * reach) continue;
					boss.nextStrike = now + 30;
					boolean fights = mob instanceof net.minecraft.world.entity.monster.Enemy;
					player.hurtServer(level, level.damageSources().mobAttack(mob), (float) (boss.def.damage() * (fights ? 0.35 : 1.0)));
					break;
				}
			}
			if (now - boss.healthStampedAt >= 100) {
				boss.healthStampedAt = now;
				boss.healthFiveSecondsAgo = mob.getHealth();
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
			double[] minionScale = {1.0};
			Mob minion = body(level, boss.def, minionScale);
			if (minion == null) continue;
			Vec3 at = boss.entity.position().add(i == 0 ? 1.5 : -1.5, 0, 0);
			minion.snapTo(at.x, at.y, at.z, 0, 0);
			set(minion.getAttribute(Attributes.SCALE), minionScale[0] * 0.6);
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
			case "charge" -> {
				LivingEntity target = mob.getTarget();
				if (target == null) return;
				Vec3 dir = target.position().subtract(at);
				if (dir.lengthSqr() < 1) return;
				Fx.line(level, at.add(0, 1, 0), target.position().add(0, 1, 0), Fx.color("nature"), ParticleTypes.CRIT, 3);
				Tasks.later(12, () -> {
					if (!mob.isAlive()) return;
					Vec3 push = dir.normalize().scale(1.6);
					mob.setDeltaMovement(push.x, 0.3, push.z);
					mob.syncVelocity = true;
				});
				Tasks.later(22, () -> {
					for (ServerPlayer p : level.players()) {
						if (!mob.isAlive() || p.distanceToSqr(mob) > 9) continue;
						p.hurtServer(level, level.damageSources().mobAttack(mob), (float) (boss.def.damage() * 1.2));
						Vec3 away = p.position().subtract(mob.position()).normalize();
						p.push(away.x * 1.4, 0.5, away.z * 1.4);
						p.syncVelocity = true;
					}
				});
			}
			case "thorn_volley" -> {
				for (ServerPlayer p : near) {
					if (p.distanceToSqr(mob) > 20 * 20) continue;
					Vec3 from = at.add(0, mob.getBbHeight() * 0.7, 0);
					Vec3 to = p.position().add(0, 1, 0);
					Fx.line(level, from, to, Fx.color("nature"), ParticleTypes.COMPOSTER, 2);
					Tasks.later(8, () -> {
						if (p.position().add(0, 1, 0).distanceToSqr(to) < 4) {
							p.hurtServer(level, level.damageSources().mobAttack(mob), (float) (boss.def.damage() * 0.7));
							p.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), mob);
						}
					});
				}
			}
			case "vanish" -> {
				LivingEntity target = mob.getTarget();
				mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
				Fx.impact(level, at, "shadow", 3);
				if (target == null) return;
				Tasks.later(50, () -> {
					if (!mob.isAlive() || !target.isAlive()) return;
					Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1.5, 0, 1.5));
					mob.teleportTo(behind.x, target.getY(), behind.z);
					mob.removeEffect(MobEffects.INVISIBILITY);
					Fx.impact(level, mob.position(), "shadow", 4);
					if (target instanceof ServerPlayer p) p.hurtServer(level, level.damageSources().mobAttack(mob), (float) (boss.def.damage() * 1.3));
				});
			}
			case "root_slam", "tidal_slam" -> {
				boolean tidal = ability.equals("tidal_slam");
				String element = tidal ? "shadow" : "nature";
				Fx.ring(level, at, 2, Fx.color(element), null);
				int waves = tidal ? 3 : 1;
				for (int wave = 0; wave < waves; wave++) {
					double radius = 5 + wave * 3;
					Tasks.later(18 + wave * 10, () -> {
						if (!mob.isAlive()) return;
						Vec3 now = mob.position();
						Fx.ring(level, now, radius, Fx.color(element), tidal ? ParticleTypes.SQUID_INK : ParticleTypes.COMPOSTER);
						for (ServerPlayer p : level.players()) {
							double d = Math.sqrt(p.distanceToSqr(mob));
							if (d > radius + 0.8 || d < radius - 3.5 || !p.onGround()) continue; // jump the wave to dodge it
							p.hurtServer(level, level.damageSources().mobAttack(mob), (float) (boss.def.damage() * 0.8));
							p.push(0, 0.9, 0);
							p.syncVelocity = true;
							p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), mob);
						}
					});
				}
			}
			case "meteor", "judgement" -> {
				boolean holy = ability.equals("judgement");
				String element = holy ? "light" : "fire";
				for (ServerPlayer p : near) {
					if (p.distanceToSqr(mob) > 30 * 30) continue;
					Vec3 mark = p.position();
					Tasks.repeat(8, 4, () -> Fx.ring(level, mark, 2.5, Fx.color(element), holy ? ParticleTypes.END_ROD : ParticleTypes.SMALL_FLAME));
					Tasks.later(32, () -> {
						Fx.line(level, mark.add(holy ? 0 : 6, 18, 0), mark, Fx.color(element), holy ? ParticleTypes.END_ROD : ParticleTypes.FLAME, 5);
						Fx.impact(level, mark, element, 5);
						level.playSound(null, BlockPos.containing(mark), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, holy ? 1.6f : 0.8f);
						boolean hit = false;
						for (ServerPlayer q : level.players()) {
							if (q.position().distanceToSqr(mark) > 7.5) continue;
							q.hurtServer(level, holy ? level.damageSources().magic() : level.damageSources().mobAttack(mob), (float) (boss.def.damage() * 1.3));
							if (!holy) q.igniteForSeconds(5);
							hit = true;
						}
						if (holy && hit && mob.isAlive()) mob.heal(mob.getMaxHealth() * 0.04f); // judgement that lands restores the judge
					});
				}
			}
			case "ice_spikes" -> {
				LivingEntity target = mob.getTarget();
				if (target == null) return;
				Vec3 dir = target.position().subtract(at);
				double length = Math.min(18, dir.length() + 4);
				Vec3 step = new Vec3(dir.x, 0, dir.z).normalize();
				for (int i = 2; i < length; i += 2) {
					Vec3 spot = at.add(step.scale(i));
					Tasks.later(6 + i * 2, () -> {
						Fx.impact(level, spot, "ice", 3);
						Fx.line(level, spot, spot.add(0, 2.2, 0), Fx.color("ice"), ParticleTypes.SNOWFLAKE, 3);
						for (ServerPlayer p : level.players()) {
							if (p.position().distanceToSqr(spot) > 3.2) continue;
							p.hurtServer(level, level.damageSources().freeze(), (float) (boss.def.damage() * 0.8));
							p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2), mob);
							p.setTicksFrozen(p.getTicksFrozen() + 120);
						}
					});
				}
			}
			case "gale" -> {
				Fx.ring(level, at, 3, Fx.color("spirit"), ParticleTypes.CLOUD);
				Fx.ring(level, at, 7, Fx.color("spirit"), ParticleTypes.GUST);
				for (ServerPlayer p : near) {
					if (p.distanceToSqr(mob) > 144) continue;
					Vec3 away = p.position().subtract(at);
					Vec3 push = new Vec3(away.x, 0, away.z).normalize().scale(1.8);
					p.push(push.x, 0.8, push.z);
					p.syncVelocity = true;
					p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 1), mob);
				}
			}
			case "void_grasp" -> {
				for (ServerPlayer p : near) {
					if (p.distanceToSqr(mob) > 24 * 24 || p.distanceToSqr(mob) < 9) continue;
					Fx.line(level, at.add(0, 1.2, 0), p.position().add(0, 1, 0), Fx.color("shadow"), ParticleTypes.REVERSE_PORTAL, 4);
					Vec3 pull = at.subtract(p.position()).normalize().scale(1.7);
					p.push(pull.x, 0.35, pull.z);
					p.syncVelocity = true;
					p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), mob);
				}
			}
			case "acid_spit" -> {
				for (ServerPlayer p : near) {
					if (p.distanceToSqr(mob) > 22 * 22) continue;
					Vec3 mark = p.position();
					Fx.line(level, at.add(0, mob.getBbHeight() * 0.8, 0), mark, Fx.color("poison"), ParticleTypes.ITEM_SLIME, 3);
					// The acid pools where it lands and burns for a few seconds.
					Tasks.repeat(10, 8, () -> {
						Fx.ring(level, mark, 2.2, Fx.color("poison"), ParticleTypes.ITEM_SLIME);
						for (ServerPlayer q : level.players()) {
							if (q.position().distanceToSqr(mark) > 5.5) continue;
							q.hurtServer(level, level.damageSources().magic(), (float) (boss.def.damage() * 0.25));
							q.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1), mob);
						}
					});
				}
			}
			case "rewind" -> {
				// Time runs backward for the wyrm: it returns to the health it had five seconds ago.
				if (boss.healthFiveSecondsAgo > mob.getHealth()) {
					mob.setHealth(Math.min(mob.getMaxHealth(), mob.getHealth() + (boss.healthFiveSecondsAgo - mob.getHealth()) * 0.6f));
					Fx.helix(level, at, 3.5, 1.5, Fx.color("arcane"), ParticleTypes.REVERSE_PORTAL);
					for (ServerPlayer p : near) p.sendOverlayMessage(Component.literal("Time runs backward. " + boss.def.name + " mends.").withStyle(ChatFormatting.LIGHT_PURPLE));
				}
			}
			case "cage" -> {
				for (ServerPlayer p : near) {
					if (p.distanceToSqr(mob) > 30 * 30) continue;
					BlockPos c = p.blockPosition();
					var glass = net.minecraft.world.level.block.Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.PURPLE);
					for (BlockPos pos : BlockPos.betweenClosed(c.offset(-1, 0, -1), c.offset(1, 2, 1))) {
						boolean edge = pos.getX() != c.getX() || pos.getZ() != c.getZ();
						if (edge) com.omnispell.spell.TempBlocks.place(level, pos.immutable(), glass, 70);
					}
					com.omnispell.spell.TempBlocks.place(level, c.above(2), glass, 70);
					Fx.impact(level, p.position(), "arcane", 4);
					p.sendOverlayMessage(Component.literal("Sealed. Break out or blink away.").withStyle(ChatFormatting.DARK_PURPLE));
				}
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
		if (entity.entityTags().contains(REWARDED)) return;
		defeated(entity, def);
	}

	private static final String REWARDED = "omnispell_boss_rewarded";

	/** The boss has fallen: scrolls, experience, and the victory written into the book of everyone near. */
	private static void defeated(LivingEntity entity, BossDef def) {
		entity.addTag(REWARDED);
		ServerLevel level = (ServerLevel) entity.level();
		int tier = def.tier;
		drop(entity, WordScrollItem.ofRarity(Math.min(5, 2 + tier / 2)));
		if (tier >= 2) drop(entity, WordScrollItem.ofRarity(Math.min(5, 1 + tier / 2)));
		// The Wizard's Hat: always from the bosses of diamond and netherite lairs and from Razza; often from stone, iron and gold lairs.
		if (tier >= 5 || (tier >= 2 && entity.getRandom().nextFloat() < 0.15f * tier)) drop(entity, new ItemStack(com.omnispell.item.OmniItems.WIZARD_HAT));
		// The robe and boots: half the time from the bosses of diamond and netherite lairs and Razza, now and then from iron and gold.
		for (net.minecraft.world.item.Item piece : new net.minecraft.world.item.Item[]{com.omnispell.item.OmniItems.WIZARD_ROBE, com.omnispell.item.OmniItems.WIZARD_BOOTS}) {
			float chance = tier >= 5 ? 0.5f : tier >= 3 ? 0.2f : 0f;
			if (entity.getRandom().nextFloat() < chance) drop(entity, new ItemStack(piece));
		}
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
				for (String type : new String[]{"conjuration"}) {
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
