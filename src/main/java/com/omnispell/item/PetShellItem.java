package com.omnispell.item;

import com.omnispell.Omnispell;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * A Pet Shell: four nautilus shells worked into one that holds a single tamed animal. Use it on
 * an animal you have tamed and the animal goes inside, whole (its name, health, collar, saddle,
 * chests and all), and the shell takes its name. Use the full shell on the ground to let it out.
 * A shell is an ordinary item: it goes in chests and bundles, the Soul Bind bundle included.
 */
public class PetShellItem extends Item {
	private static final String PET = "omni_pet";

	public PetShellItem(Properties properties) {
		super(properties);
	}

	public static boolean full(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(PET);
	}

	/** Is this an animal the player has tamed? Wolves, cats, parrots and the like, and tamed horses, donkeys, llamas and camels. */
	public static boolean tamedBy(Player player, Entity entity) {
		if (com.omnispell.familiar.Familiars.isFamiliar(entity)) return false; // a familiar has a pocket of its own
		if (entity instanceof TamableAnimal pet) return pet.isTame() && pet.isOwnedBy(player);
		if (entity instanceof AbstractHorse horse) {
			if (!horse.isTamed()) return false;
			var owner = horse.getOwnerReference();
			return owner == null || player.getUUID().equals(owner.getUUID());
		}
		return false;
	}

	/** Called before the animal's own right-click (sitting, mounting), so the shell comes first. */
	public static InteractionResult capture(Player user, InteractionHand hand, Entity entity) {
		ItemStack stack = user.getItemInHand(hand);
		if (!stack.is(OmniItems.PET_SHELL)) return InteractionResult.PASS;
		if (!(user instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
		if (full(stack)) {
			player.sendOverlayMessage(Component.literal("This shell is already home to something. Use it on the ground to let it out."));
			return InteractionResult.SUCCESS;
		}
		if (!(entity instanceof LivingEntity living) || !living.isAlive() || !tamedBy(player, entity)) {
			player.sendOverlayMessage(Component.literal(com.omnispell.familiar.Familiars.isFamiliar(entity) ? "A familiar goes in its own pocket, not a shell." : "The shell only takes an animal you have tamed."));
			return InteractionResult.SUCCESS;
		}
		ServerLevel level = player.level();
		living.ejectPassengers();
		living.stopRiding();
		if (living instanceof Leashable leashed && leashed.isLeashed()) leashed.dropLeash();
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		if (!living.save(out)) {
			player.sendOverlayMessage(Component.literal("The shell cannot hold it."));
			return InteractionResult.SUCCESS;
		}
		CompoundTag pet = out.buildResult();
		String name = living.getDisplayName().getString();
		String kind = living.getType().getDescription().getString();
		CompoundTag tag = new CompoundTag();
		tag.put(PET, pet);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Shell of " + name));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal(kind + " · " + Math.round(living.getHealth()) + " of " + Math.round(living.getMaxHealth()) + " health").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)),
				Component.literal("Use on the ground to let it out.").withStyle(s -> s.withColor(ChatFormatting.DARK_AQUA).withItalic(true)))));
		level.sendParticles(ParticleTypes.BUBBLE_POP, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(), 20, 0.3, 0.4, 0.3, 0.02);
		level.playSound(null, living.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 1.0f, 0.8f);
		living.discard();
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		if (!full(stack)) return InteractionResult.PASS;
		if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag pet = data.copyTag().getCompoundOrEmpty(PET);
		BlockPos at = context.getClickedPos().relative(context.getClickedFace());
		Entity entity;
		try {
			entity = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), pet), level, EntitySpawnReason.BUCKET, EntityProcessor.NOP);
		} catch (RuntimeException e) {
			Omnispell.LOG.warn("A pet shell could not be opened", e);
			entity = null;
		}
		if (entity == null) {
			player.sendOverlayMessage(Component.literal("The shell will not open here."));
			return InteractionResult.SUCCESS;
		}
		if (level.getEntity(entity.getUUID()) != null) {
			player.sendOverlayMessage(Component.literal("It is already out in the world."));
			return InteractionResult.SUCCESS;
		}
		entity.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0f);
		entity.setDeltaMovement(0, 0, 0);
		entity.fallDistance = 0;
		if (!level.addFreshEntity(entity)) {
			player.sendOverlayMessage(Component.literal("The shell will not open here."));
			return InteractionResult.SUCCESS;
		}
		// The shell is empty again, and plain.
		stack.remove(DataComponents.CUSTOM_DATA);
		stack.remove(DataComponents.ITEM_NAME);
		stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
		stack.remove(DataComponents.LORE);
		level.sendParticles(ParticleTypes.BUBBLE_POP, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 20, 0.3, 0.4, 0.3, 0.02);
		level.playSound(null, at, SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.PLAYERS, 1.0f, 0.8f);
		return InteractionResult.SUCCESS;
	}
}
