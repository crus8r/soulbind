package com.omnispell.spell;

import com.omnispell.Omnispell;
import com.omnispell.data.Word;
import com.omnispell.data.WordBank;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Conjured creatures. The spell's creature word (or the creature its own name speaks
 * of) decides the body: a wolf, a bear, a serpent, a bird, a golem. The base word
 * colours it. It fights beside its caster, biting what is close and casting the
 * spell's own magic at what is not, so a fire serpent spits fire and a frost wolf
 * leaves its prey frozen.
 */
public final class Conjuring {
	private Conjuring() {
	}

	public static final net.minecraft.resources.ResourceKey<EntityType<?>> SERPENT_KEY =
			net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, Omnispell.id("conjured_serpent"));
	public static final EntityType<ConjuredSerpent> SERPENT = net.minecraft.core.Registry.register(BuiltInRegistries.ENTITY_TYPE, SERPENT_KEY,
			EntityType.Builder.<ConjuredSerpent>of(ConjuredSerpent::new, MobCategory.MISC).sized(0.9f, 0.8f).eyeHeight(0.5f).clientTrackingRange(10).build(SERPENT_KEY));

	public static void register() {
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(SERPENT, ConjuredSerpent.attributes());
	}

	/** A body: its entity type and how much bigger or smaller than usual. */
	public record Body(String id, EntityType<? extends Mob> type, double scale, boolean flies) {
	}

	private static final Map<String, Body> BODIES = new HashMap<>();

	static {
		body("serpent", SERPENT, 1.0, true, "snake", "serpent", "eel", "wyrm", "naga", "leviathan", "hydra", "worm", "dragon", "basilisk", "viper", "cobra", "python");
		body("wolf", EntityTypes.WOLF, 1.15, false, "wolf", "hound", "dog", "jackal", "coyote", "cerberus");
		body("bear", EntityTypes.POLAR_BEAR, 1.0, false, "bear", "ursine", "grizzly");
		body("fox", EntityTypes.FOX, 1.3, false, "fox", "kitsune", "vixen");
		body("cat", EntityTypes.OCELOT, 1.5, false, "cat", "panther", "lion", "tiger", "lynx", "leopard", "jaguar", "feline", "sphinx");
		body("horse", EntityTypes.HORSE, 1.0, false, "horse", "steed", "stallion", "unicorn", "mare", "pegasus", "kelpie");
		body("frog", EntityTypes.FROG, 1.6, false, "frog", "toad");
		body("bird", EntityTypes.PARROT, 1.4, true, "bird", "hawk", "falcon", "raven", "crow", "owl", "phoenix", "sparrow", "swallow", "dove");
		body("eagle", EntityTypes.PARROT, 2.0, true, "eagle", "griffin", "gryphon", "roc", "condor", "thunderbird");
		body("insect", EntityTypes.BEE, 1.5, true, "insect", "bee", "wasp", "hornet", "swarm", "beetle", "moth", "locust");
		body("spider", EntityTypes.SPIDER, 1.0, false, "spider", "scorpion", "arachnid", "tarantula");
		body("golem", EntityTypes.IRON_GOLEM, 0.85, false, "golem", "construct", "statue", "sentinel", "colossus", "titan", "knight", "automaton", "gargoyle");
		body("spirit", EntityTypes.VEX, 1.4, true, "spirit", "wisp", "wraith", "ghost", "specter", "spectre", "phantom", "shade", "imp", "fairy", "sprite", "elemental", "familiar");
	}

	private static void body(String id, EntityType<? extends Mob> type, double scale, boolean flies, String... words) {
		Body b = new Body(id, type, scale, flies);
		for (String w : words) BODIES.put(w, b);
	}

	/** The body for a spell: its creature attribute word first, then its name and description, then its element. */
	public static Body bodyFor(Spell spell, String name) {
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word != null && word.type.equals("attribute")) {
				Body b = BODIES.get(word.english.toLowerCase(Locale.ROOT));
				if (b != null) return b;
			}
		}
		String text = ((name == null ? "" : name) + " " + (spell.name == null ? "" : spell.name) + " " + (spell.description == null ? "" : spell.description)).toLowerCase(Locale.ROOT);
		Body best = null;
		int at = Integer.MAX_VALUE;
		for (Map.Entry<String, Body> e : BODIES.entrySet()) {
			int i = indexOfWord(text, e.getKey());
			if (i >= 0 && i < at) {
				at = i;
				best = e.getValue();
			}
		}
		if (best != null) return best;
		return BODIES.get(switch (AttackForms.element(spell)) {
			case "water" -> "serpent";
			case "ice" -> "bear";
			case "fire" -> "fox";
			case "stone" -> "golem";
			case "wind", "storm" -> "bird";
			case "nature" -> "frog";
			case "shadow" -> "cat";
			case "blood" -> "wolf";
			default -> "spirit";
		});
	}

	private static int indexOfWord(String text, String word) {
		int i = text.indexOf(word);
		while (i >= 0) {
			boolean start = i == 0 || !Character.isLetter(text.charAt(i - 1));
			int end = i + word.length();
			boolean stop = end >= text.length() || !Character.isLetter(text.charAt(end)) || text.charAt(end) == 's';
			if (start && stop) return i;
			i = text.indexOf(word, i + 1);
		}
		return -1;
	}

	/** The creature's colour: a colour word if the spell has one, otherwise its element's. */
	public static int tintFor(Spell spell) {
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word == null || !word.type.equals("attribute")) continue;
			Integer c = switch (word.english) {
				case "Red" -> 0xE0403A;
				case "Orange" -> 0xF08A30;
				case "Yellow" -> 0xF0D840;
				case "Green" -> 0x58C04A;
				case "Blue" -> 0x4A78E8;
				case "Indigo" -> 0x5A48C8;
				case "Violet" -> 0xA858E0;
				default -> null;
			};
			if (c != null) return c;
		}
		return switch (AttackForms.element(spell)) {
			case "fire" -> 0xFF8A40;
			case "ice" -> 0xA8E4FF;
			case "water" -> 0x4AA8F0;
			case "wind" -> 0xE8F4F0;
			case "storm" -> 0xC8D8FF;
			case "stone" -> 0xB49A78;
			case "nature" -> 0x7CD060;
			case "light" -> 0xFFF2A8;
			case "shadow" -> 0x6A5A8A;
			case "void" -> 0x9A5AE0;
			case "blood" -> 0xD03A4A;
			default -> 0xC08AF0;
		};
	}

	private static final class Active {
		final Mob mob;
		final UUID owner;
		final Cast cast;
		final Body body;
		final double base;
		LivingEntity foe;
		int biteReady;
		float bite = 4;
		int boltReady = 20;

		Active(Mob mob, UUID owner, Cast cast, Body body, double base, LivingEntity foe) {
			this.mob = mob;
			this.owner = owner;
			this.cast = cast;
			this.body = body;
			this.base = base;
			this.foe = foe;
		}
	}

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();

	public static boolean isConjured(Entity entity) {
		return entity != null && ACTIVE.containsKey(entity.getUUID());
	}

	/** Brings the creature into the world. Returns false if it could not be. */
	public static boolean spawn(Cast cast, String name, double damage, double turns) {
		ServerPlayer player = cast.player;
		ServerLevel level = cast.level;
		Body body = bodyFor(cast.spell, name);
		Mob mob = body.type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (mob == null) return cast.fail("The conjuration finds no purchase here.");
		Vec3 at = player.position().add(player.getLookAngle().multiply(1.8, 0, 1.8)).add(0, body.flies ? 1.2 : 0, 0);
		mob.snapTo(at.x, player.getY() + (body.flies ? 1.2 : 0), at.z, player.getYRot(), 0);
		((com.omnispell.mixin.MobGoalsAccessor) mob).omnispell$targetSelector().removeAllGoals(goal -> true);
		mob.setTarget(null);
		mob.setPersistenceRequired();
		if (mob instanceof net.minecraft.world.entity.TamableAnimal pet) pet.tame(player);
		if (mob instanceof net.minecraft.world.entity.animal.golem.IronGolem golem) golem.setPlayerCreated(true);
		if (mob instanceof net.minecraft.world.entity.monster.Vex vex) vex.setLimitedLife((int) Math.max(40, cast.turns(turns) + 20));
		mob.setCustomName(Component.literal(name));
		mob.setCustomNameVisible(true);
		int tier = Math.max(1, Math.min(5, cast.spell.tier));
		set(mob.getAttribute(Attributes.SCALE), body.scale * (0.85 + 0.06 * tier));
		set(mob.getAttribute(Attributes.MAX_HEALTH), 14 + tier * 7);
		double biteDamage = Math.max(2.0, damage * cast.scale * cast.damageBonus(cast.spell.code.element) * 0.6);
		set(mob.getAttribute(Attributes.ATTACK_DAMAGE), biteDamage);
		set(mob.getAttribute(Attributes.FOLLOW_RANGE), 32);
		mob.setHealth(mob.getMaxHealth());
		if (isInvisibleWord(cast.spell)) mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, cast.turns(turns) + 40, 0, false, false));
		mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, cast.turns(turns) + 40, 0, false, false));
		Summons.track(mob, cast.turns(turns)); // before it enters the world, or it would be taken for a stray
		level.addFreshEntity(mob);
		int tint = tintFor(cast.spell);
		net.minecraft.world.entity.Entity e = mob;
		for (ServerPlayer viewer : level.players()) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer, new com.omnispell.net.OmniNet.ConjureTint(e.getId(), tint));
		}
		Fx.play(level, cast.spell.visual, Targeting.hand(player), mob.position());
		LivingEntity target = Targeting.entity(player, cast.range);
		double base = cast.spell.code.primaryValue > 0 ? cast.spell.code.primaryValue : tier * 6.0;
		Active active = new Active(mob, player.getUUID(), cast, body, base, target != null && target != mob && hostileTo(player, target) ? target : null);
		active.bite = (float) biteDamage;
		ACTIVE.put(mob.getUUID(), active);
		return true;
	}

	private static boolean isInvisibleWord(Spell spell) {
		return spell.words.contains("attribute:Invisible");
	}

	private static void set(AttributeInstance attribute, double value) {
		if (attribute != null) attribute.setBaseValue(value);
	}

	/** Never its caster, another player, a familiar or another conjured creature. */
	private static boolean hostileTo(ServerPlayer owner, LivingEntity e) {
		if (e == null || !e.isAlive() || e instanceof Player) return false;
		if (com.omnispell.familiar.Familiars.isFamiliar(e) || isConjured(e)) return false;
		if (e instanceof net.minecraft.world.entity.OwnableEntity own && owner.getUUID().equals(own.getOwnerReference() == null ? null : own.getOwnerReference().getUUID())) return false;
		return e.level() == owner.level();
	}

	private static LivingEntity pickFoe(ServerPlayer owner, Active a) {
		if (a.foe != null && hostileTo(owner, a.foe) && a.foe.distanceToSqr(a.mob) < 28 * 28) return a.foe;
		for (LivingEntity c : new LivingEntity[]{owner.getLastHurtByMob(), owner.getLastHurtMob(), a.mob.getLastHurtByMob()}) {
			if (hostileTo(owner, c) && c.distanceToSqr(a.mob) < 24 * 24) return c;
		}
		LivingEntity nearest = null;
		double best = 12 * 12;
		for (LivingEntity e : a.mob.level().getEntitiesOfClass(LivingEntity.class, a.mob.getBoundingBox().inflate(12), x -> x instanceof net.minecraft.world.entity.monster.Enemy)) {
			double d = e.distanceToSqr(a.mob);
			if (d < best && hostileTo(owner, e)) {
				best = d;
				nearest = e;
			}
		}
		return nearest;
	}

	/** Every tick: move, bite, cast. */
	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) return;
		for (Active a : List.copyOf(ACTIVE.values())) {
			Mob mob = a.mob;
			if (mob.isRemoved() || !mob.isAlive()) {
				ACTIVE.remove(mob.getUUID());
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(a.owner);
			if (owner == null || owner.level() != mob.level()) {
				Summons.dismiss(mob);
				ACTIVE.remove(mob.getUUID());
				continue;
			}
			ServerLevel level = owner.level();
			if (mob.getTarget() != null) mob.setTarget(null);
			if (mob instanceof net.minecraft.world.entity.animal.bee.Bee bee) bee.stopBeingAngry();
			if (a.biteReady > 0) a.biteReady--;
			if (a.boltReady > 0) a.boltReady--;
			if (mob.tickCount % 10 == 0) a.foe = pickFoe(owner, a);
			if (mob.tickCount % 8 == 0) aura(level, mob, a);
			LivingEntity foe = a.foe;
			Vec3 goal;
			if (foe != null) {
				goal = foe.position().add(0, a.body.flies ? 1.5 : 0, 0);
				double reach = 1.8 + mob.getBbWidth() / 2 + foe.getBbWidth() / 2;
				mob.getLookControl().setLookAt(foe);
				if (mob.distanceToSqr(foe) <= reach * reach && a.biteReady <= 0) {
					a.biteReady = 20;
					level.broadcastEntityEvent(mob, (byte) 4); // the attack animation, for the bodies that have one
					foe.hurtServer(level, level.damageSources().mobAttack(mob), a.bite);
				}
				// The spell's own magic, cast at what it fights.
				if (a.boltReady <= 0 && mob.distanceToSqr(foe) < 14 * 14 && mob.hasLineOfSight(foe)) {
					a.boltReady = 40;
					bolt(a, foe);
				}
			} else {
				Vec3 side = owner.getLookAngle().multiply(1, 0, 1);
				side = side.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : side.normalize();
				goal = owner.position().add(-side.z * 2.2, a.body.flies ? 1.8 : 0, side.x * 2.2);
			}
			move(a, goal, foe != null);
		}
	}

	private static void move(Active a, Vec3 goal, boolean hunting) {
		Mob mob = a.mob;
		double far = mob.position().distanceToSqr(goal);
		if (!hunting && far > 28 * 28) {
			mob.snapTo(goal.x, goal.y, goal.z, mob.getYRot(), 0);
			return;
		}
		if (mob instanceof ConjuredSerpent || mob instanceof net.minecraft.world.entity.monster.Vex) {
			// The serpent and the spirits swim through the air straight to where they want to be.
			Vec3 to = goal.subtract(mob.position());
			double dist = to.length();
			if (mob instanceof net.minecraft.world.entity.monster.Vex vex) {
				vex.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, hunting ? 1.0 : 0.6);
				return;
			}
			Vec3 v = dist < 1.2 ? mob.getDeltaMovement().scale(0.7) : to.normalize().scale(Math.min(0.42, 0.12 + dist * 0.05));
			double wave = Math.sin(mob.tickCount * 0.25) * 0.05;
			mob.setDeltaMovement(v.x, v.y + wave, v.z);
			if (v.horizontalDistanceSqr() > 0.0004) {
				float yaw = (float) (Math.toDegrees(Math.atan2(-v.x, v.z)));
				mob.setYRot(yaw);
				mob.yBodyRot = yaw;
				mob.yHeadRot = yaw;
			}
			mob.syncVelocity = true;
			return;
		}
		if (mob.tickCount % 5 == 0 && far > 2.5 * 2.5) mob.getNavigation().moveTo(goal.x, goal.y, goal.z, hunting ? 1.35 : 1.1);
	}

	/** The creature casts its spell: the spell's look, its damage, its element and its form. */
	private static void bolt(Active a, LivingEntity foe) {
		Cast cast = a.cast;
		Mob mob = a.mob;
		Vec3 from = mob.position().add(0, mob.getBbHeight() * 0.7, 0);
		Fx.play(cast.level, cast.spell.visual, from, foe.getBoundingBox().getCenter());
		Fx.sound(cast.level, mob.position(), cast.spell.code.element, Math.max(1, cast.spell.visual.intensity - 1));
		foe.setInvulnerableTime(0);
		CombatEffects.strike(cast, foe, a.base * 0.35, false);
		Spell.EffectCode code = cast.spell.code;
		if (code.status != null && !code.status.equals("none") && !code.status.equals("null") && foe.isAlive()) CombatEffects.status(cast, foe, code.status, 1);
		if (foe.isAlive() && mob.getRandom().nextFloat() < 0.3f) AttackForms.apply(cast, foe, a.base * 0.35);
	}

	/** A faint haze of its element, so you can tell a fire wolf from a frost one at a glance. */
	private static void aura(ServerLevel level, Mob mob, Active a) {
		var particle = switch (AttackForms.element(a.cast.spell)) {
			case "fire" -> net.minecraft.core.particles.ParticleTypes.SMALL_FLAME;
			case "ice" -> net.minecraft.core.particles.ParticleTypes.SNOWFLAKE;
			case "water" -> net.minecraft.core.particles.ParticleTypes.DRIPPING_WATER;
			case "wind" -> net.minecraft.core.particles.ParticleTypes.CLOUD;
			case "storm" -> net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK;
			case "stone" -> net.minecraft.core.particles.ParticleTypes.ASH;
			case "nature" -> net.minecraft.core.particles.ParticleTypes.SPORE_BLOSSOM_AIR;
			case "light" -> net.minecraft.core.particles.ParticleTypes.END_ROD;
			case "shadow" -> net.minecraft.core.particles.ParticleTypes.SMOKE;
			case "void" -> net.minecraft.core.particles.ParticleTypes.PORTAL;
			case "blood" -> net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR;
			default -> net.minecraft.core.particles.ParticleTypes.WITCH;
		};
		level.sendParticles(particle, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 2, mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.4, 0.01);
	}

	/** Conjured creatures never hurt their caster, other players, or each other. */
	public static boolean spares(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
		Entity attacker = source.getEntity();
		if (!isConjured(attacker)) return false;
		return victim instanceof Player || isConjured(victim) || com.omnispell.familiar.Familiars.isFamiliar(victim);
	}

	public static void clear() {
		ACTIVE.clear();
	}
}
