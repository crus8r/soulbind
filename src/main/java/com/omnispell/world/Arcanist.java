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

	private static final String SEEN = "omnispell_villager_seen";

	/**
	 * Villages come with the odd Arcane Well: now and then, the first time a
	 * villager is seen, a well is set down near them. Whoever in the village has
	 * no trade will take it up and become the Arcanist.
	 */
	public static void onEntityLoad(net.minecraft.world.entity.Entity entity, net.minecraft.server.level.ServerLevel level) {
		if (!(entity instanceof net.minecraft.world.entity.npc.villager.Villager villager) || entity.entityTags().contains(SEEN)) return;
		entity.addTag(SEEN);
		if (villager.tickCount > 100 || level.getRandom().nextFloat() >= 0.09f) return; // only fresh villagers, and about one village in two
		com.omnispell.spell.Tasks.later(40, () -> {
			if (!villager.isAlive()) return;
			net.minecraft.core.BlockPos near = villager.blockPosition();
			for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(near.offset(-24, -4, -24), near.offset(24, 6, 24))) {
				if (level.getBlockState(pos).is(OmniItems.ARCANE_WELL)) return; // this village has one already
			}
			for (int tries = 0; tries < 24; tries++) {
				net.minecraft.core.BlockPos pos = near.offset(level.getRandom().nextInt(9) - 4, 2, level.getRandom().nextInt(9) - 4);
				for (int down = 0; down < 5 && level.getBlockState(pos.below()).canBeReplaced(); down++) pos = pos.below();
				net.minecraft.world.level.block.state.BlockState ground = level.getBlockState(pos.below());
				if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir() || !level.canSeeSky(pos)) continue;
				if (!ground.is(net.minecraft.tags.BlockTags.DIRT) && !ground.is(net.minecraft.world.level.block.Blocks.SAND) && !ground.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)) continue;
				level.setBlockAndUpdate(pos, OmniItems.ARCANE_WELL.defaultBlockState());
				return;
			}
		});
	}

	public static void register() {
		PoiHelper.register(Omnispell.id("arcanist"), 1, 1, OmniItems.ARCANE_WELL);
		Int2ObjectMap<ResourceKey<TradeSet>> trades = new Int2ObjectOpenHashMap<>();
		for (int level = 1; level <= 5; level++) trades.put(level, ResourceKey.create(Registries.TRADE_SET, Omnispell.id("arcanist/level_" + level)));
		Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, PROFESSION, new VillagerProfession(Component.literal("Arcanist"),
				holder -> holder.is(POI), holder -> holder.is(POI), ImmutableSet.of(), ImmutableSet.of(), SoundEvents.AMETHYST_BLOCK_CHIME, trades));
	}
}
