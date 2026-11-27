package com.omnispell.item;

import com.omnispell.player.Reliquary;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Reliquary. It does nothing in your inventory but wait. If one still full of a past life's
 * things somehow reaches its owner's inventory (carried by a hopper, say), it empties itself.
 */
public class ReliquaryItem extends Item {
	public ReliquaryItem(Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (!(entity instanceof ServerPlayer player) || !Reliquary.holds(stack) || !Reliquary.ownedBy(stack, player)) return;
		ItemStack empty = Reliquary.spill(player, stack.copy());
		stack.applyComponents(empty.getComponentsPatch());
		stack.remove(net.minecraft.core.component.DataComponents.CONTAINER);
		if (!empty.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		else stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, empty.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
		stack.remove(net.minecraft.core.component.DataComponents.ITEM_NAME);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return Reliquary.holds(stack);
	}
}
