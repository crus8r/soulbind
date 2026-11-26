package com.omnispell.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets a familiar's body forget whom it would have hunted on its own. */
@Mixin(Mob.class)
public interface MobGoalsAccessor {
	@Accessor("targetSelector")
	GoalSelector omnispell$targetSelector();
}
