package com.omnispell;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.omnispell.data.Word;
import com.omnispell.data.WordBank;
import com.omnispell.item.OmniItems;
import com.omnispell.item.WandItem;
import com.omnispell.item.WandParts;
import com.omnispell.item.WordScrollItem;
import com.omnispell.player.ManaPool;
import com.omnispell.player.Mana;
import com.omnispell.player.PlayerData;
import com.omnispell.player.PlayerStore;
import com.omnispell.spell.SpellCrafting;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * /omnispell — operator commands for testing and for fixing things:
 * kit, words all, word NAME, types, essence N, mana, scroll RARITY,
 * wand WOOD CORE, craft TYPE WORD,WORD,..., reset.
 */
public final class OmniCommands {
	private OmniCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("omnispell").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("kit").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							give(player, WandItem.create("oak", "flint"));
							give(player, new ItemStack(OmniItems.GRIMOIRE));
							ctx.getSource().sendSuccess(() -> Component.literal("Starter wand and book given."), false);
							return 1;
						}))
						.then(Commands.literal("words").then(Commands.literal("all").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							PlayerData data = PlayerStore.get(player);
							for (Word word : WordBank.words()) {
								if (!word.excluded) data.words.add(word.id);
							}
							changed(player);
							ctx.getSource().sendSuccess(() -> Component.literal("You now know every word."), false);
							return 1;
						})))
						.then(Commands.literal("word").then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							String name = StringArgumentType.getString(ctx, "name");
							int learned = 0;
							for (Word word : WordBank.words()) {
								// Operators may also give the moderator-only words, such as Infinity.
								if (word.id.equalsIgnoreCase(name) || word.english.equalsIgnoreCase(name) || word.shortName().equalsIgnoreCase(name) || word.spelling.equalsIgnoreCase(name)) {
									PlayerStore.get(player).words.add(word.id);
									learned++;
								}
							}
							changed(player);
							int count = learned;
							ctx.getSource().sendSuccess(() -> Component.literal(count == 0 ? "No such word." : "Learned " + count + " word(s)."), false);
							return learned;
						})))
						.then(Commands.literal("types").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							for (WordBank.SpellType type : WordBank.spellTypes()) PlayerStore.get(player).spellTypes.add(type.id);
							changed(player);
							ctx.getSource().sendSuccess(() -> Component.literal("Every kind of spell unlocked."), false);
							return 1;
						}))
						.then(Commands.literal("essence").then(Commands.argument("amount", IntegerArgumentType.integer(0, 100000)).executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							PlayerStore.get(player).essence = IntegerArgumentType.getInteger(ctx, "amount");
							changed(player);
							return 1;
						})))
						.then(Commands.literal("mana").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							PlayerData data = PlayerStore.get(player);
							for (ManaPool pool : ManaPool.values()) data.mana[pool.ordinal()] = Mana.max(player, pool);
							data.corruption = 0;
							changed(player);
							return 1;
						}))
						.then(Commands.literal("scroll").then(Commands.argument("rarity", IntegerArgumentType.integer(1, 5)).executes(ctx -> {
							give(ctx.getSource().getPlayerOrException(), WordScrollItem.ofRarity(IntegerArgumentType.getInteger(ctx, "rarity")));
							return 1;
						})))
						.then(Commands.literal("wand").then(Commands.argument("wood", StringArgumentType.word()).then(Commands.argument("core", StringArgumentType.word()).executes(ctx -> {
							String wood = StringArgumentType.getString(ctx, "wood");
							String core = StringArgumentType.getString(ctx, "core");
							if (!WandParts.WOODS.containsKey(wood) || !WandParts.CORES.containsKey(core)) {
								ctx.getSource().sendFailure(Component.literal("Woods: " + WandParts.WOODS.keySet() + "  Cores: " + WandParts.CORES.keySet()));
								return 0;
							}
							give(ctx.getSource().getPlayerOrException(), WandItem.create(wood, core));
							return 1;
						}))))
						.then(Commands.literal("craft").then(Commands.argument("type", StringArgumentType.word()).then(Commands.argument("words", StringArgumentType.greedyString()).executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							String[] ids = StringArgumentType.getString(ctx, "words").split("[, ]+");
							SpellCrafting.craft(player, StringArgumentType.getString(ctx, "type"), Arrays.asList(ids));
							return 1;
						}))))
						.then(Commands.literal("save").executes(ctx -> {
							SpellCrafting.savePending(ctx.getSource().getPlayerOrException());
							return 1;
						}))
						.then(Commands.literal("reset").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							PlayerData data = PlayerStore.get(player);
							data.words.clear();
							data.spellTypes.clear();
							data.spells.clear();
							data.pending = null;
							data.bosses.clear();
							data.initialized = false;
							PlayerStore.unload(player);
							PlayerStore.get(player);
							changed(player);
							ctx.getSource().sendSuccess(() -> Component.literal("Your Omnispell progress is reset."), false);
							return 1;
						}))
		));
	}

	private static void changed(ServerPlayer player) {
		PlayerStore.save(player);
		PlayerStore.syncState(player);
		PlayerStore.syncVitals(player);
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
	}
}
