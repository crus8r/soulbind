package com.soulbind.mixin;

import com.soulbind.SoulBind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When a soul-bound shulker box or ender chest is placed, the placed block
 * remembers its Soul Bind level, so breaking it gives the bind back.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

	@Inject(method = "updateCustomBlockEntityTag(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;)Z",
			at = @At("HEAD"))
	private void soulbind$rememberBind(BlockPos pos, Level level, Player player, ItemStack stack, BlockState state,
									   CallbackInfoReturnable<Boolean> cir) {
		if (level.isClientSide() || !SoulBind.isContainer(stack)) return;
		int bind = SoulBind.soulBindLevel(stack);
		if (bind <= 0) return;
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity == null) return;
		blockEntity.getPersistentData().putInt(SoulBind.PLACED_LEVEL_KEY, bind);
		blockEntity.setChanged();
	}
}
