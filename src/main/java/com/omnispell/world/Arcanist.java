package com.omnispell.world;

import com.google.common.collect.ImmutableSet;
import com.omnispell.Omnispell;
import com.omnispell.item.OmniItems;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;

/**
 * The Arcanist: a villager whose workstation is an Arcane Well. They deal in
 * word scrolls, working up from common ones and arcane sundries to charmed
 * gems, ancient scrolls and, at the top of their trade, a forbidden one.
 */
public final class Arcanist {
	public static final ResourceKey<PoiType> POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Omnispell.id("arcanist"));
	public static final ResourceKey<VillagerProfession> PROFESSION = ResourceKey.create(Registries.VILLAGER_PROFESSION, Omnispell.id("arcanist"));

	private Arcanist() {
	}

	public static void register() {
		PoiHelper.register(Omnispell.id("arcanist"), 1, 1, OmniItems.ARCANE_WELL);
		Int2ObjectMap<ResourceKey<TradeSet>> trades = new Int2ObjectOpenHashMap<>();
		for (int level = 1; level <= 5; level++) trades.put(level, ResourceKey.create(Registries.TRADE_SET, Omnispell.id("arcanist/level_" + level)));
		Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, PROFESSION, new VillagerProfession(Component.literal("Arcanist"),
				holder -> holder.is(POI), holder -> holder.is(POI), ImmutableSet.of(), ImmutableSet.of(), SoundEvents.AMETHYST_BLOCK_CHIME, trades));
	}
}
