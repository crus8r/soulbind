package com.omnispell.spell;

import com.omnispell.data.Word;
import com.omnispell.data.WordBank;
import com.omnispell.player.Mana;
import com.omnispell.player.ManaPool;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Defense spells, which are support spells as much as shields. The same hearts or healing can
 * take many shapes: a shell of the element around you, a blessing (fire resistance, water
 * breathing, a slow fall, stone skin), healing over time, a burst that shields and mends
 * everyone near you, a lifeline cast at the ally you look at, a sanctuary circle on the
 * ground, a barrier wall, or a guardian creature at your side.
 *
 * The shape comes from the attribute word if there is one, then the command, then what the
 * spell's own description says, then the intention, and the element colours all of it.
 */
public final class Defenses {
	private Defenses() {
	}

	private static String command(Spell spell, boolean kind) {
		for (String id : spell.words) {
			Word w = WordBank.word(id);
			if (w != null && w.type.equals("command")) return kind ? (w.explore == null ? "" : w.explore) : w.english;
		}
		return "";
	}

	private static String intention(Spell spell) {
		for (String id : spell.words) {
			Word w = WordBank.word(id);
			if (w != null && w.type.equals("intention")) return w.english;
		}
		return "";
	}

	/** The commands that drive a wall out instead of raising it. */
	private static final java.util.Set<String> SURGING = java.util.Set.of("Repel", "Throw", "Release", "Banish", "Dismiss", "Displace", "Disperse", "Scatter", "Move");

	/** The shapes the Oracle may plan for a defense. (Aspect, walls, lifeline by Bridge and guardians come only from the words.) */
	public static final java.util.Set<String> PLANNED_FORMS = java.util.Set.of("shell", "veil", "cocoon", "boon", "bulwark", "mend", "aegis", "lifeline", "sanctuary", "barrier", "cleanse", "terror", "daze", "stagger", "enthrall");

	/** The spell's shape: what the words fix first (a creature's traits, a wall, a bridge), then the Oracle's plan, then the words again. */
	public static String form(Spell spell) {
		for (String id : spell.words) {
			switch (id) {
				case "attribute:Wall", "attribute:Tower", "attribute:Door" -> {
					return SURGING.contains(command(spell, false)) ? "surge" : "barrier";
				}
				case "attribute:Bridge" -> {
					return "lifeline";
				}
				case "attribute:Bird", "attribute:Wolf", "attribute:Bear", "attribute:Fox", "attribute:Eagle", "attribute:Snake", "attribute:Horse", "attribute:Cat", "attribute:Insect", "attribute:Frog" -> {
					return "aspect";
				}
				default -> {
				}
			}
		}
		String planned = spell.planned("form");
		if (planned != null && PLANNED_FORMS.contains(planned)) return planned;
		return wordForm(spell);
	}

	/** The shape the words alone give. */
	private static String wordForm(Spell spell) {
		for (String id : spell.words) {
			switch (id) {
				case "attribute:Shield", "attribute:Chest/Box" -> {
					return "shell";
				}
				case "attribute:Cloak" -> {
					return "veil";
				}
				case "attribute:Wall", "attribute:Tower", "attribute:Door" -> {
					// A wall that is driven (Repel, Throw, Banish...) rolls out and pushes enemies away; otherwise it stands.
					return SURGING.contains(command(spell, false)) ? "surge" : "barrier";
				}
				case "attribute:Statue" -> {
					return "bulwark";
				}
				case "attribute:Bridge" -> {
					return "lifeline";
				}
				case "attribute:Bed" -> {
					return "mend";
				}
				case "attribute:Fireplace" -> {
					return "sanctuary";
				}
				case "attribute:Cloud", "attribute:Currency" -> {
					return "boon";
				}
				case "attribute:Bird", "attribute:Wolf", "attribute:Bear", "attribute:Fox", "attribute:Eagle", "attribute:Snake", "attribute:Horse", "attribute:Cat", "attribute:Insect", "attribute:Frog" -> {
					// A creature word in a defense calls nothing living: you take on the creature's traits.
					return "aspect";
				}
				default -> {
				}
			}
		}
		if (spell.words.contains("base:Fear")) return "terror";
		String word = command(spell, false);
		// Disenchanting to conceal wipes the slate: every effect is stripped away and the cleansed slip out of sight.
		if (word.equals("Disenchant") && intention(spell).equals("Concealment")) return "cleanse";
		switch (word) {
			case "Banish", "Dismiss" -> {
				return "terror";
			}
			case "Confuse", "Disrupt", "Agitate", "Mislead", "Distort" -> {
				return "daze";
			}
			case "Paralyze", "Detain", "Stagnate", "Nullify", "Freeze", "Quell" -> {
				return "stagger";
			}
			case "Enthrall" -> {
				return "enthrall";
			}
			case "Repel", "Reflect", "Absorb", "Shield", "Protect", "Envelop", "Harbor", "Cover" -> {
				return "shell";
			}
			case "Conceal", "Obscure", "Silence" -> {
				return "veil";
			}
			case "Seal", "Close" -> {
				return "barrier";
			}
			case "Fortify", "Stabilize", "Petrify", "Solidify" -> {
				return "bulwark";
			}
			case "Soothe", "Calm", "Mend", "Regenerate", "Revive", "Grow" -> {
				return "mend";
			}
			case "Empower", "Amplify", "Enhance", "Enchant", "Imbue", "Accelerate" -> {
				return "boon";
			}
			case "Gather" -> {
				return "aegis";
			}
			default -> {
			}
		}
		String kind = command(spell, true);
		switch (kind) {
			case "scatter", "pulse" -> {
				return "aegis";
			}
			case "launch", "channel" -> {
				return "lifeline";
			}
			case "bind" -> {
				return "sanctuary";
			}
			case "summon" -> {
				return "guardian";
			}
			case "form", "shift" -> {
				return "boon";
			}
			case "grow" -> {
				return "mend";
			}
			default -> {
			}
		}
		String d = (spell.description == null ? "" : spell.description).toLowerCase(Locale.ROOT);
		if (d.contains("allies") || d.contains("companions") || d.contains("those around") || d.contains("everyone") || d.contains("teammates")) return "aegis";
		if (d.contains("circle") || d.contains("sanctuary") || d.contains("consecrat") || d.contains("zone")) return "sanctuary";
		if (d.contains("resist") || d.contains("immun") || d.contains("breathe") || d.contains("feather") || d.contains("skin")) return "boon";
		if (d.contains("regenerat") || d.contains("over time")) return "mend";
		return switch (intention(spell)) {
			case "Restoration", "Soothing" -> "mend";
			case "Fortitude" -> "bulwark";
			case "Preservation" -> "boon";
			default -> "shell";
		};
	}

	/** The shapes that work on enemies rather than friends: they draw on Spirit. */
	public static boolean controls(Spell spell) {
		String form = form(spell);
		return form.equals("terror") || form.equals("daze") || form.equals("stagger") || form.equals("enthrall");
	}

	/** Does the spell heal, rather than shield? */
	public static boolean healing(Spell spell) {
		// The Oracle's own choice comes first: a spell it made a shield stays a shield, whatever the intention.
		if ("heal".equals(spell.code.type)) return true;
		if ("shield".equals(spell.code.type)) return false;
		String i = intention(spell);
		return i.equals("Restoration") || i.equals("Soothing");
	}

	/** Maxima sometimes (the same way every time for the same spell) carries a self spell out to everyone near. */
	private static boolean spreads(Spell spell) {
		return spell.hasCharge("Maxima") && spell.id != null && (spell.id.hashCode() & 1) == 0;
	}

	/** Who counts as a friend: players, tamed animals, golems, villagers, familiars and conjured creatures. Never anything hostile. */
	public static boolean ally(Player caster, LivingEntity e) {
		if (e == null || !e.isAlive() || e instanceof net.minecraft.world.entity.monster.Enemy) return false;
		if (e instanceof Player) return !e.isSpectator();
		if (e instanceof net.minecraft.world.entity.TamableAnimal pet) return pet.isTame();
		if (e instanceof net.minecraft.world.entity.animal.golem.IronGolem || e instanceof net.minecraft.world.entity.animal.golem.SnowGolem) return true;
		if (e instanceof net.minecraft.world.entity.npc.villager.AbstractVillager) return true;
		if (e instanceof net.minecraft.world.entity.animal.allay.Allay) return true;
		if (e instanceof net.minecraft.world.entity.animal.equine.AbstractHorse horse) return horse.isTamed();
		return com.omnispell.familiar.Familiars.isFamiliar(e) || Conjuring.isConjured(e);
	}

	private static List<LivingEntity> alliesNear(ServerPlayer caster, Vec3 at, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : caster.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius), e -> e != caster && ally(caster, e))) {
			if (e.position().distanceTo(at) <= radius) out.add(e);
		}
		return out;
	}

	// ------------------------------------------------------------------ the gifts

	private static final net.minecraft.resources.Identifier SHIELD_ID = com.omnispell.Omnispell.id("ally_shield");
	private static final net.minecraft.resources.Identifier ARMOR_ID = com.omnispell.Omnispell.id("stone_boon");
	private static final Map<UUID, Long> SHIELD_UNTIL = new HashMap<>();

	/** Absorption hearts for anyone, for a while. */
	private static void shield(Cast cast, LivingEntity e, float amount, int ticks) {
		if (e == cast.player) {
			CombatEffects.giveShield(cast, amount, ticks);
			return;
		}
		AttributeInstance max = e.getAttribute(Attributes.MAX_ABSORPTION);
		if (max == null) return;
		float total = Math.max(amount, e.getAbsorptionAmount());
		max.addOrUpdateTransientModifier(new AttributeModifier(SHIELD_ID, total, AttributeModifier.Operation.ADD_VALUE));
		e.setAbsorptionAmount(total);
		long token = Tasks.now() + ticks;
		SHIELD_UNTIL.put(e.getUUID(), token);
		Tasks.later(ticks, () -> {
			if (SHIELD_UNTIL.getOrDefault(e.getUUID(), 0L) != token) return;
			SHIELD_UNTIL.remove(e.getUUID());
			AttributeInstance again = e.getAttribute(Attributes.MAX_ABSORPTION);
			if (again != null) again.removeModifier(SHIELD_ID);
			e.setAbsorptionAmount(Math.min(e.getAbsorptionAmount(), e.getMaxAbsorption()));
		});
	}

	/** The spell's heal or shield, given to one creature. */
	private static void give(Cast cast, LivingEntity e, float amount, boolean heal, int ticks) {
		if (heal) {
			e.heal(amount);
			cast.level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
		} else {
			shield(cast, e, amount, ticks);
			cast.level.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 10, 0.3, 0.4, 0.3, 0.05);
		}
	}

	private static void effect(LivingEntity e, Holder<MobEffect> effect, int ticks, int amplifier) {
		e.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, true, true));
	}

	/** The element's blessing: what a defense spell of that element is good for. */
	public static void boon(ServerPlayer caster, LivingEntity e, String element, int ticks) {
		switch (element) {
			case "fire" -> effect(e, MobEffects.FIRE_RESISTANCE, ticks, 0);
			case "water" -> {
				effect(e, MobEffects.WATER_BREATHING, ticks, 0);
				effect(e, MobEffects.DOLPHINS_GRACE, ticks, 0);
			}
			case "wind" -> {
				effect(e, MobEffects.SLOW_FALLING, ticks, 0);
				effect(e, MobEffects.JUMP_BOOST, ticks, 0);
				effect(e, MobEffects.SPEED, ticks, 0);
			}
			case "storm" -> {
				effect(e, MobEffects.SPEED, ticks, 1);
				effect(e, MobEffects.HASTE, ticks, 0);
			}
			case "stone" -> {
				effect(e, MobEffects.RESISTANCE, ticks, 0);
				AttributeInstance armor = e.getAttribute(Attributes.ARMOR);
				if (armor != null) {
					armor.addOrUpdateTransientModifier(new AttributeModifier(ARMOR_ID, 6, AttributeModifier.Operation.ADD_VALUE));
					Tasks.later(ticks, () -> {
						AttributeInstance again = e.getAttribute(Attributes.ARMOR);
						if (again != null) again.removeModifier(ARMOR_ID);
					});
				}
			}
			case "ice" -> {
				effect(e, MobEffects.RESISTANCE, ticks, 0);
				e.setTicksFrozen(0);
			}
			case "light" -> {
				effect(e, MobEffects.NIGHT_VISION, Math.max(ticks, 400), 0);
				for (LivingEntity foe : e.level().getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(16), x -> x instanceof net.minecraft.world.entity.monster.Enemy)) {
					foe.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0), caster);
				}
			}
			case "shadow" -> effect(e, MobEffects.INVISIBILITY, ticks, 0);
			case "nature" -> effect(e, MobEffects.REGENERATION, ticks, 0);
			case "blood" -> {
				effect(e, MobEffects.HEALTH_BOOST, ticks, 1);
				effect(e, MobEffects.REGENERATION, Math.min(ticks, 100), 0);
			}
			case "void" -> {
				effect(e, MobEffects.SLOW_FALLING, ticks, 0);
				e.fallDistance = 0;
			}
			default -> effect(e, MobEffects.RESISTANCE, ticks, 0);
		}
	}

	/** Mending reaches plants too: crops nearby grow a little. */
	private static void tendPlants(ServerLevel level, Vec3 at, int radius) {
		int grown = 0;
		BlockPos center = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius))) {
			if (grown >= 8) break;
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof CropBlock crop) || crop.isMaxAge(state)) continue;
			crop.growCrops(level, pos.immutable(), state);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0);
			grown++;
		}
	}

	// ------------------------------------------------------------------ casting

	public static boolean cast(Cast cast, int turns) {
		Spell spell = cast.spell;
		ServerPlayer p = cast.player;
		ServerLevel level = cast.level;
		String form = form(spell);
		String element = AttackForms.element(spell);
		boolean heal = healing(spell);
		int tier = Math.max(1, Math.min(5, spell.tier));
		int ticks = (int) (cast.turns(Math.max(2, turns)) * (spell.hasCharge("Duratus") ? 1.6 : 1.0));
		double want = spell.code.primaryValue > 0 ? spell.code.primaryValue : heal ? tier * 8.0 + 10 : tier * 5.0 + 10;
		if (!heal) want *= 1.0 + Modifiers.value(p, "shield_overcharge") / 100.0;
		if (form.equals("aegis") || form.equals("sanctuary")) want *= 1.3; // reaching others costs more
		boolean control = controls(spell);
		ManaPool pool = Casting.plannedPool(spell);
		if (pool == null) pool = control ? ManaPool.SPIRIT : heal ? ManaPool.RESTORATION : ManaPool.BARRIER;
		double paid = Mana.spend(p, pool, want);
		if (paid < 1) return cast.fail("Your " + pool.label + " mana is spent.");
		float amount = (float) (paid * cast.scale * cast.mendingBonus() / (form.equals("aegis") || form.equals("sanctuary") ? 1.3 : 1.0));
		int color = Conjuring.tintFor(spell);
		Fx.play(level, spell.visual, Targeting.hand(p), p.position());
		switch (form) {
			case "veil" -> {
				give(cast, p, amount * 0.6f, heal, ticks);
				effect(p, MobEffects.INVISIBILITY, ticks, 0);
				Wards.raise(cast, ticks, "shadow", true);
			}
			case "boon" -> {
				boon(p, p, element, ticks);
				give(cast, p, amount * 0.6f, heal, ticks);
				level.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.5);
			}
			case "bulwark" -> {
				effect(p, MobEffects.RESISTANCE, ticks, 1);
				effect(p, MobEffects.SLOWNESS, ticks, 0);
				AttributeInstance kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
				if (kb != null) {
					kb.addOrUpdateTransientModifier(new AttributeModifier(ARMOR_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
					Tasks.later(ticks, () -> {
						AttributeInstance again = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
						if (again != null) again.removeModifier(ARMOR_ID);
					});
				}
				give(cast, p, amount, heal, ticks);
				level.playSound(null, p.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0f, 0.5f);
			}
			case "mend" -> {
				effect(p, MobEffects.REGENERATION, Math.max(100, ticks / 2), tier >= 4 ? 1 : 0);
				give(cast, p, amount * 0.5f, true, ticks);
				tendPlants(level, p.position(), 4);
			}
			case "aegis" -> {
				double radius = 8 + (spell.hasCharge("Maxima") ? 4 : 0) + (spell.hasCharge("Sparsus") ? 3 : 0);
				give(cast, p, amount, heal, ticks);
				boon(p, p, element, Math.min(ticks, 400));
				int reached = 0;
				for (LivingEntity friend : alliesNear(p, p.position(), radius)) {
					give(cast, friend, amount * 0.75f, heal, ticks);
					boon(p, friend, element, Math.min(ticks, 400));
					Fx.line(level, p.getBoundingBox().getCenter(), friend.getBoundingBox().getCenter(), color, null, 2);
					reached++;
				}
				Fx.ring(level, p.position().add(0, 0.2, 0), radius, color, null);
				if (heal) tendPlants(level, p.position(), 6);
				if (reached == 0) cast.tell("No one was near enough to share it with, so it was yours alone.");
			}
			case "lifeline" -> {
				LivingEntity target = Targeting.entity(p, 24);
				if (target == null || !ally(p, target)) {
					target = null;
					double best = 10 * 10;
					for (LivingEntity friend : alliesNear(p, p.position(), 10)) {
						double d = friend.distanceToSqr(p);
						if (d < best && friend.getHealth() < friend.getMaxHealth() + 1) {
							best = d;
							target = friend;
						}
					}
				}
				LivingEntity who = target == null ? p : target;
				give(cast, who, amount, heal, ticks);
				boon(p, who, element, Math.min(ticks, 400));
				if (who != p && Beams.beamClaim(spell)) {
					link(cast, who, amount, heal, ticks, element, color); // a beam that holds while it lasts
				} else if (who != p) {
					Fx.line(level, Targeting.hand(p), who.getBoundingBox().getCenter(), color, ParticleTypes.END_ROD, 4);
					level.playSound(null, who.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);
					if (who instanceof ServerPlayer friend) friend.sendOverlayMessage(net.minecraft.network.chat.Component.literal(p.getName().getString() + "'s spell reaches you.").withStyle(net.minecraft.ChatFormatting.AQUA));
				}
			}
			case "sanctuary" -> sanctuary(cast, p.position(), amount, heal, ticks, element, color);
			case "terror", "daze", "stagger", "enthrall" -> {
				give(cast, p, amount * 0.6f, heal, ticks);
				sway(cast, form, color);
			}
			case "barrier" -> {
				give(cast, p, amount * 0.5f, heal, ticks);
				barrier(cast, element, ticks);
			}
			case "cleanse" -> {
				LivingEntity looked = Targeting.entity(p, 24);
				LivingEntity who = looked == null ? p : looked;
				boolean friend = who == p || ally(p, who);
				who.removeAllEffects();
				who.clearFire();
				level.sendParticles(ParticleTypes.END_ROD, who.getX(), who.getY() + who.getBbHeight() * 0.5, who.getZ(), 40, 0.4, 0.6, 0.4, 0.08);
				level.playSound(null, who.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.7f);
				if (who != p) Fx.line(level, Targeting.hand(p), who.getBoundingBox().getCenter(), color, ParticleTypes.END_ROD, 4);
				if (friend) {
					int unseen = 120 + 40 * tier;
					effect(who, MobEffects.INVISIBILITY, unseen, 0);
					if (who instanceof ServerPlayer hidden) Shroud.apply(hidden, unseen);
					give(cast, who, amount * 0.3f, heal, ticks);
				}
			}
			case "surge" -> {
				give(cast, p, amount * 0.4f, heal, ticks);
				Surges.roll(cast, false);
			}
			case "guardian" -> {
				give(cast, p, amount * 0.5f, heal, ticks);
				Conjuring.guardian(cast, Math.max(4, turns) * (spell.hasCharge("Duratus") ? 1.6 : 1.0));
			}
			case "aspect" -> {
				give(cast, p, amount * 0.6f, heal, ticks);
				aspect(cast, creature(spell), ticks);
			}
			case "cocoon" -> {
				give(cast, p, amount, heal, ticks);
				if (heal) effect(p, MobEffects.REGENERATION, Math.max(100, ticks / 2), 0);
				if (cocoon(cast, element, ticks) == 0) cast.tell("There was no room for it to close around you.");
			}
			default -> { // shell
				float hearts = amount * (Wards.habit(spell).equals("bulwark") ? 1.25f : 1.0f);
				give(cast, p, hearts, heal, ticks);
				Wards.raise(cast, ticks);
			}
		}
		extras(cast, form, element, heal, amount, ticks, color);
		// Maxima sometimes reaches out: a self spell also covers the friends close by, at half strength.
		if (spreads(spell) && !form.equals("aegis") && !form.equals("sanctuary") && !form.equals("lifeline")) {
			for (LivingEntity friend : alliesNear(p, p.position(), 6)) {
				give(cast, friend, amount * 0.5f, heal, ticks);
				boon(p, friend, element, Math.min(ticks, 400));
				Fx.line(level, p.getBoundingBox().getCenter(), friend.getBoundingBox().getCenter(), color, null, 2);
			}
		}
		return true;
	}

	/** What the Oracle added on top of the form: each happens only if it planned it, and never twice over. */
	private static void extras(Cast cast, String form, String element, boolean heal, float amount, int ticks, int color) {
		Spell spell = cast.spell;
		ServerPlayer p = cast.player;
		int tier = Math.max(1, Math.min(5, spell.tier));
		if (spell.plannedExtra("cocoon") && !form.equals("cocoon")) cocoon(cast, element, ticks);
		if (spell.plannedExtra("regen") && !form.equals("mend")) effect(p, MobEffects.REGENERATION, Math.max(100, ticks / 2), tier >= 4 ? 1 : 0);
		if (spell.plannedExtra("unseen") && !form.equals("veil") && !form.equals("cleanse")) {
			int unseen = 120 + 40 * tier;
			effect(p, MobEffects.INVISIBILITY, unseen, 0);
			Shroud.apply(p, unseen);
		}
		if (spell.plannedExtra("boon") && !form.equals("boon") && !form.equals("aegis") && !form.equals("lifeline")) boon(p, p, element, Math.min(ticks, 400));
		if (spell.plannedExtra("cleanse") && !form.equals("cleanse")) {
			List<Holder<MobEffect>> harmful = new ArrayList<>();
			for (MobEffectInstance instance : p.getActiveEffects()) if (!instance.getEffect().value().isBeneficial()) harmful.add(instance.getEffect());
			for (Holder<MobEffect> e : harmful) p.removeEffect(e);
			p.clearFire();
		}
		if (spell.plannedExtra("sate")) {
			// Hunger: the food bar fills, and with a spell that reaches friends (or shares), theirs too.
			sate(cast, p, tier);
			if (form.equals("aegis") || form.equals("sanctuary") || spell.plannedExtra("share")) {
				for (LivingEntity friend : alliesNear(p, p.position(), form.equals("aegis") ? 8 : 6)) if (friend instanceof ServerPlayer other) sate(cast, other, tier);
			} else if (form.equals("lifeline")) {
				LivingEntity looked = Targeting.entity(p, 24);
				if (looked instanceof ServerPlayer other && ally(p, other)) sate(cast, other, tier);
			}
		}
		if (spell.plannedExtra("share") && !spreads(spell) && !form.equals("aegis") && !form.equals("sanctuary") && !form.equals("lifeline")) {
			for (LivingEntity friend : alliesNear(p, p.position(), 6)) {
				give(cast, friend, amount * 0.5f, heal, ticks);
				Fx.line(cast.level, p.getBoundingBox().getCenter(), friend.getBoundingBox().getCenter(), color, null, 2);
			}
		}
	}

	/** Fills a player's food bar: two drumsticks and a little more for each tier, with saturation to match. */
	private static void sate(Cast cast, ServerPlayer who, int tier) {
		who.getFoodData().eat(2 + 2 * tier, 0.3f + 0.1f * tier);
		cast.level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, net.minecraft.world.item.Items.BREAD),
				who.getX(), who.getY() + 1.2, who.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
		cast.level.playSound(null, who.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
	}

	/** The block a cocoon of this element is made of: dirt for Earth, ice for frost, moss for living things, coloured glass for light and shadow. */
	private static Block cocoonBlock(Spell spell, String element) {
		return switch (element) {
			case "stone" -> {
				Block material = Terraform.material(spell);
				yield material == null ? Blocks.COBBLESTONE : material;
			}
			case "ice" -> Blocks.PACKED_ICE;
			case "water" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.LIGHT_BLUE);
			case "nature" -> Blocks.MOSS_BLOCK;
			case "fire" -> Blocks.MAGMA_BLOCK;
			case "light" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.YELLOW);
			case "shadow" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.BLACK);
			case "void" -> Blocks.TINTED_GLASS;
			case "blood" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.RED);
			case "wind", "storm" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.WHITE);
			default -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.PURPLE);
		};
	}

	/**
	 * A cocoon: a small hollow sphere of the element's own blocks closes around the caster, leaving them room to
	 * stand, and comes apart a few seconds later. Only empty space is filled, and never where someone else stands.
	 */
	public static int cocoon(Cast cast, String element, int ticks) {
		ServerPlayer p = cast.player;
		ServerLevel level = cast.level;
		Block block = cocoonBlock(cast.spell, element);
		int tier = Math.max(1, Math.min(5, cast.spell.tier));
		int life = Math.max(60, Math.min(ticks, 100 + 20 * tier));
		BlockPos feet = p.blockPosition();
		Vec3 heart = new Vec3(feet.getX() + 0.5, feet.getY() + 1.0, feet.getZ() + 0.5);
		int placed = 0;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 3, 2))) {
			double d = Vec3.atCenterOf(pos).distanceTo(heart);
			if (d < 1.6 || d > 2.6) continue;
			BlockPos at = pos.immutable();
			if (!level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at), e -> e != p).isEmpty()) continue;
			if (TempBlocks.place(level, at, block, life)) placed++;
		}
		if (placed > 0) {
			level.playSound(null, feet, block.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.PLAYERS, 1.0f, 0.8f);
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState()), heart.x, heart.y, heart.z, 30, 1.2, 1.0, 1.2, 0.1);
		}
		return placed;
	}

	/**
	 * How well a hold works on a creature: fully on anything with 20 health or less, by degrees up to
	 * 80, not at all beyond, and never on bosses. A pack of skeletons scatters; a warden does not notice.
	 */
	public static double sway(LivingEntity target) {
		if (MobControl.immune(target) || com.omnispell.boss.Bosses.defOf(target) != null) return 0;
		return Math.max(0, Math.min(1, (80 - target.getMaxHealth()) / 60.0));
	}

	/** Fear, confusion, stun or charm, on every hostile creature around you. */
	private static void sway(Cast cast, String form, int color) {
		ServerPlayer p = cast.player;
		ServerLevel level = cast.level;
		int tier = Math.max(1, Math.min(5, cast.spell.tier));
		double radius = 6 + tier + (cast.spell.hasCharge("Maxima") ? 3 : 0);
		String kind = switch (form) {
			case "terror" -> "fear";
			case "daze" -> "confusion";
			case "stagger" -> "stun";
			default -> "charm";
		};
		int held = 0, shrugged = 0;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(radius), e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive())) {
			if (mob.distanceTo(p) > radius) continue;
			double power = sway(mob);
			if (power <= 0) {
				shrugged++;
				continue;
			}
			int ticks = (int) Math.max(20, (3 + tier + (kind.equals("fear") ? 1 : 0)) * 20 * power * (cast.spell.hasCharge("Duratus") ? 1.6 : 1.0));
			MobControl.apply(p, mob, kind, ticks, null);
			if (kind.equals("fear")) {
				Vec3 away = mob.position().subtract(p.position()).multiply(1, 0, 1);
				if (away.lengthSqr() > 0.01) mob.push(away.normalize().x * 0.6, 0.2, away.normalize().z * 0.6);
			}
			var mark = switch (kind) {
				case "fear" -> ParticleTypes.SCULK_SOUL;
				case "confusion" -> ParticleTypes.WITCH;
				case "stun" -> ParticleTypes.ELECTRIC_SPARK;
				default -> ParticleTypes.HEART;
			};
			level.sendParticles(mark, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
			held++;
		}
		Fx.ring(level, p.position().add(0, 0.3, 0), radius, color, null);
		level.playSound(null, p.blockPosition(), switch (kind) {
			case "fear" -> SoundEvents.WARDEN_ROAR;
			case "stun" -> SoundEvents.ANVIL_LAND;
			case "charm" -> SoundEvents.AMETHYST_BLOCK_CHIME;
			default -> SoundEvents.ILLUSIONER_CAST_SPELL;
		}, SoundSource.PLAYERS, kind.equals("fear") ? 0.5f : 1.0f, kind.equals("stun") ? 1.6f : 1.0f);
		String verb = switch (kind) {
			case "fear" -> "flee";
			case "confusion" -> "reel, confused";
			case "stun" -> "are stunned";
			default -> "are charmed";
		};
		if (held > 0) cast.tell(held + (held == 1 ? " creature " : " creatures ") + (held == 1 ? verb.replace("flee", "flees").replace("are ", "is ").replace("reel", "reels") : verb) + (shrugged > 0 ? "; " + shrugged + " too strong to sway." : "."));
		else if (shrugged > 0) cast.tell("Nothing here is weak enough to sway.");
	}

	/** A circle on the ground: friends inside mend or keep their shield; hostile creatures inside are slowed. */
	private static void sanctuary(Cast cast, Vec3 center, float amount, boolean heal, int ticks, String element, int color) {
		ServerLevel level = cast.level;
		ServerPlayer p = cast.player;
		double radius = 4 + cast.spell.tier / 2.0 + (cast.spell.hasCharge("Maxima") ? 2 : 0);
		int seconds = Math.max(4, ticks / 20);
		float perSecond = Math.max(0.5f, amount * 1.5f / seconds);
		level.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.4f);
		for (int k = 0; k < ticks; k += 10) {
			int beat = k;
			Tasks.later(k, () -> {
				Fx.ring(level, center.add(0, 0.1, 0), radius, color, null);
				for (int i = 0; i < 6; i++) {
					double a = level.getRandom().nextDouble() * Math.PI * 2, r = level.getRandom().nextDouble() * radius;
					level.sendParticles(ParticleTypes.END_ROD, center.x + Math.cos(a) * r, center.y + 0.2, center.z + Math.sin(a) * r, 0, 0, 0.08, 0, 1);
				}
				if (beat % 20 != 0) return;
				List<LivingEntity> inside = new ArrayList<>(alliesNear(p, center, radius));
				if (p.isAlive() && p.position().distanceTo(center) <= radius) inside.add(p);
				for (LivingEntity friend : inside) {
					if (heal) friend.heal(perSecond);
					else if (friend.getAbsorptionAmount() < amount * 0.5f) shield(cast, friend, amount * 0.5f, 40);
				}
				for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(radius), x -> x instanceof net.minecraft.world.entity.monster.Enemy)) {
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1), p);
				}
				if (heal && beat % 100 == 0) tendPlants(level, center, (int) radius);
			});
		}
	}

	// ------------------------------------------------------------------ a creature's traits

	/** The creature a defense spell's attribute word names (wolf, bear, fox, eagle, bird, snake, horse, cat, insect, frog), or "". */
	public static String creature(Spell spell) {
		for (String id : spell.words) {
			switch (id) {
				case "attribute:Bird", "attribute:Wolf", "attribute:Bear", "attribute:Fox", "attribute:Eagle", "attribute:Snake", "attribute:Horse", "attribute:Cat", "attribute:Insect", "attribute:Frog" -> {
					return id.substring("attribute:".length()).toLowerCase(Locale.ROOT);
				}
				default -> {
				}
			}
		}
		return "";
	}

	private static String aspectText(String creature) {
		return switch (creature) {
			case "wolf" -> "the wolf's traits: speed and strength, and a howl that weakens hostile creatures near you, while your pets and friends close by run with you";
			case "bear" -> "the bear's traits: a thick hide that blows land softer on, and extra hearts";
			case "fox" -> "the fox's traits: quick feet, a pouncing leap and eyes for the dark, and while you crouch you vanish from sight";
			case "eagle" -> "the eagle's traits: a slow glide when you fall, eyes for the dark, and every hostile creature within thirty blocks shows itself to you";
			case "bird" -> "the bird's traits: light as a feather, you leap high, run quick and glide when you fall";
			case "snake" -> "the snake's traits: quick, and venomous: whatever strikes you is poisoned";
			case "horse" -> "the horse's traits: great speed and a strong leap";
			case "cat" -> "the cat's traits: you land on your feet from any height, you see in the dark, and creepers keep their distance";
			case "insect" -> "the insect's traits: quick hands and feet, and a sting: whatever strikes you is stung and poisoned";
			case "frog" -> "the frog's traits: a long leap, breath under water and a swimmer's speed";
			default -> "a creature's traits";
		};
	}

	/** Takes on a creature's traits for the spell's length: effects now, and the rest kept as a toggle that other parts of the game look for. */
	private static void aspect(Cast cast, String creature, int ticks) {
		ServerPlayer p = cast.player;
		ServerLevel level = cast.level;
		switch (creature) {
			case "wolf" -> {
				effect(p, MobEffects.SPEED, ticks, 0);
				effect(p, MobEffects.STRENGTH, ticks, 0);
				level.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.6f, 1.6f);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(10), x -> x.isAlive() && x != p)) {
					if (e instanceof net.minecraft.world.entity.monster.Enemy) e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), p);
					else if (ally(p, e)) {
						effect(e, MobEffects.SPEED, Math.min(ticks, 600), 0);
						effect(e, MobEffects.STRENGTH, Math.min(ticks, 600), 0);
					}
				}
			}
			case "bear" -> {
				effect(p, MobEffects.RESISTANCE, ticks, 0);
				effect(p, MobEffects.HEALTH_BOOST, ticks, 1);
				p.heal(4f);
			}
			case "fox" -> {
				effect(p, MobEffects.SPEED, ticks, 0);
				effect(p, MobEffects.JUMP_BOOST, ticks, 0);
				effect(p, MobEffects.NIGHT_VISION, ticks, 0);
			}
			case "eagle" -> {
				effect(p, MobEffects.SLOW_FALLING, ticks, 0);
				effect(p, MobEffects.NIGHT_VISION, ticks, 0);
			}
			case "bird" -> {
				effect(p, MobEffects.SLOW_FALLING, ticks, 0);
				effect(p, MobEffects.JUMP_BOOST, ticks, 1);
				effect(p, MobEffects.SPEED, ticks, 0);
			}
			case "snake" -> effect(p, MobEffects.SPEED, ticks, 0);
			case "horse" -> {
				effect(p, MobEffects.SPEED, ticks, 1);
				effect(p, MobEffects.JUMP_BOOST, ticks, 1);
			}
			case "cat" -> effect(p, MobEffects.NIGHT_VISION, ticks, 0);
			case "insect" -> {
				effect(p, MobEffects.HASTE, ticks, 1);
				effect(p, MobEffects.SPEED, ticks, 0);
			}
			case "frog" -> {
				effect(p, MobEffects.JUMP_BOOST, ticks, 1);
				effect(p, MobEffects.WATER_BREATHING, ticks, 0);
				effect(p, MobEffects.DOLPHINS_GRACE, ticks, 0);
			}
			default -> {
			}
		}
		if (!creature.isEmpty()) Modifiers.add(p, "aspect_" + creature, 1, ticks);
		level.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.5);
		aspectSecond(p);
	}

	/** Once a second, the traits that need watching: the fox's crouch, the eagle's eye, the cat that creepers fear. */
	public static void aspectSecond(ServerPlayer p) {
		ServerLevel level = p.level();
		if (Modifiers.has(p, "aspect_fox") && p.isShiftKeyDown()) effect(p, MobEffects.INVISIBILITY, 30, 0);
		if (Modifiers.has(p, "aspect_eagle")) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(30), x -> x.isAlive() && x instanceof net.minecraft.world.entity.monster.Enemy)) {
				e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false), p);
			}
		}
		if (Modifiers.has(p, "aspect_cat")) {
			for (net.minecraft.world.entity.monster.Creeper creeper : level.getEntitiesOfClass(net.minecraft.world.entity.monster.Creeper.class, p.getBoundingBox().inflate(12), x -> x.isAlive())) {
				if (!MobControl.held(creeper, "fear")) MobControl.apply(p, creeper, "fear", 60, p);
			}
		}
	}

	/** A blow struck at someone wearing a venomous creature's traits poisons the striker. */
	public static void aspectStruck(ServerPlayer p, LivingEntity attacker) {
		// Undead (and spiders) shrug off poison, so venom burns them directly instead.
		if (Modifiers.has(p, "aspect_snake") && !attacker.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0), p)) attacker.hurtServer(p.level(), p.level().damageSources().thorns(p), 3f);
		if (Modifiers.has(p, "aspect_insect")) {
			attacker.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0), p);
			attacker.hurtServer(p.level(), p.level().damageSources().thorns(p), 2f);
		}
	}

	/**
	 * A lifeline that says it is a beam (a beam, ray, stream...) holds as one: a beam joins you to the friend
	 * for as long as the spell lasts, and while it holds (within twenty-four blocks, in sight) it keeps
	 * mending them, or keeps their shield topped up.
	 */
	private static void link(Cast cast, LivingEntity who, float amount, boolean heal, int ticks, String element, int color) {
		ServerPlayer p = cast.player;
		ServerLevel level = cast.level;
		int life = Math.min(ticks, 1200);
		if (who instanceof ServerPlayer friend) friend.sendOverlayMessage(net.minecraft.network.chat.Component.literal(p.getName().getString() + "'s beam holds you.").withStyle(net.minecraft.ChatFormatting.AQUA));
		for (int t = 0; t < life; t += 10) {
			int at = t;
			Tasks.later(t, () -> {
				if (!p.isAlive() || !who.isAlive() || p.level() != who.level() || p.distanceToSqr(who) > 24 * 24 || !p.hasLineOfSight(who)) return;
				Beams.beam(level, Targeting.hand(p), who.getBoundingBox().getCenter(), element, color);
				if (at % 40 == 0) {
					if (heal) who.heal(Math.max(1f, amount * 0.1f));
					else if (who.getAbsorptionAmount() < amount * 0.5f) shield(cast, who, amount * 0.5f, 60);
				}
			});
		}
	}

	/** A barrier wall two blocks in front of you, in the element's colour, gone when the spell ends. */
	private static void barrier(Cast cast, String element, int ticks) {
		ServerPlayer p = cast.player;
		ServerLevel level = cast.level;
		net.minecraft.core.Direction facing = p.getDirection();
		net.minecraft.core.Direction side = facing.getClockWise();
		boolean big = cast.spell.hasCharge("Maxima");
		int half = big ? 2 : 1;
		int height = big ? 4 : 3;
		net.minecraft.world.level.block.Block block = element.equals("wind") ? Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.WHITE) : TempBlocks.material(element);
		BlockPos base = p.blockPosition().relative(facing, 2);
		for (int s = -half; s <= half; s++) {
			for (int h = 0; h < height; h++) TempBlocks.place(level, base.relative(side, s).above(h), block, ticks);
		}
		level.playSound(null, base, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	// ------------------------------------------------------------------ the book

	public static String describe(Spell spell) {
		String form = form(spell);
		String element = AttackForms.element(spell);
		boolean heal = healing(spell);
		String gift = heal ? "healing" : "a shield of extra hearts";
		String littleGift = heal ? "a little healing" : "a small shield of extra hearts";
		String what = switch (form) {
			case "veil" -> "It hides you: you turn invisible, take " + gift + ", and some blows pass through you.";
			case "boon" -> "It blesses you with " + boonText(element) + ", and " + littleGift + ".";
			case "bulwark" -> "It roots you like a statue: blows land much softer and nothing moves you, but you walk slowly. You also take " + gift + ".";
			case "mend" -> "It mends you over time, and makes crops nearby grow.";
			case "aegis" -> "It bursts outward: you and every friend near you (players, pets, golems, villagers, familiars, conjured creatures) get " + gift + " and " + boonText(element) + ".";
			case "lifeline" -> "It reaches out to the friend you look at (or the nearest one hurt), giving them " + gift + " and " + boonText(element) + ". With no friend near, it is yours."
					+ (Beams.beamClaim(spell) ? " The beam holds while the spell lasts, as long as they stay in sight and within twenty-four blocks, and keeps " + (heal ? "mending them." : "their shield topped up.") : "");
			case "sanctuary" -> "It marks a circle on the ground around you. Friends standing in it " + (heal ? "mend every second" : "keep a shield") + " while it lasts, and hostile creatures in it are slowed.";
			case "barrier" -> "It raises a barrier wall in front of you, and gives you " + gift + ".";
			case "cleanse" -> "It wipes the slate clean. Cast at the friend you look at (or yourself, looking at no one), it strips away every effect on them, good and bad, puts out fire, and leaves them invisible for a few seconds with " + littleGift + ", so they can slip away. Cast at an enemy, it strips every effect from them and nothing more.";
			case "surge" -> Surges.describe(false, element) + " You also get " + littleGift + ".";
			case "aspect" -> "It gives you " + aspectText(creature(spell)) + ". You also get " + littleGift + ".";
			case "cocoon" -> "A small sphere of " + cocoonName(spell, element) + " closes around you for a few seconds, keeping foes off while you take " + gift + ". You can break out of it.";
			case "guardian" -> "It calls a guardian creature that fights at your side and takes a share of the blows meant for you. You also get " + gift + ".";
			case "terror" -> "It strikes fear into every hostile creature around you, and they run. The weak (20 health or less) flee longest; the strong for less; bosses and the like not at all. You also get " + littleGift + ".";
			case "daze" -> "It leaves every hostile creature around you reeling and confused. The weak are lost longest; bosses and the like are not moved. You also get " + littleGift + ".";
			case "stagger" -> "It stuns every hostile creature around you where it stands. The weak are held longest; bosses and the like are not. You also get " + littleGift + ".";
			case "enthrall" -> "It charms every hostile creature around you: for a while they will not fight you. The weak stay charmed longest; bosses and the like are not moved. You also get " + littleGift + ".";
			default -> "It gives you " + gift + ". " + Wards.describe(spell);
		};
		if (spreads(spell) && !form.equals("aegis") && !form.equals("sanctuary") && !form.equals("lifeline")) what += " Maxima carries it out to your friends nearby too, at half strength.";
		return what;
	}

	private static String cocoonName(Spell spell, String element) {
		Block block = cocoonBlock(spell, element);
		return block == Blocks.DIRT ? "dirt" : block == Blocks.PACKED_ICE ? "ice" : block == Blocks.MOSS_BLOCK ? "moss" : block == Blocks.MAGMA_BLOCK ? "magma"
				: block == Blocks.COBBLESTONE || block == Blocks.COBBLED_DEEPSLATE ? "stone" : block == Blocks.SAND ? "sand" : block == Blocks.MUD ? "mud" : block == Blocks.GRAVEL ? "gravel"
				: block == Blocks.OAK_PLANKS ? "wood" : block == Blocks.BASALT ? "basalt" : "coloured glass";
	}

	private static String boonText(String element) {
		return switch (element) {
			case "fire" -> "fire resistance";
			case "water" -> "water breathing and swift swimming";
			case "wind" -> "slow falling, higher jumps and speed";
			case "storm" -> "speed and haste";
			case "stone" -> "stone skin (resistance and extra armor)";
			case "ice" -> "resistance and warmth against the cold";
			case "light" -> "night vision, and every hostile creature near you glows";
			case "shadow" -> "invisibility";
			case "nature" -> "regeneration";
			case "blood" -> "extra hearts of health";
			case "void" -> "slow falling";
			default -> "resistance";
		};
	}
}
