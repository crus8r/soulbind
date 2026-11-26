package com.omnispell.waypoint;

import com.omnispell.item.OmniItems;
import com.omnispell.player.PlayerData;
import com.omnispell.player.PlayerStore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

/**
 * A waypoint, as an item. Waypoints are crafted two at a time. The first time a
 * fresh pair sits in a player's inventory it is bonded and given the next of the
 * twenty colours, so each player's pairs run through the colour wheel in order.
 */
public class WaypointItem extends BlockItem {
	private static final String PAIR = "omni_pair";
	private static final String COLOR = "omni_color";
	private static final String NAME = "omni_name";

	public WaypointItem(Block block, Properties properties) {
		super(block, properties);
	}

	public static ItemStack bonded(String pairId, int color, String name, int count) {
		ItemStack stack = new ItemStack(OmniItems.WAYPOINT_ITEM, count);
		CompoundTag tag = new CompoundTag();
		tag.putString(PAIR, pairId);
		tag.putInt(COLOR, color);
		tag.putString(NAME, name == null ? "" : name);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		int c = Math.floorMod(color, Waypoints.COLORS.length);
		String label = name == null || name.isEmpty() ? Waypoints.COLOR_NAMES[c] + " Waypoint" : name;
		stack.set(DataComponents.ITEM_NAME, Component.literal(label).withStyle(s -> s.withColor(Waypoints.COLORS[c])));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(Waypoints.COLORS[c])));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("One of a pair. Place both, then use either to travel to the other.").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(true)),
				Component.literal("Sneak + use a placed waypoint to rename or recolour the pair.").withStyle(s -> s.withColor(ChatFormatting.DARK_GRAY).withItalic(true)))));
		return stack;
	}

	/** A fresh pair for this player, in their next colour. */
	public static ItemStack newPair(ServerPlayer player) {
		PlayerData data = PlayerStore.get(player);
		int color = data.waypointPairs % Waypoints.COLORS.length;
		data.waypointPairs++;
		PlayerStore.save(player);
		return bonded(UUID.randomUUID().toString(), color, "", 2);
	}

	private static CompoundTag tag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	public static String pairId(ItemStack stack) {
		return tag(stack).getStringOr(PAIR, "");
	}

	/** Freshly crafted waypoints are blank: bond them two at a time as soon as a player holds them. */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
		if (!(owner instanceof ServerPlayer player) || !pairId(stack).isEmpty() || stack.getCount() < 2) return;
		int pairs = stack.getCount() / 2;
		int leftover = stack.getCount() % 2;
		// This stack becomes one bonded pair; any further pairs are handed over as their own stacks.
		ItemStack first = newPair(player);
		stack.applyComponents(first.getComponentsPatch());
		stack.setCount(2);
		for (int i = 1; i < pairs; i++) give(player, newPair(player));
		if (leftover > 0) give(player, new ItemStack(OmniItems.WAYPOINT_ITEM, leftover));
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
	}

	@Override
	protected BlockState getPlacementState(BlockPlaceContext context) {
		BlockState state = super.getPlacementState(context);
		if (state == null) return null;
		int color = Math.floorMod(tag(context.getItemInHand()).getIntOr(COLOR, 0), Waypoints.COLORS.length);
		return state.setValue(WaypointBlock.COLOR, color);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		ItemStack stack = context.getItemInHand();
		String pairId = pairId(stack);
		if (pairId.isEmpty()) {
			if (context.getPlayer() instanceof ServerPlayer player) {
				player.sendOverlayMessage(Component.literal("A lone waypoint has no partner. Waypoints bond in twos."));
			}
			return InteractionResult.FAIL;
		}
		CompoundTag tag = tag(stack);
		int color = tag.getIntOr(COLOR, 0);
		String name = tag.getStringOr(NAME, "");
		if (context.getLevel() instanceof ServerLevel level) {
			Waypoints.Pair pair = Waypoints.pair(level.getServer(), pairId);
			if (pair != null && pair.ends.size() >= 2) {
				if (context.getPlayer() instanceof ServerPlayer player) player.sendOverlayMessage(Component.literal("Both ends of this pair are already placed."));
				return InteractionResult.FAIL;
			}
			// A pair that was recoloured or renamed while this end was in a pocket keeps the newer look.
			if (pair != null && !pair.ends.isEmpty()) {
				color = pair.color;
				name = pair.name;
			}
		}
		InteractionResult result = super.place(context);
		if (result.consumesAction() && context.getLevel() instanceof ServerLevel level) {
			Waypoints.place(level, context.getClickedPos(), pairId, color, name);
			Waypoints.Pair pair = Waypoints.pair(level.getServer(), pairId);
			level.setBlockAndUpdate(context.getClickedPos(), level.getBlockState(context.getClickedPos()).setValue(WaypointBlock.COLOR, Math.floorMod(pair.color, Waypoints.COLORS.length)));
			if (context.getPlayer() instanceof ServerPlayer player) {
				player.sendOverlayMessage(Component.literal(pair.ends.size() >= 2 ? "The pair is linked." : "First waypoint set. Place its partner elsewhere."));
			}
		}
		return result;
	}
}
