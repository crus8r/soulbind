package com.omnispell.spell;

import com.omnispell.Omnispell;
import com.omnispell.data.Word;
import com.omnispell.data.WordBank;
import com.omnispell.item.WandItem;
import com.omnispell.net.OmniNet;
import com.omnispell.player.PlayerData;
import com.omnispell.player.PlayerStore;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Crafting a spell in the book: the rules, the essence cost, and saving or discarding the result. */
public final class SpellCrafting {
	private static final Map<String, Integer> MAX_PER_TYPE = Map.of(
			"base", 1, "command", 1, "intention", 1, "charge", 1, "source", 1, "attribute", 2);
	private static final Set<UUID> BUSY = new HashSet<>();

	private SpellCrafting() {
	}

	/** Essence to craft a spell: 5 for every two words, as on the website. */
	public static int essenceCost(int wordCount) {
		return Math.max(5, (int) Math.ceil(wordCount / 2.0) * 5);
	}

	private static void tell(ServerPlayer player, boolean ok, String text) {
		ServerPlayNetworking.send(player, new OmniNet.BookMessage(ok, text));
	}

	/** Checks a formula. Returns null if it is fine, or the reason it is not. */
	public static String problem(ServerPlayer player, PlayerData data, String typeId, List<String> wordIds) {
		WordBank.SpellType type = WordBank.spellType(typeId);
		if (type == null) return "Unknown kind of spell.";
		boolean creative = player.getAbilities().instabuild;
		if (!creative && !data.spellTypes.contains(typeId)) return "You have not learned to craft " + type.name.toLowerCase() + " yet.";
		if (wordIds.isEmpty()) return "Choose at least a base word.";
		int budget = WandItem.bestWordBudget(player);
		if (creative) budget = 7;
		int counted = 0;
		for (String id : wordIds) if (!id.startsWith("charge:")) counted++; // a charge shapes a spell without taking one of the wand's word places
		if (counted > budget) return "Your wand can only hold spells of " + budget + " words. A better core allows more.";

		Map<String, Integer> counts = new HashMap<>();
		Set<String> seen = new HashSet<>();
		for (String id : wordIds) {
			Word word = WordBank.word(id);
			if (word == null || word.excluded) return "Unknown word.";
			if (!creative && !data.words.contains(id)) return "You do not know the word " + word.shortName() + ".";
			if (!seen.add(id)) return "A word can only be used once.";
			int count = counts.merge(word.type, 1, Integer::sum);
			if (count > MAX_PER_TYPE.getOrDefault(word.type, 1)) return "Too many " + word.type + " words.";
			boolean allowed = false;
			for (WordBank.Slot slot : type.wordSlots) {
				if (slot.type.equals(word.type)) allowed = true;
			}
			// Sources and attributes are optional extras on any combat spell, as on the website's [+] row.
			if (!allowed && !typeId.equals("exploration") && (word.type.equals("source") || word.type.equals("attribute") || word.type.equals("charge") || word.type.equals("intention"))) allowed = true;
			if (!allowed && typeId.equals("exploration") && (word.type.equals("charge") || word.type.equals("attribute"))) allowed = true;
			if (!allowed) return capitalize(word.type) + " words cannot go in " + type.name.toLowerCase() + ".";
		}
		for (WordBank.Slot slot : type.wordSlots) {
			if (slot.required && !counts.containsKey(slot.type)) return type.name + " need a " + slot.type + " word.";
		}
		if (data.spell(Spell.idFor(typeId, wordIds)) != null) return "That spell is already in your spellbook.";
		return null;
	}

	public static void craft(ServerPlayer player, String typeId, List<String> wordIds) {
		PlayerData data = PlayerStore.get(player);
		if (data.pending != null) {
			tell(player, false, "Save or discard the spell you just crafted first.");
			return;
		}
		if (BUSY.contains(player.getUUID())) {
			tell(player, false, "The Oracle is still considering your last formula.");
			return;
		}
		String problem = problem(player, data, typeId, wordIds);
		if (problem != null) {
			tell(player, false, problem);
			return;
		}
		int cost = essenceCost(wordIds.size());
		if (data.essence < cost && !player.getAbilities().instabuild) {
			tell(player, false, "You need " + cost + " arcane essence (you have " + data.essence + "). Draw more from an arcane well.");
			return;
		}
		if (!player.getAbilities().instabuild) data.essence -= cost;

		Spell spell = new Spell();
		spell.type = typeId;
		spell.words = new ArrayList<>(wordIds);
		spell.id = Spell.idFor(typeId, wordIds);
		spell.name = Spell.incantation(wordIds);

		if (typeId.equals("exploration")) {
			fillExploration(spell);
			finish(player, spell, true);
			return;
		}

		BUSY.add(player.getUUID());
		tell(player, true, "The Oracle considers your words...");
		PlayerStore.syncVitals(player);
		MinecraftServer server = player.level().getServer();
		UUID uuid = player.getUUID();
		SpellGenerator.generate(spell, player.getName().getString()).whenComplete((fromOracle, error) -> server.execute(() -> {
			BUSY.remove(uuid);
			ServerPlayer online = server.getPlayerList().getPlayer(uuid);
			if (online == null) return;
			if (error != null) {
				Omnispell.LOG.error("Spell crafting failed", error);
				Fallback.fill(spell);
			}
			finish(online, spell, error == null && Boolean.TRUE.equals(fromOracle));
		}));
	}

	/** Exploration spells come from Omnispell's fixed table of 540 combinations: no AI involved. */
	private static void fillExploration(Spell spell) {
		String base = null, command = null, intention = null;
		Word baseWord = null;
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word == null) continue;
			switch (word.type) {
				case "base" -> {
					base = word.explore;
					baseWord = word;
				}
				case "command" -> command = word.explore;
				case "intention" -> intention = word.explore;
				default -> {
				}
			}
		}
		WordBank.ExploreSpell entry = WordBank.exploreSpell(base, command, intention);
		spell.element = base;
		spell.tier = Math.max(1, Math.min(5, baseWord == null ? 1 : baseWord.tierOr(2)));
		if (spell.hasCharge("Maxima")) spell.tier = Math.min(5, spell.tier + 1);
		if (spell.hasCharge("Minima")) spell.tier = Math.max(1, spell.tier - 1);
		if (spell.hasCharge("Sparsus")) spell.tier = Math.min(5, spell.tier + 1); // wider: an environment spell's reach is its tier
		spell.school = "Exploration";
		if (command == null && base != null) {
			// A base word by itself: a cantrip. Quick, cheap, and useful every day.
			String cantrip = Terraform.cantripFor(base);
			spell.toggle = cantrip;
			spell.tier = 1;
			spell.name = spell.name + "se";
			spell.effect = Terraform.effectName(cantrip);
			spell.description = Terraform.describe(cantrip, spell);
			WordBank.Flavor cantripFlavor = WordBank.flavor(base);
			if (cantripFlavor != null) {
				spell.visual.color1 = cantripFlavor.color;
				spell.visual.color2 = cantripFlavor.color2;
				spell.visual.particles = cantripFlavor.particle;
				spell.code.element = cantripFlavor.element;
			}
			return;
		}
		if (intention == null && command != null && Terraform.effectFor(command, "wild") != null) {
			// No intention: the command let loose, for its own sake.
			String wild = Terraform.effectFor(command, "wild");
			spell.toggle = wild;
			spell.effect = Terraform.effectName(wild);
			spell.description = Terraform.describe(wild, spell);
			WordBank.Flavor wildFlavor = WordBank.flavor(base);
			if (wildFlavor != null) {
				spell.visual.color1 = wildFlavor.color;
				spell.visual.color2 = wildFlavor.color2;
				spell.visual.particles = wildFlavor.particle;
				spell.code.element = wildFlavor.element;
			}
			return;
		}
		if (entry == null) {
			spell.fizzle = true;
			spell.toggle = "";
			spell.description = "These words do not answer each other.";
			return;
		}
		spell.toggle = entry.toggle;
		spell.fizzle = entry.fizzle;
		spell.siphon = entry.siphon;
		spell.description = entry.description;
		spell.effect = entry.name;
		spell.visual.pattern = entry.pattern;
		spell.visual.shape = entry.shape;
		spell.visual.motion = entry.motion;
		spell.visual.intensity = Math.max(1, Math.min(5, entry.intensity));
		String effect = Terraform.effectFor(command, intention);
		if (effect != null && !effect.equals(entry.toggle)) {
			spell.toggle = effect;
			spell.fizzle = false;
			spell.siphon = false;
			spell.effect = Terraform.effectName(effect);
			spell.description = Terraform.describe(effect, spell);
		} else if (effect != null && Terraform.handles(effect)) {
			spell.description = Terraform.describe(effect, spell);
		}
		WordBank.Flavor flavor = WordBank.flavor(base);
		if (flavor != null) {
			spell.visual.color1 = flavor.color;
			spell.visual.color2 = flavor.color2;
			spell.visual.particles = flavor.particle;
			spell.code.element = flavor.element;
		}
	}

	private static void finish(ServerPlayer player, Spell spell, boolean stable) {
		PlayerData data = PlayerStore.get(player);
		data.pending = spell;
		PlayerStore.save(player);
		PlayerStore.syncState(player);
		PlayerStore.syncVitals(player);
		player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, stable ? 1.0f : 0.6f);
		tell(player, true, stable ? "Spell complete! Save it to your spellbook or discard it."
				: "The Oracle could not be reached. This spell is unstable: it works, and can be re-crafted later.");
	}

	public static void savePending(ServerPlayer player) {
		PlayerData data = PlayerStore.get(player);
		if (data.pending == null) return;
		Spell spell = data.pending;
		data.pending = null;
		data.spells.removeIf(s -> s.id.equals(spell.id));
		data.spells.add(spell);
		// Fill the first free slot of the held wand, so a new spell is usable straight away.
		ItemStack wand = WandItem.heldWand(player);
		if (!wand.isEmpty()) {
			for (int i = 0; i < WandItem.SLOTS; i++) {
				if (WandItem.spell(wand, i) == null) {
					WandItem.setSpell(wand, i, spell);
					break;
				}
			}
		}
		PlayerStore.save(player);
		PlayerStore.syncState(player);
		tell(player, true, spell.name + " is now in your spellbook.");
	}

	public static void discardPending(ServerPlayer player) {
		PlayerData data = PlayerStore.get(player);
		if (data.pending == null) return;
		int refund = essenceCost(data.pending.words.size()) / 2;
		data.pending = null;
		data.essence = Math.min(Omnispell.CONFIG.essenceCap, data.essence + refund);
		PlayerStore.save(player);
		PlayerStore.syncState(player);
		PlayerStore.syncVitals(player);
		tell(player, true, "Discarded. " + refund + " essence returned.");
	}

	/** Gives a spell the player's own name ("id|name"; an empty name takes it away), in the spellbook and on every wand carried. */
	public static void rename(ServerPlayer player, String arg) {
		int bar = arg.lastIndexOf('|'); // a spell's id has bars of its own, so the name is whatever follows the last one
		if (bar < 0) return;
		String id = arg.substring(0, bar);
		String name = arg.substring(bar + 1).strip();
		if (name.length() > 32) name = name.substring(0, 32);
		Spell spell = PlayerStore.get(player).spell(id);
		if (spell == null) return;
		spell.nickname = name.isEmpty() ? null : name;
		refreshWands(player, spell);
		PlayerStore.save(player);
		PlayerStore.syncState(player);
		PlayerStore.syncVitals(player);
		tell(player, true, name.isEmpty() ? spell.name + " goes by its own name again." : spell.name + " is now called " + name + ".");
	}

	/** Puts a spell away, or brings it back. An archived spell is kept and still works on a wand; it is only out of the list. */
	public static void archive(ServerPlayer player, String id) {
		Spell spell = PlayerStore.get(player).spell(id);
		if (spell == null) return;
		spell.archived = !spell.archived;
		refreshWands(player, spell);
		PlayerStore.save(player);
		PlayerStore.syncState(player);
		tell(player, true, spell.label() + (spell.archived ? " is archived." : " is back in your spellbook."));
	}

	private static void refreshWands(ServerPlayer player, Spell spell) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!WandItem.isWand(stack)) continue;
			for (int slot = 0; slot < WandItem.SLOTS; slot++) {
				Spell there = WandItem.spell(stack, slot);
				if (there != null && there.id.equals(spell.id)) WandItem.setSpell(stack, slot, spell);
			}
		}
	}

	public static void forget(ServerPlayer player, String spellId) {
		PlayerData data = PlayerStore.get(player);
		if (data.spells.removeIf(s -> s.id.equals(spellId))) {
			PlayerStore.save(player);
			PlayerStore.syncState(player);
			tell(player, true, "The spell is struck from your spellbook. Wands that hold it keep it.");
		}
	}

	/** "slot:spellId" — puts a spellbook spell on the held wand; an empty id clears the slot. */
	public static void equip(ServerPlayer player, String arg) {
		ItemStack wand = WandItem.heldWand(player);
		if (wand.isEmpty()) {
			tell(player, false, "Hold a wand in either hand to change its spells.");
			return;
		}
		int colon = arg.indexOf(':');
		if (colon < 0) return;
		int slot;
		try {
			slot = Integer.parseInt(arg.substring(0, colon));
		} catch (NumberFormatException e) {
			return;
		}
		String id = arg.substring(colon + 1);
		PlayerData data = PlayerStore.get(player);
		Spell spell = id.isEmpty() ? null : data.spell(id);
		if (!id.isEmpty() && spell == null) return;
		if (spell != null) {
			for (int i = 0; i < WandItem.SLOTS; i++) {
				Spell other = WandItem.spell(wand, i);
				if (i != slot && other != null && other.id.equals(spell.id)) WandItem.setSpell(wand, i, null);
			}
		}
		WandItem.setSpell(wand, slot, spell);
		PlayerStore.syncVitals(player);
		tell(player, true, spell == null ? "Slot " + (slot + 1) + " cleared." : spell.label() + " set to slot " + (slot + 1) + ".");
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
