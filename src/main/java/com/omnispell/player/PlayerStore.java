package com.omnispell.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.omnispell.Omnispell;
import com.omnispell.data.Word;
import com.omnispell.data.WordBank;
import com.omnispell.item.WandItem;
import com.omnispell.net.OmniNet;
import com.omnispell.spell.Casting;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Loads, saves and syncs each player's Omnispell data. */
public final class PlayerStore {
	/** Saved with the player and carried through death. Stored as JSON text so the format can grow freely. */
	private static final AttachmentType<String> SAVED = AttachmentRegistry.create(
			Omnispell.id("player"), builder -> builder.persistent(Codec.STRING).copyOnDeath());

	private static final Map<UUID, PlayerData> LOADED = new HashMap<>();

	private PlayerStore() {
	}

	public static void register() {
		// Touching the class registers the attachment.
	}

	public static PlayerData get(ServerPlayer player) {
		PlayerData data = LOADED.get(player.getUUID());
		if (data == null) {
			data = read(player);
			LOADED.put(player.getUUID(), data);
		}
		return data;
	}

	private static PlayerData read(ServerPlayer player) {
		PlayerData data = null;
		String json = player.getAttached(SAVED);
		if (json != null && !json.isEmpty()) {
			try {
				data = WordBank.GSON.fromJson(json, PlayerData.class);
			} catch (Exception e) {
				Omnispell.LOG.error("Could not read Omnispell data for {}; starting fresh", player.getName().getString(), e);
			}
		}
		if (data == null) data = new PlayerData();
		if (data.mana == null || data.mana.length != ManaPool.values().length) data.mana = new double[ManaPool.values().length];
		if (data.folders == null) data.folders = new java.util.ArrayList<>();
		if (data.folder("Archive") == null) data.folders.add(0, new PlayerData.Folder("Archive", false)); // every spellbook has an Archive, hidden from the main list
		// The single archive of older saves becomes a folder called Archive, hidden from the main list.
		for (com.omnispell.spell.Spell spell : data.spells) {
			if (!spell.archived) continue;
			spell.archived = false;
			spell.folder = "Archive";
			if (data.folder("Archive") == null) data.folders.add(new PlayerData.Folder("Archive", false));
		}
		if (!data.initialized) {
			data.initialized = true;
			for (Word word : WordBank.words()) {
				if (word.rarity == 0 && !word.excluded) data.words.add(word.id);
				// Maxima and Minima are known from the start, so even first spells can be pushed harder or reined in.
				if (word.type.equals("charge") && (word.english.startsWith("Maxima") || word.english.startsWith("Minima"))) data.words.add(word.id);
			}
			data.spellTypes.add("attack");
			data.spellTypes.add("defense");
			data.spellTypes.add("exploration");
			data.essence = Omnispell.CONFIG.startingEssence;
			for (ManaPool pool : ManaPool.values()) data.mana[pool.ordinal()] = pool.baseMax;
		}
		return data;
	}

	/** Writes the data back onto the player so the game saves it. Call after any lasting change. */
	public static void save(ServerPlayer player) {
		PlayerData data = LOADED.get(player.getUUID());
		if (data != null) player.setAttached(SAVED, WordBank.GSON.toJson(data));
	}

	public static void unload(ServerPlayer player) {
		save(player);
		LOADED.remove(player.getUUID());
	}

	/** Sends the dictionary, spellbook and the rest of the slow-changing state to the player's game. */
	public static void syncState(ServerPlayer player) {
		PlayerData data = get(player);
		JsonObject json = new JsonObject();
		// In creative mode the whole dictionary and every kind of spell are open, without changing what the player has earned.
		boolean creative = player.getAbilities().instabuild;
		java.util.Set<String> words = data.words;
		java.util.Set<String> types = data.spellTypes;
		if (creative) {
			words = new java.util.LinkedHashSet<>();
			for (Word word : WordBank.words()) if (!word.excluded) words.add(word.id);
			types = new java.util.LinkedHashSet<>();
			for (WordBank.SpellType type : WordBank.spellTypes()) types.add(type.id);
		}
		json.add("words", WordBank.GSON.toJsonTree(words));
		json.add("spellTypes", WordBank.GSON.toJsonTree(types));
		json.addProperty("creative", creative);
		json.add("spells", WordBank.GSON.toJsonTree(data.spells));
		json.add("pending", WordBank.GSON.toJsonTree(data.pending));
		json.add("bosses", WordBank.GSON.toJsonTree(data.bosses));
		json.add("folders", WordBank.GSON.toJsonTree(data.folders));
		json.addProperty("maxWords", creative ? 7 : WandItem.bestWordBudget(player));
		ServerPlayNetworking.send(player, new OmniNet.SyncState(json.toString()));
	}

	/** Sends essence, mana and cooldowns. Cheap, so it runs a few times a second. */
	public static void syncVitals(ServerPlayer player) {
		PlayerData data = get(player);
		JsonObject json = new JsonObject();
		json.addProperty("essence", data.essence);
		json.addProperty("essenceCap", Omnispell.CONFIG.essenceCap);
		json.addProperty("corruption", data.corruption);
		JsonArray mana = new JsonArray();
		JsonArray max = new JsonArray();
		for (ManaPool pool : ManaPool.values()) {
			mana.add(Math.round(data.mana[pool.ordinal()] * 10.0) / 10.0);
			max.add(Mana.max(player, pool));
		}
		json.add("mana", mana);
		json.add("manaMax", max);
		json.add("cooldowns", Casting.cooldownsJson(player));
		ItemStack wand = WandItem.heldWand(player);
		json.addProperty("selected", wand.isEmpty() ? -1 : WandItem.selected(wand));
		ServerPlayNetworking.send(player, new OmniNet.SyncVitals(json.toString()));
	}
}
