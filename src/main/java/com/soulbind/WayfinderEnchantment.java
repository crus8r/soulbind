package com.soulbind;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/** Wayfinder: a cheap, common enchantment for helmets and filled maps. */
public class WayfinderEnchantment extends Enchantment {

	public WayfinderEnchantment() {
		super(Rarity.UNCOMMON, SoulBind.WAYFINDABLE, new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND});
	}

	@Override
	public int getMinCost(int level) {
		return 5;
	}

	@Override
	public int getMaxCost(int level) {
		return 35;
	}

	@Override
	public boolean canEnchant(ItemStack stack) {
		return stack.is(Items.FILLED_MAP)
				|| (stack.getItem() instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.HELMET);
	}
}
