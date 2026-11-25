package com.omnispell.spell;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Finds what a caster is looking at. */
public final class Targeting {
	private Targeting() {
	}

	public static Vec3 hand(ServerPlayer player) {
		return player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0, -0.25, 0);
	}

	public static BlockHitResult block(ServerPlayer player, double range) {
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getLookAngle().scale(range));
		return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
	}

	/** The living thing in the caster's sights, not hidden behind a wall. Null if none. */
	public static LivingEntity entity(ServerPlayer player, double range) {
		Vec3 from = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		BlockHitResult wall = block(player, range);
		double reach = wall.getType() == HitResult.Type.MISS ? range : wall.getLocation().distanceTo(from);
		Vec3 to = from.add(look.scale(reach));
		AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.5);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, from, to, box,
				e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator() && e.isPickable(), reach * reach);
		if (hit != null && hit.getEntity() instanceof LivingEntity living) return living;

		// Be forgiving: spells are not arrows. Take the closest living thing near the line of sight.
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (Entity e : player.level().getEntities(player, box, x -> x instanceof LivingEntity l && l.isAlive() && !x.isSpectator())) {
			Vec3 toEntity = e.getBoundingBox().getCenter().subtract(from);
			double along = toEntity.dot(look);
			if (along < 0.5 || along > reach) continue;
			double off = toEntity.subtract(look.scale(along)).length();
			if (off > 1.2 + along * 0.04) continue;
			if (along + off * 4 < bestScore) {
				bestScore = along + off * 4;
				best = (LivingEntity) e;
			}
		}
		return best;
	}

	/** Where a spell lands: on the targeted entity, the block in sight, or the end of its reach. */
	public static Vec3 point(ServerPlayer player, double range) {
		LivingEntity target = entity(player, range);
		if (target != null) return target.getBoundingBox().getCenter();
		BlockHitResult hit = block(player, range);
		return hit.getLocation();
	}

	public static List<LivingEntity> around(ServerPlayer caster, Vec3 center, double radius, boolean includeCaster) {
		List<LivingEntity> list = new ArrayList<>();
		AABB box = new AABB(center, center).inflate(radius);
		for (Entity e : caster.level().getEntities(includeCaster ? null : caster, box, x -> x instanceof LivingEntity l && l.isAlive() && !x.isSpectator())) {
			if (e.getBoundingBox().getCenter().distanceTo(center) <= radius + 0.5) list.add((LivingEntity) e);
		}
		return list;
	}

	/** Living things in a cone in front of the caster. */
	public static List<LivingEntity> cone(ServerPlayer caster, double range, double halfAngleDegrees) {
		List<LivingEntity> list = new ArrayList<>();
		Vec3 from = caster.getEyePosition();
		Vec3 look = caster.getLookAngle();
		double cos = Math.cos(Math.toRadians(halfAngleDegrees));
		for (LivingEntity e : around(caster, from, range, false)) {
			Vec3 dir = e.getBoundingBox().getCenter().subtract(from);
			double len = dir.length();
			if (len < 0.01 || dir.scale(1 / len).dot(look) >= cos) list.add(e);
		}
		return list;
	}

	/** Living things along the caster's line of sight, nearest first. */
	public static List<LivingEntity> along(ServerPlayer caster, double range, double width) {
		List<LivingEntity> list = new ArrayList<>();
		Vec3 from = caster.getEyePosition();
		Vec3 look = caster.getLookAngle();
		BlockHitResult wall = block(caster, range);
		double reach = wall.getType() == HitResult.Type.MISS ? range : wall.getLocation().distanceTo(from);
		for (LivingEntity e : around(caster, from.add(look.scale(reach / 2)), reach / 2 + 2, false)) {
			Vec3 toEntity = e.getBoundingBox().getCenter().subtract(from);
			double along = toEntity.dot(look);
			if (along < 0 || along > reach) continue;
			if (toEntity.subtract(look.scale(along)).length() <= width + e.getBbWidth() / 2) list.add(e);
		}
		list.sort((a, b) -> Double.compare(a.distanceToSqr(caster), b.distanceToSqr(caster)));
		return list;
	}
}
