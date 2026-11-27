package com.omnispell.boss;

import com.omnispell.Omnispell;
import com.omnispell.item.OmniItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.HashMap;
import java.util.Map;

/**
 * A lair is sealed while its boss still sleeps: nothing of it can be broken by
 * hand. Only an environment spell strong enough for the lair's tier opens it.
 */
public final class Lairs {
	private Lairs() {
	}

	/** The spell tier needed to break into a lair, by the lair's own tier (0 hay ... 6 netherite). */
	public static int spellTierFor(int lairTier) {
		return new int[]{1, 1, 2, 3, 3, 4, 5}[Math.max(0, Math.min(6, lairTier))];
	}

	/** The sealed lair at a position, or null. */
	private static StructureStart lairAt(ServerLevel level, BlockPos pos) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, holder ->
				holder.unwrapKey().map(key -> key.identifier().getNamespace().equals(Omnispell.MOD_ID) && key.identifier().getPath().startsWith("boss_lair_")).orElse(false));
		return start == null || !start.isValid() ? null : start;
	}

	private static int lairTier(ServerLevel level, StructureStart start) {
		Identifier id = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
		try {
			return id == null ? 0 : Integer.parseInt(id.getPath().substring("boss_lair_".length()));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** True while the lair's seal has not yet been woken. */
	private static boolean stillSealed(ServerLevel level, StructureStart start) {
		BoundingBox box = start.getBoundingBox();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(pos);
			if (state.is(OmniItems.BOSS_ALTAR) && !state.getValue(BossAltarBlock.SPENT)) return true;
		}
		return false;
	}

	/** Remembers the answer for each lair while one spell is cast, so a big spell asks only once. */
	public static final class Guard {
		private final ServerLevel level;
		private final Map<StructureStart, Integer> known = new HashMap<>();

		public Guard(ServerLevel level) {
			this.level = level;
		}

		/** The spell tier needed to change this block; 0 if it is not part of a sealed lair. */
		public int tierNeeded(BlockPos pos) {
			StructureStart start = lairAt(level, pos);
			if (start == null) return 0;
			return known.computeIfAbsent(start, s -> stillSealed(level, s) ? spellTierFor(lairTier(level, s)) : 0);
		}
	}

	/**
	 * Can a spell carry someone from here to there? Not into a sealed lair, and not through its walls: a sealed
	 * lair opens only to an environment spell strong enough to break its door. Returns the spell tier that door
	 * needs if the way is barred, or 0 if it is clear.
	 */
	public static int sealedBetween(ServerLevel level, net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to) {
		Guard guard = new Guard(level);
		net.minecraft.world.phys.Vec3 d = to.subtract(from);
		int steps = Math.max(1, (int) Math.ceil(d.length() / 0.5));
		for (int i = 1; i <= steps; i++) {
			BlockPos at = BlockPos.containing(from.add(d.scale(i / (double) steps)));
			int need = Math.max(guard.tierNeeded(at), guard.tierNeeded(at.above()));
			if (need > 0) return need;
		}
		return 0;
	}

	public static String sealedMessage(int tier) {
		return "A sealed lair turns the spell aside. Only an environment spell of tier " + tier + " or stronger breaks its door.";
	}

	/** After something moved a player at random (a void ward, a displacing blow): if it put them inside a sealed lair, put them back. */
	public static void keepOut(net.minecraft.world.entity.LivingEntity who, net.minecraft.world.phys.Vec3 from) {
		if (!(who instanceof ServerPlayer player) || !(who.level() instanceof ServerLevel level)) return;
		if (sealedBetween(level, from, who.position()) > 0) {
			player.teleportTo(from.x, from.y, from.z);
			player.sendOverlayMessage(Component.literal("A sealed lair turns you aside."));
		}
	}

	public static void register() {
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel serverLevel) || player.getAbilities().instabuild) return true;
			int needed = new Guard(serverLevel).tierNeeded(pos);
			if (needed == 0) return true;
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.literal("The lair is sealed. Only an environment spell of tier " + needed + " or stronger will open it."));
			}
			return false;
		});
	}
}
