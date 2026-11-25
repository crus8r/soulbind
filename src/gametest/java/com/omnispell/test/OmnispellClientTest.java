package com.omnispell.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Starts a real game, joins a new world and exercises Omnispell. */
public class OmnispellClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("time set noon");
			context.waitTicks(60);
			context.takeScreenshot("01-joined");
			context.runOnClient(client -> client.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player)));
			context.waitTicks(10);
			context.takeScreenshot("02-inventory");
		}
	}
}
