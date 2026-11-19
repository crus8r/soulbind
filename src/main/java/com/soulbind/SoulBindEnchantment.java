package com.soulbind;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Soul Bind I-V. Rarity RARE makes a book cost 2 levels per Soul Bind level in an
 * anvil on a fresh item, and gives it the same enchanting-table weight as Mending.
 */
public class SoulBindEnchantment extends Enchantment {

	public SoulBindEnchantment() {
		super(Rarity.RARE, SoulBind.BINDABLE, EquipmentSlot.values());
	}

	@Override
	public int getMaxLevel() {
		return 5;
	}

	@Override
	public int getMinCost(int level) {
		return 1 + (level - 1) * 17;
	}

	@Override
	public int getMaxCost(int level) {
		return getMinCost(level) + 17;
	}

	@Override
	public boolean canEnchant(ItemStack stack) {
		return stack.isDamageableItem() || SoulBind.isContainer(stack);
	}

	@Override
	public boolean isTradeable() {
		return false;
	}
}
