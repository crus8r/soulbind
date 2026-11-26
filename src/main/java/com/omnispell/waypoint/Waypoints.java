package com.omnispell.waypoint;

import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.Codec;
import com.omnispell.Omnispell;
import com.omnispell.data.WordBank;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every waypoint pair in the world: its colour, its name and where its (up to
 * two) ends stand. Saved with the overworld.
 */
public final class Waypoints {
	public static final String[] COLOR_NAMES = {
			"Red", "Orange", "Amber", "Yellow", "Lime", "Green", "Emerald", "Teal", "Cyan", "Sky",
			"Blue", "Indigo", "Violet", "Purple", "Magenta", "Pink", "Rose", "White", "Silver", "Black"};
	public static final int[] COLORS = {
			0xE03C3C, 0xF08030, 0xF0A830, 0xF0D840, 0xA8E040, 0x48C048, 0x30C890, 0x30B0A8, 0x40D0E0, 0x60B0F0,
			0x4070E8, 0x5848D8, 0x8858E8, 0xA848D0, 0xD848C8, 0xF078B8, 0xF05078, 0xF0F0F0, 0xA0A8B0, 0x303038};

	public static final class End {
		public String dimension;
		public int x, y, z;

		public BlockPos pos() {
			return new BlockPos(x, y, z);
		}

		public boolean is(ServerLevel level, BlockPos pos) {
			return level.dimension().identifier().toString().equals(dimension) && pos.getX() == x && pos.getY() == y && pos.getZ() == z;
		}
	}

	public static final class Pair {
		public int color;
		public String name = "";
		public List<End> ends = new ArrayList<>();
	}

	private static final AttachmentType<String> SAVED = AttachmentRegistry.create(
			Omnispell.id("waypoints"), builder -> builder.persistent(Codec.STRING));

	private static Map<String, Pair> pairs;
	private static MinecraftServer loadedFor;

	private Waypoints() {
	}

	public static void register() {
		// Touching the class registers the attachment.
	}

	private static Map<String, Pair> all(MinecraftServer server) {
		if (pairs == null || loadedFor != server) {
			loadedFor = server;
			pairs = null;
			String json = server.overworld().getAttached(SAVED);
			if (json != null && !json.isEmpty()) {
				try {
					pairs = WordBank.GSON.fromJson(json, new TypeToken<HashMap<String, Pair>>() { }.getType());
				} catch (Exception e) {
					Omnispell.LOG.error("Could not read the saved waypoints; starting with none", e);
				}
			}
			if (pairs == null) pairs = new HashMap<>();
		}
		return pairs;
	}

	private static void save(MinecraftServer server) {
		server.overworld().setAttached(SAVED, WordBank.GSON.toJson(all(server)));
	}

	public static void unload() {
		pairs = null;
		loadedFor = null;
	}

	public static Pair pair(MinecraftServer server, String pairId) {
		return all(server).get(pairId);
	}

	/** The pair that has an end at this spot, or null. */
	public static String pairAt(ServerLevel level, BlockPos pos) {
		for (Map.Entry<String, Pair> e : all(level.getServer()).entrySet()) {
			for (End end : e.getValue().ends) {
				if (end.is(level, pos)) return e.getKey();
			}
		}
		return null;
	}

	/** Records a placed waypoint. Returns false if both ends of the pair already stand somewhere. */
	public static boolean place(ServerLevel level, BlockPos pos, String pairId, int color, String name) {
		Map<String, Pair> map = all(level.getServer());
		Pair pair = map.computeIfAbsent(pairId, k -> new Pair());
		if (pair.ends.size() >= 2) return false;
		if (pair.ends.isEmpty()) {
			pair.color = color;
			pair.name = name == null ? "" : name;
		}
		End end = new End();
		end.dimension = level.dimension().identifier().toString();
		end.x = pos.getX();
		end.y = pos.getY();
		end.z = pos.getZ();
		pair.ends.add(end);
		save(level.getServer());
		return true;
	}

	public static void remove(ServerLevel level, BlockPos pos) {
		Map<String, Pair> map = all(level.getServer());
		String id = pairAt(level, pos);
		if (id == null) return;
		Pair pair = map.get(id);
		pair.ends.removeIf(end -> end.is(level, pos));
		// The pair itself is kept while either end still exists as an item or a block,
		// so its colour and name survive being picked up.
		save(level.getServer());
	}

	/** The other end of the pair, or null if it has not been placed. */
	public static End partner(ServerLevel level, BlockPos pos) {
		String id = pairAt(level, pos);
		if (id == null) return null;
		for (End end : all(level.getServer()).get(id).ends) {
			if (!end.is(level, pos)) return end;
		}
		return null;
	}

	public static ServerLevel levelOf(MinecraftServer server, End end) {
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(end.dimension));
		return server.getLevel(key);
	}

	/** Renames and recolours a pair, and repaints whichever of its ends are placed. */
	public static void edit(MinecraftServer server, String pairId, String name, int color) {
		Pair pair = all(server).get(pairId);
		if (pair == null) return;
		pair.name = name == null ? "" : name.strip();
		if (pair.name.length() > 32) pair.name = pair.name.substring(0, 32);
		pair.color = Math.floorMod(color, COLORS.length);
		for (End end : pair.ends) {
			ServerLevel level = levelOf(server, end);
			if (level == null) continue;
			BlockPos pos = end.pos();
			if (level.getBlockState(pos).getBlock() instanceof WaypointBlock) {
				level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(WaypointBlock.COLOR, pair.color));
			}
		}
		save(server);
	}
}
