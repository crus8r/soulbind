package com.omnispell.world;

import com.omnispell.Omnispell;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Abandoned alchemy cauldrons: now and then, in the stranger biomes, one still
 * sits over its campfire. They generate in every biome listed in the
 * omnispell:has_abandoned_cauldron tag.
 */
public final class Cauldrons {
	private Cauldrons() {
	}

	public static void register() {
		TagKey<Biome> where = TagKey.create(Registries.BIOME, Omnispell.id("has_abandoned_cauldron"));
		ResourceKey<PlacedFeature> feature = ResourceKey.create(Registries.PLACED_FEATURE, Omnispell.id("abandoned_cauldron"));
		BiomeModifications.addFeature(context -> context.hasTag(where), GenerationStep.Decoration.VEGETAL_DECORATION, feature);
	}
}
