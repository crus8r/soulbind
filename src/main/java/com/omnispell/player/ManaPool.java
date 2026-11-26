package com.omnispell.player;

/**
 * The five mana reserves from Omnispell. Each one pays for a different kind of
 * spell effect and refills slowly on its own. Sizes are in the website's units,
 * so numbers in spell descriptions line up with what is spent.
 */
public enum ManaPool {
	BARRIER("Barrier", 0x3b82f6, 100),       // shields, the wand's ward, and damage thrown back
	RESTORATION("Restoration", 0x22c55e, 75), // healing
	SPIRIT("Spirit", 0xa855f7, 60),           // holds on the mind (stun, charm, fear) and attack spells of shadow, light, void and blood
	PHASE("Phase", 0x06b6d4, 5),              // dodges, the wand sprint and air-walking
	FORCE("Force", 0xef4444, 60);             // attack spells and the wand's plain bolt

	public final String label;
	public final int color;
	public final double baseMax;

	ManaPool(String label, int color, double baseMax) {
		this.label = label;
		this.color = color;
		this.baseMax = baseMax;
	}

	public String attributeName() {
		return name().toLowerCase() + "_mana";
	}
}
