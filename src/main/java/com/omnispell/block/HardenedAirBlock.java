package com.omnispell.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Air hardened by a wind spell. Solid underfoot and against the shoulder for players and
 * anything friendly, but hostile creatures wade through it like powder snow: slowly,
 * and unable to climb. A wall of it slows them down; a platform of it in the sky cannot
 * be climbed onto from below.
 */
public class HardenedAirBlock extends TransparentBlock {
	public HardenedAirBlock(Properties properties) {
		super(properties);
	}

	/** Hostile creatures, and the projectiles they fire, pass through. */
	public static boolean passes(Entity entity) {
		if (entity == null) return false;
		if (entity instanceof Enemy) return true;
		if (entity instanceof net.minecraft.world.entity.projectile.Projectile projectile) return projectile.getOwner() instanceof Enemy;
		return false;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (context instanceof EntityCollisionContext entityContext && passes(entityContext.getEntity())) return Shapes.empty();
		return Shapes.block();
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
		if (passes(entity) && !(entity instanceof net.minecraft.world.entity.projectile.Projectile)) entity.makeStuckInBlock(state, new Vec3(0.45, 0.4, 0.45));
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return type == PathComputationType.LAND; // hostile creatures will walk into it to reach you
	}
}
