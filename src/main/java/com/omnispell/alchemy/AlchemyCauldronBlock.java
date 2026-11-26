package com.omnispell.alchemy;

import com.omnispell.data.WordBank;
import com.omnispell.item.OmniItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The alchemy cauldron. No screen and no minigame: the craft is in what you
 * gather and combine.
 *
 *  1. Charge it with blaze powder (four brews each).
 *  2. Pour in a liquid: water, honey, milk, lava or dragon's breath.
 *  3. Add up to three ingredients, and up to two catalysts (lapis, honeycomb,
 *     redstone, fermented spider eye, sugar, prismarine crystals).
 *  4. What burns underneath sets the heat: fire, campfire, lava or magma is
 *     high; soul fire, soul campfire or ice is low; anything else is room.
 *  5. Use a glass bottle to draw the potion: three bottles per brew.
 *
 * Empty hand shows what is inside. Sneak + empty hand tips it out.
 */
public class AlchemyCauldronBlock extends Block {
	public static final IntegerProperty CONTENTS = IntegerProperty.create("contents", 0, 5);
	public static final int MAX_PRIMARY = 3, MAX_CATALYST = 2, MAX_CHARGES = 16;

	public AlchemyCauldronBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(CONTENTS, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CONTENTS);
	}

	/** The heat under a cauldron: high, low or room. */
	public static String heat(ServerLevel level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		if (below.is(Blocks.SOUL_FIRE) || (below.is(Blocks.SOUL_CAMPFIRE) && below.getValue(CampfireBlock.LIT))
				|| below.is(Blocks.ICE) || below.is(Blocks.PACKED_ICE) || below.is(Blocks.BLUE_ICE) || below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.POWDER_SNOW)) return "low";
		if (below.is(Blocks.FIRE) || (below.is(Blocks.CAMPFIRE) && below.getValue(CampfireBlock.LIT))
				|| below.is(Blocks.LAVA) || below.is(Blocks.MAGMA_BLOCK)) return "high";
		return "room";
	}

	private static void say(ServerPlayer player, String text, ChatFormatting color) {
		player.sendOverlayMessage(Component.literal(text).withStyle(color));
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
	}

	private static void swap(ServerPlayer player, ItemStack held, Item container) {
		if (player.getAbilities().instabuild) return;
		held.shrink(1);
		if (container != null) give(player, new ItemStack(container));
	}

	private void look(ServerLevel level, BlockPos pos, Vats.Vat vat) {
		Alchemy.Base base = vat.base == null ? null : Alchemy.base(vat.base);
		BlockState state = level.getBlockState(pos);
		int want = base == null ? 0 : base.model;
		if (state.getValue(CONTENTS) != want) level.setBlockAndUpdate(pos, state.setValue(CONTENTS, want));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player user, InteractionHand hand, BlockHitResult hit) {
		if (!(user instanceof ServerPlayer player) || !(world instanceof ServerLevel level)) return InteractionResult.SUCCESS;
		Vats.Vat vat = Vats.at(level, pos);
		if (vat.brewing) {
			say(player, "The brew is still settling...", ChatFormatting.GRAY);
			return InteractionResult.SUCCESS;
		}

		if (stack.is(Items.BLAZE_POWDER)) {
			if (vat.charges > MAX_CHARGES - 4) {
				say(player, "The cauldron is fully charged.", ChatFormatting.GRAY);
				return InteractionResult.SUCCESS;
			}
			vat.charges += 4;
			swap(player, stack, null);
			level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.5f, 1.4f);
			level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.01);
			say(player, "Charged: " + vat.charges + " brews.", ChatFormatting.GOLD);
			Vats.save(level.getServer());
			return InteractionResult.SUCCESS;
		}

		if (stack.is(Items.GLASS_BOTTLE)) return bottle(level, pos, player, stack, vat);

		// A liquid, if the cauldron is empty.
		if (vat.base == null) {
			String base = null;
			Item container = null;
			if (stack.is(Items.WATER_BUCKET)) {
				base = "water";
				container = Items.BUCKET;
			} else if (stack.is(Items.POTION)) {
				base = "water";
				container = Items.GLASS_BOTTLE;
			} else if (stack.is(Items.HONEY_BOTTLE)) {
				base = "honey";
				container = Items.GLASS_BOTTLE;
			} else if (stack.is(Items.MILK_BUCKET)) {
				base = "milk";
				container = Items.BUCKET;
			} else if (stack.is(Items.LAVA_BUCKET)) {
				base = "lava";
				container = Items.BUCKET;
			} else if (stack.is(Items.DRAGON_BREATH)) {
				base = "dragon";
				container = Items.GLASS_BOTTLE;
			}
			if (base != null) {
				vat.base = base;
				vat.items.clear();
				swap(player, stack, container);
				level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8f, 1.0f);
				look(level, pos, vat);
				say(player, Alchemy.base(base).name + " fills the cauldron. Heat: " + heat(level, pos) + ".", ChatFormatting.AQUA);
				Vats.save(level.getServer());
				return InteractionResult.SUCCESS;
			}
		}

		Alchemy.Ingredient ingredient = Alchemy.ingredient(stack);
		if (ingredient == null) {
			describe(level, pos, player, vat);
			return InteractionResult.SUCCESS;
		}
		if (vat.base == null) {
			say(player, "Pour in a liquid first: water, honey, milk, lava or dragon's breath.", ChatFormatting.GRAY);
			return InteractionResult.SUCCESS;
		}
		if (vat.servings > 0) {
			say(player, "Bottle the finished potion first (" + vat.servings + " left).", ChatFormatting.GRAY);
			return InteractionResult.SUCCESS;
		}
		int primaries = 0, catalysts = 0;
		for (String key : vat.items) {
			Alchemy.Ingredient in = Alchemy.ingredient(key);
			if (in != null && in.role != null) catalysts++;
			else primaries++;
		}
		if (ingredient.role != null ? catalysts >= MAX_CATALYST : primaries >= MAX_PRIMARY) {
			say(player, ingredient.role != null ? "The brew can take no more catalysts." : "The brew can take no more ingredients (three).", ChatFormatting.GRAY);
			return InteractionResult.SUCCESS;
		}
		vat.items.add(Alchemy.key(stack));
		swap(player, stack, stack.is(Items.HONEY_BOTTLE) || stack.is(Items.EXPERIENCE_BOTTLE) ? Items.GLASS_BOTTLE : null);
		level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4f, 1.3f);
		level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.2, 0.05, 0.2, 0.0);
		say(player, stack.getHoverName().getString() + " goes in" + (ingredient.role != null ? " (" + ingredient.role.replace('_', ' ') + ")" : "") + ". " + vat.items.size() + " in the brew.", ChatFormatting.GREEN);
		Vats.save(level.getServer());
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player user, BlockHitResult hit) {
		if (!(user instanceof ServerPlayer player) || !(world instanceof ServerLevel level)) return InteractionResult.SUCCESS;
		Vats.Vat vat = Vats.at(level, pos);
		if (player.isShiftKeyDown() && !vat.brewing) {
			if (vat.base == null) return InteractionResult.SUCCESS;
			vat.base = null;
			vat.items.clear();
			vat.servings = 0;
			vat.brew = null;
			look(level, pos, vat);
			level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8f, 0.7f);
			say(player, "You tip the cauldron out.", ChatFormatting.GRAY);
			Vats.save(level.getServer());
			return InteractionResult.SUCCESS;
		}
		describe(level, pos, player, vat);
		return InteractionResult.SUCCESS;
	}

	private static void describe(ServerLevel level, BlockPos pos, ServerPlayer player, Vats.Vat vat) {
		if (vat.base == null) {
			say(player, "Empty. Charges: " + vat.charges + ". Pour in water, honey, milk, lava or dragon's breath.", ChatFormatting.GRAY);
			return;
		}
		if (vat.servings > 0) {
			Brew brew = WordBank.GSON.fromJson(vat.brew, Brew.class);
			say(player, brew.name + ": " + vat.servings + " bottle" + (vat.servings == 1 ? "" : "s") + " left. Use a glass bottle.", ChatFormatting.LIGHT_PURPLE);
			return;
		}
		StringBuilder sb = new StringBuilder(Alchemy.base(vat.base).name);
		for (String key : vat.items) {
			Alchemy.Ingredient in = Alchemy.ingredient(key);
			sb.append(" + ").append(in == null ? key : in.name);
		}
		say(player, sb + "  ·  heat " + heat(level, pos) + "  ·  charges " + vat.charges, ChatFormatting.AQUA);
	}

	/** Problems the potioneer should know about: heat that does not suit an ingredient, flora and fauna mixed raw. */
	static List<String> warnings(Alchemy.Base base, List<Alchemy.Ingredient> ingredients, String heat) {
		List<String> warnings = new ArrayList<>();
		boolean negated = false, harmonized = false, flora = false, fauna = false;
		for (Alchemy.Ingredient in : ingredients) {
			if ("temperature_negator".equals(in.role)) negated = true;
			if ("harmonizer".equals(in.role)) harmonized = true;
			if (in.role == null && in.category.equals("flora")) flora = true;
			if (in.role == null && in.category.equals("fauna")) fauna = true;
		}
		String brewHeat = heat.equals("room") ? base.temperature : heat;
		if (!negated) {
			for (Alchemy.Ingredient in : ingredients) {
				if (in.role == null && !in.temperature.equals("room") && !in.temperature.equals(brewHeat)) {
					warnings.add(in.name + " prefers " + in.temperature + " heat but the brew is at " + brewHeat + " heat");
				}
			}
		}
		if (flora && fauna && !harmonized) warnings.add("Flora and fauna are mixed without a harmonizer");
		return warnings;
	}

	private InteractionResult bottle(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack bottles, Vats.Vat vat) {
		if (vat.servings > 0) {
			Brew brew = WordBank.GSON.fromJson(vat.brew, Brew.class);
			vat.servings--;
			swap(player, bottles, null);
			give(player, BrewItem.of(brew));
			level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
			if (vat.servings == 0) {
				vat.base = null;
				vat.items.clear();
				vat.brew = null;
				look(level, pos, vat);
			}
			Vats.save(level.getServer());
			return InteractionResult.SUCCESS;
		}
		if (vat.base == null || vat.items.isEmpty()) {
			say(player, "There is nothing to bottle yet: a liquid and at least one ingredient.", ChatFormatting.GRAY);
			return InteractionResult.SUCCESS;
		}
		if (vat.charges <= 0 && !player.getAbilities().instabuild) {
			say(player, "The cauldron is cold. Charge it with blaze powder.", ChatFormatting.RED);
			return InteractionResult.SUCCESS;
		}
		Alchemy.Base base = Alchemy.base(vat.base);
		List<Alchemy.Ingredient> ingredients = new ArrayList<>();
		for (String key : vat.items) {
			Alchemy.Ingredient in = Alchemy.ingredient(key);
			if (in != null) ingredients.add(in);
		}
		String heat = heat(level, pos);
		List<String> warnings = warnings(base, ingredients, heat);
		boolean gunpowder = vat.items.contains("gunpowder");
		String key = PotionGenerator.recipeKey(vat.base, vat.items, heat);
		if (!player.getAbilities().instabuild) vat.charges--;
		vat.brewing = true;
		say(player, "The brew begins to turn...", ChatFormatting.LIGHT_PURPLE);
		level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0f, 1.0f);
		MinecraftServer server = level.getServer();
		UUID uuid = player.getUUID();
		BlockPos at = pos.immutable();
		PotionGenerator.brew(key, base, ingredients, warnings).whenComplete((brew, error) -> server.execute(() -> {
			vat.brewing = false;
			Brew result = brew;
			if (error != null || result == null) {
				result = new Brew();
				PotionGenerator.fallback(result, base, ingredients, warnings);
			}
			result.decideSplash(gunpowder);
			vat.brew = WordBank.GSON.toJson(result);
			vat.servings = 3;
			Vats.save(server);
			level.sendParticles(ParticleTypes.WITCH, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, 24, 0.25, 0.3, 0.25, 0.02);
			level.playSound(null, at, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0f, 1.5f);
			ServerPlayer online = server.getPlayerList().getPlayer(uuid);
			if (online != null) {
				online.sendSystemMessage(Component.literal("You have brewed " + result.name + ". " + result.description).withStyle(ChatFormatting.LIGHT_PURPLE));
				if (!warnings.isEmpty()) online.sendSystemMessage(Component.literal("Trouble in the brew: " + String.join("; ", warnings) + ".").withStyle(ChatFormatting.GOLD));
				if (result.offline) online.sendSystemMessage(Component.literal("The potioneer could not be reached; this is a plain brew.").withStyle(ChatFormatting.GRAY));
			}
		}));
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server) Vats.remove(server, pos);
		return super.playerWillDestroy(level, pos, state, player);
	}
}
