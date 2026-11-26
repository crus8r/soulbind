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
	private static final java.util.Map<String, Spell> PARSED = new java.util.concurrent.ConcurrentHashMap<>();
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
			String text = (i + 1) + ". " + (spell == null ? "—" : spell.label());
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
		return Math.max(-1, Math.min(SLOTS - 1, tag(stack).getIntOr(SELECTED, 0))); // -1: no spell selected
	}

	public static void select(ItemStack stack, int slot) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(SELECTED, Math.max(-1, Math.min(SLOTS - 1, slot))));
		refreshLook(stack);
	}

	public static Spell spell(ItemStack stack, int slot) {
		String json = tag(stack).getStringOr(SPELL + slot, "");
		if (json.isEmpty()) return null;
		// The spell bar asks for every slot on every frame, so the parsed spells are remembered.
		Spell known = PARSED.get(json);
		if (known != null) return known;
		try {
			Spell parsed = WordBank.GSON.fromJson(json, Spell.class);
			if (PARSED.size() > 400) PARSED.clear();
			PARSED.put(json, parsed);
			return parsed;
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
	/** Steps the wand to its next slot and says which spell is there. */
	public static void cycle(ServerPlayer player, ItemStack stack) {
		int next = selected(stack) + 1;
		if (next >= SLOTS) next = -1; // after the last slot comes "none": the wand's plain bolt
		select(stack, next);
		Spell spell = next < 0 ? null : spell(stack, next);
		player.sendOverlayMessage(Component.literal(next < 0 ? "No spell selected: left-click throws a bolt" : "Slot " + (next + 1) + ": " + (spell == null ? "empty" : spell.label())));
	}

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
		// The Eloquence charm, on a wand in hand or on armour, lends up to two more words.
		return best + Math.min(2, com.omnispell.charm.Charms.level(player, "eloquence"));
	}

	/** A freshly crafted wand has no tooltip yet: write it the first time the wand sits in an inventory. */
	@Override
	public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity owner, net.minecraft.world.entity.EquipmentSlot slot) {
		if (!stack.has(DataComponents.LORE) || stack.get(DataComponents.LORE).lines().isEmpty()) refreshLook(stack);
		if (core(stack).equals("enchanting_table") && !tag(stack).getBooleanOr(ROLLED, false)) rollEnchantments(stack, level);
		if (owner instanceof ServerPlayer player && (slot == net.minecraft.world.entity.EquipmentSlot.MAINHAND || slot == net.minecraft.world.entity.EquipmentSlot.OFFHAND)
				&& com.omnispell.spell.Weapons.imbued(player) && level.getGameTime() % 3 == 0) com.omnispell.spell.Weapons.aura(player);
	}

	private static final String ROLLED = "omni_rolled";

	/**
	 * A wand with an enchanting table for a core comes with enchantments of its
	 * own, once, by chance: often Unbreaking, often Soul Bind, never certain.
	 */
	public static void rollEnchantments(ItemStack stack, net.minecraft.server.level.ServerLevel level) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(ROLLED, true));
		var random = level.getRandom();
		var soulBind = com.soulbind.SoulBind.findEnchantment(level.registryAccess(), com.soulbind.SoulBind.ENCHANTMENT_ID);
		var unbreaking = com.soulbind.SoulBind.findEnchantment(level.registryAccess(), com.soulbind.SoulBind.UNBREAKING_ID);
		int bind = random.nextFloat() < 0.5f ? (random.nextFloat() < 0.75f ? 1 : random.nextFloat() < 0.8f ? 2 : 3) : 0;
		int keep = random.nextFloat() < 0.6f ? 1 + random.nextInt(3) : 0;
		if (bind == 0 && keep == 0) keep = 1; // the table always gives something
		int soul = bind, sturdy = keep;
		net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(stack, enchantments -> {
			if (soulBind != null && soul > 0) enchantments.set(soulBind, soul);
			if (unbreaking != null && sturdy > 0) enchantments.set(unbreaking, sturdy);
		});
	}

	/** Right-click casts the selected spell. Sneak + right-click steps to the next slot. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player instanceof ServerPlayer serverPlayer) {
			ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
			if (com.omnispell.familiar.Familiars.isEgg(other)) {
				com.omnispell.familiar.Familiars.describeEgg(serverPlayer, other);
				return InteractionResult.SUCCESS;
			}
			if (other.is(OmniItems.FAMILIAR)) {
				com.omnispell.familiar.Familiars.describe(serverPlayer, other.getHoverName().getString(), com.omnispell.familiar.Familiars.pocketStats(other), 0, 0);
				return InteractionResult.SUCCESS;
			}
			if (!player.isShiftKeyDown()) {
				// Held right-click: the wand's ward. It costs Barrier mana for as long as it is up.
				if (com.omnispell.player.Mana.available(serverPlayer, com.omnispell.player.ManaPool.BARRIER) < 1) {
					serverPlayer.sendOverlayMessage(Component.literal("No Barrier mana left to raise a ward."));
					return InteractionResult.FAIL;
				}
				player.startUsingItem(hand);
				return InteractionResult.CONSUME;
			}
			// Sneak + right-click passes the wand to the other hand. Casting is left-click and the spell keys.
			InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			player.setItemInHand(hand, other);
			player.setItemInHand(otherHand, stack);
			level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.4f);
		}
		ItemStack inOtherHand = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		boolean reading = com.omnispell.familiar.Familiars.isEgg(inOtherHand) || inOtherHand.is(OmniItems.FAMILIAR); // reading an egg or familiar, not raising the ward
		if (level.isClientSide() && !player.isShiftKeyDown() && !reading) {
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
		return 72000;
	}

	@Override
	public net.minecraft.world.item.ItemUseAnimation getUseAnimation(ItemStack stack) {
		return net.minecraft.world.item.ItemUseAnimation.NONE;
	}

	/** True while the player is holding the wand's ward up. */
	public static boolean warding(Player player) {
		return player.isUsingItem() && isWand(player.getUseItem());
	}
}
