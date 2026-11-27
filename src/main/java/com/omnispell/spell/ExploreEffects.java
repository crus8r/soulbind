package com.omnispell.spell;

import com.omnispell.data.WordBank;
import com.omnispell.player.Mana;
import com.omnispell.player.ManaPool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The exploration toggles: bridges, teleports, invisibility and the rest of the
 * utility magic. Which words give which toggle, and each spell's description,
 * come from Omnispell's exploration spell table; this class is what each toggle
 * does in a Minecraft world.
 */
public final class ExploreEffects {
	private static final net.minecraft.resources.Identifier ANCHOR_ID = com.omnispell.Omnispell.id("anchor");

	private ExploreEffects() {
	}

	/** Seconds before the same spell can be cast again, by toggle. */
	public static double cooldownSeconds(String toggle) {
		if (Terraform.handles(toggle)) return Terraform.cooldownSeconds(toggle);
		if (Rifts.handles(toggle)) return Rifts.cooldownSeconds(toggle);
		if (Mines.handles(toggle)) return Mines.cooldownSeconds(toggle);
		if (Wonders.handles(toggle)) return Wonders.cooldownSeconds(toggle);
		return switch (toggle) {
			case "projectile", "push", "shrapnel" -> 3;
			case "beam", "area_damage", "eruption", "dash", "leap", "deflect", "disintegrate" -> 5;
			case "detonate", "grapple", "tether", "bridge", "barrier_wall", "transmute", "current", "track" -> 8;
			case "teleport", "phase", "stun", "restraint", "entangle", "trap", "reveal", "smoke_screen", "blackout" -> 12;
			case "charm", "decoy", "mirage", "phantom", "scout", "heal", "force_field", "polymorph" -> 20;
			case "invisibility", "fade", "camouflage", "silent_step", "armor", "anchor", "ward_element", "true_sight", "construct_weapon" -> 30;
			case "golem", "guardian", "mount" -> 60;
			default -> 10;
		};
	}

	/** A line out of the wand: a lightning bolt for a lightning spell, otherwise the usual line of the spell's colour. */
	private static void ray(boolean storm, ServerLevel level, Vec3 from, Vec3 to, int color, ParticleOptions extra, int intensity) {
		if (storm) Beams.bolt(level, from, to, color);
		else Fx.line(level, from, to, color, extra, intensity);
	}

	public static boolean cast(Cast c) {
		Spell spell = c.spell;
		ServerPlayer p = c.player;
		ServerLevel level = c.level;
		String element = spell.element == null ? "stone" : spell.element;
		WordBank.Flavor flavor = WordBank.flavor(element);
		// Lightning and thunder explore like the wind, but they are lightning: they look it, and leave the wand as bolts.
		boolean storm = Beams.stormWords(spell);
		int color = storm ? Fx.color("storm") : Fx.color(flavor == null ? "arcane" : flavor.color);
		ParticleOptions extra = storm ? ParticleTypes.ELECTRIC_SPARK : Fx.themed(flavor == null ? "sparks" : flavor.particle);
		String fxElement = storm ? "storm" : flavor == null ? "arcane" : flavor.element;
		int tier = spell.tier;
		double power = spell.hasCharge("Maxima") ? 1.5 : spell.hasCharge("Minima") ? 0.7 : 1.0;
		double lasting = spell.hasCharge("Duratus") ? 1.6 : 1.0;
		Vec3 hand = Targeting.hand(p);
		Vec3 look = p.getLookAngle();

		if (spell.fizzle) {
			level.sendParticles(ParticleTypes.SMOKE, hand.x, hand.y, hand.z, 14, 0.2, 0.2, 0.2, 0.02);
			level.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.3f);
			c.tell("The spell fizzles. These words refuse each other.");
			c.cooldownFactor = 0.3;
			return true;
		}

		// Spells saved before the pulling and sinking effects existed take them up too.
		String rift = Rifts.forEntry(Rifts.entryId(spell));
		if (rift != null && !Rifts.handles(spell.toggle) && !Terraform.handles(spell.toggle)) spell.toggle = rift;
		if (Rifts.handles(spell.toggle)) return Rifts.cast(c, spell.toggle);
		if (Mines.handles(spell.toggle)) return Mines.cast(c, spell.toggle);
		String wonder = Wonders.primary(Rifts.entryId(spell));
		if (wonder != null && !Wonders.handles(spell.toggle)) spell.toggle = wonder;
		if (Wonders.handles(spell.toggle)) return Wonders.cast(c, spell.toggle);
		// Spells saved before the land-shaping effects existed are read the new way too.
		if (Terraform.handles(spell.toggle)) return Terraform.cast(c, spell.toggle);

		boolean ok = switch (spell.toggle == null ? "" : spell.toggle) {
			// ----- destruction -----
			case "projectile" -> {
				LivingEntity target = Targeting.entity(p, c.range);
				Vec3 end = target != null ? target.getBoundingBox().getCenter() : Targeting.point(p, c.range);
				ray(storm, level, hand, end, color, extra, 3);
				Fx.burst(level, end, color, extra, 2);
				if (target != null) hurt(c, target, (6 + 2 * tier) * power, fxElement);
				yield true;
			}
			case "beam" -> {
				Vec3 end = Targeting.block(p, c.range).getLocation();
				ray(storm, level, hand, end, color, extra, 4);
				Fx.line(level, hand.add(0, 0.15, 0), end.add(0, 0.15, 0), color, null, 2);
				for (LivingEntity e : Targeting.along(p, c.range, 0.7)) hurt(c, e, (4 + 2 * tier) * power, fxElement);
				yield true;
			}
			case "area_damage" -> {
				Vec3 at = Targeting.point(p, c.range);
				double radius = 4.5 * Math.sqrt(power);
				ray(storm, level, hand, at, color, null, 2);
				Fx.ring(level, at, radius, color, extra);
				Fx.burst(level, at.add(0, 0.8, 0), color, extra, 4);
				for (LivingEntity e : Targeting.around(p, at, radius, false)) hurt(c, e, (5 + 2 * tier) * power, fxElement);
				yield true;
			}
			case "shrapnel" -> {
				for (int i = 0; i < 7; i++) {
					Vec3 dir = look.add((p.getRandom().nextDouble() - 0.5) * 0.7, (p.getRandom().nextDouble() - 0.5) * 0.4, (p.getRandom().nextDouble() - 0.5) * 0.7).normalize();
					ray(storm, level, hand, hand.add(dir.scale(8)), color, i % 2 == 0 ? extra : null, 1);
				}
				for (LivingEntity e : Targeting.cone(p, 8, 35)) hurt(c, e, (4 + tier) * power, fxElement);
				yield true;
			}
			case "eruption" -> {
				Vec3 at = Targeting.point(p, c.range);
				double radius = 3.5 * Math.sqrt(power);
				Fx.ring(level, at, radius, color, extra);
				Fx.helix(level, at, 3.0, radius * 0.5, color, extra);
				for (LivingEntity e : Targeting.around(p, at, radius, false)) {
					hurt(c, e, (5 + 2 * tier) * power, fxElement);
					e.push(0, 0.7, 0);
					e.syncVelocity = true;
				}
				yield true;
			}
			case "detonate" -> {
				Vec3 at = Targeting.point(p, c.range);
				double radius = 5 * Math.sqrt(power);
				Fx.ring(level, at, 1.0, color, extra);
				Tasks.repeat(10, 4, () -> Fx.ring(level, at, radius, color, null));
				Tasks.later(40, () -> {
					Fx.burst(level, at.add(0, 1, 0), color, extra, 5);
					level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 1, at.z, 3, 1.0, 0.5, 1.0, 0.0);
					level.playSound(null, BlockPos.containing(at), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
					for (LivingEntity e : Targeting.around(p, at, radius, false)) hurt(c, e, (8 + 2 * tier) * power, fxElement);
				});
				c.tell("The charge is set...");
				yield true;
			}
			case "disintegrate" -> {
				BlockHitResult hit = Targeting.block(p, 6);
				if (hit.getType() == HitResult.Type.MISS) yield c.fail("There is nothing solid within reach.");
				int depth = (int) Math.round((2 + tier) * power);
				BlockPos pos = hit.getBlockPos();
				Direction into = hit.getDirection().getOpposite();
				int broken = 0;
				for (int i = 0; i < depth; i++) {
					BlockState state = level.getBlockState(pos);
					float hardness = state.getDestroySpeed(level, pos);
					if (state.isAir() || hardness < 0 || hardness > 50) break;
					level.destroyBlock(pos, true, p);
					Fx.burst(level, Vec3.atCenterOf(pos), color, extra, 1);
					broken++;
					pos = pos.relative(into);
				}
				yield broken > 0 || c.fail("The matter here will not come apart.");
			}

			// ----- protection -----
			case "force_field" -> {
				double paid = Mana.spend(p, ManaPool.BARRIER, (15 + 5 * tier) * power);
				if (paid < 1) yield c.fail("Your Barrier mana is spent.");
				CombatEffects.giveShield(c, (float) (paid * c.scale * c.mendingBonus()), (int) (400 * lasting));
				for (LivingEntity e : Targeting.around(p, p.position(), 3.5, false)) shove(e, e.position().subtract(p.position()), 0.9);
				Fx.ring(level, p.position(), 2.0, color, extra);
				Fx.helix(level, p.position(), 2.0, 1.0, color, null);
				yield true;
			}
			case "barrier_wall" -> {
				Block block = TempBlocks.material(element);
				Direction facing = p.getDirection();
				Direction side = facing.getClockWise();
				BlockPos center = p.blockPosition().relative(facing, 3);
				int half = power > 1 ? 3 : 2;
				int placed = 0;
				for (int w = -half; w <= half; w++) {
					for (int h = 0; h < 3; h++) {
						if (TempBlocks.place(level, center.relative(side, w).above(h), block, (int) (300 * lasting))) placed++;
					}
				}
				Fx.burst(level, Vec3.atCenterOf(center.above()), color, extra, 3);
				yield placed > 0 || c.fail("There is no room for a wall here.");
			}
			case "armor" -> {
				p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, (int) (600 * lasting), tier >= 4 || power > 1 ? 1 : 0));
				Fx.helix(level, p.position(), 2.0, 0.7, color, extra);
				yield true;
			}
			case "deflect" -> {
				double radius = 6 * Math.sqrt(power);
				Fx.ring(level, p.position(), radius * 0.5, color, extra);
				Fx.ring(level, p.position(), radius, color, null);
				for (LivingEntity e : Targeting.around(p, p.position(), radius, false)) shove(e, e.position().subtract(p.position()), 1.3);
				for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(radius), x -> x instanceof Projectile)) {
					e.setDeltaMovement(e.getDeltaMovement().scale(-1.0));
					e.syncVelocity = true;
				}
				yield true;
			}
			case "heal" -> {
				double paid = Mana.spend(p, ManaPool.RESTORATION, (15 + 5 * tier) * power);
				if (paid < 1) yield c.fail("Your Restoration mana is spent.");
				p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, (int) (paid * 8 * lasting), 1));
				Fx.helix(level, p.position(), 2.0, 0.6, color, extra);
				level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.6, p.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
				yield true;
			}
			case "anchor" -> {
				int ticks = (int) (400 * lasting);
				AttributeInstance kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
				if (kb != null) {
					kb.addOrUpdateTransientModifier(new AttributeModifier(ANCHOR_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
					Tasks.later(ticks, () -> {
						AttributeInstance again = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
						if (again != null) again.removeModifier(ANCHOR_ID);
					});
				}
				p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks, 0));
				p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 1));
				Fx.ring(level, p.position(), 1.2, color, extra);
				yield true;
			}
			case "ward_element" -> {
				int ticks = (int) (600 * lasting);
				String warded = switch (element) {
					case "fire" -> "fire";
					case "ice", "water" -> "ice";
					case "shadow", "void", "blood" -> "shadow";
					case "light" -> "light";
					case "wind" -> "nature";
					default -> "arcane";
				};
				Modifiers.add(p, warded + "_resistance", 60 * Math.min(1.5, power), ticks);
				if (warded.equals("fire")) p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, ticks, 0));
				Fx.helix(level, p.position(), 2.0, 0.8, color, extra);
				c.tell("Warded against " + warded + ".");
				yield true;
			}
			case "construct_weapon" -> {
				p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, (int) (600 * lasting), tier >= 3 || power > 1 ? 1 : 0));
				ray(storm, level, hand, hand.add(look.scale(1.5)), color, extra, 3);
				c.tell("A weapon of " + (flavor == null ? "magic" : flavor.material) + " forms around your hand.");
				yield true;
			}

			// ----- concealment -----
			case "invisibility", "fade", "camouflage", "silent_step" -> {
				if (Mana.spend(p, ManaPool.PHASE, 1) < 1) yield c.fail("Your Phase mana is spent.");
				int seconds = switch (spell.toggle) {
					case "invisibility" -> 30;
					case "camouflage" -> 45;
					case "fade" -> 20;
					default -> 15;
				};
				int ticks = (int) (seconds * 20 * lasting * Math.min(power, 1.3));
				p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false));
				if (spell.toggle.equals("camouflage")) p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 0, false, false));
				if (spell.toggle.equals("silent_step")) p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 0, false, false));
				// A strong invisibility (Maxima, or a tier 4 base word or better) hides armour and what you carry as well.
				if ((spell.toggle.equals("invisibility") || spell.toggle.equals("fade")) && (power > 1 || tier >= 4)) {
					Shroud.apply(p, ticks);
					c.tell("You vanish entirely, armour and all.");
				}
				forget(p, 24);
				Fx.helix(level, p.position(), 2.0, 0.6, color, extra);
				yield true;
			}
			case "smoke_screen", "blackout" -> {
				boolean blackout = spell.toggle.equals("blackout");
				Vec3 at = blackout ? p.position() : Targeting.point(p, c.range);
				double radius = (blackout ? 10 : 6) * Math.sqrt(power);
				int ticks = (int) ((blackout ? 200 : 160) * lasting);
				if (!blackout) ray(storm, level, hand, at, color, extra, 2);
				ParticleOptions smoke = blackout ? ParticleTypes.SQUID_INK : ParticleTypes.CAMPFIRE_COSY_SMOKE;
				Tasks.repeat(10, ticks / 10, () -> level.sendParticles(smoke, at.x, at.y + 1.2, at.z, 14, radius * 0.4, 0.8, radius * 0.4, 0.005));
				for (LivingEntity e : Targeting.around(p, at, radius, false)) {
					if (e instanceof ServerPlayer other) other.addEffect(new MobEffectInstance(blackout ? MobEffects.DARKNESS : MobEffects.BLINDNESS, ticks, 0), p);
					else MobControl.apply(p, e, "distracted", ticks, null);
				}
				yield true;
			}
			case "decoy", "mirage", "phantom" -> {
				Vec3 at = Targeting.point(p, Math.min(c.range, 16));
				ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.MOB_SUMMONED);
				if (stand == null) yield c.fail("The illusion will not hold here.");
				stand.snapTo(at.x, at.y, at.z, p.getYRot(), 0);
				stand.setCustomName(p.getName());
				stand.setCustomNameVisible(true);
				stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.PLAYER_HEAD));
				stand.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, false));
				int ticks = (int) (300 * lasting);
				Summons.track(stand, ticks);
				level.addFreshEntity(stand);
				for (LivingEntity e : Targeting.around(p, at, 16 * Math.sqrt(power), false)) {
					if (e instanceof Mob mob && mob.getTarget() == p) MobControl.apply(p, e, "decoy", ticks, stand);
					else if (e instanceof Monster) MobControl.apply(p, e, "decoy", ticks, stand);
				}
				Fx.burst(level, at.add(0, 1, 0), color, extra, 3);
				yield true;
			}

			// ----- traversal -----
			case "teleport" -> {
				if (Mana.spend(p, ManaPool.PHASE, 1) < 1) yield c.fail("Your Phase mana is spent.");
				BlockHitResult hit = Targeting.block(p, 32 * power);
				Vec3 to;
				if (hit.getType() == HitResult.Type.MISS) to = hit.getLocation();
				else to = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
				BlockPos feet = BlockPos.containing(to);
				for (int i = 0; i < 4 && !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty(); i++) feet = feet.above();
				Fx.helix(level, p.position(), 2.0, 0.6, color, extra);
				p.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
				p.fallDistance = 0;
				Fx.helix(level, p.position(), 2.0, 0.6, color, extra);
				yield true;
			}
			case "phase" -> {
				BlockHitResult hit = Targeting.block(p, 5);
				// Nothing to pass through: the same freedom takes you upward instead.
				if (hit.getType() == HitResult.Type.MISS) yield Terraform.cast(c, "launch");
				if (Mana.spend(p, ManaPool.PHASE, 1) < 1) yield c.fail("Your Phase mana is spent.");
				Vec3 flat = new Vec3(look.x, 0, look.z);
				if (flat.lengthSqr() < 0.01) flat = new Vec3(0, look.y > 0 ? 1 : -1, 0);
				Vec3 dir = flat.lengthSqr() > 0.9 ? flat.normalize() : look;
				int reach = (int) Math.round(8 * power);
				BlockPos found = null;
				boolean inside = false;
				for (int i = 1; i <= reach + 5; i++) {
					BlockPos pos = BlockPos.containing(p.position().add(dir.scale(i)).add(0, 0.2, 0));
					boolean open = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
					if (!open) inside = true;
					else if (inside) {
						found = pos;
						break;
					}
				}
				if (found == null) yield c.fail("The wall is too thick to pass through.");
				Fx.burst(level, p.position().add(0, 1, 0), color, extra, 3);
				p.teleportTo(found.getX() + 0.5, found.getY(), found.getZ() + 0.5);
				Fx.burst(level, p.position().add(0, 1, 0), color, extra, 3);
				yield true;
			}
			case "dash" -> {
				Vec3 dir = new Vec3(look.x, Math.max(look.y, 0) * 0.4 + 0.15, look.z).normalize();
				p.setDeltaMovement(dir.scale(1.9 * Math.sqrt(power)));
				p.syncVelocity = true;
				p.fallDistance = 0;
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false));
				Fx.line(level, p.position().add(0, 1, 0), p.position().add(0, 1, 0).add(dir.scale(5)), color, extra, 2);
				yield true;
			}
			case "leap" -> {
				p.setDeltaMovement(look.x * 0.9, 1.15 * Math.sqrt(power), look.z * 0.9);
				p.syncVelocity = true;
				p.fallDistance = 0;
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, false, false));
				Fx.ring(level, p.position(), 1.2, color, extra);
				yield true;
			}
			case "bridge" -> {
				// Common stuff (dirt, sand, stone, wood...) makes a real, lasting bridge. Strange magic makes one that fades.
				Block real = Terraform.material(spell);
				Block block = real != null ? real : TempBlocks.material(element);
				Vec3 flat = new Vec3(look.x, 0, look.z);
				if (flat.lengthSqr() < 0.001) yield c.fail("Look where the bridge should go.");
				flat = flat.normalize();
				int length = (int) Math.round(14 * power);
				int ticks = (int) (400 * lasting);
				int y = p.blockPosition().getY() - 1;
				Vec3 sideways = new Vec3(-flat.z, 0, flat.x);
				int placed = 0;
				for (int i = 1; i <= length; i++) {
					Vec3 at = p.position().add(flat.scale(i));
					for (double s = -0.5; s <= 0.5; s += 1.0) {
						Vec3 q = at.add(sideways.scale(s));
						BlockPos spot = new BlockPos((int) Math.floor(q.x), y, (int) Math.floor(q.z));
						if (real == null) {
							if (TempBlocks.place(level, spot, block, ticks)) placed++;
						} else {
							BlockState there = level.getBlockState(spot);
							if (!there.isAir() && !there.canBeReplaced()) continue;
							if (TempBlocks.melts(block) && TempBlocks.scorching(level, spot)) TempBlocks.placeMelting(level, spot, block, tier);
							else level.setBlockAndUpdate(spot, block.defaultBlockState());
							placed++;
						}
					}
				}
				Fx.line(level, p.position(), p.position().add(flat.scale(length)), color, extra, 2);
				yield placed > 0 || c.fail("The ground ahead is already solid.");
			}
			case "grapple", "tether" -> {
				LivingEntity target = spell.toggle.equals("tether") ? Targeting.entity(p, c.range) : null;
				if (target != null) {
					Vec3 pull = p.position().subtract(target.position());
					shove(target, pull, Math.min(2.2, 0.4 + pull.length() * 0.12));
					ray(storm, level, hand, target.getBoundingBox().getCenter(), color, extra, 2);
					yield true;
				}
				BlockHitResult hit = Targeting.block(p, 30 * power);
				if (hit.getType() == HitResult.Type.MISS) yield c.fail("There is nothing to anchor to.");
				Vec3 delta = hit.getLocation().subtract(p.position());
				double distance = delta.length();
				Vec3 velocity = delta.normalize().scale(Math.min(2.6, 0.6 + distance * 0.11)).add(0, 0.25, 0);
				p.setDeltaMovement(velocity);
				p.syncVelocity = true;
				p.fallDistance = 0;
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
				ray(storm, level, hand, hit.getLocation(), color, extra, 2);
				yield true;
			}
			case "current" -> {
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
				int pushes = (int) (15 * lasting);
				Tasks.repeat(4, pushes, () -> {
					if (!p.isAlive() || p.isRemoved()) return;
					Vec3 dir = p.getLookAngle();
					p.setDeltaMovement(dir.scale(0.85 * Math.sqrt(power)));
					p.syncVelocity = true;
					p.fallDistance = 0;
					level.sendParticles(extra, p.getX(), p.getY() + 0.3, p.getZ(), 4, 0.3, 0.2, 0.3, 0.02);
				});
				yield true;
			}
			case "mount" -> {
				Horse horse = EntityTypes.HORSE.create(level, EntitySpawnReason.MOB_SUMMONED);
				if (horse == null) yield c.fail("Nothing answers the call here.");
				horse.snapTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
				horse.tameWithName(p);
				horse.setItemSlot(net.minecraft.world.entity.EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
				horse.setDropChance(net.minecraft.world.entity.EquipmentSlot.SADDLE, 0);
				horse.setCustomName(Component.literal((flavor == null ? "Conjured" : capitalize(flavor.adj)) + " Steed"));
				AttributeInstance speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed != null) speed.setBaseValue(0.28 + 0.02 * tier);
				Summons.track(horse, (int) (1800 * lasting * power));
				level.addFreshEntity(horse);
				p.startRiding(horse);
				Fx.helix(level, p.position(), 2.5, 1.0, color, extra);
				yield true;
			}

			// ----- discovery -----
			case "reveal", "scout", "true_sight" -> {
				double radius = (spell.toggle.equals("scout") ? 48 : 24) * power;
				int ticks = (int) (300 * lasting);
				int found = 0;
				for (LivingEntity e : Targeting.around(p, p.position(), radius, false)) {
					e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false), p);
					found++;
				}
				if (spell.toggle.equals("true_sight")) {
					p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, (int) (1200 * lasting), 0, false, false));
					Modifiers.add(p, "true_sight", 1, (int) (1200 * lasting));
				}
				Fx.ring(level, p.position(), 3, color, extra);
				Fx.ring(level, p.position(), 6, color, null);
				c.tell(found == 0 ? "Nothing living is near." : found + " living thing" + (found == 1 ? "" : "s") + " revealed.");
				yield true;
			}
			case "track" -> {
				LivingEntity nearest = null;
				double best = Double.MAX_VALUE;
				for (LivingEntity e : Targeting.around(p, p.position(), 64 * power, false)) {
					if (!(e instanceof Monster) && !(e instanceof ServerPlayer)) continue;
					double d = e.distanceToSqr(p);
					if (d < best) {
						best = d;
						nearest = e;
					}
				}
				if (nearest == null) {
					c.tell("No trail to follow.");
					yield true;
				}
				Vec3 to = nearest.position().add(0, 1, 0);
				Vec3 from = p.position().add(0, 1, 0);
				Vec3 dir = to.subtract(from).normalize();
				Fx.line(level, from, from.add(dir.scale(Math.min(12, Math.sqrt(best)))), color, extra, 2);
				nearest.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) (300 * lasting), 0, false, false), p);
				c.tell(nearest.getName().getString() + ", " + (int) Math.sqrt(best) + " blocks away.");
				yield true;
			}

			// ----- manipulation -----
			case "push" -> {
				for (LivingEntity e : Targeting.cone(p, 9, 30)) shove(e, look, 1.6 * Math.sqrt(power));
				ray(storm, level, hand, hand.add(look.scale(8)), color, extra, 3);
				yield true;
			}
			case "stun", "charm", "restraint", "entangle" -> {
				LivingEntity target = Targeting.entity(p, c.range);
				if (target == null) {
					ray(storm, level, hand, Targeting.point(p, c.range), color, extra, 2);
					c.cooldownFactor = 0.4;
					yield true;
				}
				if (MobControl.immune(target)) yield c.fail(target.getName().getString() + " resists.");
				double turns = (spell.toggle.equals("stun") ? 2 : 3) * power * lasting;
				double paid = Mana.spend(p, ManaPool.SPIRIT, turns * 10.0) / 10.0;
				if (paid < 0.5) yield c.fail("Your Control mana is spent.");
				String kind = switch (spell.toggle) {
					case "stun" -> "stun";
					case "charm" -> "charm";
					default -> "root";
				};
				MobControl.apply(p, target, kind, c.turns(paid), null);
				if (spell.toggle.equals("entangle")) MobControl.dot(p, target, (float) (1 + tier * 0.5), (int) Math.ceil(paid), fxElement);
				ray(storm, level, hand, target.getBoundingBox().getCenter(), color, extra, 2);
				Fx.helix(level, target.position(), target.getBbHeight(), 0.7, color, extra);
				yield true;
			}
			case "trap" -> {
				BlockHitResult hit = Targeting.block(p, Math.min(c.range, 16));
				if (hit.getType() == HitResult.Type.MISS) yield c.fail("A trap needs ground to rest on.");
				Vec3 at = hit.getLocation();
				double turns = 3 * power;
				int life = (int) (1200 * lasting);
				boolean[] sprung = {false};
				Fx.ring(level, at, 1.5, color, extra);
				Tasks.repeat(10, life / 10, () -> {
					if (sprung[0]) return;
					for (LivingEntity e : Targeting.around(p, at, 1.8, false)) {
						if (!(e instanceof Mob)) continue;
						sprung[0] = true;
						double paid = Mana.spend(p, ManaPool.SPIRIT, turns * 10.0) / 10.0;
						if (paid >= 0.5) MobControl.apply(p, e, "root", c.turns(paid), null);
						hurt(c, e, (4 + 2 * tier) * power, fxElement);
						Fx.ring(level, at, 1.8, color, extra);
						Fx.burst(level, at.add(0, 0.6, 0), color, extra, 3);
						break;
					}
				});
				c.tell("The trap is set.");
				yield true;
			}
			case "golem", "guardian" -> {
				IronGolem golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
				if (golem == null) yield c.fail("Nothing answers the call here.");
				Vec3 at = p.position().add(look.multiply(2, 0, 2));
				golem.snapTo(at.x, p.getY(), at.z, p.getYRot(), 0);
				golem.setPlayerCreated(true);
				golem.setCustomName(Component.literal((flavor == null ? "Conjured" : capitalize(flavor.adj)) + (spell.toggle.equals("guardian") ? " Guardian" : " Golem")));
				AttributeInstance health = golem.getAttribute(Attributes.MAX_HEALTH);
				if (health != null) health.setBaseValue(30 + tier * 10 * power);
				golem.setHealth(golem.getMaxHealth());
				AttributeInstance attack = golem.getAttribute(Attributes.ATTACK_DAMAGE);
				if (attack != null) attack.setBaseValue(5 + tier * 2 * power);
				Summons.track(golem, (int) ((400 + tier * 100) * lasting));
				level.addFreshEntity(golem);
				LivingEntity target = Targeting.entity(p, c.range);
				if (target != null && target != golem) golem.setTarget(target);
				Fx.helix(level, golem.position(), 2.8, 1.0, color, extra);
				yield true;
			}
			case "polymorph" -> {
				LivingEntity target = Targeting.entity(p, c.range);
				if (target == null) {
					c.cooldownFactor = 0.4;
					yield true;
				}
				if (!(target instanceof Mob mob) || MobControl.immune(target) || target.getMaxHealth() > 30 + tier * 10 || target.entityTags().contains(Summons.TAG)) {
					yield c.fail(target.getName().getString() + " cannot be reshaped.");
				}
				if (Mana.spend(p, ManaPool.SPIRIT, 30) < 30) yield c.fail("Your Spirit mana is spent.");
				EntityType<?> original = mob.getType();
				Vec3 where = mob.position();
				float health = mob.getHealth();
				Fx.helix(level, where, 2.0, 0.6, color, extra);
				mob.discard();
				Mob sheep = EntityTypes.SHEEP.create(level, EntitySpawnReason.MOB_SUMMONED);
				if (sheep == null) yield true;
				sheep.snapTo(where.x, where.y, where.z, 0, 0);
				int sheepTicks = (int) (300 * lasting * power);
				Summons.track(sheep, sheepTicks + 2);
				level.addFreshEntity(sheep);
				Tasks.later(sheepTicks, () -> {
					if (sheep.isRemoved()) return; // it died as a sheep
					Vec3 at = sheep.position();
					sheep.discard();
					Entity back = original.create(level, EntitySpawnReason.MOB_SUMMONED);
					if (back instanceof LivingEntity living) {
						back.snapTo(at.x, at.y, at.z, 0, 0);
						living.setHealth(Math.min(health, living.getMaxHealth()));
						level.addFreshEntity(back);
						level.sendParticles(ParticleTypes.POOF, at.x, at.y + 1, at.z, 12, 0.3, 0.4, 0.3, 0.02);
					}
				});
				yield true;
			}
			case "transmute" -> {
				BlockHitResult hit = Targeting.block(p, 8);
				if (hit.getType() == HitResult.Type.MISS) yield c.fail("There is nothing to transmute within reach.");
				int radius = power > 1 ? 2 : 1;
				int changed = 0;
				for (BlockPos pos : BlockPos.betweenClosed(hit.getBlockPos().offset(-radius, -radius, -radius), hit.getBlockPos().offset(radius, radius, radius))) {
					if (element.equals("ice") && TempBlocks.scorching(level, pos) && TempBlocks.freezeLava(level, pos, tier)) {
						changed++;
						continue;
					}
					Block result = transmuted(level.getBlockState(pos), element);
					if (result != null) {
						level.setBlockAndUpdate(pos.immutable(), result.defaultBlockState());
						changed++;
					}
				}
				Fx.burst(level, hit.getLocation(), color, extra, 3);
				yield changed > 0 || c.fail("This material does not answer to " + element + ".");
			}
			default -> c.fail("This spell has no shape in this world yet.");
		};

		if (ok && spell.siphon) {
			// Siphon spells drain a little energy back into the caster.
			for (ManaPool pool : ManaPool.values()) {
				double max = Mana.max(p, pool);
				double[] mana = com.omnispell.player.PlayerStore.get(p).mana;
				mana[pool.ordinal()] = Math.min(max, mana[pool.ordinal()] + max * 0.04);
			}
		}
		if (ok) {
			Fx.sound(level, p.position(), fxElement, tier);
			// A flourish at the wand, in the element's own particles, bigger for stronger spells.
			Fx.impact(level, hand, fxElement, Math.max(1, tier - 1));
		}
		return ok;
	}

	private static void hurt(Cast c, LivingEntity target, double amount, String element) {
		float damage = (float) (amount * c.damageBonus(element));
		Modifiers.withSpellElement(element, () ->
				target.hurtServer(c.level, c.level.damageSources().indirectMagic(c.player, c.player), damage));
		Fx.impact(c.level, target.position(), element, c.spell.tier);
		if ("fire".equals(element)) target.igniteForSeconds(3);
		if ("ice".equals(element)) target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0), c.player);
	}

	private static void shove(LivingEntity e, Vec3 direction, double strength) {
		Vec3 flat = new Vec3(direction.x, 0, direction.z);
		if (flat.lengthSqr() < 0.0001) flat = new Vec3(1, 0, 0);
		flat = flat.normalize().scale(strength);
		e.push(flat.x, 0.35, flat.z);
		e.syncVelocity = true;
	}

	/** Creatures hunting the caster lose track of them. */
	private static void forget(ServerPlayer p, double radius) {
		for (LivingEntity e : Targeting.around(p, p.position(), radius, false)) {
			if (e instanceof Mob mob && mob.getTarget() == p) mob.setTarget(null);
		}
	}

	private static String capitalize(String s) {
		return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** What a block becomes when transmuted by an element. Null = unchanged. Nothing valuable can be made this way. */
	private static Block transmuted(BlockState state, String element) {
		Block b = state.getBlock();
		switch (element) {
			case "fire":
				if (b == Blocks.ICE || b == Blocks.SNOW_BLOCK || b == Blocks.PACKED_ICE) return Blocks.WATER;
				if (b == Blocks.SAND) return Blocks.GLASS;
				if (b == Blocks.COBBLESTONE) return Blocks.STONE;
				if (b == Blocks.CLAY) return Blocks.TERRACOTTA;
				if (b == Blocks.WET_SPONGE) return Blocks.SPONGE;
				return null;
			case "ice":
			case "water":
				if (b == Blocks.WATER && state.getFluidState().isSource()) return element.equals("ice") ? Blocks.ICE : null;
				if (b == Blocks.LAVA) return state.getFluidState().isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE;
				if (b == Blocks.MAGMA_BLOCK) return Blocks.BASALT;
				if (b == Blocks.DIRT && element.equals("water")) return Blocks.MUD;
				if (b == Blocks.FIRE) return Blocks.AIR;
				return null;
			case "stone":
				if (b == Blocks.GRAVEL || b == Blocks.DIRT) return Blocks.COBBLESTONE;
				if (b == Blocks.SAND) return Blocks.SANDSTONE;
				if (b == Blocks.COBBLESTONE) return Blocks.STONE;
				return null;
			case "wind":
				if (b == Blocks.DIRT || b == Blocks.COARSE_DIRT) return Blocks.GRASS_BLOCK;
				if (b == Blocks.COBBLESTONE) return Blocks.MOSSY_COBBLESTONE;
				if (b == Blocks.STONE_BRICKS) return Blocks.MOSSY_STONE_BRICKS;
				if (b == Blocks.SANDSTONE) return Blocks.SAND;
				return null;
			case "light":
				if (b == Blocks.SOUL_SAND || b == Blocks.SOUL_SOIL) return Blocks.SAND;
				if (b == Blocks.SCULK) return Blocks.STONE;
				return null;
			case "shadow":
			case "void":
			case "blood":
				if (b == Blocks.GRASS_BLOCK) return Blocks.DIRT;
				if (b == Blocks.DIRT) return Blocks.COARSE_DIRT;
				if (b == Blocks.STONE) return Blocks.COBBLESTONE;
				return null;
			default:
				return null;
		}
	}
}
