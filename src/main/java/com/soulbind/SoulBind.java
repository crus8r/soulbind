package com.soulbind;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Mod(SoulBind.MOD_ID)
public class SoulBind {

	public static final String MOD_ID = "soulbind";

	// ---------------------------------------------------------------
	// Balance knobs. Change these and rebuild.
	// Arrays are indexed by enchantment level: { unused, I, II, III }.
	// ---------------------------------------------------------------

	/** Chance an enderman killed by a player drops a Soul Bind I book. Pearls are ~50%, so this is twice as rare. */
	public static final float BOOK_DROP_CHANCE = 0.25f;

	/** false = endermen in the End never drop books, so End farms can't mass-produce them. */
	public static final boolean DROPS_IN_THE_END = false;

	/** Chance a Soul Bind I book appears in each of the chests listed below (pearls are ~20-23%). */
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
			"minecraft:chests/bastion_other"
	);

	/** How many Soul Bind books of the same level fit in one stack. */
	public static final int BOOK_STACK_SIZE = 16;

	/** Fraction of MAX durability a tool, weapon or armor piece loses each time it is kept through a death. */
	public static final float[] DURABILITY_COST = {0f, 0.20f, 0.12f, 0.06f};

	/** Fraction of your XP each WORN armor piece saves on death. Four pieces of Soul Bind III = 80%. */
	public static final float[] XP_KEPT_PER_ARMOR_PIECE = {0f, 0f, 0.10f, 0.20f};

	// Bundles, shulker boxes and ender chests have no durability. They lose one
	// level of Soul Bind per death instead: V -> IV -> III -> II -> I -> gone.
	// Unbreaking on them gives a chance to skip that loss: I = 50%, II = 67%, III = 75%.

	/** Highest Soul Bind level tools, weapons and armor can hold, and the highest found naturally. */
	public static final int MAX_GEAR_LEVEL = 3;

	/** Chance each listed chest also holds a bundle enchanted with Soul Bind or Unbreaking (I-III). */
	public static final float BUNDLE_CHEST_CHANCE = 0.03f;

	/** Every player gets one bundle with these levels the first time they join. 0 = leave that enchantment off. */
	public static final int STARTER_SOUL_BIND = 2;
	public static final int STARTER_UNBREAKING = 2;

	/** Wayfinder: how far the dotted trail reaches, and the gap between dots, in blocks. */
	public static final double TRAIL_LENGTH = 14.0;
	public static final double TRAIL_SPACING = 1.5;

	/** Wayfinder: within this many blocks of the target you have arrived. */
	public static final double ARRIVAL_DISTANCE = 8.0;

	/** Wayfinder: helmet durability lost per second while the trail is showing. */
	public static final int HELMET_COST_PER_SECOND = 1;

	// ---------------------------------------------------------------

	private static final String STARTER_TAG = "soulbind.starter_bundle";
	/** Key in a placed block's saved data that remembers its Soul Bind level. */
	public static final String PLACED_LEVEL_KEY = "soulbind_level";

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	// ----- Registration -----

	public static final EnchantmentCategory BINDABLE =
			EnchantmentCategory.create("SOULBIND_BINDABLE", item -> item.canBeDepleted() || isContainer(item));
	public static final EnchantmentCategory WAYFINDABLE =
			EnchantmentCategory.create("SOULBIND_WAYFINDABLE", item ->
					(item instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.HELMET) || item == Items.FILLED_MAP);

	public static final DeferredRegister<Enchantment> ENCHANTMENTS =
			DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, MOD_ID);
	public static final RegistryObject<Enchantment> SOUL_BIND = ENCHANTMENTS.register("soul_bind", SoulBindEnchantment::new);
	public static final RegistryObject<Enchantment> WAYFINDER = ENCHANTMENTS.register("wayfinder", WayfinderEnchantment::new);

	public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
			DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MOD_ID);
	static {
		LOOT_MODIFIERS.register("loot", () -> SoulBindLootModifier.CODEC);
	}

	public SoulBind() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		ENCHANTMENTS.register(modBus);
		LOOT_MODIFIERS.register(modBus);
		MinecraftForge.EVENT_BUS.addListener(SoulBind::onLivingDeath);
		MinecraftForge.EVENT_BUS.addListener(SoulBind::onPlayerClone);
		MinecraftForge.EVENT_BUS.addListener(SoulBind::onExperienceDrop);
		MinecraftForge.EVENT_BUS.addListener(SoulBind::onServerTick);
	}

	// ----- Events -----

	// Endermen sometimes drop a Soul Bind book when a player kills them.
	private static void onLivingDeath(LivingDeathEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity.level().isClientSide()) return;
		if (!(event.getSource().getEntity() instanceof Player)) return;
		if (!"minecraft:enderman".equals(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())))) return;
		if (!DROPS_IN_THE_END && entity.level().dimension() == Level.END) return;
		if (entity.getRandom().nextFloat() >= BOOK_DROP_CHANCE) return;
		ItemEntity drop = new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), createBook());
		drop.setDefaultPickUpDelay();
		entity.level().addFreshEntity(drop);
	}

	// On respawn, hand back kept items and saved XP.
	private static void onPlayerClone(PlayerEvent.Clone event) {
		if (!event.isWasDeath()) return; // returning from the End, nothing was lost
		Player oldPlayer = event.getOriginal();
		Player newPlayer = event.getEntity();
		Inventory from = oldPlayer.getInventory();
		Inventory to = newPlayer.getInventory();
		for (int i = 0; i < from.getContainerSize(); i++) {
			ItemStack stack = from.getItem(i);
			// If the slot is already filled, keepInventory copied it for us.
			if (!stack.isEmpty() && to.getItem(i).isEmpty()) {
				to.setItem(i, stack.copy());
			}
		}
		// keepInventory already carried XP over.
		if (newPlayer.experienceLevel > 0 || newPlayer.experienceProgress > 0f) return;
		int points = keptXp(oldPlayer);
		if (points > 0) newPlayer.giveExperiencePoints(points);
	}

	// XP saved by Soul Bind armor comes out of the orbs dropped at the death spot.
	private static void onExperienceDrop(LivingExperienceDropEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;
		int kept = keptXp(player);
		if (kept <= 0) return;
		int left = Math.max(0, totalXp(player) - kept);
		event.setDroppedExperience(Math.min(event.getDroppedExperience(), left));
	}

	// Once a second, for every player: starter bundle and Wayfinder trail.
	private static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null || server.getTickCount() % 20 != 0) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			giveStarterBundle(player);
			showWayfinderTrail(player);
		}
	}

	// ----- Items -----

	public static ItemStack createBook() {
		return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(SOUL_BIND.get(), 1));
	}

	private static ItemStack createBundle(int soulBindLevel, int unbreakingLevel) {
		ItemStack bundle = new ItemStack(Items.BUNDLE);
		if (soulBindLevel > 0) bundle.enchant(SOUL_BIND.get(), soulBindLevel);
		if (unbreakingLevel > 0) bundle.enchant(Enchantments.UNBREAKING, unbreakingLevel);
		return bundle;
	}

	private static void giveStarterBundle(ServerPlayer player) {
		if (player.getTags().contains(STARTER_TAG)) return;
		player.addTag(STARTER_TAG);
		ItemStack bundle = createBundle(STARTER_SOUL_BIND, STARTER_UNBREAKING);
		if (!player.getInventory().add(bundle)) {
			ItemEntity drop = new ItemEntity(player.serverLevel(), player.getX(), player.getY(), player.getZ(), bundle);
			player.serverLevel().addFreshEntity(drop);
		}
	}

	// ----- Loot (called by SoulBindLootModifier for every loot table) -----

	public static void modifyLoot(String tableId, LootContext context, List<ItemStack> drops) {
		// Random enchanted books and gear in loot stop at Soul Bind III.
		for (ItemStack stack : drops) capLevel(stack, MAX_GEAR_LEVEL);

		if (CHESTS.contains(tableId)) {
			if (context.getRandom().nextFloat() < CHEST_CHANCE) drops.add(createBook());
			if (context.getRandom().nextFloat() < BUNDLE_CHEST_CHANCE) {
				int level = 1 + context.getRandom().nextInt(3);
				boolean soulBind = context.getRandom().nextBoolean();
				drops.add(createBundle(soulBind ? level : 0, soulBind ? 0 : level));
			}
		}

		// A placed shulker box or ender chest remembers its Soul Bind level. Give it back when broken.
		BlockEntity blockEntity = context.getParamOrNull(LootContextParams.BLOCK_ENTITY);
		if (blockEntity == null) return;
		int level = blockEntity.getPersistentData().getInt(PLACED_LEVEL_KEY);
		if (level <= 0) return;
		Item blockItem = blockEntity.getBlockState().getBlock().asItem();
		for (ItemStack drop : drops) {
			if (drop.is(blockItem) && soulBindLevel(drop) == 0) setLevel(drop, SOUL_BIND.get(), level);
		}
	}

	// ----- Checks -----

	/** Bundles, shulker boxes and ender chests: Soul Bind items with no durability. */
	public static boolean isContainer(Item item) {
		return item == Items.BUNDLE || item == Items.ENDER_CHEST || Block.byItem(item) instanceof ShulkerBoxBlock;
	}

	public static boolean isContainer(ItemStack stack) {
		return !stack.isEmpty() && isContainer(stack.getItem());
	}

	/** Level of an enchantment on an item or book: 0 if it has none. */
	public static int level(ItemStack stack, Enchantment enchantment) {
		if (stack.isEmpty()) return 0;
		Integer level = EnchantmentHelper.getEnchantments(stack).get(enchantment);
		return level == null ? 0 : level;
	}

	public static int soulBindLevel(ItemStack stack) {
		return level(stack, SOUL_BIND.get());
	}

	/** Sets (or with 0, removes) one enchantment on an item or book, keeping the others. */
	public static void setLevel(ItemStack stack, Enchantment enchantment, int level) {
		Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
		if (level > 0) enchantments.put(enchantment, level);
		else enchantments.remove(enchantment);
		EnchantmentHelper.setEnchantments(enchantments, stack);
	}

	/** An enchanted book that carries Soul Bind and nothing else. Only these stack. */
	public static boolean isPureSoulBindBook(ItemStack stack) {
		if (!stack.is(Items.ENCHANTED_BOOK)) return false;
		Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
		return enchantments.size() == 1 && enchantments.containsKey(SOUL_BIND.get());
	}

	/** Lowers Soul Bind to the given level if it is higher. Containers are exempt from the gear cap. */
	public static void capLevel(ItemStack stack, int max) {
		if (isContainer(stack)) return;
		if (soulBindLevel(stack) > max) setLevel(stack, SOUL_BIND.get(), max);
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
			float fraction = DURABILITY_COST[Math.min(level, DURABILITY_COST.length - 1)];
			int cost = Math.max(1, (int) Math.ceil(max * fraction));
			int remaining = max - stack.getDamageValue();
			if (remaining <= cost) return false; // too worn to pay: it drops
			stack.setDamageValue(stack.getDamageValue() + cost);
			return true;
		}

		// Bundles, shulker boxes, ender chests, unbreakable items: lose one level,
		// unless Unbreaking saves it.
		int unbreaking = level(stack, Enchantments.UNBREAKING);
		if (unbreaking > 0 && ThreadLocalRandom.current().nextInt(unbreaking + 1) > 0) return true;
		setLevel(stack, SOUL_BIND.get(), level - 1);
		return true;
	}

	// ----- Wayfinder -----

	private static void showWayfinderTrail(ServerPlayer player) {
		if (!player.isShiftKeyDown()) return;
		ItemStack map = player.getMainHandItem();
		if (!map.is(Items.FILLED_MAP)) map = player.getOffhandItem();
		if (!map.is(Items.FILLED_MAP)) return;

		ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
		boolean onHelmet = level(helmet, WAYFINDER.get()) > 0;
		boolean onMap = level(map, WAYFINDER.get()) > 0;
		if (!onHelmet && !onMap) return;

		ServerLevel level = player.serverLevel();
		double[] target = mapTarget(map, level);
		if (target == null) return;

		double dx = target[0] - player.getX();
		double dz = target[1] - player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance <= ARRIVAL_DISTANCE) {
			if (!onHelmet) setLevel(map, WAYFINDER.get(), 0);
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
			helmet.hurtAndBreak(HELMET_COST_PER_SECOND, player, p -> p.broadcastBreakEvent(EquipmentSlot.HEAD));
		}
	}

	/** Where a map points: its marked target (treasure X, structure icon) if it has one, else its center. */
	private static double[] mapTarget(ItemStack map, ServerLevel level) {
		CompoundTag tag = map.getTag();
		if (tag != null && tag.contains("Decorations", Tag.TAG_LIST)) {
			ListTag decorations = tag.getList("Decorations", Tag.TAG_COMPOUND);
			if (!decorations.isEmpty()) {
				CompoundTag first = decorations.getCompound(0);
				return new double[]{first.getDouble("x"), first.getDouble("z")};
			}
		}
		MapItemSavedData data = MapItem.getSavedData(map, level);
		if (data == null) return null;
		return new double[]{data.centerX, data.centerZ};
	}

	// ----- XP -----

	/** How many XP points this player's worn Soul Bind armor saves on death. */
	public static int keptXp(Player player) {
		float kept = 0f;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			int level = soulBindLevel(player.getItemBySlot(slot));
			kept += XP_KEPT_PER_ARMOR_PIECE[Math.min(level, XP_KEPT_PER_ARMOR_PIECE.length - 1)];
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
}
