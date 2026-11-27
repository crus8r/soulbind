package com.omnispell.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.omnispell.Omnispell;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Dark veins over a corrupted player's body. They creep in from half corruption and spread
 * as it fills, in four stages, for everyone who can see the player.
 */
public final class VeinLayer extends RenderLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier[] VEINS = {
			Omnispell.id("textures/entity/corruption_veins_1.png"), Omnispell.id("textures/entity/corruption_veins_2.png"),
			Omnispell.id("textures/entity/corruption_veins_3.png"), Omnispell.id("textures/entity/corruption_veins_4.png")};

	public VeinLayer(RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> parent) {
		super(parent);
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) { // only players ever have vein data, so the rest draw nothing
				@SuppressWarnings({"unchecked", "rawtypes"})
				RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> parent = (RenderLayerParent) renderer;
				helper.register(new VeinLayer(parent));
			}
		});
	}

	/** Which stage of veins a corruption level shows: none below half, then four steps to full. */
	public static int stage(int corruption) {
		if (corruption < 50) return 0;
		if (corruption < 65) return 1;
		if (corruption < 80) return 2;
		if (corruption < 95) return 3;
		return 4;
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot) {
		Integer stage = ClientState.veinStates.get(state);
		if (stage == null || stage <= 0) return;
		renderColoredCutoutModel(getParentModel(), VEINS[Math.min(4, stage) - 1], pose, collector, light, state, -1, 3);
	}
}
