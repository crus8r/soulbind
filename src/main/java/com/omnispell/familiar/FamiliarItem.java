package com.omnispell.familiar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** A familiar being carried. Use it on the ground to let it out. */
public class FamiliarItem extends Item {
	public FamiliarItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
		ItemStack stack = context.getItemInHand();
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return InteractionResult.PASS;
		long rest = data.copyTag().getLongOr("rest_until", 0) - player.level().getGameTime();
		if (rest > 0 && rest <= Familiars.REST) {
			player.sendOverlayMessage(Component.literal("It is still resting. " + (rest / 20 + 1) + " seconds more."));
			return InteractionResult.SUCCESS;
		}
		if (Familiars.familiarOf(player) != null) {
			player.sendOverlayMessage(Component.literal("You already have a familiar at your side."));
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		String name = data.copyTag().getStringOr("name", "");
		Mob mob = Familiars.release(player, Familiars.pocketSpecies(stack), Familiars.pocketStats(stack), name, Vec3.atBottomCenterOf(pos));
		if (mob == null) {
			player.sendOverlayMessage(Component.literal("It will not come out here."));
			return InteractionResult.SUCCESS;
		}
		stack.shrink(1);
		return InteractionResult.SUCCESS;
	}
}
