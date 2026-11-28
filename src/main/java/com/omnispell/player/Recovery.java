package com.omnispell.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.omnispell.Omnispell;
import com.omnispell.data.WordBank;
import com.omnispell.item.WandItem;
import com.omnispell.spell.Spell;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Getting a lost spellbook back. Spells survive in more places than the book: the game's own backup of the
 * player's save (playerdata/&lt;id&gt;.dat_old) and the save itself, any saved data that could not be read
 * (kept aside), and every wand, which carries whole copies of the spells set on it. Restoring gathers them
 * all into the book; nothing already there is removed or changed.
 */
public final class Recovery {
	private Recovery() {
	}

	/** What a restore found, for telling the player. */
	public record Found(int fromSaves, int fromWands, int words) {
	}

	public static Found restore(ServerPlayer player) {
		PlayerData data = PlayerStore.get(player);
		java.util.Set<String> have = new java.util.HashSet<>();
		for (Spell s : data.spells) if (s != null && s.id != null) have.add(s.id);
		int fromSaves = 0, fromWands = 0, words = 0;

		// 1. Every saved copy we can find: the save files on disk (the backup first), and anything kept aside.
		List<String> saved = new ArrayList<>();
		Path dir = player.level().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
		for (String name : new String[]{player.getStringUUID() + ".dat_old", player.getStringUUID() + ".dat"}) {
			Path file = dir.resolve(name);
			if (!Files.exists(file)) continue;
			try {
				CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
				String json = attachment(tag, Omnispell.MOD_ID + ":player");
				if (!json.isEmpty()) saved.add(json);
				String kept = attachment(tag, Omnispell.MOD_ID + ":player_unreadable");
				if (!kept.isEmpty()) saved.add(kept);
			} catch (Exception e) {
				Omnispell.LOG.warn("Could not read {} while restoring a spellbook", file, e);
			}
		}
		// In a single-player world (or one opened to friends), the host's own save lives in level.dat, with its backup beside it.
		Path root = player.level().getServer().getWorldPath(LevelResource.ROOT);
		for (String name : new String[]{"level.dat_old", "level.dat"}) {
			Path file = root.resolve(name);
			if (!Files.exists(file)) continue;
			try {
				CompoundTag host = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompoundOrEmpty("Data").getCompoundOrEmpty("Player");
				// level.dat holds the host's save: only use it if this player is the host.
				if (!(host.get("UUID") instanceof net.minecraft.nbt.IntArrayTag id) || !net.minecraft.core.UUIDUtil.uuidFromIntArray(id.getAsIntArray()).equals(player.getUUID())) continue;
				String json = attachment(host, Omnispell.MOD_ID + ":player");
				if (!json.isEmpty()) saved.add(json);
				String kept = attachment(host, Omnispell.MOD_ID + ":player_unreadable");
				if (!kept.isEmpty()) saved.add(kept);
			} catch (Exception e) {
				Omnispell.LOG.warn("Could not read {} while restoring a spellbook", file, e);
			}
		}
		for (String json : saved) {
			JsonObject old = parse(json);
			if (old == null) continue;
			if (old.get("spells") instanceof JsonArray spells) {
				for (JsonElement element : spells) {
					try {
						Spell spell = WordBank.GSON.fromJson(element, Spell.class);
						if (spell == null || spell.id == null || (spell.legacy() && data.retired.contains(spell.id)) || !have.add(spell.id)) continue;
						data.spells.add(spell);
						fromSaves++;
					} catch (Exception ignored) {
						// one spoiled spell does not stop the rest
					}
				}
			}
			if (old.get("words") instanceof JsonArray known) {
				for (JsonElement w : known) {
					if (w.isJsonPrimitive() && data.words.add(w.getAsString())) words++;
				}
			}
		}

		// 2. Every wand carried: in the inventory, the ender chest, and bundles in either.
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) stacks.add(player.getInventory().getItem(i));
		for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) stacks.add(player.getEnderChestInventory().getItem(i));
		List<ItemStack> inside = new ArrayList<>();
		for (ItemStack stack : stacks) {
			var bundle = stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
			if (bundle != null) bundle.itemCopies().forEach(inside::add);
		}
		stacks.addAll(inside);
		for (ItemStack stack : stacks) {
			if (!WandItem.isWand(stack)) continue;
			for (int slot = 0; slot < WandItem.SLOTS; slot++) {
				Spell spell = WandItem.spell(stack, slot);
				if (spell == null || spell.id == null || (spell.legacy() && data.retired.contains(spell.id)) || !have.add(spell.id)) continue;
				data.spells.add(WordBank.GSON.fromJson(WordBank.GSON.toJson(spell), Spell.class)); // its own copy
				fromWands++;
				for (String id : spell.words) if (data.words.add(id)) words++;
			}
		}
		PlayerStore.save(player);
		PlayerStore.syncState(player);
		return new Found(fromSaves, fromWands, words);
	}

	/** Writes the player's saved Omnispell data, as the game holds it now and as the save files hold it, to a file in the world folder. */
	public static Path dump(ServerPlayer player) throws java.io.IOException {
		JsonObject out = new JsonObject();
		out.addProperty("player", player.getName().getString());
		out.add("now", WordBank.GSON.toJsonTree(PlayerStore.get(player)));
		Path dir = player.level().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
		for (String name : new String[]{player.getStringUUID() + ".dat", player.getStringUUID() + ".dat_old"}) {
			Path file = dir.resolve(name);
			if (!Files.exists(file)) continue;
			try {
				CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
				out.addProperty(name, attachment(tag, Omnispell.MOD_ID + ":player"));
				String kept = attachment(tag, Omnispell.MOD_ID + ":player_unreadable");
				if (!kept.isEmpty()) out.addProperty(name + " (unreadable, kept aside)", kept);
			} catch (Exception e) {
				out.addProperty(name, "could not be read: " + e);
			}
		}
		Path file = player.level().getServer().getWorldPath(LevelResource.ROOT).resolve("omnispell-" + player.getName().getString() + ".json");
		Files.writeString(file, WordBank.GSON.toJson(out));
		return file.toAbsolutePath().normalize();
	}

	/** A Fabric data attachment's text, wherever this version keeps attachments in the save. */
	private static String attachment(CompoundTag tag, String id) {
		for (String holder : tag.keySet()) {
			if (!holder.contains("attachment")) continue;
			String found = tag.getCompoundOrEmpty(holder).getStringOr(id, "");
			if (!found.isEmpty()) return found;
		}
		return "";
	}

	/** Reads saved data leniently: broken numbers and the like do not stop the spells being found. */
	private static JsonObject parse(String json) {
		try {
			JsonReader reader = new JsonReader(new StringReader(json));
			reader.setStrictness(Strictness.LENIENT);
			JsonElement element = JsonParser.parseReader(reader);
			return element.isJsonObject() ? element.getAsJsonObject() : null;
		} catch (Exception e) {
			return null;
		}
	}
}
