package com.omnispell.client;

import com.omnispell.data.Word;
import com.omnispell.data.WordBank;
import com.omnispell.item.WandItem;
import com.omnispell.net.OmniNet;
import com.omnispell.spell.Spell;
import com.omnispell.spell.SpellCrafting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Book of Words. Five tabs: Dictionary (the words you know), Craft (build a
 * spell), Spellbook (your spells and the wand's five slots), Guide and Bosses.
 */
public class BookScreen extends Screen {
	private static final String[] TABS = {"Dictionary", "Craft", "Spellbook", "Guide", "Bosses"};
	private static final String[] ADDABLE = {"intention", "charge", "source", "attribute"};

	// Colours (ARGB)
	private static final int PANEL = 0xF0141824, PANEL_EDGE = 0xFF3A4670, INSET = 0xFF0E111A, LINE = 0xFF2A3350;
	private static final int TEXT = 0xFFD8E0F0, DIM = 0xFF7A86A6, ACCENT = 0xFF7FB4FF, GOLD = 0xFFFAD66E, BAD = 0xFFFF7070, GOOD = 0xFF7FE0A0;

	// Remembered between openings of the book.
	private static int tab = 1;
	private static String craftType = "attack";
	private static List<String> slotTypes = new ArrayList<>();
	private static List<String> slotWords = new ArrayList<>();
	private static int activeSlot = 0;
	private static String dictFilter = "base";
	private static String selectedSpell;
	private static String guideSection = "Getting started";

	private record Region(int x, int y, int w, int h, Runnable action, Runnable alt) {
		boolean has(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final List<Region> regions = new ArrayList<>();
	private EditBox search;
	private int px, py, pw, ph;
	private int listScroll, detailScroll;
	private boolean addMenu;
	/** The spell slot whose key is being chosen, or -1. */
	private int capturing = -1;
	private int mouseX, mouseY;
	private List<Component> tooltip;

	public BookScreen() {
		super(Component.literal("Book of Words"));
		if (slotTypes.isEmpty()) resetSlots();
	}

	/** Opens the book on a given tab (0 Dictionary, 1 Craft, 2 Spellbook, 3 Guide, 4 Bosses). */
	public static BookScreen onTab(int index) {
		tab = Math.max(0, Math.min(TABS.length - 1, index));
		return new BookScreen();
	}

	public static void showGuide(String section) {
		guideSection = section;
	}

	/** Fills the Craft tab with a formula. Used by the automated tests and handy for "craft again". */
	public static void preset(String spellType, List<String> wordIds) {
		craftType = spellType;
		resetSlots();
		for (String id : wordIds) {
			Word word = WordBank.word(id);
			if (word == null) continue;
			int slot = -1;
			for (int s = 0; s < slotTypes.size(); s++) {
				if (slotTypes.get(s).equals(word.type) && slotWords.get(s) == null) slot = s;
			}
			if (slot < 0) {
				slotTypes.add(word.type);
				slotWords.add(null);
				slot = slotTypes.size() - 1;
			}
			slotWords.set(slot, id);
		}
	}

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	@Override
	protected void init() {
		pw = Math.min(400, width - 20);
		ph = Math.min(236, height - 20);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		search = new EditBox(font(), px + pw - 128, py + 30, 120, 14, Component.literal("Search"));
		search.setHint(Component.literal("Search words..."));
		search.setMaxLength(32);
		search.setResponder(text -> listScroll = 0);
		addRenderableWidget(search);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ drawing helpers

	private void hit(int x, int y, int w, int h, Runnable action) {
		regions.add(new Region(x, y, w, h, action, null));
	}

	private void hit(int x, int y, int w, int h, Runnable action, Runnable alt) {
		regions.add(new Region(x, y, w, h, action, alt));
	}

	private boolean over(int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean enabled, boolean lit, Runnable action) {
		boolean hover = enabled && over(x, y, w, h);
		g.fill(x, y, x + w, y + h, lit ? 0xFF2A3E78 : hover ? 0xFF222C4A : INSET);
		g.outline(x, y, w, h, lit ? ACCENT : hover ? 0xFF5A6AA0 : LINE);
		g.centeredText(font(), fit(label, w - 4), x + w / 2, y + (h - 8) / 2, enabled ? (lit ? 0xFFFFFFFF : TEXT) : 0xFF4A5268);
		if (enabled && action != null) hit(x, y, w, h, action);
	}

	private static String fit(String text, int width) {
		Font f = font();
		if (f.width(text) <= width) return text;
		return f.plainSubstrByWidth(text, Math.max(4, width - f.width("…"))) + "…";
	}

	private int wrapped(GuiGraphicsExtractor g, String text, int x, int y, int width, int color) {
		for (FormattedCharSequence line : font().split(Component.literal(text), width)) {
			g.text(font(), line, x, y, color, false);
			y += 10;
		}
		return y;
	}

	private static String label(String wordType) {
		return Character.toUpperCase(wordType.charAt(0)) + wordType.substring(1);
	}

	private static int typeColor(String wordType) {
		return switch (wordType) {
			case "base" -> 0xFF6FA8FF;
			case "command" -> 0xFF9A8CFF;
			case "intention" -> 0xFF6FCF8F;
			case "charge" -> 0xFFFF8A60;
			case "source" -> 0xFFFAD66E;
			default -> 0xFF60D0D0;
		};
	}

	private static void send(String action, String arg) {
		ClientPlayNetworking.send(new OmniNet.BookAction(action, arg));
	}

	// ------------------------------------------------------------------ frame

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
		mouseX = mx;
		mouseY = my;
		regions.clear();
		tooltip = null;

		g.fill(px, py, px + pw, py + ph, PANEL);
		g.outline(px, py, pw, ph, PANEL_EDGE);
		g.outline(px - 1, py - 1, pw + 2, ph + 2, 0xFF0A0C14);

		// Tabs
		int tw = 68;
		for (int i = 0; i < TABS.length; i++) {
			int index = i;
			button(g, px + 6 + i * (tw + 3), py + 6, tw, 16, TABS[i], true, tab == i, () -> {
				tab = index;
				listScroll = 0;
				detailScroll = 0;
				addMenu = false;
				search.setValue("");
			});
		}
		g.text(font(), "✦ " + ClientState.essence, px + pw - 44, py + 10, 0xFF7FD4FF, false);
		g.horizontalLine(px + 4, px + pw - 5, py + 25, LINE);

		search.visible = tab == 0 || (tab == 1 && ClientState.pending == null);

		switch (tab) {
			case 0 -> dictionary(g);
			case 1 -> craft(g);
			case 2 -> spellbook(g);
			case 3 -> guide(g);
			default -> bosses(g);
		}

		// Message line
		if (!ClientState.message.isEmpty() && ClientState.clientTick - ClientState.messageAtTick < 160) {
			g.text(font(), fit(ClientState.message, pw - 16), px + 8, py + ph - 12, ClientState.messageOk ? GOOD : BAD, false);
		}

		super.extractRenderState(g, mx, my, partial);
		if (tooltip != null) g.setComponentTooltipForNextFrame(font(), tooltip, mx, my);
	}

	// ------------------------------------------------------------------ dictionary

	private void dictionary(GuiGraphicsExtractor g) {
		search.setY(py + 30);
		int x = px + 8, y = py + 30;
		int bw = 37;
		List<String> filters = WordBank.WORD_TYPES;
		for (int i = 0; i < filters.size(); i++) {
			String type = filters.get(i);
			button(g, x + i * (bw + 2), y, bw, 14, label(type).substring(0, Math.min(6, type.length())), true, dictFilter.equals(type), () -> {
				dictFilter = type;
				listScroll = 0;
			});
		}

		List<Word> all = WordBank.wordsOfType(dictFilter);
		String query = search.getValue().trim().toLowerCase();
		List<Word> known = new ArrayList<>();
		for (Word word : all) {
			if (!ClientState.words.contains(word.id)) continue;
			if (!query.isEmpty() && !word.english.toLowerCase().contains(query) && !word.spelling.toLowerCase().contains(query)) continue;
			known.add(word);
		}
		known.sort((a, b) -> a.spelling.compareToIgnoreCase(b.spelling));
		int knownOfType = 0;
		for (Word word : all) if (ClientState.words.contains(word.id)) knownOfType++;

		int top = py + 50, bottom = py + ph - 28;
		int cols = 3, cw = (pw - 16) / cols, rh = 12;
		int rows = (bottom - top) / rh;
		int maxScroll = Math.max(0, (known.size() + cols - 1) / cols - rows);
		listScroll = Math.max(0, Math.min(listScroll, maxScroll));
		g.fill(px + 6, top - 2, px + pw - 6, bottom, INSET);
		for (int i = listScroll * cols; i < known.size() && i < (listScroll + rows) * cols; i++) {
			Word word = known.get(i);
			int cx = px + 10 + (i % cols) * cw;
			int cy = top + (i / cols - listScroll) * rh;
			g.text(font(), word.spelling, cx, cy, typeColor(word.type), false);
			g.text(font(), fit(word.shortName(), cw - font().width(word.spelling) - 10), cx + font().width(word.spelling) + 5, cy, DIM, false);
			if (over(cx, cy, cw - 4, rh)) tooltip = wordTooltip(word);
		}
		if (known.isEmpty()) g.centeredText(font(), query.isEmpty() ? "You know no " + dictFilter + " words yet." : "No match.", px + pw / 2, top + 30, DIM);

		int total = 0;
		for (Word word : WordBank.words()) if (!word.excluded) total++;
		g.text(font(), label(dictFilter) + " words: " + knownOfType + " of " + all.size() + " known   ·   All words: " + ClientState.words.size() + " of " + total,
				px + 8, py + ph - 24, DIM, false);
	}

	private static List<Component> wordTooltip(Word word) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(word.spelling + " — " + word.english));
		String info = label(word.type) + (word.category != null ? " · " + word.category : "") + (word.tier != null ? " · power " + word.tier : "");
		lines.add(Component.literal(info).withStyle(net.minecraft.ChatFormatting.GRAY));
		if (word.description != null && !word.description.isBlank()) lines.add(Component.literal(word.description).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
		return lines;
	}

	// ------------------------------------------------------------------ craft

	private static void resetSlots() {
		slotTypes = new ArrayList<>();
		slotWords = new ArrayList<>();
		WordBank.SpellType type = WordBank.spellType(craftType);
		if (type != null) {
			for (WordBank.Slot slot : type.wordSlots) {
				if (slot.required || slot.type.equals("command") || slot.type.equals("intention")) {
					slotTypes.add(slot.type);
					slotWords.add(null);
				}
			}
		}
		activeSlot = 0;
	}

	private static int filled() {
		int n = 0;
		for (String w : slotWords) if (w != null) n++;
		return n;
	}

	private static boolean required(String wordType) {
		WordBank.SpellType type = WordBank.spellType(craftType);
		if (type == null) return false;
		for (WordBank.Slot slot : type.wordSlots) if (slot.type.equals(wordType) && slot.required) return true;
		return false;
	}

	private static boolean canAdd(String wordType) {
		if (craftType.equals("exploration") && !wordType.equals("charge")) return false;
		int have = 0;
		for (String t : slotTypes) if (t.equals(wordType)) have++;
		return have < (wordType.equals("attribute") ? 2 : 1) && slotTypes.size() < ClientState.maxWords;
	}

	private void craft(GuiGraphicsExtractor g) {
		if (ClientState.pending != null) {
			pending(g);
			return;
		}
		// Spell type chips
		search.setY(py + 48);
		int x = px + 8, y = py + 30;
		int i = 0;
		int chipW = (pw - 16 - 6 * 2) / 7;
		for (WordBank.SpellType type : WordBank.spellTypes()) {
			boolean unlocked = ClientState.spellTypes.contains(type.id);
			String name = switch (type.id) {
				case "enchantment" -> "Enchant";
				case "conjuration" -> "Conjure";
				case "exploration" -> "Explore";
				default -> type.name.replace(" Spells", "");
			};
			int bx = x + i * (chipW + 2);
			button(g, bx, y, chipW, 14, name, unlocked, craftType.equals(type.id), () -> {
				craftType = type.id;
				resetSlots();
				listScroll = 0;
				addMenu = false;
			});
			if (over(bx, y, chipW, 14)) {
				tooltip = new ArrayList<>();
				tooltip.add(Component.literal(type.name));
				tooltip.add(Component.literal(type.description).withStyle(net.minecraft.ChatFormatting.GRAY));
				if (!unlocked) tooltip.add(Component.literal("Locked. Find the treatise.").withStyle(net.minecraft.ChatFormatting.RED));
			}
			i++;
		}

		// Formula slots, left column
		int sx = px + 8, sy = py + 50, sw = 150, sh = 17;
		for (int s = 0; s < slotTypes.size(); s++) {
			int index = s;
			String wordType = slotTypes.get(s);
			Word word = slotWords.get(s) == null ? null : WordBank.word(slotWords.get(s));
			int by = sy + s * (sh + 3);
			boolean active = s == activeSlot;
			g.fill(sx, by, sx + sw, by + sh, active ? 0xFF1C2744 : INSET);
			g.outline(sx, by, sw, sh, active ? typeColor(wordType) : LINE);
			g.fill(sx, by, sx + 3, by + sh, typeColor(wordType));
			String tag = label(wordType) + (required(wordType) ? " *" : "");
			if (word == null) {
				g.text(font(), tag, sx + 7, by + 5, DIM, false);
			} else {
				g.text(font(), word.spelling, sx + 7, by + 5, 0xFFFFFFFF, false);
				g.text(font(), fit(word.shortName(), sw - font().width(word.spelling) - 30), sx + 12 + font().width(word.spelling), by + 5, DIM, false);
			}
			hit(sx, by, sw - 14, sh, () -> {
				activeSlot = index;
				listScroll = 0;
			}, () -> slotWords.set(index, null));
			// x: clear the word, or remove an optional empty slot
			g.text(font(), "×", sx + sw - 10, by + 5, over(sx + sw - 14, by, 14, sh) ? BAD : DIM, false);
			hit(sx + sw - 14, by, 14, sh, () -> {
				if (slotWords.get(index) != null) slotWords.set(index, null);
				else if (!required(slotTypes.get(index))) {
					slotTypes.remove(index);
					slotWords.remove(index);
					activeSlot = Math.max(0, Math.min(activeSlot, slotTypes.size() - 1));
				}
			});
		}
		// [+] add an optional part
		int addY = sy + slotTypes.size() * (sh + 3);
		boolean anyAddable = false;
		for (String t : ADDABLE) anyAddable |= canAdd(t);
		if (anyAddable) {
			button(g, sx, addY, 18, 14, "+", true, addMenu, () -> addMenu = !addMenu);
			if (addMenu) {
				int ax = sx + 22;
				for (String t : ADDABLE) {
					if (!canAdd(t)) continue;
					int w = font().width(label(t)) + 8;
					button(g, ax, addY, w, 14, label(t), true, false, () -> {
						slotTypes.add(t);
						slotWords.add(null);
						activeSlot = slotTypes.size() - 1;
						addMenu = false;
						listScroll = 0;
					});
					ax += w + 2;
				}
			} else {
				g.text(font(), "add a part", sx + 24, addY + 3, DIM, false);
			}
		}

		// Incantation and craft button, bottom left
		List<String> chosen = new ArrayList<>();
		for (String w : slotWords) if (w != null) chosen.add(w);
		int by = py + ph - 62;
		g.fill(sx, by, sx + sw, by + 30, INSET);
		g.outline(sx, by, sw, 30, LINE);
		g.centeredText(font(), "INCANTATION", sx + sw / 2, by + 3, DIM);
		String name = chosen.isEmpty() ? "…" : Spell.incantation(chosen);
		g.centeredText(font(), fit(name, sw - 6), sx + sw / 2, by + 16, ACCENT);
		int cost = SpellCrafting.essenceCost(Math.max(1, chosen.size()));
		boolean ready = true;
		for (int s = 0; s < slotTypes.size(); s++) if (slotWords.get(s) == null && required(slotTypes.get(s))) ready = false;
		ready &= !chosen.isEmpty();
		g.text(font(), chosen.size() + " / " + ClientState.maxWords + " words", sx, by + 36, chosen.size() > ClientState.maxWords ? BAD : DIM, false);
		button(g, sx + 62, by + 33, sw - 62, 16, "CRAFT  ✦ " + cost, ready, false, () -> {
			ClientPlayNetworking.send(new OmniNet.CraftSpell(craftType, String.join(",", chosen)));
			ClientState.say(true, "...");
		});

		// Word list for the active slot, right column
		int lx = px + 166, lw = pw - 174, top = py + 68, bottom = py + ph - 28;
		g.fill(lx, top - 2, lx + lw, bottom, INSET);
		if (slotTypes.isEmpty()) return;
		activeSlot = Math.max(0, Math.min(activeSlot, slotTypes.size() - 1));
		String wordType = slotTypes.get(activeSlot);
		g.text(font(), label(wordType) + " words", lx, py + 51, typeColor(wordType), false);

		String query = search.getValue().trim().toLowerCase();
		List<Word> fits = new ArrayList<>();
		List<Word> odd = new ArrayList<>();
		for (Word word : WordBank.wordsOfType(wordType)) {
			if (!ClientState.words.contains(word.id) || slotWords.contains(word.id)) continue;
			if (!query.isEmpty() && !word.english.toLowerCase().contains(query) && !word.spelling.toLowerCase().contains(query)) continue;
			boolean suits = !wordType.equals("intention") || WordBank.intentionFits(craftType, word);
			(suits ? fits : odd).add(word);
		}
		fits.sort((a, b) -> a.spelling.compareToIgnoreCase(b.spelling));
		odd.sort((a, b) -> a.spelling.compareToIgnoreCase(b.spelling));
		List<Word> list = new ArrayList<>(fits);
		list.addAll(odd);

		int cols = 2, cw = lw / cols, rh = 12;
		int rows = (bottom - top) / rh;
		int maxScroll = Math.max(0, (list.size() + cols - 1) / cols - rows);
		listScroll = Math.max(0, Math.min(listScroll, maxScroll));
		for (int n = listScroll * cols; n < list.size() && n < (listScroll + rows) * cols; n++) {
			Word word = list.get(n);
			boolean suits = n < fits.size();
			int cx = lx + 4 + (n % cols) * cw;
			int cy = top + (n / cols - listScroll) * rh;
			boolean hover = over(cx - 2, cy - 1, cw - 2, rh);
			if (hover) g.fill(cx - 2, cy - 1, cx + cw - 4, cy + rh - 1, 0xFF222C4A);
			g.text(font(), word.spelling, cx, cy, suits ? (hover ? 0xFFFFFFFF : typeColor(wordType)) : 0xFF505870, false);
			g.text(font(), fit(word.shortName(), cw - font().width(word.spelling) - 12), cx + font().width(word.spelling) + 5, cy, suits ? DIM : 0xFF3E4458, false);
			if (hover) {
				tooltip = wordTooltip(word);
				if (!suits) tooltip.add(Component.literal("Unusual for this kind of spell. The Oracle will adapt.").withStyle(net.minecraft.ChatFormatting.GOLD));
			}
			hit(cx - 2, cy - 1, cw - 2, rh, () -> {
				slotWords.set(activeSlot, word.id);
				for (int s = 0; s < slotTypes.size(); s++) {
					if (slotWords.get(s) == null) {
						activeSlot = s;
						break;
					}
				}
				listScroll = 0;
				search.setValue("");
			});
		}
		if (list.isEmpty()) g.centeredText(font(), query.isEmpty() ? "No " + wordType + " words left to use." : "No match.", lx + lw / 2, top + 30, DIM);
	}

	private void pending(GuiGraphicsExtractor g) {
		Spell spell = ClientState.pending;
		int x = px + 16, y = py + 34, w = pw - 32;
		g.centeredText(font(), "A NEW SPELL", px + pw / 2, y, DIM);
		y = spellDetails(g, spell, x, y + 14, w, py + ph - 50);
		int bw = 110, by = py + ph - 44;
		button(g, px + pw / 2 - bw - 6, by, bw, 18, "Save to Spellbook", true, true, () -> send("save", ""));
		button(g, px + pw / 2 + 6, by, bw, 18, "Discard", true, false, () -> send("discard", ""));
		g.centeredText(font(), "Discarding returns half the essence.", px + pw / 2, by + 22, DIM);
	}

	/** Draws a spell's name, tier line, description and effect. Returns the y below the text. */
	private int spellDetails(GuiGraphicsExtractor g, Spell spell, int x, int y, int w, int maxY) {
		g.text(font(), fit(spell.name, w), x, y, SpellLook.tierColor(spell.tier), false);
		y += 12;
		String typeName = WordBank.spellType(spell.type) == null ? spell.type : WordBank.spellType(spell.type).name.replace(" Spells", "");
		String line = "Tier " + spell.tier + " · " + typeName + (spell.school == null || spell.school.isBlank() || spell.isExploration() ? "" : " · " + spell.school);
		g.text(font(), line, x, y, DIM, false);
		y += 12;
		if (spell.dark) {
			g.text(font(), "Dark magic: each cast adds corruption.", x, y, 0xFFC080FF, false);
			y += 11;
		}
		if (spell.offline) {
			g.text(font(), "Unstable: crafted without the Oracle.", x, y, GOLD, false);
			y += 11;
		}
		if (spell.fizzle) {
			g.text(font(), "These words refuse each other. It will only fizzle.", x, y, BAD, false);
			y += 11;
		}
		g.enableScissor(x - 2, y, x + w + 2, maxY);
		int ty = y + 2 - detailScroll * 10;
		ty = wrapped(g, spell.description, x, ty, w, TEXT);
		if (spell.effect != null && !spell.effect.isBlank()) ty = wrapped(g, spell.effect, x, ty + 4, w, 0xFFA8B8D8);
		StringBuilder words = new StringBuilder();
		for (String id : spell.words) {
			Word word = WordBank.word(id);
			if (word != null) words.append(words.isEmpty() ? "" : " + ").append(word.shortName());
		}
		ty = wrapped(g, words.toString(), x, ty + 4, w, DIM);
		g.disableScissor();
		return Math.min(ty, maxY);
	}

	// ------------------------------------------------------------------ spellbook

	private void spellbook(GuiGraphicsExtractor g) {
		List<Spell> spells = ClientState.spells;
		int lx = px + 8, lw = 150, top = py + 32, bottom = py + ph - 74, rh = 13;
		g.fill(lx, top - 2, lx + lw, bottom, INSET);
		int rows = (bottom - top) / rh;
		listScroll = Math.max(0, Math.min(listScroll, Math.max(0, spells.size() - rows)));
		if (spells.isEmpty()) g.text(font(), "No spells yet.", lx + 6, top + 4, DIM, false);
		Spell selected = null;
		for (Spell spell : spells) if (spell.id.equals(selectedSpell)) selected = spell;
		if (selected == null && !spells.isEmpty()) {
			selected = spells.get(0);
			selectedSpell = selected.id;
		}
		for (int i = listScroll; i < spells.size() && i < listScroll + rows; i++) {
			Spell spell = spells.get(i);
			int cy = top + (i - listScroll) * rh;
			boolean isSelected = spell.id.equals(selectedSpell);
			if (isSelected || over(lx, cy - 1, lw, rh)) g.fill(lx, cy - 1, lx + lw, cy + rh - 1, isSelected ? 0xFF2A3E78 : 0xFF222C4A);
			g.fill(lx + 2, cy + 1, lx + 9, cy + 8, 0xFF000000 | SpellLook.color(spell));
			g.text(font(), SpellLook.glyph(spell), lx + 12, cy + 1, DIM, false);
			g.text(font(), fit(spell.name, lw - 26), lx + 21, cy + 1, SpellLook.tierColor(spell.tier), false);
			hit(lx, cy - 1, lw, rh, () -> {
				selectedSpell = spell.id;
				detailScroll = 0;
			});
		}

		int dx = px + 166, dw = pw - 176;
		ItemStack wand = minecraft.player == null ? ItemStack.EMPTY : WandItem.heldWand(minecraft.player);
		if (selected != null) {
			Spell shown = selected;
			spellDetails(g, shown, dx, top, dw, bottom - 22);
			int by = bottom - 18;
			if (!wand.isEmpty()) {
				g.text(font(), "Set to slot:", dx, by + 4, DIM, false);
				for (int s = 0; s < WandItem.SLOTS; s++) {
					int slot = s;
					Spell there = WandItem.spell(wand, s);
					boolean here = there != null && there.id.equals(shown.id);
					button(g, dx + 62 + s * 18, by, 16, 15, String.valueOf(s + 1), true, here, () -> send("equip", slot + ":" + (here ? "" : shown.id)));
				}
			} else {
				g.text(font(), "Hold a wand to equip spells.", dx, by + 4, DIM, false);
			}
			button(g, dx + dw - 44, by, 44, 15, "Forget", true, false, () -> {
				send("forget", shown.id);
				selectedSpell = null;
			});
		}

		// The held wand's five slots, each with its key
		int wy = py + ph - 68;
		g.horizontalLine(px + 4, px + pw - 5, wy - 4, LINE);
		ClientSettings settings = ClientSettings.get();
		String tapLabel = "Double-tap to cast: " + (settings.doubleTap ? "on" : "off");
		int tapW = font().width(tapLabel) + 10;
		button(g, px + pw - 8 - tapW, wy - 2, tapW, 12, tapLabel, true, settings.doubleTap, () -> {
			settings.doubleTap = !settings.doubleTap;
			ClientSettings.save();
		});
		if (over(px + pw - 8 - tapW, wy - 2, tapW, 12)) {
			tooltip = new ArrayList<>();
			tooltip.add(Component.literal("On: the first press of a spell key shows the spell, the second casts it."));
			tooltip.add(Component.literal("Off: one press casts.").withStyle(net.minecraft.ChatFormatting.GRAY));
		}
		if (wand.isEmpty()) {
			g.text(font(), "No wand in hand.", px + 8, wy + 1, DIM, false);
			return;
		}
		g.text(font(), wand.getHoverName().getString(), px + 8, wy + 1, ACCENT, false);
		int sw = (pw - 16 - 4 * 3) / 5;
		for (int s = 0; s < WandItem.SLOTS; s++) {
			int slot = s;
			Spell spell = WandItem.spell(wand, s);
			int sx = px + 8 + s * (sw + 3);
			boolean isSel = s == WandItem.selected(wand);
			g.fill(sx, wy + 13, sx + sw, wy + 41, INSET);
			g.outline(sx, wy + 13, sw, 28, isSel ? ACCENT : LINE);
			g.text(font(), spell == null ? "—" : fit(spell.name, sw - 6), sx + 3, wy + 16, spell == null ? DIM : SpellLook.tierColor(spell.tier), false);
			hit(sx, wy + 13, sw, 14, () -> send("select", String.valueOf(slot)), () -> send("equip", slot + ":"));
			if (spell != null && over(sx, wy + 13, sw, 14)) {
				tooltip = new ArrayList<>();
				tooltip.add(Component.literal(spell.name));
				tooltip.add(Component.literal("Click to select · Right-click to clear").withStyle(net.minecraft.ChatFormatting.GRAY));
			}
			// The key for this slot: click, then press the key you want.
			boolean waiting = capturing == s;
			String keyText = waiting ? "press a key" : "Key: " + OmnispellClient.CAST[s].getTranslatedKeyMessage().getString();
			int ky = wy + 28;
			boolean hoverKey = over(sx + 1, ky, sw - 2, 12);
			g.fill(sx + 1, ky, sx + sw - 1, ky + 12, waiting ? 0xFF3A2E10 : hoverKey ? 0xFF222C4A : 0xFF161B2A);
			g.centeredText(font(), fit(keyText, sw - 6), sx + sw / 2, ky + 2, waiting ? GOLD : hoverKey ? 0xFFFFFFFF : DIM);
			hit(sx + 1, ky, sw - 2, 12, () -> capturing = waiting ? -1 : slot);
			if (hoverKey && !waiting) {
				tooltip = new ArrayList<>();
				tooltip.add(Component.literal("Click, then press the key for spell " + (slot + 1) + "."));
				tooltip.add(Component.literal("Escape cancels. Delete clears the key.").withStyle(net.minecraft.ChatFormatting.GRAY));
			}
		}
	}

	// ------------------------------------------------------------------ guide

	private void guide(GuiGraphicsExtractor g) {
		Map<String, List<String>> sections = Guide.sections();
		int lx = px + 8, top = py + 32, lw = 104;
		int i = 0;
		for (String name : sections.keySet()) {
			button(g, lx, top + i * 16, lw, 14, name, true, name.equals(guideSection), () -> {
				guideSection = name;
				detailScroll = 0;
			});
			i++;
		}
		int dx = lx + lw + 10, dw = pw - lw - 30, bottom = py + ph - 16;
		g.fill(dx - 4, top - 2, px + pw - 6, bottom, INSET);
		List<String> paragraphs = sections.getOrDefault(guideSection, List.of());
		g.enableScissor(dx - 4, top, px + pw - 6, bottom - 2);
		int y = top + 2 - detailScroll * 10;
		for (String p : paragraphs) {
			boolean heading = p.equals(p.toUpperCase()) && p.length() < 12;
			y = wrapped(g, p, dx, y, dw, heading ? GOLD : TEXT) + 5;
		}
		g.disableScissor();
		int contentHeight = y + detailScroll * 10 - top;
		if (contentHeight <= bottom - top && detailScroll > 0) detailScroll = Math.max(0, detailScroll - 1);
	}

	// ------------------------------------------------------------------ bosses

	private void bosses(GuiGraphicsExtractor g) {
		int x = px + 16, y = py + 36, w = pw - 32;
		g.text(font(), "FOES DEFEATED", x, y, DIM, false);
		y += 14;
		String[][] known = {
				{"wither", "The Wither", "Built from soul sand and three skulls. Guards a word of ruin."},
				{"warden", "The Warden", "Sleeps beneath the deep dark. Guards a word of the unseen."},
				{"ender_dragon", "The Ender Dragon", "Circles the End. Guards one of the rarest words."},
		};
		for (String[] boss : known) {
			boolean done = ClientState.bosses.contains(boss[0]);
			g.fill(x, y, x + w, y + 26, INSET);
			g.outline(x, y, w, 26, done ? GOOD : LINE);
			g.text(font(), done ? "✔" : "·", x + 6, y + 9, done ? GOOD : DIM, false);
			g.text(font(), boss[1], x + 20, y + 4, done ? 0xFFFFFFFF : TEXT, false);
			g.text(font(), fit(boss[2], w - 26), x + 20, y + 15, DIM, false);
			y += 30;
		}
		// Razza stands apart.
		boolean razza = ClientState.bosses.contains("razza");
		y += 4;
		g.fill(x, y, x + w, y + 34, 0xFF1A1020);
		g.outline(x, y, w, 34, razza ? GOLD : 0xFF6A3A8A);
		g.outline(x + 1, y + 1, w - 2, 32, razza ? 0xFF8A6A20 : 0xFF2A1838);
		g.text(font(), razza ? "✔" : "?", x + 6, y + 13, razza ? GOLD : 0xFFC080FF, false);
		g.text(font(), "Razza, the Unbound", x + 20, y + 6, razza ? GOLD : 0xFFE0B0FF, false);
		g.text(font(), razza ? "The seals are broken." : "He believes his seals are permanent. He is not here. Not yet.", x + 20, y + 19, DIM, false);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (capturing >= 0) {
			net.minecraft.client.KeyMapping mapping = OmnispellClient.CAST[capturing];
			if (!event.isEscape()) {
				boolean clear = event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_DELETE;
				mapping.setKey(clear ? com.mojang.blaze3d.platform.InputConstants.UNKNOWN : com.mojang.blaze3d.platform.InputConstants.getKey(event));
				net.minecraft.client.KeyMapping.resetMapping();
				minecraft.options.save();
			}
			capturing = -1;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		boolean right = event.buttonInfo().button() == 1;
		// Later regions are drawn on top, so they get the click first.
		for (int i = regions.size() - 1; i >= 0; i--) {
			Region region = regions.get(i);
			if (!region.has(event.x(), event.y())) continue;
			Runnable action = right ? region.alt : region.action;
			if (action == null) continue;
			action.run();
			Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
			return true;
		}
		if (addMenu) addMenu = false;
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		boolean overList = tab == 0 || (tab == 1 && x >= px + 166) || (tab == 2 && x < px + 162);
		int step = scrollY > 0 ? -1 : 1;
		if (overList) listScroll = Math.max(0, listScroll + step);
		else detailScroll = Math.max(0, Math.min(200, detailScroll + step * 2));
		return true;
	}
}
