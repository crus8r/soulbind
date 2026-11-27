package com.omnispell.client;

import com.omnispell.Omnispell;
import com.omnispell.spell.ConjuredSerpent;
import com.omnispell.spell.Conjuring;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * A conjured serpent: a horned head and eight segments that ripple as it swims through
 * the air. The skin is pale so the magic that made it can colour it.
 */
public final class SerpentRender {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Omnispell.id("conjured_serpent"), "main");
	private static final int SEGMENTS = 8;

	private SerpentRender() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, SerpentRender::layer);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(Conjuring.SERPENT, Renderer::new);
	}

	static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3f, -2.5f, -6f, 6f, 5f, 6f), PartPose.offset(0f, 16f, -6f));
		head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(24, 0).addBox(-2f, -1f, -10f, 4f, 3f, 4f), PartPose.ZERO);
		head.addOrReplaceChild("horns", CubeListBuilder.create().texOffs(40, 0).addBox(-3f, -5.5f, -3f, 1f, 3f, 1f).addBox(2f, -5.5f, -3f, 1f, 3f, 1f), PartPose.rotation(-0.5f, 0f, 0f));
		head.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(44, 0).addBox(-0.5f, -4.5f, -4f, 1f, 2f, 5f), PartPose.ZERO);
		for (int i = 0; i < SEGMENTS; i++) {
			float size = 5f - i * 0.45f;
			root.addOrReplaceChild("segment" + i, CubeListBuilder.create().texOffs(0, 12 + (i % 2) * 10).addBox(-size / 2, -size / 2, 0f, size, size, 5f)
					.texOffs(22, 12).addBox(-0.5f, -size / 2 - 1.5f, 1f, 1f, 1.5f, 3f), PartPose.offset(0f, 16f, -6f + i * 4.6f));
		}
		return LayerDefinition.create(mesh, 64, 32);
	}

	public static final class Model extends EntityModel<LivingEntityRenderState> {
		private final ModelPart head;
		private final ModelPart[] segments = new ModelPart[SEGMENTS];

		public Model(ModelPart root) {
			super(root);
			head = root.getChild("head");
			for (int i = 0; i < SEGMENTS; i++) segments[i] = root.getChild("segment" + i);
		}

		@Override
		public void setupAnim(LivingEntityRenderState state) {
			super.setupAnim(state);
			float t = state.ageInTicks * 0.35f;
			head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.5f + Mth.sin(t) * 0.15f;
			head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5f;
			head.x = Mth.sin(t) * 0.8f;
			for (int i = 0; i < SEGMENTS; i++) {
				float phase = t - (i + 1) * 0.7f;
				segments[i].x = Mth.sin(phase) * (1.0f + i * 0.35f);
				segments[i].y += Mth.cos(phase * 0.8f) * 0.6f;
				segments[i].yRot = Mth.cos(phase) * 0.35f;
			}
		}
	}

	public static final class Renderer extends MobRenderer<ConjuredSerpent, LivingEntityRenderState, Model> {
		public Renderer(EntityRendererProvider.Context context) {
			super(context, new Model(context.bakeLayer(LAYER)), 0.4f);
		}

		@Override
		public LivingEntityRenderState createRenderState() {
			return new LivingEntityRenderState();
		}

		@Override
		public Identifier getTextureLocation(LivingEntityRenderState state) {
			return Omnispell.id("textures/entity/conjured_serpent.png");
		}
	}
}
