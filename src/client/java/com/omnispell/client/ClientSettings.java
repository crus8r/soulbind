package com.omnispell.client;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** This player's own display choices, kept in config/omnispell-client.json. */
public final class ClientSettings {
	/** true: the first press of a spell key shows the spell's name, a second press casts. false: one press casts. */
	public boolean doubleTap = true;
	/** How long after the first press a second press still counts, in ticks. */
	public int doubleTapTicks = 30;
	public boolean showMana = true;

	private static ClientSettings instance;

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("omnispell-client.json");
	}

	public static ClientSettings get() {
		if (instance == null) {
			try {
				if (Files.exists(path())) instance = new GsonBuilder().create().fromJson(Files.readString(path()), ClientSettings.class);
			} catch (Exception ignored) {
			}
			if (instance == null) instance = new ClientSettings();
		}
		return instance;
	}

	public static void save() {
		try {
			Files.createDirectories(path().getParent());
			Files.writeString(path(), new GsonBuilder().setPrettyPrinting().create().toJson(get()));
		} catch (Exception ignored) {
		}
	}
}
