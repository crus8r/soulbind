package com.omnispell.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.omnispell.Omnispell;
import com.omnispell.item.OmniItems;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * The robe's belt and the sash that hangs from it, drawn over the dyed leather
 * so the chestpiece reads as a robe. The texture layout must match tools/gen_assets.py.
 */
public final class WizardRobeLayer extends RenderLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier BASE = Omnispell.id("textures/entity/wizard_robe_parts.png");
	private static final Identifier TRIM = Omnispell.id("textures/entity/wizard_robe_parts_trim.png");
	private static final int UNDYED = 0xFF4031A6;
	private static final ModelLayerLocation BELT = new ModelLayerLocation(Omnispell.id("wizard_robe"), "belt");
	private static Model<Object> belt;

	public WizardRobeLayer(RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> parent) {
		super(parent);
	}

	static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("belt", CubeListBuilder.create().texOffs(0, 0).addBox(-4f, 10.2f, -2f, 8, 2, 4, new CubeDeformation(0.55f)), PartPose.ZERO);
		root.addOrReplaceChild("sash", CubeListBuilder.create().texOffs(0, 8).addBox(1f, 11.5f, -3.1f, 2, 7, 1, new CubeDeformation(0.05f)), PartPose.rotation(0.04f, 0f, -0.06f));
		root.addOrReplaceChild("sash_short", CubeListBuilder.create().texOffs(8, 8).addBox(2.6f, 11.5f, -3.0f, 2, 5, 1), PartPose.rotation(0.04f, 0f, -0.14f));
		return LayerDefinition.create(mesh, 64, 64);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(BELT, WizardRobeLayer::layer);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (belt == null) belt = new Model<>(context.bakeLayer(BELT), RenderTypes::entityCutout) { };
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				@SuppressWarnings({"unchecked", "rawtypes"})
				RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> parent = (RenderLayerParent) renderer;
				helper.register(new WizardRobeLayer(parent));
			}
		});
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot) {
		if (belt == null || state.chestEquipment == null || !state.chestEquipment.is(OmniItems.WIZARD_ROBE)) return;
		DyedItemColor dyed = state.chestEquipment.get(DataComponents.DYED_COLOR);
		int color = dyed == null ? UNDYED : 0xFF000000 | dyed.rgb();
		pose.pushPose();
		getParentModel().body.translateAndRotate(pose);
		renderColoredCutoutModel(belt, BASE, pose, collector, light, state, color, 1);
		renderColoredCutoutModel(belt, TRIM, pose, collector, light, state, -1, 2);
		pose.popPose();
	}
}
