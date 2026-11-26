package com.omnispell.alchemy;

import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.Codec;
import com.omnispell.Omnispell;
import com.omnispell.data.WordBank;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What is inside every alchemy cauldron in the world. Saved with the overworld. */
public final class Vats {
	public static final class Vat {
		public String base;                          // water, honey, milk, lava, dragon; null when empty
		public List<String> items = new ArrayList<>(); // ingredient keys, in the order added
		public int charges;                          // blaze powder charges: one per brew
		public int servings;                         // bottles of finished potion left
		public String brew;                          // the finished potion, as JSON
		public transient boolean brewing;
	}

	private static final AttachmentType<String> SAVED = AttachmentRegistry.create(
			Omnispell.id("vats"), builder -> builder.persistent(Codec.STRING));

	private static Map<String, Vat> vats;
	private static MinecraftServer loadedFor;

	private Vats() {
	}

	public static void register() {
	}

	private static Map<String, Vat> all(MinecraftServer server) {
		if (vats == null || loadedFor != server) {
			loadedFor = server;
			vats = null;
			String json = server.overworld().getAttached(SAVED);
			if (json != null && !json.isEmpty()) {
				try {
					vats = WordBank.GSON.fromJson(json, new TypeToken<HashMap<String, Vat>>() { }.getType());
				} catch (Exception e) {
					Omnispell.LOG.error("Could not read the saved cauldrons; starting empty", e);
				}
			}
			if (vats == null) vats = new HashMap<>();
		}
		return vats;
	}

	private static String key(ServerLevel level, BlockPos pos) {
		return level.dimension().identifier() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	public static Vat at(ServerLevel level, BlockPos pos) {
		return all(level.getServer()).computeIfAbsent(key(level, pos), k -> new Vat());
	}

	public static void remove(ServerLevel level, BlockPos pos) {
		all(level.getServer()).remove(key(level, pos));
		save(level.getServer());
	}

	public static void save(MinecraftServer server) {
		server.overworld().setAttached(SAVED, WordBank.GSON.toJson(all(server)));
	}

	public static void unload() {
		vats = null;
		loadedFor = null;
	}
}
