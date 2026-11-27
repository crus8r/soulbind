package com.omnispell.player;

import com.omnispell.spell.Spell;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Everything Omnispell remembers about one player. Kept through death. */
public final class PlayerData {
	public Set<String> words = new LinkedHashSet<>();       // known word ids
	public Set<String> spellTypes = new LinkedHashSet<>();  // unlocked spell types
	public List<Spell> spells = new ArrayList<>();          // the spellbook
	public Spell pending;                                    // crafted but not yet saved or discarded
	public int essence;
	public double[] mana = new double[ManaPool.values().length];
	public double corruption;
	public Set<String> bosses = new LinkedHashSet<>();      // bosses defeated
	/** A named folder of spells. When shown, its spells also appear in the main list. */
	public static final class Folder {
		public String name;
		public boolean shown;

		public Folder(String name, boolean shown) {
			this.name = name;
			this.shown = shown;
		}
	}

	public List<Folder> folders = new ArrayList<>();

	public Folder folder(String name) {
		if (name == null) return null;
		for (Folder folder : folders) if (folder.name.equalsIgnoreCase(name)) return folder;
		return null;
	}

	/** World time at which a totem-core wand can save this player again. */
	public long totemReadyAt;
	public boolean totemWarned;
	public int waypointPairs;                                // how many waypoint pairs this player has crafted
	public boolean initialized;
	/** True if this player switched off the wand's sprint boost in the Spellbook. */
	public boolean noWandSprint;

	public Spell spell(String id) {
		for (Spell spell : spells) {
			if (spell.id.equals(id)) return spell;
		}
		return null;
	}
}
