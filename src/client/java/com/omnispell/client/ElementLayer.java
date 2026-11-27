package com.omnispell.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.omnispell.Omnispell;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * The markings a conjured creature wears: veins of its element over its own hide (flame,
 * frost cracks, rippling water, vines, storm forks, runes...). The bright elements glow in
 * the dark and pulse slowly. Every other creature draws nothing.
 */
public final class ElementLayer extends RenderLayer<LivingEntityRenderState, EntityModel<LivingEntityRenderState>> {
	private static final Set<String> ELEMENTS = Set.of("fire", "water", "ice", "wind", "storm", "stone", "nature", "light", "shadow", "void", "blood", "arcane");
	private static final Set<String> GLOWING = Set.of("fire", "light", "storm", "void", "arcane", "ice");
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	private static final int FULL_BRIGHT = 0xF000F0;

	static {
		for (String element : ELEMENTS) TEXTURES.put(element, Omnispell.id("textures/entity/overlay/" + element + ".png"));
	}

	public ElementLayer(RenderLayerParent<LivingEntityRenderState, EntityModel<LivingEntityRenderState>> parent) {
		super(parent);
	}

	public static boolean knows(String element) {
		return element != null && ELEMENTS.contains(element);
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			@SuppressWarnings({"unchecked", "rawtypes"})
			RenderLayerParent<LivingEntityRenderState, EntityModel<LivingEntityRenderState>> parent = (RenderLayerParent) renderer;
			helper.register(new ElementLayer(parent));
		});
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, LivingEntityRenderState state, float yRot, float xRot) {
		String element = ClientState.elementStates.get(state);
		if (element == null || state.isInvisible) return;
		Identifier texture = TEXTURES.get(element);
		if (texture == null) return;
		int color = -1;
		boolean glows = GLOWING.contains(element);
		if (glows) {
			// a slow pulse, as if the magic in it breathes
			double pulse = 0.78 + 0.22 * Math.sin(state.ageInTicks * 0.12);
			int v = (int) Math.round(255 * pulse);
			color = 0xFF000000 | v << 16 | v << 8 | v;
		}
		renderColoredCutoutModel(getParentModel(), texture, pose, collector, glows ? FULL_BRIGHT : light, state, color, 3);
	}
}
