package com.soulbind;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;


/**
 * Runs after every loot table. Adds Soul Bind books and enchanted bundles to
 * chests, and puts Soul Bind back on shulker boxes and ender chests when broken.
 */
public class SoulBindLootModifier extends LootModifier {

	public static final MapCodec<SoulBindLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
			codecStart(instance).apply(instance, SoulBindLootModifier::new));

	public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
			DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, SoulBind.MOD_ID);

	static {
		SERIALIZERS.register("loot", () -> CODEC);
	}

	public SoulBindLootModifier(LootItemCondition[] conditions) {
		super(conditions);
	}

	@Override
	protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
		SoulBind.addBookToChest(String.valueOf(context.getQueriedLootTableId()), context, generatedLoot);
		SoulBind.carryBindFromPlacedBlock(context, generatedLoot);
		return generatedLoot;
	}

	@Override
	public MapCodec<? extends IGlobalLootModifier> codec() {
		return CODEC;
	}
}
