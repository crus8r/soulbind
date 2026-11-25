package com.omnispell;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Balance knobs and the address of the spell generator. Written to
 * config/omnispell.json on first run so it can be changed without rebuilding.
 */
public final class OmniConfig {
	/** The Omnispell website's own spell generator (Lovable Cloud). The key is the site's public key. */
	public String generatorUrl = "https://htzuhpsejbpodpqqnfqz.supabase.co/functions/v1/generate-spell";
	public String generatorKey = "";
	/** false = never call the generator; every combat spell is made by the built-in fallback rules. */
	public boolean useGenerator = true;
	public int generatorTimeoutSeconds = 45;

	/** One website "turn" lasts this many seconds in Minecraft (durations and cooldowns). */
	public double turnSeconds = 5.0;
	/** Website numbers (damage, healing, shields) are multiplied by this. Players have 100 HP there, 20 here. */
	public double valueScale = 0.4;
	/** Shortest wait between two casts of the same spell, in seconds, even for tier 1 spells. */
	public double minimumCooldownSeconds = 1.5;
	/** How far a spell can reach, in blocks. */
	public double spellRange = 28.0;

	public int essenceCap = 100;
	public int startingEssence = 40;
	public int essencePerWellPull = 10;
	public int essencePerPotion = 50;
	public int wellPullsPerPotion = 3;

	/** Seconds for an empty mana reserve to refill completely. */
	public double manaRefillSeconds = 120.0;

	/** Chance that a naturally generated chest holds a word scroll. */
	public double scrollChestChance = 0.22;
	/** Chance that a hostile mob killed by a player drops a word scroll. */
	public double scrollMobChance = 0.03;

	public static OmniConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("omnispell.json");
		OmniConfig config = new OmniConfig();
		try {
			if (Files.exists(path)) {
				OmniConfig read = new GsonBuilder().create().fromJson(Files.readString(path), OmniConfig.class);
				if (read != null) config = read;
			}
			if (config.generatorKey == null || config.generatorKey.isEmpty()) config.generatorKey = DefaultKey.VALUE;
			Files.createDirectories(path.getParent());
			Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(config));
		} catch (Exception e) {
			Omnispell.LOG.warn("Could not read or write {}; using defaults", path, e);
			if (config.generatorKey == null || config.generatorKey.isEmpty()) config.generatorKey = DefaultKey.VALUE;
		}
		return config;
	}

	public int turnsToTicks(double turns) {
		return (int) Math.round(turns * turnSeconds * 20.0);
	}
}
