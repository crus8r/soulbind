package com.omnispell.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Wizard's Hat. Worn on the head, it guards nothing and never wears out, it
 * is never lost on death, and wizards pull things out of it: it holds 64 things
 * of any kind, beds, books and totems included, used like a bundle.
 */
public class WizardHatItem extends Item {
	public static final int CAPACITY = 64;

	public WizardHatItem(Properties properties) {
		super(properties);
	}

	public static List<ItemStack> contents(ItemStack hat) {
		ItemContainerContents held = hat.get(DataComponents.CONTAINER);
		List<ItemStack> out = new ArrayList<>();
		if (held != null) held.nonEmptyItemCopyStream().forEach(out::add);
		return out;
	}

	private static void store(ItemStack hat, List<ItemStack> items) {
		hat.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
	}

	public static int count(ItemStack hat) {
		int n = 0;
		for (ItemStack stack : contents(hat)) n += stack.getCount();
		return n;
	}

	/** Puts as much of a stack into the hat as fits. Returns how many went in. */
	public static int insert(ItemStack hat, ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() instanceof WizardHatItem || !stack.getItem().canFitInsideContainerItems()) return 0;
		int room = CAPACITY - count(hat);
		int moving = Math.min(room, stack.getCount());
		if (moving <= 0) return 0;
		List<ItemStack> items = contents(hat);
		int left = moving;
		// Top up matching piles first; a pile may hold up to 64 whatever the item's usual stack size.
		for (ItemStack pile : items) {
			if (left <= 0) break;
			if (!ItemStack.isSameItemSameComponents(pile, stack) || pile.getMaxStackSize() < 2) continue;
			int add = Math.min(left, pile.getMaxStackSize() - pile.getCount());
			pile.grow(add);
			left -= add;
		}
		while (left > 0) {
			int pile = Math.min(left, stack.getMaxStackSize());
			items.add(0, stack.copyWithCount(pile));
			left -= pile;
		}
		stack.shrink(moving);
		store(hat, items);
		return moving;
	}

	/** Takes out the most recent pile, or nothing if the hat is empty. */
	public static ItemStack takeOut(ItemStack hat) {
		List<ItemStack> items = contents(hat);
		if (items.isEmpty()) return ItemStack.EMPTY;
		ItemStack out = items.remove(0);
		store(hat, items);
		return out;
	}

	/** The hat on the cursor, clicked on a slot: put the slot's item in, or (right-click on an empty slot) take one pile out into it. */
	@Override
	public boolean overrideStackedOnOther(ItemStack hat, Slot slot, ClickAction action, Player player) {
		ItemStack there = slot.getItem();
		if (action == ClickAction.PRIMARY && !there.isEmpty()) {
			if (!slot.mayPickup(player)) return false;
			ItemStack taken = slot.safeTake(there.getCount(), there.getCount(), player);
			int moved = insert(hat, taken);
			if (!taken.isEmpty()) slot.safeInsert(taken);
			if (moved > 0) player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
			return true;
		}
		if (action == ClickAction.SECONDARY && there.isEmpty()) {
			ItemStack out = takeOut(hat);
			if (!out.isEmpty()) {
				ItemStack rest = slot.safeInsert(out);
				if (!rest.isEmpty()) insert(hat, rest);
				player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
			}
			return true;
		}
		return false;
	}

	/** Something on the cursor clicked onto the hat: put it in, or (right-click with an empty cursor) take one pile out. */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack hat, ItemStack cursor, Slot slot, ClickAction action, Player player, SlotAccess access) {
		if (!slot.allowModification(player)) return false;
		if (action == ClickAction.PRIMARY && !cursor.isEmpty()) {
			if (insert(hat, cursor) > 0) player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
			return true;
		}
		if (action == ClickAction.SECONDARY && cursor.isEmpty()) {
			ItemStack out = takeOut(hat);
			if (!out.isEmpty()) {
				access.set(out);
				player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean isBarVisible(ItemStack hat) {
		return count(hat) > 0;
	}

	@Override
	public int getBarWidth(ItemStack hat) {
		return Math.min(13, 1 + 12 * count(hat) / CAPACITY);
	}

	@Override
	public int getBarColor(ItemStack hat) {
		return count(hat) >= CAPACITY ? 0xFF5555 : 0x9A6CFF;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack hat, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		List<ItemStack> items = contents(hat);
		lines.accept(Component.literal("Holds " + count(hat) + " / " + CAPACITY).withStyle(ChatFormatting.LIGHT_PURPLE));
		int shown = 0;
		for (ItemStack stack : items) {
			if (shown++ >= 6) {
				lines.accept(Component.literal("  and " + (items.size() - 6) + " more...").withStyle(ChatFormatting.DARK_GRAY));
				break;
			}
			lines.accept(Component.literal("  " + stack.getCount() + " x ").append(stack.getHoverName()).withStyle(ChatFormatting.GRAY));
		}
		lines.accept(Component.literal("Kept when you die. Never wears out. Guards nothing.").withStyle(ChatFormatting.DARK_GRAY));
	}
}
