package com.omnispell.player;

/**
 * The five mana reserves from Omnispell. Each one pays for a different kind of
 * spell effect and refills slowly on its own. Sizes are in the website's units,
 * so numbers in spell descriptions line up with what is spent.
 */
public enum ManaPool {
	BARRIER("Barrier", 0x3b82f6, 100),       // shields
	RESTORATION("Restoration", 0x22c55e, 75), // healing
	CONTROL("Control", 0xa855f7, 6),          // turns of stun, charm, fear, confusion
	MIRROR("Mirror", 0xf59e0b, 60),           // damage reflected
	PHASE("Phase", 0x06b6d4, 5);              // dodges

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
