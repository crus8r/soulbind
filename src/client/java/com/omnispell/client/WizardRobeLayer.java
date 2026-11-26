package com.omnispell.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.omnispell.Omnispell;
import com.omnispell.item.OmniItems;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * The lower half of the Wizard's Robe. The robe is worn in the chest slot, so the
 * game draws only its upper half; this draws the skirt over the legs too, as
 * leggings would be drawn, whenever the leg slot is empty.
 */
public final class WizardRobeLayer extends RenderLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier SKIRT = Omnispell.id("textures/entity/equipment/humanoid_leggings/wizard_robe.png");
	private static final Identifier SKIRT_TRIM = Omnispell.id("textures/entity/equipment/humanoid_leggings/wizard_robe_trim.png");
	private static final int UNDYED = 0xFFB928FA;
	private static HumanoidModel<HumanoidRenderState> skirt;

	public WizardRobeLayer(RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> parent) {
		super(parent);
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (skirt == null) skirt = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_ARMOR.legs()));
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				@SuppressWarnings({"unchecked", "rawtypes"})
				RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> parent = (RenderLayerParent) renderer;
				helper.register(new WizardRobeLayer(parent));
			}
		});
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot) {
		if (skirt == null || state.chestEquipment == null || !state.chestEquipment.is(OmniItems.WIZARD_ROBE)) return;
		if (state.legsEquipment != null && !state.legsEquipment.isEmpty()) return; // real leggings are drawn instead
		DyedItemColor dyed = state.chestEquipment.get(DataComponents.DYED_COLOR);
		int color = dyed == null ? UNDYED : 0xFF000000 | dyed.rgb();
		renderColoredCutoutModel(skirt, SKIRT, pose, collector, light, state, color, 1);
		renderColoredCutoutModel(skirt, SKIRT_TRIM, pose, collector, light, state, -1, 2);
	}
}
