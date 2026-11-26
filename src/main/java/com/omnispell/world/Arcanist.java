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
	 * About half of all villages have an Arcanist. The first time a villager of a
	 * village is seen, the village is checked once: if it is one of the chosen
	 * half and has no Arcane Well yet, a well is set down in the open nearby and,
	 * if nobody there is free to take it up, a newcomer arrives who will.
	 * Which villages are chosen is fixed by the world seed and the place.
	 */
	public static void onEntityLoad(net.minecraft.world.entity.Entity entity, net.minecraft.server.level.ServerLevel level) {
		if (!(entity instanceof net.minecraft.world.entity.npc.villager.Villager villager) || entity.entityTags().contains(SEEN)) return;
		entity.addTag(SEEN);
		if (villager.isBaby()) return;
		net.minecraft.core.BlockPos at = villager.blockPosition();
		long region = (long) (at.getX() >> 7) * 341873128712L + (long) (at.getZ() >> 7) * 132897987541L;
		if (Math.floorMod(level.getSeed() ^ region ^ 0x5DEECE66DL, 100) >= 50) return;
		com.omnispell.spell.Tasks.later(60, () -> {
			if (!villager.isAlive()) return;
			net.minecraft.core.BlockPos near = villager.blockPosition();
			for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(near.offset(-48, -8, -48), near.offset(48, 8, 48))) {
				if (level.getBlockState(pos).is(OmniItems.ARCANE_WELL)) return; // this village has one already
			}
			net.minecraft.core.BlockPos well = placeWell(level, near);
			if (well != null) ensureTaker(level, well);
		});
	}

	/** Sets an Arcane Well down in the open near a place. Returns where, or null if no spot was found. */
	public static net.minecraft.core.BlockPos placeWell(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos near) {
		var random = level.getRandom();
		for (int tries = 0; tries < 80; tries++) {
			int spread = 4 + tries / 4; // try close first, then further out
			int x = near.getX() + random.nextInt(spread * 2 + 1) - spread;
			int z = near.getZ() + random.nextInt(spread * 2 + 1) - spread;
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
			if (Math.abs(y - near.getY()) > 6) continue;
			net.minecraft.world.level.block.state.BlockState ground = level.getBlockState(pos.below());
			net.minecraft.world.level.block.state.BlockState here = level.getBlockState(pos);
			if (!here.isAir() && !here.canBeReplaced()) continue;
			if (!level.getBlockState(pos.above()).isAir()) continue;
			if (!level.getFluidState(pos.below()).isEmpty() || !ground.isCollisionShapeFullBlock(level, pos.below())) continue;
			if (ground.is(net.minecraft.tags.BlockTags.LOGS) || ground.is(net.minecraft.tags.BlockTags.PLANKS) || ground.is(net.minecraft.world.level.block.Blocks.HAY_BLOCK)) continue; // not on a roof or a haystack
			if (ground.is(net.minecraft.world.level.block.Blocks.DIRT_PATH) || ground.is(net.minecraft.tags.BlockTags.STAIRS)) continue; // not in the road
			level.setBlockAndUpdate(pos, OmniItems.ARCANE_WELL.defaultBlockState());
			return pos;
		}
		return null;
	}

	/** If no free villager is near the well, one arrives to take it up. */
	private static void ensureTaker(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos well) {
		var box = new net.minecraft.world.phys.AABB(well).inflate(40);
		for (var villager : level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, box)) {
			var profession = villager.getVillagerData().profession();
			if (!villager.isBaby() && profession.is(net.minecraft.world.entity.npc.villager.VillagerProfession.NONE)) return;
		}
		var newcomer = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
		if (newcomer == null) return;
		newcomer.addTag(SEEN);
		newcomer.snapTo(well.getX() + 1.5, well.getY(), well.getZ() + 0.5, 0, 0);
		newcomer.setPersistenceRequired();
		level.addFreshEntity(newcomer);
	}

	public static void register() {
		PoiHelper.register(Omnispell.id("arcanist"), 1, 1, OmniItems.ARCANE_WELL);
		Int2ObjectMap<ResourceKey<TradeSet>> trades = new Int2ObjectOpenHashMap<>();
		for (int level = 1; level <= 5; level++) trades.put(level, ResourceKey.create(Registries.TRADE_SET, Omnispell.id("arcanist/level_" + level)));
		Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, PROFESSION, new VillagerProfession(Component.literal("Arcanist"),
				holder -> holder.is(POI), holder -> holder.is(POI), ImmutableSet.of(), ImmutableSet.of(), SoundEvents.AMETHYST_BLOCK_CHIME, trades));
	}
}
