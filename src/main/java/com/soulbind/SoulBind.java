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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

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
	// level of Soul Bind per death instead: V -> IV -> III -> II -> I -> gone.
	// Unbreaking on them gives a chance to skip that loss, using the vanilla
	// Unbreaking odds: I = 50%, II = 67%, III = 75%.

	/** Highest Soul Bind level tools, weapons and armor can hold. Containers can reach V. */
	public static final int MAX_GEAR_LEVEL = 3;

	/** Chance each listed chest also holds a bundle enchanted with Soul Bind or Unbreaking (I-III). */
	public static final float BUNDLE_CHEST_CHANCE = 0.03f;

	/** Every player gets one bundle with these levels the first time they join. 0 = leave that enchantment off. */
	public static final int STARTER_SOUL_BIND = 2;
	public static final int STARTER_UNBREAKING = 2;

	/** Wayfinder: how far the dotted trail reaches, and the gap between dots, in blocks. */
	public static final double TRAIL_LENGTH = 14.0;
	public static final double TRAIL_SPACING = 1.5;

	/** Wayfinder: within this many blocks of the target you have arrived. The trail stops, and a map's Wayfinder is used up. */
	public static final double ARRIVAL_DISTANCE = 8.0;

	/** Wayfinder: helmet durability lost per second while the trail is showing. */
	public static final int HELMET_COST_PER_SECOND = 1;

	// ---------------------------------------------------------------

	public static final String ENCHANTMENT_ID = "soulbind:soul_bind";
	public static final String WAYFINDER_ID = "soulbind:wayfinder";
	public static final String UNBREAKING_ID = "minecraft:unbreaking";
	private static final String STARTER_TAG = "soulbind.starter_bundle";
	private static final String STARTER_KIT_TAG = "omnispell.starter_kit";

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

		// Once a second, for every player: starter bundle, book stacking, Wayfinder trail.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				giveStarterBundle(player);
				// Books from the enchanting table, anvils or commands don't know they can stack.
				Inventory inventory = player.getInventory();
				for (int i = 0; i < inventory.getContainerSize(); i++) {
					ItemStack stack = inventory.getItem(i);
					if (isPureSoulBindBook(stack) && stack.getMaxStackSize() != BOOK_STACK_SIZE) {
						stack.set(DataComponents.MAX_STACK_SIZE, BOOK_STACK_SIZE);
					}
				}
				showWayfinderTrail(player);
			}
		});
	}

	// ----- Getting the book -----

	private static void addBookToChest(String tableId, LootContext context, List<ItemStack> drops) {
		if (!CHESTS.contains(tableId)) return;
		RegistryAccess registries = context.getLevel().registryAccess();
		if (context.getRandom().nextFloat() < CHEST_CHANCE) {
			ItemStack book = createBook(registries);
			if (!book.isEmpty()) drops.add(book);
		}
		if (context.getRandom().nextFloat() < BUNDLE_CHEST_CHANCE) {
			int level = 1 + context.getRandom().nextInt(3);
			boolean soulBind = context.getRandom().nextBoolean();
			drops.add(createBundle(registries, soulBind ? level : 0, soulBind ? 0 : level));
		}
	}

	private static ItemStack createBundle(RegistryAccess registries, int soulBindLevel, int unbreakingLevel) {
		ItemStack bundle = new ItemStack(Items.BUNDLE);
		Holder<Enchantment> soulBind = findEnchantment(registries, ENCHANTMENT_ID);
		Holder<Enchantment> unbreaking = findEnchantment(registries, UNBREAKING_ID);
		EnchantmentHelper.updateEnchantments(bundle, enchantments -> {
			if (soulBind != null && soulBindLevel > 0) enchantments.set(soulBind, soulBindLevel);
			if (unbreaking != null && unbreakingLevel > 0) enchantments.set(unbreaking, unbreakingLevel);
		});
		return bundle;
	}

	private static void giveStarterBundle(ServerPlayer player) {
		boolean hadBundle = player.entityTags().contains(STARTER_TAG);
		boolean hadKit = player.entityTags().contains(STARTER_KIT_TAG);
		if (hadBundle && hadKit) return;
		// Marked only after the enchantment was found, so a bundle is never given blank and then never again.
		Holder<Enchantment> soulBind = findEnchantment(player.level().registryAccess(), ENCHANTMENT_ID);
		if (soulBind == null && STARTER_SOUL_BIND > 0) return;
		player.addTag(STARTER_TAG);
		player.addTag(STARTER_KIT_TAG);

		List<ItemStack> kit = com.omnispell.Omnispell.starterKit(player);
		Holder<Enchantment> unbreaking = findEnchantment(player.level().registryAccess(), UNBREAKING_ID);
		for (ItemStack stack : kit) {
			if (!com.omnispell.item.WandItem.isWand(stack)) continue;
			EnchantmentHelper.updateEnchantments(stack, enchantments -> {
				if (soulBind != null && STARTER_SOUL_BIND > 0) enchantments.set(soulBind, STARTER_SOUL_BIND);
				if (unbreaking != null && STARTER_UNBREAKING > 0) enchantments.set(unbreaking, STARTER_UNBREAKING);
			});
		}
		if (hadBundle) {
			// A player from before Omnispell already has their bundle: hand the new items over directly.
			for (ItemStack stack : kit) giveOrDrop(player, stack);
			return;
		}
		ItemStack bundle = createBundle(player.level().registryAccess(), STARTER_SOUL_BIND, STARTER_UNBREAKING);
		List<net.minecraft.world.item.ItemStackTemplate> contents = new java.util.ArrayList<>();
		for (ItemStack stack : kit) contents.add(net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(stack));
		bundle.set(DataComponents.BUNDLE_CONTENTS, new net.minecraft.world.item.component.BundleContents(contents));
		giveOrDrop(player, bundle);
	}

	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
			player.level().addFreshEntity(drop);
		}
	}

	public static Holder<Enchantment> findEnchantment(RegistryAccess registries, String id) {
		return registries.lookupOrThrow(Registries.ENCHANTMENT)
				.listElements()
				.filter(holder -> id.equals(holder.getRegisteredName()))
				.findFirst()
				.orElse(null);
	}

	private static void dropBook(LivingEntity entity) {
		ItemStack book = createBook(entity.level().registryAccess());
		if (book.isEmpty()) return;
		ItemEntity drop = new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), book);
		drop.setDefaultPickUpDelay();
		entity.level().addFreshEntity(drop);
	}

	private static ItemStack createBook(RegistryAccess registries) {
		Holder<Enchantment> enchantment = findEnchantment(registries, ENCHANTMENT_ID);
		if (enchantment == null) return ItemStack.EMPTY;

		ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		stored.set(enchantment, 1);
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
		BlockEntity blockEntity = context.getOptional(LootContextParams.BLOCK_ENTITY);
		if (blockEntity == null) return;
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

	/** Level of any enchantment on an item, by id: 0 if it has none. */
	public static int enchantmentLevel(ItemStack stack, String id) {
		if (stack.isEmpty()) return 0;
		ItemEnchantments enchantments = stack.getEnchantments();
		for (Holder<Enchantment> holder : enchantments.keySet()) {
			if (id.equals(holder.getRegisteredName())) return enchantments.getLevel(holder);
		}
		return 0;
	}

	/**
	 * Tools, weapons and armor stop at Soul Bind III. Called on every anvil result,
	 * so a level IV or V book can't push gear past the cap.
	 */
	public static void capGearLevel(ItemStack stack) {
		if (!stack.isDamageableItem() || soulBindLevel(stack) <= MAX_GEAR_LEVEL) return;
		Holder<Enchantment> bind = findSoulBind(stack.getEnchantments());
		EnchantmentHelper.updateEnchantments(stack, enchantments -> enchantments.set(bind, MAX_GEAR_LEVEL));
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

		// Bundles, shulker boxes, ender chests, unbreakable items: lose one level,
		// unless Unbreaking saves it.
		int unbreaking = enchantmentLevel(stack, UNBREAKING_ID);
		if (unbreaking > 0 && ThreadLocalRandom.current().nextInt(unbreaking + 1) > 0) return true;
		Holder<Enchantment> bind = findSoulBind(stack.getEnchantments());
		EnchantmentHelper.updateEnchantments(stack, enchantments -> enchantments.set(bind, level - 1));
		return true;
	}

	// ----- Wayfinder -----

	/**
	 * While a player sneaks holding a map, draw a short dotted line of particles
	 * from them toward the map's target. Needs Wayfinder on the helmet (costs
	 * durability) or on the map itself (used up on arrival).
	 */
	private static void showWayfinderTrail(ServerPlayer player) {
		if (!player.isShiftKeyDown()) return;
		ItemStack map = player.getMainHandItem();
		if (!map.is(Items.FILLED_MAP)) map = player.getOffhandItem();
		if (!map.is(Items.FILLED_MAP)) return;

		ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
		boolean onHelmet = enchantmentLevel(helmet, WAYFINDER_ID) > 0;
		boolean onMap = enchantmentLevel(map, WAYFINDER_ID) > 0;
		if (!onHelmet && !onMap) return;

		ServerLevel level = player.level();
		double[] target = mapTarget(map, level);
		if (target == null) return;

		double dx = target[0] - player.getX();
		double dz = target[1] - player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance <= ARRIVAL_DISTANCE) {
			if (!onHelmet) {
				EnchantmentHelper.updateEnchantments(map, enchantments ->
						enchantments.removeIf(holder -> WAYFINDER_ID.equals(holder.getRegisteredName())));
			}
			return;
		}

		double stepX = dx / distance;
		double stepZ = dz / distance;
		double y = player.getY() + 0.6;
		for (double d = 2.0; d <= Math.min(TRAIL_LENGTH, distance); d += TRAIL_SPACING) {
			level.sendParticles(ParticleTypes.END_ROD,
					player.getX() + stepX * d, y, player.getZ() + stepZ * d,
					1, 0.0, 0.0, 0.0, 0.0);
		}

		if (onHelmet && HELMET_COST_PER_SECOND > 0) {
			helmet.hurtAndBreak(HELMET_COST_PER_SECOND, player, EquipmentSlot.HEAD);
		}
	}

	/** Where a map points: its marked target (treasure X, structure icon) if it has one, else its center. */
	private static double[] mapTarget(ItemStack map, ServerLevel level) {
		MapDecorations decorations = map.get(DataComponents.MAP_DECORATIONS);
		if (decorations != null) {
			for (MapDecorations.Entry entry : decorations.decorations().values()) {
				return new double[]{entry.x(), entry.z()};
			}
		}
		MapItemSavedData data = MapItem.getSavedData(map, level);
		if (data == null) return null;
		return new double[]{data.centerX, data.centerZ};
	}

	// ----- XP -----

	private static void restoreXp(Player oldPlayer, Player newPlayer) {
		// keepInventory already carried XP over.
		if (newPlayer.experienceLevel > 0 || newPlayer.experienceProgress > 0f) return;
		int points = keptXp(oldPlayer);
		if (points > 0) newPlayer.giveExperiencePoints(points);
	}

	/**
	 * How many XP points this player's worn Soul Bind armor saves on death.
	 * Used twice: to hand the points back on respawn, and to shrink the pile of
	 * XP orbs dropped at the death spot so dying can never create XP.
	 */
	public static int keptXp(Player player) {
		float kept = 0f;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			kept += byLevel(XP_KEPT_PER_ARMOR_PIECE, soulBindLevel(player.getItemBySlot(slot)));
		}
		if (kept <= 0f) return 0;
		return (int) (totalXp(player) * Math.min(kept, 1f));
	}

	public static int totalXp(Player player) {
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
