package com.omnispell.familiar;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** A familiar's egg. Soak it to shape what hatches; sneak and set it down to hatch it. */
public class FamiliarEggItem extends Item {
	public FamiliarEggItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
		ItemStack stack = context.getItemInHand();
		if (!player.isShiftKeyDown()) {
			player.sendOverlayMessage(Component.literal("Sneak and use the egg on the ground to hatch it. Soak it first to shape it."));
			return InteractionResult.SUCCESS;
		}
		if (Familiars.familiarOf(player) != null) {
			player.sendOverlayMessage(Component.literal("You already have a familiar at your side. Pocket it first."));
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		Familiars.Species species = Familiars.pocketSpecies(stack);
		int[] stats = Familiars.hatchStats(stack);
		Mob mob = Familiars.release(player, species, stats, "", Vec3.atBottomCenterOf(pos));
		if (mob == null) {
			player.sendOverlayMessage(Component.literal("Nothing stirs inside."));
			return InteractionResult.SUCCESS;
		}
		stack.shrink(1);
		player.sendSystemMessage(Component.literal("The shell splits. A " + species.name().toLowerCase() + " looks up at you."));
		Familiars.describe(player, mob.getCustomName().getString(), stats, mob.getHealth(), mob.getMaxHealth());
		return InteractionResult.SUCCESS;
	}
}
