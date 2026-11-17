package com.soulbind;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SoulBind implements ModInitializer {

	// ---------------------------------------------------------------
	// Balance knobs. Change these and rebuild.
	// Arrays are indexed by enchantment level: { unused, I, II, III }.
	// ---------------------------------------------------------------

	/** Chance an enderman killed by a player drops a Soul Bind I book. Pearls are ~50%, so this is twice as rare. */
	public static final float BOOK_DROP_CHANCE = 0.25f;

	/** Which mob drops the book. */
	public static final String DROP_SOURCE = "minecraft:enderman";

	/** false = endermen in the End never drop books, so End farms can't mass-produce them. */
	public static final boolean DROPS_IN_THE_END = false;

	/**
	 * Chance a Soul Bind I book appears in each of the chests listed below.
	 * Ender pearls show up in roughly 20-23% of the chests that hold them, so this is half that.
	 */
	public static final float CHEST_CHANCE = 0.11f;

	/** Chests that hold enchanted books and/or ender pearls in vanilla. */
	public static final Set<String> CHESTS = Set.of(
			"minecraft:chests/simple_dungeon",
			"minecraft:chests/abandoned_mineshaft",
			"minecraft:chests/desert_pyramid",
			"minecraft:chests/jungle_temple",
			"minecraft:chests/stronghold_corridor",
			"minecraft:chests/stronghold_crossing",
			"minecraft:chests/stronghold_library",
			"minecraft:chests/woodland_mansion",
			"minecraft:chests/ancient_city",
			"minecraft:chests/underwater_ruin_big",
			"minecraft:chests/underwater_ruin_small",
			"minecraft:chests/pillager_outpost",
			"minecraft:chests/bastion_other",
			"minecraft:chests/trial_chambers/corridor"
	);

	/** How many Soul Bind books of the same level fit in one stack. */
	public static final int BOOK_STACK_SIZE = 16;

	/** Fraction of MAX durability a tool, weapon or armor piece loses each time it is kept through a death. */
	public static final float[] DURABILITY_COST = {0f, 0.20f, 0.12f, 0.06f};

	/** Fraction of your XP each WORN armor piece saves on death. Four pieces of Soul Bind III = 80%. */
	public static final float[] XP_KEPT_PER_ARMOR_PIECE = {0f, 0f, 0.10f, 0.20f};

	// Bundles, shulker boxes and ender chests have no durability. They lose one
	// level of Soul Bind per death instead: III -> II -> I -> gone.

	// ---------------------------------------------------------------

	public static final String ENCHANTMENT_ID = "soulbind:soul_bind";

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	@Override
	public void onInitialize() {
		// Endermen sometimes drop a Soul Bind book when a player kills them.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(damageSource.getEntity() instanceof Player)) return;
			if (!DROP_SOURCE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())) return;
			if (!DROPS_IN_THE_END && entity.level().dimension() == Level.END) return;
			if (entity.getRandom().nextFloat() >= BOOK_DROP_CHANCE) return;
			dropBook(entity);
		});

		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			addBookToChest(table.getRegisteredName(), context, drops);
			carryBindFromPlacedBlock(context, drops);
		});

		// On respawn, hand back kept items and saved XP.
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if (alive) return; // returning from the End, nothing was lost
			Inventory from = oldPlayer.getInventory();
			Inventory to = newPlayer.getInventory();
			for (int i = 0; i < from.getContainerSize(); i++) {
				ItemStack stack = from.getItem(i);
				// If the slot is already filled, keepInventory copied it for us.
				if (!stack.isEmpty() && to.getItem(i).isEmpty()) {
					to.setItem(i, stack.copy());
				}
			}
			restoreXp(oldPlayer, newPlayer);
		});

		// Books from the enchanting table, anvils or commands don't know they can stack.
		// Once a second, teach any pure Soul Bind book in a player's inventory.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) return;
			for (Player player : server.getPlayerList().getPlayers()) {
				Inventory inventory = player.getInventory();
				for (int i = 0; i < inventory.getContainerSize(); i++) {
					ItemStack stack = inventory.getItem(i);
					if (isPureSoulBindBook(stack) && stack.getMaxStackSize() != BOOK_STACK_SIZE) {
						stack.set(DataComponents.MAX_STACK_SIZE, BOOK_STACK_SIZE);
					}
				}
			}
		});
	}

	// ----- Getting the book -----

	private static void addBookToChest(String tableId, LootContext context, List<ItemStack> drops) {
		if (!CHESTS.contains(tableId)) return;
		if (context.getRandom().nextFloat() >= CHEST_CHANCE) return;
		ItemStack book = createBook(context.getLevel().registryAccess());
		if (!book.isEmpty()) drops.add(book);
	}

	private static void dropBook(LivingEntity entity) {
		ItemStack book = createBook(entity.level().registryAccess());
		if (book.isEmpty()) return;
		ItemEntity drop = new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), book);
		drop.setDefaultPickUpDelay();
		entity.level().addFreshEntity(drop);
	}

	private static ItemStack createBook(RegistryAccess registries) {
		Optional<Holder.Reference<Enchantment>> enchantment = registries
				.lookupOrThrow(Registries.ENCHANTMENT)
				.listElements()
				.filter(SoulBind::isSoulBind)
				.findFirst();
		if (enchantment.isEmpty()) return ItemStack.EMPTY;

		ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		stored.set(enchantment.get(), 1);
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
		book.set(DataComponents.MAX_STACK_SIZE, BOOK_STACK_SIZE);
		return book;
	}

	// ----- Placed shulker boxes and ender chests -----

	/**
	 * A placed block remembers the enchantments of the item that placed it, but vanilla
	 * does not give them back when the block is broken. This puts Soul Bind back on the
	 * dropped item, so the bind is only paused while the box sits in the world.
	 */
	private static void carryBindFromPlacedBlock(LootContext context, List<ItemStack> drops) {
		if (!context.hasParameter(LootContextParams.BLOCK_ENTITY)) return;
		BlockEntity blockEntity = context.getParameter(LootContextParams.BLOCK_ENTITY);
		ItemEnchantments remembered = blockEntity.components().get(DataComponents.ENCHANTMENTS);
		if (remembered == null) return;
		Holder<Enchantment> bind = findSoulBind(remembered);
		if (bind == null) return;
		int level = remembered.getLevel(bind);

		Item blockItem = blockEntity.getBlockState().getBlock().asItem();
		for (ItemStack drop : drops) {
			if (drop.is(blockItem) && !hasSoulBind(drop)) {
				EnchantmentHelper.updateEnchantments(drop, enchantments -> enchantments.set(bind, level));
			}
		}
	}

	// ----- Checks -----

	private static boolean isSoulBind(Holder<Enchantment> holder) {
		return ENCHANTMENT_ID.equals(holder.getRegisteredName());
	}

	private static Holder<Enchantment> findSoulBind(ItemEnchantments enchantments) {
		for (Holder<Enchantment> holder : enchantments.keySet()) {
			if (isSoulBind(holder)) return holder;
		}
		return null;
	}

	/** Soul Bind level on an item: 0 if it has none. */
	public static int soulBindLevel(ItemStack stack) {
		if (stack.isEmpty()) return 0;
		ItemEnchantments enchantments = stack.getEnchantments();
		Holder<Enchantment> bind = findSoulBind(enchantments);
		return bind == null ? 0 : enchantments.getLevel(bind);
	}

	public static boolean hasSoulBind(ItemStack stack) {
		return soulBindLevel(stack) > 0;
	}

	/** An enchanted book that carries Soul Bind (possibly with other enchantments). */
	public static boolean isSoulBindBook(ItemStack stack) {
		if (!stack.is(Items.ENCHANTED_BOOK)) return false;
		ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
		return stored != null && findSoulBind(stored) != null;
	}

	/** An enchanted book that carries Soul Bind and nothing else. Only these stack. */
	public static boolean isPureSoulBindBook(ItemStack stack) {
		if (!isSoulBindBook(stack)) return false;
		return stack.get(DataComponents.STORED_ENCHANTMENTS).keySet().size() == 1;
	}

	// ----- Death -----

	/**
	 * Called for every inventory slot when a player dies.
	 * Charges the item for being kept and returns true if it stays with the player.
	 * Returns false if the item should drop like normal.
	 */
	public static boolean payDeathCost(ItemStack stack) {
		int level = soulBindLevel(stack);
		if (level <= 0) return false;

		if (stack.isDamageableItem()) {
			int max = stack.getMaxDamage();
			int cost = Math.max(1, (int) Math.ceil(max * byLevel(DURABILITY_COST, level)));
			int remaining = max - stack.getDamageValue();
			if (remaining <= cost) return false; // too worn to pay: it drops
			stack.setDamageValue(stack.getDamageValue() + cost);
			return true;
		}

		// Bundles, shulker boxes, ender chests, unbreakable items: lose one level.
		Holder<Enchantment> bind = findSoulBind(stack.getEnchantments());
		EnchantmentHelper.updateEnchantments(stack, enchantments -> enchantments.set(bind, level - 1));
		return true;
	}

	// ----- XP -----

	private static void restoreXp(Player oldPlayer, Player newPlayer) {
		// keepInventory already carried XP over.
		if (newPlayer.experienceLevel > 0 || newPlayer.experienceProgress > 0f) return;

		float kept = 0f;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			kept += byLevel(XP_KEPT_PER_ARMOR_PIECE, soulBindLevel(oldPlayer.getItemBySlot(slot)));
		}
		if (kept <= 0f) return;
		kept = Math.min(kept, 1f);

		int total = totalXp(oldPlayer);
		// The player also dropped this much as orbs on death. Never hand back more
		// than what is left, or dying would create XP.
		int droppedAsOrbs = Math.min(oldPlayer.experienceLevel * 7, 100);
		int points = Math.min((int) (total * kept), Math.max(0, total - droppedAsOrbs));
		if (points > 0) newPlayer.giveExperiencePoints(points);
	}

	private static int totalXp(Player player) {
		int level = player.experienceLevel;
		double atLevel;
		if (level <= 16) {
			atLevel = level * level + 6 * level;
		} else if (level <= 31) {
			atLevel = 2.5 * level * level - 40.5 * level + 360;
		} else {
			atLevel = 4.5 * level * level - 162.5 * level + 2220;
		}
		return (int) (atLevel + player.experienceProgress * player.getXpNeededForNextLevel());
	}

	private static float byLevel(float[] values, int level) {
		return values[Math.max(0, Math.min(level, values.length - 1))];
	}
}
