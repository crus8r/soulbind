package com.omnispell.alchemy;

import com.omnispell.Omnispell;
import com.omnispell.data.WordBank;
import com.omnispell.item.OmniItems;
import com.omnispell.player.ManaPool;
import com.omnispell.player.Mana;
import com.omnispell.player.PlayerStore;
import com.omnispell.spell.Fx;
import com.omnispell.spell.MobControl;
import com.omnispell.spell.Modifiers;
import com.omnispell.spell.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** A potion from the alchemy cauldron. Use it to drink it, or to throw it if it is that kind of potion. */
public class BrewItem extends Item {
	private static final String DATA = "omni_brew";
	private static final net.minecraft.resources.Identifier SHIELD_ID = Omnispell.id("potion_shield");

	public BrewItem(Properties properties) {
		super(properties);
	}

	public static ItemStack of(Brew brew) {
		ItemStack stack = new ItemStack(OmniItems.BREW);
		CompoundTag tag = new CompoundTag();
		tag.putString(DATA, WordBank.GSON.toJson(brew));
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		stack.set(DataComponents.ITEM_NAME, Component.literal(brew.name));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(brew.color())));
		stack.set(DataComponents.RARITY, brew.tier >= 5 ? Rarity.EPIC : brew.tier >= 4 ? Rarity.RARE : brew.tier >= 3 ? Rarity.UNCOMMON : Rarity.COMMON);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("Tier " + brew.tier + " · " + (brew.splash ? "Throw" : "Drink")).withStyle(s -> s.withColor(brew.color()).withItalic(false)));
		lines.add(Component.literal(brew.effect).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)));
		if (brew.downside != null && !brew.downside.isBlank()) lines.add(Component.literal(brew.downside).withStyle(s -> s.withColor(ChatFormatting.RED).withItalic(true)));
		stack.set(DataComponents.LORE, new ItemLore(lines));
		return stack;
	}

	public static Brew read(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		String json = data.copyTag().getStringOr(DATA, "");
		try {
			return json.isEmpty() ? null : WordBank.GSON.fromJson(json, Brew.class);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public InteractionResult use(Level world, Player user, InteractionHand hand) {
		if (!(user instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
		ItemStack stack = player.getItemInHand(hand);
		Brew brew = read(stack);
		if (brew == null) return InteractionResult.FAIL;
		ServerLevel level = player.level();
		if (brew.splash) {
			Vec3 at = Targeting.point(player, 14);
			Fx.line(level, Targeting.hand(player), at, brew.color(), null, 1);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
			Fx.burst(level, at.add(0, 0.5, 0), brew.color(), ParticleTypes.SPLASH, 3);
			Fx.impact(level, at, brew.element, brew.tier);
			for (LivingEntity target : Targeting.around(player, at, 3.5, false)) apply(player, brew, target);
		} else {
			apply(player, brew, player);
			level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
			Fx.helix(level, player.position(), 2.0, 0.6, brew.color(), null);
		}
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
			if (!brew.splash) {
				ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
				if (!player.getInventory().add(bottle)) player.drop(bottle, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		if (brew.downside != null && !brew.downside.isBlank() && !brew.splash) {
			// An unstable brew bites back a little.
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0));
			player.sendOverlayMessage(Component.literal(brew.downside).withStyle(ChatFormatting.RED));
		}
		PlayerStore.syncVitals(player);
		return InteractionResult.SUCCESS;
	}

	private static ManaPool pool(String manaType) {
		if (manaType == null) return null;
		return switch (manaType) {
			case "shield_generated" -> ManaPool.BARRIER;
			case "healing_done" -> ManaPool.RESTORATION;
			case "control_turns" -> ManaPool.CONTROL;
			case "damage_reflected" -> ManaPool.MIRROR;
			case "evasion_dodges" -> ManaPool.PHASE;
			default -> null;
		};
	}

	/** Carries out a potion on whoever drank it or was splashed by it. Numbers are the potioneer's, scaled like spells. */
	static void apply(ServerPlayer user, Brew brew, LivingEntity target) {
		ServerLevel level = user.level();
		double scale = Omnispell.CONFIG.valueScale;
		int turns = Math.max(brew.duration, 0);
		int ticks = Omnispell.CONFIG.turnsToTicks(Math.max(1, turns));
		double value = brew.value > 0 ? brew.value : brew.tier * 8;
		ServerPlayer targetPlayer = target instanceof ServerPlayer p ? p : null;
		switch (brew.codeType) {
			case "sludge" -> {
				target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 0));
				target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 0));
			}
			case "heal" -> {
				// Healing lands at once; a duration on a healing potion belongs to its status (regen, fortify...).
				target.heal((float) (value * scale));
				level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight(), target.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
			}
			case "damage" -> {
				float amount = (float) (value * scale);
				Modifiers.withSpellElement(brew.element, () -> target.hurtServer(level, level.damageSources().indirectMagic(user, user), amount));
				if (brew.element.equals("fire")) target.igniteForSeconds(2 + brew.tier);
				if (brew.element.equals("ice")) target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1), user);
				if (turns > 0) MobControl.dot(user, target, Math.max(1f, amount / 4f), turns, brew.element);
			}
			case "shield" -> {
				AttributeInstance max = target.getAttribute(Attributes.MAX_ABSORPTION);
				if (max != null) {
					float amount = Math.max((float) (value * scale), target.getAbsorptionAmount());
					max.addOrUpdateTransientModifier(new AttributeModifier(SHIELD_ID, amount, AttributeModifier.Operation.ADD_VALUE));
					target.setAbsorptionAmount(amount);
					com.omnispell.spell.Tasks.later(ticks, () -> {
						AttributeInstance again = target.getAttribute(Attributes.MAX_ABSORPTION);
						if (again != null) again.removeModifier(SHIELD_ID);
						target.setAbsorptionAmount(Math.min(target.getAbsorptionAmount(), target.getMaxAbsorption()));
					});
				}
			}
			case "resist" -> {
				String type = brew.resistType == null ? brew.element : brew.resistType;
				if (targetPlayer != null) Modifiers.add(targetPlayer, type + "_resistance", Math.min(90, value), ticks);
				if (type.equals("fire")) target.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, ticks, 0));
				else if (targetPlayer == null) target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks, 0));
			}
			case "debuff" -> {
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, brew.tier >= 4 ? 1 : 0), user);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, brew.tier >= 3 ? 1 : 0), user);
			}
			case "mana_restore" -> {
				ManaPool pool = pool(brew.manaType);
				if (targetPlayer != null) {
					double[] mana = PlayerStore.get(targetPlayer).mana;
					for (ManaPool each : ManaPool.values()) {
						if (pool != null && each != pool) continue;
						mana[each.ordinal()] = Math.min(Mana.max(targetPlayer, each), mana[each.ordinal()] + (pool != null ? value : value / 3));
					}
				}
			}
			case "mana_drain" -> {
				ManaPool pool = pool(brew.manaType);
				if (targetPlayer != null && pool != null) {
					double[] mana = PlayerStore.get(targetPlayer).mana;
					mana[pool.ordinal()] = Math.max(0, mana[pool.ordinal()] - value);
				} else {
					target.hurtServer(level, level.damageSources().indirectMagic(user, user), (float) (value * scale * 0.5));
				}
			}
			case "status" -> {
			}
			default -> { // buff
				if (targetPlayer != null) Modifiers.add(targetPlayer, "damage_amplify", Math.min(100, value), ticks);
				else target.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, 0));
			}
		}
		switch (brew.status == null ? "none" : brew.status) {
			case "poison" -> target.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, brew.tier >= 4 ? 1 : 0), user);
			case "regen" -> target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks, brew.tier >= 4 ? 1 : 0));
			case "haste" -> {
				target.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, brew.tier >= 4 ? 1 : 0));
				target.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks, 0));
			}
			case "fortify" -> target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks, brew.tier >= 4 ? 1 : 0));
			default -> {
			}
		}
	}
}
