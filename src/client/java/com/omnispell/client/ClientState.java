package com.omnispell.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.omnispell.data.WordBank;
import com.omnispell.spell.Spell;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** What this player's game knows about their Omnispell progress, as last sent by the server. */
public final class ClientState {
	public static Set<String> words = new LinkedHashSet<>();
	public static Set<String> spellTypes = new LinkedHashSet<>();
	public static List<Spell> spells = new ArrayList<>();
	public static Spell pending;
	public static Set<String> bosses = new LinkedHashSet<>();
	public static int maxWords = 3;
	/** Conjured creatures' colours, by entity id, as the server sent them. Lightened so the creature's markings still show. */
	public static final java.util.Map<Integer, Integer> conjureTints = new java.util.HashMap<>();
	/** The render states this frame that belong to a tinted creature. */
	public static final java.util.Map<Object, Integer> tintedStates = new java.util.WeakHashMap<>();
	/** Other players' corruption, by entity id, as the server sent it. */
	public static final java.util.Map<Integer, Integer> corruptionLevels = new java.util.HashMap<>();
	/** The render states this frame that belong to a player with veins, and which stage. */
	public static final java.util.Map<Object, Integer> veinStates = new java.util.WeakHashMap<>();

	/** How corrupted a player looks: your own from your vitals, anyone else's from the server's word. */
	public static int corruptionOf(net.minecraft.world.entity.Entity entity) {
		var self = net.minecraft.client.Minecraft.getInstance().player;
		if (self != null && entity.getId() == self.getId()) return (int) corruption;
		return corruptionLevels.getOrDefault(entity.getId(), 0);
	}
	/** Whether sprinting with a wand in hand gives the Phase-powered speed boost. Kept on the server. */
	public static boolean wandSprint = true;

	public static int essence;
	public static int essenceCap = 100;
	public static double corruption;
	public static double[] mana = new double[com.omnispell.player.ManaPool.values().length];
	public static double[] manaMax = {1, 1, 1, 1, 1, 1};
	/** The client tick at which each reserve was last drawn on, for the flash on its bar. */
	/** True just after a spell was deselected, so its name is not shown as if it had been picked. */
	public static boolean hideName;
	/** Players fully shrouded by a strong invisibility, by entity id, until a client tick. */
	public static final java.util.Map<Integer, Long> shrouded = new java.util.HashMap<>();

	public static boolean isShrouded(int entityId) {
		Long until = shrouded.get(entityId);
		return until != null && until > clientTick;
	}
	/** The player's spell folders, in order. */
	public static List<com.omnispell.player.PlayerData.Folder> folders = new ArrayList<>();
	public static long[] manaSpentAt = new long[com.omnispell.player.ManaPool.values().length];
	public static long[] cooldownLeft = new long[5];
	public static long[] cooldownTotal = {1, 1, 1, 1, 1};
	public static int selected = -1;
	/** Client tick when the vitals last arrived, so cooldowns can count down smoothly between updates. */
	public static long vitalsAtTick;

	/** The spell slot whose name is being shown above the bar after a key press, and when it was pressed. */
	public static int shownSlot = -1;
	public static long shownAtTick = -1000;
	public static long castAtTick = -1000;

	public static String message = "";
	public static boolean messageOk = true;
	public static long messageAtTick;

	public static String flashKind = "none";
	public static int flashColor;
	public static int flashIntensity;
	public static long flashAtTick = -1000;

	public static long clientTick;

	private ClientState() {
	}

	public static void readState(String text) {
		JsonObject json = WordBank.GSON.fromJson(text, JsonObject.class);
		words = WordBank.GSON.fromJson(json.get("words"), new TypeToken<LinkedHashSet<String>>() { }.getType());
		spellTypes = WordBank.GSON.fromJson(json.get("spellTypes"), new TypeToken<LinkedHashSet<String>>() { }.getType());
		spells = WordBank.GSON.fromJson(json.get("spells"), new TypeToken<ArrayList<Spell>>() { }.getType());
		pending = json.has("pending") && !json.get("pending").isJsonNull() ? WordBank.GSON.fromJson(json.get("pending"), Spell.class) : null;
		bosses = WordBank.GSON.fromJson(json.get("bosses"), new TypeToken<LinkedHashSet<String>>() { }.getType());
		folders = json.has("folders") ? WordBank.GSON.fromJson(json.get("folders"), new TypeToken<ArrayList<com.omnispell.player.PlayerData.Folder>>() { }.getType()) : null;
		if (folders == null) folders = new ArrayList<>();
		maxWords = json.get("maxWords").getAsInt();
		wandSprint = !json.has("wandSprint") || json.get("wandSprint").getAsBoolean();
		if (words == null) words = new LinkedHashSet<>();
		if (spellTypes == null) spellTypes = new LinkedHashSet<>();
		if (spells == null) spells = new ArrayList<>();
		if (bosses == null) bosses = new LinkedHashSet<>();
	}

	public static void readVitals(String text) {
		JsonObject json = WordBank.GSON.fromJson(text, JsonObject.class);
		essence = json.get("essence").getAsInt();
		essenceCap = json.get("essenceCap").getAsInt();
		corruption = json.get("corruption").getAsDouble();
		JsonArray m = json.getAsJsonArray("mana");
		JsonArray mm = json.getAsJsonArray("manaMax");
		for (int i = 0; i < mana.length && i < m.size(); i++) {
			if (m.get(i).getAsDouble() < mana[i] - 0.4) manaSpentAt[i] = clientTick;
			mana[i] = m.get(i).getAsDouble();
			manaMax[i] = Math.max(0.001, mm.get(i).getAsDouble());
		}
		JsonArray cds = json.getAsJsonArray("cooldowns");
		for (int i = 0; i < 5 && i < cds.size(); i++) {
			cooldownLeft[i] = cds.get(i).getAsJsonArray().get(0).getAsLong();
			cooldownTotal[i] = Math.max(1, cds.get(i).getAsJsonArray().get(1).getAsLong());
		}
		selected = json.get("selected").getAsInt();
		vitalsAtTick = clientTick;
	}

	/** Fraction of a slot's cooldown still to run, 0 when ready. */
	public static float cooldownFraction(int slot) {
		long left = cooldownLeft[slot] - (clientTick - vitalsAtTick);
		if (left <= 0) return 0f;
		return Math.min(1f, (float) left / cooldownTotal[slot]);
	}

	public static void say(boolean ok, String text) {
		message = text;
		messageOk = ok;
		messageAtTick = clientTick;
	}
}
