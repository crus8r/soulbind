package com.soulbind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

/**
 * Runs after every loot table. Adds Soul Bind books and enchanted bundles to
 * chests, caps random Soul Bind loot at III, and puts Soul Bind back on shulker
 * boxes and ender chests when broken.
 */
public class SoulBindLootModifier extends LootModifier {

	public static final Codec<SoulBindLootModifier> CODEC = RecordCodecBuilder.create(instance ->
			codecStart(instance).apply(instance, SoulBindLootModifier::new));

	public SoulBindLootModifier(LootItemCondition[] conditions) {
		super(conditions);
	}

	@Override
	protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
		SoulBind.modifyLoot(String.valueOf(context.getQueriedLootTableId()), context, generatedLoot);
		return generatedLoot;
	}

	@Override
	public Codec<? extends IGlobalLootModifier> codec() {
		return CODEC;
	}
}
