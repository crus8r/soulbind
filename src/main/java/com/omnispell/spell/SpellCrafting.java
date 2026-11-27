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
			if (word == null || (word.excluded && !data.words.contains(id))) return "Unknown word."; // moderator-only words work for those given them
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
			if (word.type.equals("attribute") && !WordBank.attributeFits(typeId, word)) {
				String hint = typeId.equals("attack") && Conjuring.creatureWord(word.english) ? " A creature has to be called up: try it in a Conjuration instead." : typeId.equals("conjuration") ? " Conjurations call creatures, armed warriors, or masonry (Bridge, Wall, Tower). Try it in an Environment or Defense spell." : "";
				return word.shortName() + " does not belong in " + type.name.toLowerCase() + "." + hint;
			}
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
			shapeByAttribute(spell);
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

	/**
	 * An attribute word names what an environment spell makes, and it makes that: Bridge a
	 * bridge, Wall a wall, Tower a pillar to stand on, Cloud a cloud underfoot, Bed a place to
	 * wake, Fireplace a campfire, Chest/Box your ender chest, Currency the ores nearby.
	 */
	static void shapeByAttribute(Spell spell) {
		if (spell.fizzle) return;
		String toggle = null;
		for (String id : spell.words) {
			toggle = switch (id) {
				case "attribute:Bridge" -> switch (Mines.element(spell)) {
					case "fire" -> "fuse";       // a bridge of fire is a fuse of mines
					case "storm" -> "tripwire";  // a bridge of lightning is a live wire across the way
					default -> "bridge";
				};
				case "attribute:Wall" -> "raise_wall";
				case "attribute:Tower" -> "pillar";
				case "attribute:Cloud" -> "cloud";
				case "attribute:Bed" -> "stone".equals(spell.element) ? "rest" : "mine"; // only earth makes a bed to wake in; anything else, a mine
				case "attribute:Fireplace" -> "hearth";
				case "attribute:Chest/Box" -> "stash";
				case "attribute:Currency" -> "prospect";
				default -> toggle;
			};
		}
		if (toggle == null) return;
		spell.toggle = toggle;
		spell.siphon = false;
		spell.effect = Terraform.effectName(toggle);
		spell.description = Mines.handles(toggle) ? Mines.describe(toggle, spell) : Terraform.describe(toggle, spell);
	}

	/** Exploration spells come from Omnispell's fixed table of 540 combinations: no AI involved. */
	private static void fillExploration(Spell spell) {
		String base = null, command = null, intention = null, commandName = null, intentionName = null;
		Word baseWord = null;
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word == null) continue;
			switch (word.type) {
				case "base" -> {
					base = word.explore;
					baseWord = word;
				}
				case "command" -> {
					command = word.explore;
					commandName = word.english;
				}
				case "intention" -> {
					intention = word.explore;
					intentionName = word.english;
				}
				default -> {
				}
			}
		}
		WordBank.ExploreSpell entry = WordBank.exploreSpell(base, command, intention);
		spell.element = base;
		spell.tier = Math.max(1, Math.min(5, baseWord == null ? 1 : baseWord.tierOr(2)));
		if (spell.hasCharge("Maxima")) spell.tier = Math.min(5, spell.tier + 1);
		if (spell.hasCharge("Minima")) spell.tier = Math.max(1, spell.tier - 1);
		if (spell.hasCharge("Sparsus")) spell.tier = Math.min(5, spell.tier + 1);
		if (spell.words.contains("base:Infinity")) spell.tier = 5; // the moderators' word: always the fullest reach, as the website treats it // wider: an environment spell's reach is its tier
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
		if (intention == null && command != null && Terraform.effectFor(commandName, command, "wild") != null) {
			// No intention: the command let loose, for its own sake.
			String wild = Terraform.effectFor(commandName, command, "wild");
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
		String effect = Terraform.effectFor(commandName, intentionName, command, intention);
		String rift = Rifts.forEntry(entry.id);
		if (rift != null) {
			// A rare pairing whose own words describe a pull, a vacuum or sinking ground: it does just that.
			spell.toggle = rift;
			spell.fizzle = false;
			spell.siphon = false;
			spell.description = entry.description + " " + Rifts.describe(rift, spell.tier);
			spell.effect = Rifts.label(rift);
			effect = null;
		}
		String wonder = Wonders.primary(entry.id);
		if (wonder != null) {
			spell.toggle = wonder;
			spell.fizzle = false;
			spell.siphon = false;
			spell.description = entry.description + " " + Wonders.describe(wonder, spell.tier);
			spell.effect = Wonders.label(wonder).replaceFirst("^(An? )", "");
			effect = null;
		}
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
		}		String extras = Wonders.describeExtras(spell);
		if (!extras.isEmpty()) spell.description = (spell.description == null ? "" : spell.description) + extras;
	}

	/** The Oracle's own words for a spell, without what this mod wrote after them. */
	public static String oracleText(Spell spell) {
		if (spell.oracle != null) return spell.oracle;
		String d = spell.description == null ? "" : spell.description;
		int cut = d.length();
		String[] marks = "defense".equals(spell.type) ? new String[]{" It calls a guardian creature", " It gives you the "} : new String[]{" It comes as ", " It raises ", " It closes ", " What it hits is ", " Sometimes, ", " Often, ", " It sends a wall"};
		for (String mark : marks) {
			int i = d.indexOf(mark);
			if (i >= 0) cut = Math.min(cut, i);
		}
		return d.substring(0, cut).trim();
	}

	/** Rewrites the part of a combat spell's description that says what it really does here, so older spells keep up with the game. */
	public static void refresh(Spell spell) {
		if (spell == null || spell.isExploration() || spell.type == null) return;
		if (!spell.type.equals("conjuration") && !spell.type.equals("defense") && !spell.type.equals("attack")) return;
		if (spell.oracle == null) {
			String guess = oracleText(spell);
			String whole = spell.description == null ? "" : spell.description.trim();
			// An older defense spell whose added text cannot be told apart from the Oracle's own is left exactly as it is.
			if ("defense".equals(spell.type) && guess.length() >= whole.length()) return;
			spell.oracle = guess;
		}
		spell.description = spell.oracle;
		describeWorking(spell);
	}

	private static void finish(ServerPlayer player, Spell spell, boolean stable) {
		if (spell.oracle == null && !spell.isExploration()) spell.oracle = spell.description == null ? "" : spell.description.trim();
		describeWorking(spell);
		finishRest(player, spell, stable);
	}

	private static void describeWorking(Spell spell) {
		if ("conjuration".equals(spell.type)) {
			String really = Conjuring.describe(spell, null);
			if (spell.description == null || !spell.description.contains(really)) spell.description = (spell.description == null || spell.description.isBlank() ? "" : spell.description.trim() + " ") + really;
		}
		if ("defense".equals(spell.type) || (spell.code != null && "shield".equals(spell.code.type))) {
			String ward = "defense".equals(spell.type) ? Defenses.describe(spell) : Wards.describe(spell);
			if (spell.description == null || !spell.description.contains(ward)) spell.description = (spell.description == null || spell.description.isBlank() ? "" : spell.description.trim() + " ") + ward;
		}
		if ("attack".equals(spell.type)) {
			// A Wall (with no weapon word) rolls out as a surging wall instead of the command's own shape.
			String forms = Surges.wallAttack(spell) && Weapons.kind(spell) == null ? Surges.describe(true, AttackForms.element(spell)) : AttackForms.describe(spell);
			if (!forms.isEmpty() && (spell.description == null || !spell.description.contains(forms))) spell.description = (spell.description == null || spell.description.isBlank() ? "" : spell.description.trim() + " ") + forms;
		}
	}

	private static void finishRest(ServerPlayer player, Spell spell, boolean stable) {
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
		int placed = -1;
		if (!wand.isEmpty()) {
			for (int i = 0; i < WandItem.SLOTS; i++) {
				if (WandItem.spell(wand, i) == null) {
					WandItem.setSpell(wand, i, spell);
					WandItem.select(wand, i); // the new spell is the one a left-click casts
					placed = i;
					break;
				}
			}
		}
		PlayerStore.save(player);
		PlayerStore.syncState(player);
		PlayerStore.syncVitals(player);
		if (wand.isEmpty()) tell(player, true, spell.name + " is in your spellbook. Hold a wand and set it to a slot in the Spellbook tab.");
		else if (placed < 0) tell(player, false, spell.name + " is in your spellbook, but your wand's five slots are full. Set it to a slot in the Spellbook tab.");
		else tell(player, true, spell.name + " is in your spellbook and on your wand, slot " + (placed + 1) + ".");
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

	public static final int MAX_FOLDERS = 12;

	private static String folderName(String text) {
		String name = text == null ? "" : text.replace("|", " ").strip();
		return name.length() > 24 ? name.substring(0, 24) : name;
	}

	/** Makes a new folder. New folders start hidden from the main list. */
	public static void newFolder(ServerPlayer player, String text) {
		PlayerData data = PlayerStore.get(player);
		String name = folderName(text);
		if (name.isEmpty()) return;
		if (data.folder(name) != null) {
			tell(player, false, "You already have a folder called " + name + ".");
			return;
		}
		if (data.folders.size() >= MAX_FOLDERS) {
			tell(player, false, "You can keep at most " + MAX_FOLDERS + " folders.");
			return;
		}
		data.folders.add(new PlayerData.Folder(name, false));
		folderChanged(player, "Folder " + name + " made.");
	}

	/** "old|new": renames a folder, and every spell in it follows. */
	public static void renameFolder(ServerPlayer player, String arg) {
		PlayerData data = PlayerStore.get(player);
		int bar = arg.indexOf('|');
		if (bar < 0) return;
		PlayerData.Folder folder = data.folder(arg.substring(0, bar));
		String name = folderName(arg.substring(bar + 1));
		if (folder == null || name.isEmpty()) return;
		if (folder.name.equals("Archive")) {
			tell(player, false, "The Archive keeps its name. Make a new folder for anything else.");
			return;
		}
		PlayerData.Folder clash = data.folder(name);
		if (clash != null && clash != folder) {
			tell(player, false, "You already have a folder called " + name + ".");
			return;
		}
		for (Spell spell : data.spells) if (folder.name.equalsIgnoreCase(spell.folder)) spell.folder = name;
		folder.name = name;
		folderChanged(player, "Folder renamed to " + name + ".");
	}

	/** Whether a folder's spells also show in the main list. */
	public static void toggleFolder(ServerPlayer player, String name) {
		PlayerData.Folder folder = PlayerStore.get(player).folder(name);
		if (folder == null) return;
		folder.shown = !folder.shown;
		folderChanged(player, folder.name + (folder.shown ? " is shown in the main list." : " is hidden from the main list."));
	}

	/** Removes a folder. Its spells go back to the main list; nothing is forgotten. */
	public static void deleteFolder(ServerPlayer player, String name) {
		PlayerData data = PlayerStore.get(player);
		PlayerData.Folder folder = data.folder(name);
		if (folder == null) return;
		if (folder.name.equals("Archive")) {
			tell(player, false, "The Archive always stays. Move spells out of it instead.");
			return;
		}
		for (Spell spell : data.spells) if (folder.name.equalsIgnoreCase(spell.folder)) spell.folder = null;
		data.folders.remove(folder);
		folderChanged(player, "Folder " + folder.name + " removed. Its spells are back in the main list.");
	}

	/** "id|folder": puts a spell in a folder; an empty folder name takes it back to the main list. */
	public static void move(ServerPlayer player, String arg) {
		PlayerData data = PlayerStore.get(player);
		int bar = arg.lastIndexOf('|');
		if (bar < 0) return;
		Spell spell = data.spell(arg.substring(0, bar));
		String name = arg.substring(bar + 1);
		PlayerData.Folder folder = data.folder(name);
		if (spell == null || (!name.isEmpty() && folder == null)) return;
		spell.folder = folder == null ? null : folder.name;
		refreshWands(player, spell);
		folderChanged(player, spell.label() + (folder == null ? " is back in the main list." : " moved to " + folder.name + "."));
	}

	/** The old single archive: puts a spell into a hidden folder called Archive, or brings it back. */
	public static void archive(ServerPlayer player, String id) {
		PlayerData data = PlayerStore.get(player);
		Spell spell = data.spell(id);
		if (spell == null) return;
		if ("Archive".equalsIgnoreCase(spell.folder)) {
			spell.folder = null;
		} else {
			if (data.folder("Archive") == null) data.folders.add(new PlayerData.Folder("Archive", false));
			spell.folder = data.folder("Archive").name;
		}
		folderChanged(player, spell.label() + (spell.folder == null ? " is back in your spellbook." : " is archived."));
	}

	private static void folderChanged(ServerPlayer player, String message) {
		PlayerStore.save(player);
		PlayerStore.syncState(player);
		tell(player, true, message);
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
