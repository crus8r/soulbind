package com.omnispell.item;

import com.omnispell.data.WordBank;
import com.omnispell.spell.Casting;
import com.omnispell.spell.Spell;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A wand: two planks of one wood around a core. It holds five spells, which
 * stay on the wand, so a second wand is a second loadout.
 */
public class WandItem extends Item {
	public static final int SLOTS = 5;
	private static final String WOOD = "omni_wood";
	private static final String CORE = "omni_core";
	private static final String SELECTED = "omni_selected";
	private static final String SPELL = "omni_spell_";

	public WandItem(Properties properties) {
		super(properties);
	}

	public static ItemStack create(String wood, String core) {
		ItemStack stack = new ItemStack(OmniItems.WAND);
		CompoundTag tag = new CompoundTag();
		tag.putString(WOOD, wood);
		tag.putString(CORE, core);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		refreshLook(stack);
		return stack;
	}

	/** Name, colours and tooltip all derive from the wood, core and equipped spells. */
	public static void refreshLook(ItemStack stack) {
		WandParts.Wood wood = WandParts.wood(wood(stack));
		WandParts.Core core = WandParts.core(core(stack));
		stack.set(DataComponents.ITEM_NAME, Component.literal(wood.label + " Wand"));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(wood.color, core.color)));

		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(core.label + " core").withStyle(s -> s.withColor(core.color).withItalic(false)));
		lines.add(Component.literal(wood.blurb + " " + core.blurb).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(true)));
		lines.add(Component.literal("Spells of up to " + core.words + " words").withStyle(s -> s.withColor(ChatFormatting.DARK_GRAY).withItalic(false)));
		int selected = selected(stack);
		for (int i = 0; i < SLOTS; i++) {
			Spell spell = spell(stack, i);
			String text = (i + 1) + ". " + (spell == null ? "—" : spell.name);
			boolean isSelected = i == selected;
			lines.add(Component.literal(text).withStyle(s -> s.withColor(isSelected ? ChatFormatting.AQUA : ChatFormatting.BLUE).withItalic(false)));
		}
		stack.set(DataComponents.LORE, new ItemLore(lines));
	}

	private static CompoundTag tag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	public static boolean isWand(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof WandItem;
	}

	public static String wood(ItemStack stack) {
		return tag(stack).getStringOr(WOOD, "oak");
	}

	public static String core(ItemStack stack) {
		return tag(stack).getStringOr(CORE, "flint");
	}

	public static int selected(ItemStack stack) {
		return Math.max(0, Math.min(SLOTS - 1, tag(stack).getIntOr(SELECTED, 0)));
	}

	public static void select(ItemStack stack, int slot) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(SELECTED, Math.max(0, Math.min(SLOTS - 1, slot))));
		refreshLook(stack);
	}

	public static Spell spell(ItemStack stack, int slot) {
		String json = tag(stack).getStringOr(SPELL + slot, "");
		if (json.isEmpty()) return null;
		try {
			return WordBank.GSON.fromJson(json, Spell.class);
		} catch (Exception e) {
			return null;
		}
	}

	/** Puts a spell in a slot, or clears the slot when {@code spell} is null. */
	public static void setSpell(ItemStack stack, int slot, Spell spell) {
		if (slot < 0 || slot >= SLOTS) return;
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			if (spell == null) tag.remove(SPELL + slot);
			else tag.putString(SPELL + slot, WordBank.GSON.toJson(spell));
		});
		refreshLook(stack);
	}

	/** The wand in the main hand, else the off hand, else nothing. */
	public static ItemStack heldWand(Player player) {
		if (isWand(player.getMainHandItem())) return player.getMainHandItem();
		if (isWand(player.getOffhandItem())) return player.getOffhandItem();
		return ItemStack.EMPTY;
	}

	/** The longest spell this player can craft: set by the best wand core they carry. Three words with no wand. */
	public static int bestWordBudget(Player player) {
		int best = 3;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (isWand(stack)) best = Math.max(best, WandParts.core(core(stack)).words);
		}
		return best;
	}

	/** A freshly crafted wand has no tooltip yet: write it the first time the wand sits in an inventory. */
	@Override
	public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity owner, net.minecraft.world.entity.EquipmentSlot slot) {
		if (!stack.has(DataComponents.LORE) || stack.get(DataComponents.LORE).lines().isEmpty()) refreshLook(stack);
	}

	/** Right-click casts the selected spell. Sneak + right-click steps to the next slot. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player instanceof ServerPlayer serverPlayer) {
			if (player.isShiftKeyDown()) {
				int next = (selected(stack) + 1) % SLOTS;
				select(stack, next);
				Spell spell = spell(stack, next);
				serverPlayer.sendOverlayMessage(Component.literal("Slot " + (next + 1) + ": " + (spell == null ? "empty" : spell.name)));
			} else {
				Casting.cast(serverPlayer, stack, selected(stack));
			}
		}
		return InteractionResult.SUCCESS;
	}
}
