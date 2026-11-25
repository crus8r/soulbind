package com.omnispell.data;

/** One word of power from the Omnispell dictionary. */
public final class Word {
	public String id;        // "base:Fire"
	public String type;      // base, command, charge, intention, source, attribute
	public String english;   // "Fire"
	public String spelling;  // "Ignis" — what the incantation shows
	public Integer tier;     // power tier from the website data (bases only)
	public String category;  // "Elemental", "Forbidden", "Color", ...
	public String description;
	public boolean hidden;
	public boolean excluded; // never obtainable (admin-only words)
	public int rarity;       // 0 = known from the start, 1 (common) .. 5 (rarest)
	public String explore;   // which exploration element / action / purpose this word counts as

	public int tierOr(int fallback) {
		return tier == null ? fallback : tier;
	}

	/** "Maxima (Amplify power)" -> "Maxima". */
	public String shortName() {
		int paren = english.indexOf(" (");
		return paren < 0 ? english : english.substring(0, paren);
	}
}
