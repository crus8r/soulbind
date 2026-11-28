package com.omnispell.spell;

import com.omnispell.data.Word;
import com.omnispell.data.WordBank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * How an attack spell takes hold of what it hits, beyond the damage. The command decides
 * the form for certain: a launching command hurls the target up, a binding one roots it,
 * a forming one encases it, and so on. The element adds its own signature now and then:
 * ice freezes its target into a block of ice, wind catches it in a vortex, fire engulfs it.
 */
public final class AttackForms {
	private AttackForms() {
	}

	/** The spell's element, folded into one of twelve. */
	public static String element(Spell spell) {
		// The base word's own element comes first: water is water, earth is stone, thunder is storm, whatever the Oracle labelled it.
		for (String id : spell.words) {
			if (!id.startsWith("base:")) continue;
			String own = switch (id.substring(5)) {
				case "Fire", "Ember", "Lava", "Heat", "Heatwave", "Sulfur" -> "fire";
				case "Water", "Wave", "Steam", "Mist" -> "water";
				case "Earth", "Sand", "Dust", "Mud", "Metal", "Crystal", "Obsidian", "Amber", "Pressure", "Gravity" -> "stone";
				case "Air", "Gale", "Sound", "Echo", "Vibration", "Music", "Thunder" -> "wind"; // thunder is sound, not lightning
				case "Lightning" -> "storm";
				case "Ice", "Frost", "Frostbite", "Sleet" -> "ice";
				case "Nature", "Vine", "Verdant", "Thorn", "Wood", "Poison" -> "nature";
				case "Light", "Radiance", "Starlight", "Moonlight", "Sunlight", "Beacon" -> "light";
				case "Shadow", "Darkness", "Gloom", "Shroud" -> "shadow";
				case "Void", "Space", "Hollow" -> "void";
				case "Blood", "Marrow", "Flesh" -> "blood";
				default -> null;
			};
			if (own != null) return own;
		}
		String e = spell.code.element == null ? "" : spell.code.element.toLowerCase();
		return switch (e) {
			case "fire", "flame", "heat", "lava", "ember", "magma", "inferno" -> "fire";
			case "ice", "frost", "cold", "snow" -> "ice";
			case "wind", "air", "gale", "sound" -> "wind";
			case "storm", "lightning", "thunder", "electric" -> "storm";
			case "stone", "earth", "rock", "metal", "sand", "gravity" -> "stone";
			case "nature", "plant", "poison", "life", "acid" -> "nature";
			case "water", "tide" -> "water";
			case "light", "holy", "radiant" -> "light";
			case "shadow", "dark", "darkness", "death", "necrotic" -> "shadow";
			case "void", "ethereal", "space" -> "void";
			case "blood" -> "blood";
			default -> "arcane";
		};
	}

	private static Word command(Spell spell) {
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word != null && word.type.equals("command")) return word;
		}
		return null;
	}

	public static final java.util.Set<String> PLANNED_FORMS = java.util.Set.of("none", "hurl", "blast", "root", "encase", "engulf", "burst", "smite", "siphon", "displace", "linger", "pull", "slam", "rend", "crumble");

	/** The form the command gives, or null for none. */
	public static String commandForm(Spell spell) {
		// The Oracle's planned form comes first ("none": just the hit).
		String planned = "attack".equals(spell.type) ? spell.planned("form") : null;
		if (planned != null && PLANNED_FORMS.contains(planned)) return planned.equals("none") ? null : planned;
		Word word = command(spell);
		if (word == null) return null;
		switch (word.english) {
			case "Sever", "Cleave", "Pierce", "Fracture", "Breach", "Wither", "Decay" -> {
				return "rend";
			}
			case "Disintegrate", "Erode" -> {
				return "crumble";
			}
			case "Banish", "Repel", "Displace", "Dismiss" -> {
				return word.english.equals("Displace") || word.english.equals("Dismiss") ? "displace" : "blast";
			}
			case "Freeze", "Petrify", "Solidify", "Seal", "Stagnate" -> {
				return "encase";
			}
			case "Gather", "Attract", "Retract" -> {
				// Gravity and its kin do not merely drag: they slam what they draw together.
				for (String id : spell.words) if (id.equals("base:Gravity") || id.equals("base:Magnetism") || id.equals("base:Pressure")) return "slam";
				return "pull";
			}
			case "Drain", "Absorb", "Consume" -> {
				return "siphon";
			}
			default -> {
			}
		}
		return switch (word.explore == null ? "" : word.explore) {
			case "launch" -> "hurl";
			case "pulse" -> "blast";
			case "bind" -> "root";
			case "form" -> "encase";
			case "envelop" -> "engulf";
			case "scatter" -> "burst";
			case "summon" -> "smite";
			case "channel" -> "siphon";
			case "shift" -> "displace";
			case "grow" -> "linger";
			default -> null;
		};
	}

	/** Tips an arrow with an element: fire burns, ice slows, nature poisons, shadow withers, and so on. */
	public static void tip(net.minecraft.world.entity.projectile.arrow.Arrow arrow, String element, int tier) {
		int t = Math.max(1, Math.min(5, tier));
		switch (element) {
			case "fire" -> arrow.igniteForSeconds(100);
			case "ice" -> arrow.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40 + 10 * t, t >= 4 ? 2 : 1));
			case "nature" -> arrow.addEffect(new MobEffectInstance(MobEffects.POISON, 60 + 10 * t, t >= 4 ? 1 : 0));
			case "shadow" -> arrow.addEffect(new MobEffectInstance(MobEffects.WITHER, 40 + 10 * t, 0));
			case "light" -> arrow.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0));
			case "water", "arcane" -> arrow.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
			case "void" -> arrow.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 15 + 3 * t, 0));
			case "blood" -> arrow.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 1));
			case "wind", "storm" -> arrow.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0));
			case "stone" -> arrow.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0));
			default -> {
			}
		}
	}

	/** The element's own form, which shows up some of the time. */
	public static String signature(String element) {
		return switch (element) {
			case "fire" -> "engulf";
			case "ice" -> "encase";
			case "wind" -> "vortex";
			case "storm" -> "smite";
			case "stone" -> "hurl";
			case "nature" -> "root";
			case "water" -> "blast";
			case "light", "shadow" -> "engulf";
			case "void" -> "displace";
			case "blood" -> "siphon";
			default -> "burst";
		};
	}

	/** Words for the book: what the spell does to what it hits. */
	public static String describe(Spell spell) {
		String element = element(spell);
		String form = commandForm(spell);
		String sig = signature(element);
		StringBuilder sb = new StringBuilder();
		if (form != null) sb.append("What it hits is ").append(phrase(form, element)).append('.');
		if (!sig.equals(form)) {
			if (sb.length() > 0) sb.append(' ');
			sb.append(element.equals("ice") ? "Often" : "Sometimes").append(", ").append(phrase(sig, element)).append(" as well.");
		}
		return sb.toString();
	}

	private static String phrase(String form, String element) {
		return switch (form) {
			case "hurl" -> element.equals("stone") ? "thrown skyward by the ground heaving under it" : "hurled into the air";
			case "blast" -> element.equals("water") ? "swept back by a wave" : "blasted backward";
			case "root" -> element.equals("nature") ? "rooted in place by grasping vines" : "rooted in place";
			case "encase" -> switch (element) {
				case "ice" -> "frozen in a block of ice";
				case "stone" -> "sealed in a shell of stone";
				case "nature" -> "bound up in leaves";
				default -> "locked in a shell of hardened magic";
			};
			case "engulf" -> switch (element) {
				case "fire" -> "engulfed in flame that spreads to what is near";
				case "wind" -> "caught in a vortex";
				case "water" -> "swallowed by water and left gasping";
				case "light" -> "blinded by a flash";
				case "shadow" -> "blinded, and it loses track of you";
				case "void" -> "lifted weightless";
				case "ice" -> "frozen in a block of ice";
				case "nature" -> "poisoned and rooted";
				default -> "weakened";
			};
			case "vortex" -> "caught in a vortex";
			case "burst" -> "the heart of a burst that hits everything near";
			case "smite" -> element.equals("storm") ? "struck again by lightning from above" : "struck again from above";
			case "siphon" -> "drained, healing you";
			case "displace" -> "flung a few blocks away through space";
			case "linger" -> "left hurting and slowed for a while";
			case "pull" -> "dragged toward you";
			case "slam" -> "dragged together with the foes around it and slammed into them (alone, it is lifted and slammed into the ground)";
			case "rend" -> "cut deep, bleeding for a while";
			case "crumble" -> switch (element) {
				case "stone" -> "swallowed by the ground crumbling to sand beneath it";
				case "fire" -> "burned toward ash, and weakened";
				case "light" -> "seared by the glare of its unmaking, blinded and weakened";
				default -> "crumbling, blinded by its own dust and weakened";
			};
			default -> "struck";
		};
	}

	/** After an attack spell hits: its command's form for certain, and now and then its element's. */
	public static void apply(Cast cast, LivingEntity target, double base) {
		if (!target.isAlive()) return;
		String element = element(cast.spell);
		String form = commandForm(cast.spell);
		if ("engulf".equals(form) && element.equals("wind")) form = "vortex";
		if ("engulf".equals(form) && element.equals("ice")) form = "encase";
		if (form != null) manifest(cast, target, form, element, base);
		String sig = signature(element);
		float chance = element.equals("ice") ? 0.5f : 0.3f;
		if (!sig.equals(form) && target.isAlive() && cast.level.getRandom().nextFloat() < chance) manifest(cast, target, sig, element, base);
	}

	private static boolean steadfast(LivingEntity target) {
		return MobControl.immune(target) || com.omnispell.boss.Bosses.defOf(target) != null || com.omnispell.familiar.Familiars.isFamiliar(target);
	}

	public static void manifest(Cast cast, LivingEntity target, String form, String element, double base) {
		ServerLevel level = cast.level;
		ServerPlayer player = cast.player;
		int tier = Math.max(1, Math.min(5, cast.spell.tier));
		int color = Fx.color(cast.spell.visual.color1);
		Vec3 at = target.position();
		switch (form) {
			case "hurl" -> {
				if (steadfast(target)) return;
				target.setDeltaMovement(target.getDeltaMovement().x * 0.3, 0.75 + 0.12 * tier, target.getDeltaMovement().z * 0.3);
				target.syncVelocity = true;
				target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, false, false));
				BlockState under = level.getBlockState(target.blockPosition().below());
				if (!under.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), at.x, at.y + 0.1, at.z, 30, 0.5, 0.1, 0.5, 0.2);
				level.playSound(null, target.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.8f, 0.7f);
			}
			case "blast" -> {
				Vec3 away = at.subtract(player.position()).multiply(1, 0, 1);
				if (away.lengthSqr() < 0.0001) away = player.getLookAngle().multiply(1, 0, 1);
				double strength = (steadfast(target) ? 0.6 : 1.4) + 0.2 * tier;
				away = away.normalize().scale(strength);
				target.push(away.x, 0.35, away.z);
				target.syncVelocity = true;
				level.sendParticles(element.equals("water") ? ParticleTypes.SPLASH : ParticleTypes.GUST, at.x, at.y + 1, at.z, element.equals("water") ? 40 : 3, 0.5, 0.5, 0.5, 0.1);
			}
			case "root" -> {
				int ticks = 30 + 12 * tier;
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, steadfast(target) ? ticks / 2 : ticks, steadfast(target) ? 1 : 6), player);
				if (element.equals("nature")) target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), player);
				for (int i = 0; i < ticks; i += 10) {
					Tasks.later(i, () -> {
						if (target.isAlive()) level.sendParticles(element.equals("nature") ? ParticleTypes.SPORE_BLOSSOM_AIR : ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY() + 0.2, target.getZ(), 6, 0.35, 0.1, 0.35, 0.0);
					});
				}
			}
			case "encase" -> encase(cast, target, element, tier);
			case "engulf" -> {
				switch (element) {
					case "fire" -> {
						CombatEffects.burn(cast, target, 4 + tier);
						for (LivingEntity near : Targeting.around(player, at, 2.5, false)) if (near != target) CombatEffects.burn(cast, near, 2 + tier / 2);
						level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 1, at.z, 40, 0.5, 0.8, 0.5, 0.05);
					}
					case "water" -> {
						target.setAirSupply(Math.max(0, target.getAirSupply() - 120));
						target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), player);
						level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y + 1, at.z, 40, 0.4, 0.6, 0.4, 0.05);
					}
					case "light" -> {
						target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40 + 10 * tier, 0), player);
						target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0), player);
						if (target instanceof Mob mob && !steadfast(target)) mob.setTarget(null);
						level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 30, 0.4, 0.6, 0.4, 0.15);
					}
					case "shadow" -> {
						target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60 + 10 * tier, 0), player);
						target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), player);
						if (target instanceof Mob mob && !steadfast(target)) mob.setTarget(null);
						level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1, at.z, 30, 0.4, 0.6, 0.4, 0.02);
					}
					case "void" -> {
						if (!steadfast(target)) target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20 + 6 * tier, 0), player);
						level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 30, 0.4, 0.6, 0.4, 0.05);
					}
					case "nature" -> {
						target.addEffect(new MobEffectInstance(MobEffects.POISON, 60 + 20 * tier, tier >= 4 ? 1 : 0), player);
						manifest(cast, target, "root", element, base);
					}
					default -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), player);
				}
			}
			case "vortex" -> Terraform.vortex(level, player, target, tier);
			case "burst" -> {
				double radius = 2.5 + 0.5 * tier;
				for (LivingEntity other : Targeting.around(player, at, radius, false)) {
					if (other == target || com.omnispell.familiar.Familiars.isFamiliar(other)) continue;
					CombatEffects.strike(cast, other, base * 0.4, false);
				}
				Fx.ring(level, at.add(0, 0.5, 0), radius, color, null);
			}
			case "smite" -> Tasks.later(10, () -> {
				if (!target.isAlive()) return;
				if (element.equals("storm")) {
					var bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
					if (bolt != null) {
						bolt.snapTo(target.getX(), target.getY(), target.getZ());
						bolt.setVisualOnly(true);
						level.addFreshEntity(bolt);
					}
				} else {
					Vec3 top = target.position().add(0, 6, 0);
					Fx.line(level, top, target.getBoundingBox().getCenter(), color, null, 3);
				}
				target.damageCooldownTime = 0;
				CombatEffects.strike(cast, target, base * 0.5, false);
			});
			case "siphon" -> {
				float heal = (float) Math.min(6.0, base * cast.scale * 0.25);
				player.heal(heal);
				Fx.line(level, target.getBoundingBox().getCenter(), player.getBoundingBox().getCenter(), element.equals("blood") ? 0xB0102A : color, null, 2);
			}
			case "displace" -> {
				if (steadfast(target)) return;
				Vec3 from = target.position();
				for (int i = 0; i < 12; i++) {
					double angle = level.getRandom().nextDouble() * Math.PI * 2;
					double dist = 3 + tier + level.getRandom().nextDouble() * 2;
					if (target.randomTeleport(from.x + Math.cos(angle) * dist, from.y + 2, from.z + Math.sin(angle) * dist, true, state -> true)) break;
				}
				com.omnispell.boss.Lairs.keepOut(target, from);
				if (target instanceof Mob mob) mob.setTarget(null);
				level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 40, 0.4, 0.6, 0.4, 0.5);
			}
			case "linger" -> {
				MobControl.dot(player, target, (float) Math.max(1.0, base * cast.scale * 0.15) * CombatEffects.versusPlayers(cast, target), 3, element);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 0), player);
			}
			case "slam" -> slam(cast, target, base, tier, color);
			case "pull" -> {
				if (steadfast(target)) return;
				Vec3 toward = player.position().subtract(at);
				double dist = toward.length();
				if (dist < 2.5) return;
				Vec3 v = toward.normalize().scale(Math.min(2.0, 0.4 + dist * 0.12));
				target.setDeltaMovement(v.x, 0.3, v.z);
				target.syncVelocity = true;
			}
			case "crumble" -> {
				BlockState ground = level.getBlockState(target.blockPosition().below());
				if (element.equals("stone") && !steadfast(target) && target.onGround()) {
					// Earth comes apart under it: it sinks into sand for a moment, held fast.
					Rifts.sink(cast, target, 30 + 10 * tier, "stone", Blocks.SAND.defaultBlockState());
				} else if (element.equals("fire")) {
					CombatEffects.burn(cast, target, 3 + tier);
					target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80 + 20 * tier, 1), player);
					level.sendParticles(ParticleTypes.ASH, at.x, at.y + 1, at.z, 40, 0.4, 0.6, 0.4, 0.02);
				} else {
					target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40 + 10 * tier, 0), player);
					target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80 + 20 * tier, 0), player);
					MobControl.dot(player, target, (float) Math.max(1.0, base * cast.scale * 0.1) * CombatEffects.versusPlayers(cast, target), 3, element);
				}
				if (element.equals("light")) {
					// Light does not leave dust: it unmakes with glare.
					level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.2, at.z, 24, 0.4, 0.6, 0.4, 0.05);
				} else {
					BlockState dust = ground.isAir() ? Blocks.SAND.defaultBlockState() : ground;
					level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, dust), at.x, at.y + 1.2, at.z, 30, 0.5, 0.6, 0.5, 0.0);
				}
			}
			case "rend" -> {
				MobControl.dot(player, target, (float) Math.max(1.0, base * cast.scale * 0.12) * CombatEffects.versusPlayers(cast, target), 3, "blood");
				level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y + 1, at.z, 6, 0.3, 0.4, 0.3, 0.1);
			}
			default -> {
			}
		}
	}

	/**
	 * Slam: the foes around what it hits are hauled in toward it and smashed together; each takes a share of the
	 * spell, the target once for every foe that strikes it. With no other foe near, the target is lifted and
	 * slammed into the ground. Bosses and the like are not moved, but still feel the impact.
	 */
	static void slam(Cast cast, LivingEntity target, double base, int tier, int color) {
		ServerLevel level = cast.level;
		ServerPlayer player = cast.player;
		java.util.List<LivingEntity> drawn = new java.util.ArrayList<>();
		for (LivingEntity e : Targeting.around(player, target.position(), 5 + tier * 0.5, false)) {
			if (e == target || e == player || !e.isAlive() || Defenses.ally(player, e) || steadfast(e)) continue;
			drawn.add(e);
			if (drawn.size() >= 4) break;
		}
		Vec3 heart = target.position();
		if (drawn.isEmpty()) {
			if (steadfast(target)) return;
			target.setDeltaMovement(0, 0.9, 0);
			target.syncVelocity = true;
			Tasks.later(8, () -> {
				if (!target.isAlive()) return;
				target.setDeltaMovement(0, -2.2, 0);
				target.syncVelocity = true;
			});
			Tasks.later(13, () -> {
				if (!target.isAlive()) return;
				CombatEffects.strike(cast, target, base * 0.45, false);
				level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(), target.getZ(), 1, 0, 0, 0, 0);
				BlockState under = level.getBlockState(target.blockPosition().below());
				if (!under.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), target.getX(), target.getY() + 0.1, target.getZ(), 30, 0.6, 0.1, 0.6, 0.2);
				level.playSound(null, target.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0f, 0.9f);
			});
			return;
		}
		for (LivingEntity e : drawn) {
			Vec3 in = heart.subtract(e.position());
			double dist = in.length();
			Vec3 v = in.normalize().scale(Math.min(2.2, 0.5 + dist * 0.28));
			e.setDeltaMovement(v.x, 0.25, v.z);
			e.syncVelocity = true;
			Fx.line(level, e.getBoundingBox().getCenter(), target.getBoundingBox().getCenter(), color, null, 2);
		}
		Tasks.later(7, () -> {
			for (LivingEntity e : drawn) {
				if (!e.isAlive()) continue;
				CombatEffects.strike(cast, e, base * 0.4, false);
				if (target.isAlive()) CombatEffects.strike(cast, target, base * 0.2, false);
				Vec3 mid = e.position().add(target.position()).scale(0.5);
				level.sendParticles(ParticleTypes.CRIT, mid.x, mid.y + 1, mid.z, 14, 0.4, 0.4, 0.4, 0.3);
				level.sendParticles(ParticleTypes.EXPLOSION, mid.x, mid.y + 1, mid.z, 1, 0, 0, 0, 0);
			}
			level.playSound(null, target.blockPosition(), SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.0f, 0.8f);
		});
	}

	/** The shell: temporary blocks in the empty spaces around the target, gone again a few seconds later. */
	static int encase(Cast cast, LivingEntity target, String element, int tier) {
		if (steadfast(target) || target.getBbWidth() > 1.6 || target.getBbHeight() > 3.2) return 0;
		ServerLevel level = cast.level;
		BlockState shell = switch (element) {
			case "ice", "water" -> Blocks.ICE.defaultBlockState();
			case "stone" -> {
				Block material = Terraform.material(cast.spell);
				yield (material == null ? Blocks.COBBLESTONE : material).defaultBlockState();
			}
			case "nature" -> Blocks.AZALEA_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
			case "fire" -> Blocks.MAGMA_BLOCK.defaultBlockState();
			case "light" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState();
			case "shadow" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState();
			case "void" -> Blocks.TINTED_GLASS.defaultBlockState();
			case "blood" -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState();
			default -> Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.PURPLE).defaultBlockState();
		};
		AABB box = target.getBoundingBox();
		BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
		BlockPos max = BlockPos.containing(box.maxX - 1e-4, box.maxY - 1e-4, box.maxZ - 1e-4);
		java.util.List<BlockPos> placed = new java.util.ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(min.offset(-1, 0, -1), max.offset(1, 1, 1))) {
			boolean inside = pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ() && pos.getY() <= max.getY();
			if (inside) continue;
			BlockState now = level.getBlockState(pos);
			if (!now.canBeReplaced() || !now.getFluidState().isEmpty()) continue;
			if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> e != target).isEmpty()) continue; // never wall in a bystander
			level.setBlock(pos, shell, 3);
			placed.add(pos.immutable());
		}
		if (placed.isEmpty()) return 0;
		target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
		target.syncVelocity = true;
		target.teleportTo(target.getX(), target.getY(), target.getZ());
		int ticks = 50 + 10 * tier;
		if (element.equals("ice")) target.setTicksFrozen(Math.max(target.getTicksFrozen(), 140 + ticks));
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 6, false, false));
		level.playSound(null, target.blockPosition(), element.equals("ice") ? SoundEvents.GLASS_PLACE : SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
		Tasks.later(ticks, () -> {
			for (BlockPos pos : placed) {
				if (level.getBlockState(pos).is(shell.getBlock())) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, shell), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
				}
			}
			level.playSound(null, placed.get(0), element.equals("ice") ? SoundEvents.GLASS_BREAK : SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.8f, 1.0f);
		});
		return placed.size();
	}
}
