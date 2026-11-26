package com.omnispell;

import com.omnispell.data.WordBank;
import com.omnispell.item.WordScrollItem;
import com.omnispell.player.PlayerData;
import com.omnispell.player.PlayerStore;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Where word scrolls come from: chests in generated structures, hostile mobs,
 * and the three great vanilla bosses. Rarer places hold rarer words, and the
 * fuller your dictionary, the less often a new scroll turns up.
 */
public final class OmniLoot {
	private OmniLoot() {
	}

	public static void register() {
		// Fire magic cooks what it kills: raw food among a creature's drops comes off cooked.
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (!(context.getOptional(LootContextParams.LAST_DAMAGE_PLAYER) instanceof ServerPlayer killer)) return;
			if (context.getOptional(LootContextParams.THIS_ENTITY) instanceof ServerPlayer) return;
			if (!com.omnispell.spell.Casting.fireMagicActive(killer)) return;
			for (int i = 0; i < drops.size(); i++) {
				net.minecraft.world.item.Item cooked = cooked(drops.get(i).getItem());
				if (cooked != null) drops.set(i, new net.minecraft.world.item.ItemStack(cooked, drops.get(i).getCount()));
			}
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			String name = table.getRegisteredName();
			int colon = name.indexOf(':');
			String path = colon < 0 ? name : name.substring(colon + 1);
			if (!path.startsWith("chests/") && !path.contains("/chests/")) return;
			Entity opener = context.getOptional(LootContextParams.THIS_ENTITY);
			if (path.contains("familiar_nest")) {
				drops.add(com.omnispell.familiar.Familiars.randomEgg(context.getRandom()));
				if (context.getRandom().nextInt(3) == 0) drops.add(com.omnispell.familiar.Familiars.randomEgg(context.getRandom()));
				return;
			}
			double chance = Omnispell.CONFIG.scrollChestChance * bias(opener);
			RandomSource random = context.getRandom();
			int[] range = chestRarity(path);
			// Chests in the Nether, the End, the Enchanted Forest and other mods' realms hold rarer words.
			int bonus = com.omnispell.realm.Realms.rarityBonus(context.getLevel());
			range = new int[]{Math.min(5, range[0] + bonus), Math.min(5, range[1] + bonus)};
			if (random.nextDouble() < chance) {
				drops.add(WordScrollItem.ofRarity(range[0] + random.nextInt(range[1] - range[0] + 1)));
			}
			// The rarer vaults sometimes hold a treatise that unlocks a whole kind of spell.
			if (range[1] >= 3 && random.nextDouble() < 0.10) {
				String type = treatiseFor(opener, random);
				if (type != null) drops.add(WordScrollItem.ofSpellType(type));
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getEntity() instanceof ServerPlayer killer)) return;
			String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
			switch (id) {
				case "minecraft:witch" -> {
					if (entity.getRandom().nextFloat() < 0.04f) drop(entity, new net.minecraft.world.item.ItemStack(com.omnispell.item.OmniItems.WIZARD_HAT)); // a witch's hat, now and then
				}
				case "minecraft:wither" -> bossDrop(killer, entity, "wither", "The Wither", WordScrollItem.ofWord("command:Wither"));
				case "minecraft:warden" -> bossDrop(killer, entity, "warden", "The Warden", WordScrollItem.ofWord("base:Spirit"));
				case "minecraft:ender_dragon" -> bossDrop(killer, entity, "ender_dragon", "The Ender Dragon", WordScrollItem.ofRarity(5));
				default -> {
					if (!(entity instanceof Monster)) return;
					if (entity.getRandom().nextDouble() >= Omnispell.CONFIG.scrollMobChance * bias(killer)) return;
					int rarity = 1 + entity.getRandom().nextInt(2) + com.omnispell.realm.Realms.rarityBonus(entity.level());
					drop(entity, WordScrollItem.ofRarity(rarity));
				}
			}
		});
	}

	/** The cooked form of a raw food, found by name (beef -> cooked_beef), so other mods' meats work too. Null if it has none. */
	public static net.minecraft.world.item.Item cooked(net.minecraft.world.item.Item raw) {
		net.minecraft.resources.Identifier id = BuiltInRegistries.ITEM.getKey(raw);
		String path = id.getPath();
		if (path.startsWith("cooked_") || !new net.minecraft.world.item.ItemStack(raw).has(net.minecraft.core.component.DataComponents.FOOD)) {
			return raw == net.minecraft.world.item.Items.POTATO ? net.minecraft.world.item.Items.BAKED_POTATO : null;
		}
		String name = path.startsWith("raw_") ? path.substring(4) : path;
		if (raw == net.minecraft.world.item.Items.POTATO) return net.minecraft.world.item.Items.BAKED_POTATO;
		return BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.fromNamespaceAndPath(id.getNamespace(), "cooked_" + name)).orElse(null);
	}

	/** Scrolls get scarcer as the dictionary fills: full chance when empty, 40% of it when complete. */
	private static double bias(Entity entity) {
		if (!(entity instanceof ServerPlayer player)) return 0.8;
		PlayerData data = PlayerStore.get(player);
		double known = (double) data.words.size() / Math.max(1, WordBank.obtainableCount());
		return 1.0 - 0.6 * Math.min(1.0, known);
	}

	/** Lowest and highest scroll rarity for a chest, from where it is. Works for other mods' chests by name too. */
	private static int[] chestRarity(String path) {
		if (has(path, "end_city", "ancient_city")) return new int[]{4, 5};
		if (has(path, "boss_lair_5", "boss_lair_6")) return new int[]{4, 5};
		if (has(path, "boss_lair_3", "boss_lair_4", "forest_ruin")) return new int[]{3, 4};
		if (has(path, "boss_lair", "shrine")) return new int[]{2, 3};
		if (has(path, "stronghold", "bastion", "woodland_mansion", "trial_chambers", "nether_bridge", "fortress", "boss", "vault")) return new int[]{3, 4};
		if (has(path, "pyramid", "temple", "outpost", "ruined_portal", "treasure", "shipwreck", "underwater_ruin", "igloo", "dungeon", "mineshaft", "tower", "castle", "crypt")) return new int[]{2, 3};
		return new int[]{1, 2};
	}

	private static boolean has(String text, String... parts) {
		for (String part : parts) {
			if (text.contains(part)) return true;
		}
		return false;
	}

	private static String treatiseFor(Entity opener, RandomSource random) {
		String[] all = {"conjuration"};
		if (!(opener instanceof ServerPlayer player)) return all[random.nextInt(all.length)];
		PlayerData data = PlayerStore.get(player);
		for (String type : all) {
			if (!data.spellTypes.contains(type)) return type; // unlocked in the website's order
		}
		return null;
	}

	private static void bossDrop(ServerPlayer killer, LivingEntity boss, String id, String name, ItemStack scroll) {
		drop(boss, scroll);
		PlayerData data = PlayerStore.get(killer);
		if (data.bosses.add(id)) {
			killer.sendSystemMessage(Component.literal(name + " is recorded in your book.").withStyle(ChatFormatting.GOLD));
			PlayerStore.save(killer);
			PlayerStore.syncState(killer);
		}
	}

	private static void drop(LivingEntity from, ItemStack stack) {
		ItemEntity item = new ItemEntity(from.level(), from.getX(), from.getY() + 0.5, from.getZ(), stack);
		item.setDefaultPickUpDelay();
		from.level().addFreshEntity(item);
	}
}
