package com.soulbind.mixin;

import com.soulbind.SoulBind;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Enchanted books never stack in vanilla. Books holding only Soul Bind stack to
 * 16 with books of the same level. This overrides Forge's per-stack size hook.
 */
@Mixin(EnchantedBookItem.class)
public abstract class EnchantedBookItemMixin {

	public int getMaxStackSize(ItemStack stack) {
		return SoulBind.isPureSoulBindBook(stack) ? SoulBind.BOOK_STACK_SIZE : 1;
	}
}
