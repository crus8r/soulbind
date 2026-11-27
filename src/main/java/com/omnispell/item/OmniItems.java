package com.omnispell.item;

import com.omnispell.Omnispell;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import com.omnispell.block.ArcaneWellBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Function;

public final class OmniItems {
	public static final Item WAND = register("wand", WandItem::new, new Item.Properties().stacksTo(1).equippableUnswappable(net.minecraft.world.entity.EquipmentSlot.OFFHAND)
			.component(net.minecraft.core.component.DataComponents.USE_EFFECTS, new net.minecraft.world.item.component.UseEffects(true, false, 1.0f)));
	public static final Item GRIMOIRE = register("grimoire", GrimoireItem::new, new Item.Properties().stacksTo(1));
	public static final Item WORD_SCROLL = register("word_scroll", WordScrollItem::new, new Item.Properties().stacksTo(16));
	public static final Item ARCANE_POTION = register("arcane_potion", ArcanePotionItem::new, new Item.Properties().stacksTo(16));

	public static final Block ARCANE_WELL = registerBlock("arcane_well", ArcaneWellBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.0f, 9.0f).sound(SoundType.AMETHYST).lightLevel(state -> 9));
	public static final Item ARCANE_WELL_ITEM = register("arcane_well", p -> new BlockItem(ARCANE_WELL, p), new Item.Properties().useBlockDescriptionPrefix());

	/** Air hardened by wind magic: solid for you and your friends, thick as powder snow for anything hostile. Conjured only; it fades. */
	public static final Block HARDENED_AIR = registerBlock("hardened_air", com.omnispell.block.HardenedAirBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.3f).sound(SoundType.GLASS).noOcclusion()
					.isValidSpawn((state, level, pos, type) -> false).isRedstoneConductor((state, level, pos) -> false)
					.isSuffocating((state, level, pos) -> false).noLootTable());
	public static final Block WAYPOINT = registerBlock("waypoint", com.omnispell.waypoint.WaypointBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5f, 1200.0f).sound(SoundType.AMETHYST).lightLevel(state -> 11)
					.pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE).noLootTable());
	public static final Item WAYPOINT_ITEM = register("waypoint", p -> new com.omnispell.waypoint.WaypointItem(WAYPOINT, p), new Item.Properties().useBlockDescriptionPrefix().stacksTo(2));

	public static final Block BOSS_ALTAR = registerBlock("boss_altar", com.omnispell.boss.BossAltarBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000.0f).sound(SoundType.DEEPSLATE)
					.lightLevel(state -> state.getValue(com.omnispell.boss.BossAltarBlock.SPENT) ? 0 : 12)
					.pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE).noLootTable());
	public static final Item BOSS_ALTAR_ITEM = register("boss_altar", p -> new BlockItem(BOSS_ALTAR, p), new Item.Properties().useBlockDescriptionPrefix());

	public static final Block ALCHEMY_CAULDRON = registerBlock("alchemy_cauldron", com.omnispell.alchemy.AlchemyCauldronBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops());
	public static final Item ALCHEMY_CAULDRON_ITEM = register("alchemy_cauldron", p -> new BlockItem(ALCHEMY_CAULDRON, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item BREW = register("brew", com.omnispell.alchemy.BrewItem::new, new Item.Properties().stacksTo(16));

	// ----- the Enchanted Forest
	public static final Block FAEWOOD_LOG = registerBlock("faewood_log", net.minecraft.world.level.block.RotatedPillarBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0f).sound(SoundType.CHERRY_WOOD).lightLevel(state -> 3).ignitedByLava());
	public static final Item FAEWOOD_LOG_ITEM = register("faewood_log", p -> new BlockItem(FAEWOOD_LOG, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Block FAEWOOD_LEAVES = registerBlock("faewood_leaves", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(0.2f).sound(SoundType.CHERRY_LEAVES).lightLevel(state -> 8).ignitedByLava());
	public static final Item FAEWOOD_LEAVES_ITEM = register("faewood_leaves", p -> new BlockItem(FAEWOOD_LEAVES, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Block FAEWOOD_PLANKS = registerBlock("faewood_planks", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0f, 3.0f).sound(SoundType.CHERRY_WOOD).ignitedByLava());
	public static final Item FAEWOOD_PLANKS_ITEM = register("faewood_planks", p -> new BlockItem(FAEWOOD_PLANKS, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Block RUNIC_BOOKSHELF = registerBlock("runic_bookshelf", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(1.5f).sound(SoundType.CHISELED_BOOKSHELF).lightLevel(state -> 6).ignitedByLava());
	public static final Item RUNIC_BOOKSHELF_ITEM = register("runic_bookshelf", p -> new BlockItem(RUNIC_BOOKSHELF, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Block FOREST_GATE = registerBlock("forest_gate", com.omnispell.realm.ForestGateBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(3.0f, 1200.0f).sound(SoundType.AMETHYST).lightLevel(state -> 12));
	/** The lit inside of a forest portal frame. Not an item: it is woken with an ender pearl. */
	public static final Block FOREST_PORTAL = registerBlock("forest_portal", com.omnispell.realm.ForestPortalBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noCollision().strength(-1.0f).sound(SoundType.GLASS).lightLevel(state -> 11).noLootTable());
	public static final Item FOREST_GATE_ITEM = register("forest_gate", p -> new BlockItem(FOREST_GATE, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Block WITHERED_GATE = registerBlock("withered_gate", com.omnispell.realm.WitheredGateBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(50.0f, 1200.0f).sound(SoundType.STONE).requiresCorrectToolForDrops());
	public static final Item WITHERED_GATE_ITEM = register("withered_gate", p -> new BlockItem(WITHERED_GATE, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item FAE_DUST = register("fae_dust", Item::new, new Item.Properties());
	public static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> HAT_ASSET =
			net.minecraft.resources.ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, Omnispell.id("wizard_hat"));
	public static final Item WIZARD_HAT = register("wizard_hat", WizardHatItem::new, new Item.Properties().stacksTo(1)
			.component(net.minecraft.core.component.DataComponents.EQUIPPABLE, net.minecraft.world.item.equipment.Equippable.builder(net.minecraft.world.entity.EquipmentSlot.HEAD)
					.setAsset(HAT_ASSET).setEquipSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER).setDamageOnHurt(false).build())
			.component(net.minecraft.core.component.DataComponents.ENCHANTABLE, new net.minecraft.world.item.enchantment.Enchantable(15))
			.component(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY)
			.rarity(net.minecraft.world.item.Rarity.EPIC));
	public static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> ROBE_ASSET =
			net.minecraft.resources.ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, Omnispell.id("wizard_robe"));
	public static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> BOOTS_ASSET =
			net.minecraft.resources.ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, Omnispell.id("wizard_boots"));
	public static final Item WIZARD_ROBE = register("wizard_robe", Item::new, attire(net.minecraft.world.entity.EquipmentSlot.CHEST, ROBE_ASSET));
	public static final Item WIZARD_BOOTS = register("wizard_boots", Item::new, attire(net.minecraft.world.entity.EquipmentSlot.FEET, BOOTS_ASSET));
	/** A bundle bound to a totem: catches what falls from you when you die. Six uses; mended with bundles. */
	public static final Item PET_SHELL = register("pet_shell", PetShellItem::new, new Item.Properties().stacksTo(1));
	public static final Item RELIQUARY = register("reliquary", ReliquaryItem::new, new Item.Properties().durability(com.omnispell.player.Reliquary.USES)
			.repairable(net.minecraft.world.item.Items.BUNDLE).fireResistant().rarity(net.minecraft.world.item.Rarity.RARE));
	public static final Item ARCANE_FOCUS = register("arcane_focus", Item::new, new Item.Properties().stacksTo(1));
	public static final Item FAMILIAR_EGG = register("familiar_egg", com.omnispell.familiar.FamiliarEggItem::new, new Item.Properties().stacksTo(1));
	public static final Item FAMILIAR = register("familiar", com.omnispell.familiar.FamiliarItem::new, new Item.Properties().stacksTo(1));

	private OmniItems() {
	}

	/** Robe or boots: no armour, no durability, dyeable, enchantable with the mana enchantments. */
	private static Item.Properties attire(net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> asset) {
		return new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)
				.component(net.minecraft.core.component.DataComponents.EQUIPPABLE, net.minecraft.world.item.equipment.Equippable.builder(slot)
						.setAsset(asset).setEquipSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER).setDamageOnHurt(false).build())
				.component(net.minecraft.core.component.DataComponents.ENCHANTABLE, new net.minecraft.world.item.enchantment.Enchantable(15));
	}

	static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Omnispell.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Omnispell.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	/** A creative tab with everything in Omnispell, including a scroll for every word. */
	private static void registerCreativeTab() {
		ResourceKey<net.minecraft.world.item.CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Omnispell.id("omnispell"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder()
				.title(net.minecraft.network.chat.Component.literal("Omnispell"))
				.icon(() -> new net.minecraft.world.item.ItemStack(GRIMOIRE))
				.displayItems((parameters, output) -> {
					output.accept(new net.minecraft.world.item.ItemStack(GRIMOIRE));
					for (WandParts.Core core : WandParts.CORES.values()) output.accept(WandItem.create("oak", core.id));
					for (WandParts.Wood wood : WandParts.WOODS.values()) {
						if (!wood.id.equals("oak")) output.accept(WandItem.create(wood.id, "amethyst_shard"));
					}
					output.accept(new net.minecraft.world.item.ItemStack(ARCANE_WELL_ITEM));
					output.accept(new net.minecraft.world.item.ItemStack(ARCANE_POTION));
					output.accept(new net.minecraft.world.item.ItemStack(ARCANE_FOCUS));
					output.accept(new net.minecraft.world.item.ItemStack(RELIQUARY));
					output.accept(new net.minecraft.world.item.ItemStack(PET_SHELL));
					output.accept(new net.minecraft.world.item.ItemStack(WIZARD_HAT));
					output.accept(new net.minecraft.world.item.ItemStack(WIZARD_ROBE));
					output.accept(new net.minecraft.world.item.ItemStack(WIZARD_BOOTS));
					output.accept(new net.minecraft.world.item.ItemStack(ALCHEMY_CAULDRON_ITEM));
					output.accept(new net.minecraft.world.item.ItemStack(WAYPOINT_ITEM, 2));
					output.accept(new net.minecraft.world.item.ItemStack(BOSS_ALTAR_ITEM));
					output.accept(new net.minecraft.world.item.ItemStack(FOREST_GATE_ITEM));
					output.accept(new net.minecraft.world.item.ItemStack(WITHERED_GATE_ITEM));
					output.accept(new net.minecraft.world.item.ItemStack(FAEWOOD_LOG_ITEM));
					output.accept(new net.minecraft.world.item.ItemStack(FAEWOOD_PLANKS_ITEM));
					output.accept(new net.minecraft.world.item.ItemStack(FAEWOOD_LEAVES_ITEM));
					output.accept(new net.minecraft.world.item.ItemStack(RUNIC_BOOKSHELF_ITEM));
					output.accept(new net.minecraft.world.item.ItemStack(FAE_DUST));
					for (String species : com.omnispell.familiar.Familiars.SPECIES.keySet()) {
						output.accept(com.omnispell.familiar.Familiars.egg(species, null, null));
						com.omnispell.familiar.Familiars.Stat gift = com.omnispell.familiar.Familiars.SPECIES.get(species).gift();
						output.accept(com.omnispell.familiar.Familiars.egg(species, gift, com.omnispell.familiar.Familiars.Stat.values()[(gift.ordinal() + 1) % 4]));
					}
					for (int rarity = 1; rarity <= 5; rarity++) output.accept(WordScrollItem.ofRarity(rarity));
					for (com.omnispell.data.WordBank.SpellType type : com.omnispell.data.WordBank.spellTypes()) {
						if (!type.id.equals("attack") && !type.id.equals("defense") && !type.id.equals("exploration")) output.accept(WordScrollItem.ofSpellType(type.id));
					}
					for (com.omnispell.data.Word word : com.omnispell.data.WordBank.words()) {
						if (!word.excluded) output.accept(WordScrollItem.ofWord(word.id));
					}
				}).build());
	}

	public static void register() {
		registerCreativeTab();
		// Touching the class registers the items.
	}
}
