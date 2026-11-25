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
	public static final Item WAND = register("wand", WandItem::new, new Item.Properties().stacksTo(1));
	public static final Item GRIMOIRE = register("grimoire", GrimoireItem::new, new Item.Properties().stacksTo(1));
	public static final Item WORD_SCROLL = register("word_scroll", WordScrollItem::new, new Item.Properties().stacksTo(16));
	public static final Item ARCANE_POTION = register("arcane_potion", ArcanePotionItem::new, new Item.Properties().stacksTo(16));

	public static final Block ARCANE_WELL = registerBlock("arcane_well", ArcaneWellBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.0f, 9.0f).sound(SoundType.AMETHYST).lightLevel(state -> 9));
	public static final Item ARCANE_WELL_ITEM = register("arcane_well", p -> new BlockItem(ARCANE_WELL, p), new Item.Properties().useBlockDescriptionPrefix());

	private OmniItems() {
	}

	static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Omnispell.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Omnispell.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	public static void register() {
		// Touching the class registers the items.
	}
}
