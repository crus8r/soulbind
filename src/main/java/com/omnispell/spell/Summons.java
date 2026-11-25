package com.omnispell.spell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Conjured creatures (allies, golems, mounts, decoys) that vanish after a while. */
public final class Summons {
	/** Marks an entity as conjured, so a stray one left by a crash is removed when it next loads. */
	public static final String TAG = "omnispell_summon";
	private static final Set<UUID> ALIVE = new HashSet<>();

	private Summons() {
	}

	public static void track(Entity entity, int ticks) {
		entity.addTag(TAG);
		ALIVE.add(entity.getUUID());
		Tasks.later(ticks, () -> dismiss(entity));
	}

	public static void dismiss(Entity entity) {
		ALIVE.remove(entity.getUUID());
		if (entity.isRemoved()) return;
		if (entity.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
		}
		entity.ejectPassengers();
		entity.discard();
	}

	/** True for a conjured entity nobody is keeping track of any more: left over from before a restart. */
	public static boolean isStray(Entity entity) {
		return entity.entityTags().contains(TAG) && !ALIVE.contains(entity.getUUID());
	}
}
