package com.omnispell.item;

import com.omnispell.Omnispell;
import com.omnispell.data.Word;
import com.omnispell.data.WordBank;
import com.omnispell.player.PlayerData;
import com.omnispell.player.PlayerStore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A scroll that teaches one word of power when read. A scroll only fixes how rare
 * its word is; the word itself is chosen when you read it, from the words you do
 * not know yet, so a scroll is never a duplicate.
 */
public class WordScrollItem extends Item {
	private static final String RARITY = "omni_rarity";
	private static final String WORD = "omni_word";
	private static final String SPELL_TYPE = "omni_type";
	private static final String[] RARITY_NAMES = {"", "Common", "Uncommon", "Rare", "Ancient", "Forbidden"};
	private static final Rarity[] RARITY_LOOK = {Rarity.COMMON, Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC, Rarity.EPIC};

	public WordScrollItem(Properties properties) {
		super(properties);
	}

	/** A scroll holding an unknown word of this rarity (1 common .. 5 rarest). */
	public static ItemStack ofRarity(int rarity) {
		int r = Math.max(1, Math.min(5, rarity));
		ItemStack stack = new ItemStack(OmniItems.WORD_SCROLL);
		CompoundTag tag = new CompoundTag();
		tag.putInt(RARITY, r);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		stack.set(DataComponents.ITEM_NAME, Component.literal(RARITY_NAMES[r] + " Word Scroll"));
		stack.set(DataComponents.RARITY, RARITY_LOOK[r]);
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("Read it to learn a word of power.").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(true)))));
		return stack;
	}

	/** A scroll that teaches one specific word, e.g. a boss drop. */
	public static ItemStack ofWord(String wordId) {
		Word word = WordBank.word(wordId);
		ItemStack stack = new ItemStack(OmniItems.WORD_SCROLL);
		CompoundTag tag = new CompoundTag();
		tag.putString(WORD, wordId);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		String name = word == null ? wordId : word.spelling;
		stack.set(DataComponents.ITEM_NAME, Component.literal("Scroll of " + name));
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		if (word != null) {
			stack.set(DataComponents.LORE, new ItemLore(List.of(
					Component.literal(word.shortName() + " — " + word.type).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(true)))));
		}
		return stack;
	}

	/** A treatise that unlocks a whole spell type (enchantment, illusion, charm, conjuration). */
	public static ItemStack ofSpellType(String typeId) {
		WordBank.SpellType type = WordBank.spellType(typeId);
		ItemStack stack = new ItemStack(OmniItems.WORD_SCROLL);
		CompoundTag tag = new CompoundTag();
		tag.putString(SPELL_TYPE, typeId);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		String name = type == null ? typeId : type.name.replace(" Spells", "");
		stack.set(DataComponents.ITEM_NAME, Component.literal("Treatise on " + name));
		stack.set(DataComponents.RARITY, Rarity.RARE);
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("Read it to craft " + name.toLowerCase() + " spells.").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(true)))));
		return stack;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		ItemStack stack = player.getItemInHand(hand);
		CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = custom == null ? new CompoundTag() : custom.copyTag();
		PlayerData data = PlayerStore.get(serverPlayer);

		String typeId = tag.getStringOr(SPELL_TYPE, "");
		if (!typeId.isEmpty()) {
			if (data.spellTypes.contains(typeId)) {
				serverPlayer.sendOverlayMessage(Component.literal("You already know this craft."));
				return InteractionResult.FAIL;
			}
			data.spellTypes.add(typeId);
			WordBank.SpellType type = WordBank.spellType(typeId);
			finishReading(serverPlayer, stack, Component.literal("You can now craft " + (type == null ? typeId : type.name) + ".").withStyle(ChatFormatting.LIGHT_PURPLE));
			return InteractionResult.SUCCESS;
		}

		Word word = WordBank.word(tag.getStringOr(WORD, ""));
		if (word != null && data.words.contains(word.id)) word = null; // already known: fall through to a random word
		if (word == null) word = pickUnknown(data, tag.getIntOr(RARITY, word == null && tag.contains(WORD) ? 4 : 1), serverPlayer.getRandom());

		if (word == null) {
			// The dictionary is complete: the scroll's magic becomes essence instead.
			data.essence = Math.min(Omnispell.CONFIG.essenceCap, data.essence + 15);
			finishReading(serverPlayer, stack, Component.literal("You know every word this scroll could hold. Its magic becomes essence.").withStyle(ChatFormatting.AQUA));
			return InteractionResult.SUCCESS;
		}
		data.words.add(word.id);
		finishReading(serverPlayer, stack, Component.literal("Learned " + word.spelling + " (" + word.shortName() + ", " + word.type + ").").withStyle(ChatFormatting.LIGHT_PURPLE));
		return InteractionResult.SUCCESS;
	}

	private static void finishReading(ServerPlayer player, ItemStack stack, Component message) {
		stack.shrink(1);
		player.sendSystemMessage(message);
		player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
		PlayerStore.save(player);
		PlayerStore.syncState(player);
	}

	/** An unknown word of the given rarity; if none are left, the nearest rarity that still has one. */
	public static Word pickUnknown(PlayerData data, int rarity, RandomSource random) {
		for (int distance = 0; distance <= 5; distance++) {
			for (int sign = -1; sign <= 1; sign += 2) {
				int r = rarity + sign * distance;
				if (r < 1 || r > 5) continue;
				List<Word> pool = new ArrayList<>();
				for (Word word : WordBank.words()) {
					if (word.rarity == r && !word.excluded && !data.words.contains(word.id)) pool.add(word);
				}
				if (!pool.isEmpty()) return pool.get(random.nextInt(pool.size()));
				if (distance == 0) break;
			}
		}
		return null;
	}
}
