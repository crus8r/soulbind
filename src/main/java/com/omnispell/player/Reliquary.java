package com.omnispell.player;

import com.omnispell.Omnispell;
import com.omnispell.item.OmniItems;
import com.omnispell.net.OmniNet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * The Reliquary: a bundle bound to a totem. Carried loose in your inventory, it does nothing
 * until you truly die. Then, of everything that falls from you (never what Soul Bind keeps:
 * the bound bundle, the book, the wizard's hat and robes, a totem wand), it catches every
 * item, and half the experience you would have lost, and lies where you fell. It does not
 * burn, blast apart or despawn, and only you can pick it up. When you do, it gives it all
 * back, filling your inventory and setting the rest at your feet. A diamond on your
 * experience bar points to it, or, from another realm, to the portal that leads there.
 *
 * It holds six deaths, and a bundle on an anvil mends one.
 */
public final class Reliquary {
	private Reliquary() {
	}

	public static final int USES = 6;
	private static final String DATA = "omni_reliquary";

	// ------------------------------------------------------------------ the catch at death

	private static final ThreadLocal<List<ItemEntity>> CAPTURE = new ThreadLocal<>();
	private static final Set<UUID> TOOK_XP = new HashSet<>();

	public static boolean capturing() {
		return CAPTURE.get() != null;
	}

	/** An item falling from the dying player: held back for now. */
	public static void captured(ItemEntity item) {
		CAPTURE.get().add(item);
	}

	/** True if the Reliquary took this player's experience, so no orbs fall. Cleared on respawn. */
	public static boolean tookXp(net.minecraft.world.entity.player.Player player) {
		return TOOK_XP.contains(player.getUUID());
	}

	public static void respawned(ServerPlayer player) {
		TOOK_XP.remove(player.getUUID());
	}

	/** Called as a dying player's things are about to fall. */
	public static void begin(ServerPlayer player) {
		if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) return;
		CAPTURE.set(new ArrayList<>());
	}

	private static boolean usable(ItemStack stack) {
		return stack.is(OmniItems.RELIQUARY) && stack.getDamageValue() < USES && !holds(stack);
	}

	/** Everything has fallen. If a Reliquary fell among it, it gathers the rest up; if not, they fall as they always do. */
	public static void end(ServerPlayer player) {
		List<ItemEntity> caught = CAPTURE.get();
		CAPTURE.remove();
		if (caught == null) return;
		ServerLevel level = player.level();
		try {
			int which = -1;
			for (int i = 0; i < caught.size(); i++) {
				if (usable(caught.get(i).getItem())) {
					which = i;
					break;
				}
			}
			if (which < 0) {
				for (ItemEntity item : caught) level.addFreshEntity(item);
				return;
			}
			ItemStack charm = caught.get(which).getItem().copy();
			List<ItemStack> things = new ArrayList<>();
			for (int i = 0; i < caught.size(); i++) if (i != which && !caught.get(i).getItem().isEmpty()) things.add(caught.get(i).getItem().copy());
			if (things.size() > ItemContainerContents.MAX_SIZE) {
				// More than it can hold: the rest falls as usual.
				for (int i = ItemContainerContents.MAX_SIZE; i < things.size(); i++) player.drop(things.get(i), true, net.minecraft.util.Prediction.SERVER_ONLY);
				things = new ArrayList<>(things.subList(0, ItemContainerContents.MAX_SIZE));
			}
			// Half of the experience that would have been lost (what Soul Bind armor keeps is kept as before).
			int lost = Math.max(0, com.soulbind.SoulBind.totalXp(player) - com.soulbind.SoulBind.keptXp(player));
			int xp = lost / 2;
			TOOK_XP.add(player.getUUID());

			charm.setDamageValue(charm.getDamageValue() + 1);
			charm.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(things));
			String id = UUID.randomUUID().toString();
			CompoundTag tag = charm.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
			CompoundTag mark = new CompoundTag();
			mark.putString("owner", player.getUUID().toString());
			mark.putString("name", player.getName().getString());
			mark.putInt("xp", xp);
			mark.putString("id", id);
			tag.put(DATA, mark);
			charm.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			charm.set(DataComponents.ITEM_NAME, Component.literal(player.getName().getString() + "'s Reliquary"));

			Vec3 at = player.position();
			double y = Math.max(at.y, level.getMinY() + 1);
			ItemEntity token = new ItemEntity(level, at.x, y + 0.2, at.z, charm);
			token.setDeltaMovement(Vec3.ZERO);
			token.setUnlimitedLifetime();
			token.setPermanentlyInvulnerable(true); // no blast, cactus or lava takes it
			token.setTarget(player.getUUID());
			if (at.y < level.getMinY() + 1) token.setNoGravity(true); // it will not fall into the void
			token.setGlowingTag(false);
			level.addFreshEntity(token);
			level.sendParticles(ParticleTypes.SOUL, at.x, y + 0.6, at.z, 20, 0.3, 0.4, 0.3, 0.02);

			PlayerData data = PlayerStore.get(player);
			data.reliquaries.add(new PlayerData.Cache(id, token.getUUID().toString(), level.dimension().identifier().toString(), token.getX(), token.getY(), token.getZ()));
			int left = USES - charm.getDamageValue();
			player.sendSystemMessage(Component.literal("Your Reliquary holds your things at " + (int) Math.floor(token.getX()) + " " + (int) Math.floor(token.getY()) + " " + (int) Math.floor(token.getZ())
					+ " (" + realmName(level.dimension()) + "). It has " + left + (left == 1 ? " use" : " uses") + " left.").withStyle(ChatFormatting.LIGHT_PURPLE));
		} catch (Exception e) {
			Omnispell.LOG.error("The Reliquary failed to catch a death's items; letting them fall", e);
			for (ItemEntity item : caught) if (!item.isRemoved() && level.getEntity(item.getUUID()) == null) level.addFreshEntity(item);
		}
	}

	private static String realmName(ResourceKey<Level> key) {
		if (key == Level.OVERWORLD) return "the Overworld";
		if (key == Level.NETHER) return "the Nether";
		if (key == Level.END) return "the End";
		if (key == com.omnispell.realm.Realms.FOREST) return "the Enchanted Forest";
		return key.identifier().getPath().replace('_', ' ');
	}

	// ------------------------------------------------------------------ giving it back

	/** True if a Reliquary is carrying someone's things. */
	public static boolean holds(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(DATA);
	}

	public static boolean ownedBy(ItemStack stack, ServerPlayer player) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return false;
		return data.copyTag().getCompoundOrEmpty(DATA).getStringOr("owner", "").equals(player.getUUID().toString());
	}

	/** The owner picks it up: everything comes back, into the inventory as far as it goes, the rest at their feet. */
	public static ItemStack spill(ServerPlayer player, ItemStack charm) {
		CompoundTag tag = charm.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		CompoundTag mark = tag.getCompoundOrEmpty(DATA);
		int xp = mark.getIntOr("xp", 0);
		String id = mark.getStringOr("id", "");
		ItemContainerContents contents = charm.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		List<ItemStack> things = contents.nonEmptyItemCopyStream().toList();
		ItemStack empty = charm.copy();
		tag.remove(DATA);
		if (tag.isEmpty()) empty.remove(DataComponents.CUSTOM_DATA);
		else empty.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		empty.remove(DataComponents.CONTAINER);
		empty.remove(DataComponents.ITEM_NAME);
		for (ItemStack thing : things) {
			// Armor goes back on, where the slot is free.
			var equippable = thing.get(DataComponents.EQUIPPABLE);
			if (equippable != null && equippable.slot().isArmor() && player.getItemBySlot(equippable.slot()).isEmpty()) {
				player.setItemSlot(equippable.slot(), thing);
				continue;
			}
			if (!player.getInventory().add(thing)) player.drop(thing, false, net.minecraft.util.Prediction.SERVER_ONLY);
			else if (!thing.isEmpty()) player.drop(thing, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		if (xp > 0) player.giveExperiencePoints(xp);
		PlayerStore.get(player).reliquaries.removeIf(c -> c.id.equals(id));
		player.level().playSound(null, player.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1.0f, 0.8f);
		player.level().sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 16, 0.4, 0.5, 0.4, 0.02);
		int left = USES - empty.getDamageValue();
		player.sendOverlayMessage(Component.literal("Your Reliquary gives back what it kept. " + (left <= 0 ? "It is spent: mend it with a bundle on an anvil." : left + (left == 1 ? " use" : " uses") + " left."))
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		sendTarget(player);
		return empty;
	}

	// ------------------------------------------------------------------ finding it again

	/** Every two seconds: where each player's nearest Reliquary is, or the portal toward it. */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 0) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PlayerData data = PlayerStore.get(player);
			if (data.reliquaries.isEmpty() && !data.reliquaryShown) continue;
			refresh(server, player, data);
			sendTarget(player);
		}
	}

	/** Follows tokens that have drifted, and forgets ones that are plainly gone. */
	private static void refresh(MinecraftServer server, ServerPlayer player, PlayerData data) {
		for (Iterator<PlayerData.Cache> it = data.reliquaries.iterator(); it.hasNext(); ) {
			PlayerData.Cache cache = it.next();
			ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(cache.dimension)));
			if (level == null) continue;
			Entity token = level.getEntity(UUID.fromString(cache.entity));
			if (token != null) {
				cache.x = token.getX();
				cache.y = token.getY();
				cache.z = token.getZ();
			} else if (player.level() == level && player.position().distanceTo(new Vec3(cache.x, cache.y, cache.z)) < 6) {
				it.remove(); // standing right where it was, and it is not there
			}
		}
	}

	public static void sendTarget(ServerPlayer player) {
		PlayerData data = PlayerStore.get(player);
		Vec3 target = target(player, data);
		data.reliquaryShown = target != null;
		ServerPlayNetworking.send(player, new OmniNet.CacheTarget(target != null, target == null ? 0 : target.x, target == null ? 0 : target.y, target == null ? 0 : target.z));
	}

	/** Where the diamond should point: the nearest token in this realm, else the way to a token in another, else nowhere. */
	private static Vec3 target(ServerPlayer player, PlayerData data) {
		if (data.reliquaries.isEmpty()) return null;
		String here = player.level().dimension().identifier().toString();
		PlayerData.Cache best = null;
		double bestDistance = Double.MAX_VALUE;
		for (PlayerData.Cache cache : data.reliquaries) {
			if (!cache.dimension.equals(here)) continue;
			double d = player.position().distanceToSqr(cache.x, cache.y, cache.z);
			if (d < bestDistance) {
				bestDistance = d;
				best = cache;
			}
		}
		if (best != null) return new Vec3(best.x, best.y, best.z);
		for (PlayerData.Cache cache : data.reliquaries) {
			BlockPos portal = portalToward(player, ResourceKey.create(Registries.DIMENSION, Identifier.parse(cache.dimension)));
			if (portal != null) return Vec3.atCenterOf(portal);
		}
		return null;
	}

	/** The nearest way from here into another realm, or null if there is none to be found. */
	private static BlockPos portalToward(ServerPlayer player, ResourceKey<Level> realm) {
		ServerLevel level = player.level();
		ResourceKey<Level> here = level.dimension();
		BlockPos at = player.blockPosition();
		boolean overworldNether = (here == Level.OVERWORLD && realm == Level.NETHER) || (here == Level.NETHER && realm == Level.OVERWORLD);
		if (overworldNether) {
			return level.getPoiManager().findClosest(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.NETHER_PORTAL), at, 128,
					net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).orElse(null);
		}
		if (here == Level.OVERWORLD && realm == Level.END) {
			return level.findNearestMapStructure(net.minecraft.tags.StructureTags.EYE_OF_ENDER_LOCATED, at, 100, false);
		}
		if (here == Level.END) return new BlockPos(0, 64, 0); // the way out is at the island's heart
		boolean forest = (here == Level.OVERWORLD && realm == com.omnispell.realm.Realms.FOREST) || (here == com.omnispell.realm.Realms.FOREST && realm == Level.OVERWORLD);
		if (forest) {
			BlockPos best = null;
			double bestDistance = Double.MAX_VALUE;
			for (BlockPos pos : BlockPos.betweenClosed(at.offset(-24, -12, -24), at.offset(24, 12, 24))) {
				if (!level.getBlockState(pos).is(OmniItems.FOREST_GATE)) continue;
				double d = pos.distSqr(at);
				if (d < bestDistance) {
					bestDistance = d;
					best = pos.immutable();
				}
			}
			return best;
		}
		return null;
	}

	public static void clear() {
		TOOK_XP.clear();
	}
}
