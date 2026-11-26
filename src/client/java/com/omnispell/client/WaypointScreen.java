package com.omnispell.client;

import com.omnispell.net.OmniNet;
import com.omnispell.waypoint.Waypoints;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Rename a waypoint pair and pick its colour. Both ends change together. */
public class WaypointScreen extends Screen {
	private final String pairId;
	private final String startName;
	private int color;
	private EditBox nameBox;
	private int px, py;
	private static final int PW = 232, PH = 132, CELL = 20;

	public WaypointScreen(String pairId, String name, int color) {
		super(Component.literal("Waypoint"));
		this.pairId = pairId;
		this.startName = name;
		this.color = Math.floorMod(color, Waypoints.COLORS.length);
	}

	@Override
	protected void init() {
		px = (width - PW) / 2;
		py = (height - PH) / 2;
		Font font = Minecraft.getInstance().font;
		nameBox = new EditBox(font, px + 12, py + 28, PW - 24, 16, Component.literal("Name"));
		nameBox.setMaxLength(32);
		nameBox.setValue(startName);
		nameBox.setHint(Component.literal("Name this pair..."));
		addRenderableWidget(nameBox);
		setInitialFocus(nameBox);
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> done()).bounds(px + PW / 2 - 40, py + PH - 24, 80, 18).build());
	}

	private void done() {
		ClientPlayNetworking.send(new OmniNet.EditWaypoint(pairId, nameBox.getValue(), color));
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		Font font = Minecraft.getInstance().font;
		g.fill(px, py, px + PW, py + PH, 0xF0141824);
		g.outline(px, py, PW, PH, 0xFF000000 | Waypoints.COLORS[color]);
		g.text(font, "Waypoint pair", px + 12, py + 10, 0xFFD8E0F0, false);
		g.text(font, Waypoints.COLOR_NAMES[color], px + PW - 12 - font.width(Waypoints.COLOR_NAMES[color]), py + 10, 0xFF000000 | Waypoints.COLORS[color], false);
		for (int i = 0; i < Waypoints.COLORS.length; i++) {
			int cx = px + 16 + (i % 10) * CELL;
			int cy = py + 52 + (i / 10) * CELL;
			g.fill(cx, cy, cx + CELL - 3, cy + CELL - 3, 0xFF000000 | Waypoints.COLORS[i]);
			g.outline(cx - 1, cy - 1, CELL - 1, CELL - 1, i == color ? 0xFFFFFFFF : 0xFF2A3350);
		}
		super.extractRenderState(g, mouseX, mouseY, partial);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (int i = 0; i < Waypoints.COLORS.length; i++) {
			int cx = px + 16 + (i % 10) * CELL;
			int cy = py + 52 + (i / 10) * CELL;
			if (event.x() >= cx && event.x() < cx + CELL - 3 && event.y() >= cy && event.y() < cy + CELL - 3) {
				color = i;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}
}
