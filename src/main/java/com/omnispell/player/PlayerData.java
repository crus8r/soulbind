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
	public int waypointPairs;                                // how many waypoint pairs this player has crafted
	public boolean initialized;

	public Spell spell(String id) {
		for (Spell spell : spells) {
			if (spell.id.equals(id)) return spell;
		}
		return null;
	}
}
