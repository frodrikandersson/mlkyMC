package com.mlkymc.client;

import com.mlkymc.MlkyMC;
import com.mlkymc.registry.GrapplingHookEntity;
import com.mlkymc.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;

/**
 * Replaces vanilla FishingHookRenderer.
 * Grappling Hook: custom bobber texture (billboarded) + chain texture line (tiled per link).
 * Fishing Rod: vanilla rendering unchanged.
 */
public class GrapplingHookRenderer extends FishingHookRenderer {

    private static final Identifier GRAPPLE_TEXTURE = Identifier.fromNamespaceAndPath(
            MlkyMC.MOD_ID, "textures/entity/grappling_hook_entity.png");
    private static final Identifier CHAIN_TEXTURE = Identifier.fromNamespaceAndPath(
            MlkyMC.MOD_ID, "textures/entity/grappling_hook_chain.png");

    private static final RenderType GRAPPLE_RENDER_TYPE = RenderTypes.entityCutout(GRAPPLE_TEXTURE);
    private static final RenderType CHAIN_RENDER_TYPE = RenderTypes.entityCutoutNoCull(CHAIN_TEXTURE);

    private boolean isGrappleHook = false;
    // Store camera position for billboarding
    private Vec3 cameraPos = Vec3.ZERO;
    private Vec3 hookWorldPos = Vec3.ZERO;

    public GrapplingHookRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void extractRenderState(FishingHook entity, FishingHookRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        Player owner = entity.getPlayerOwner();
        isGrappleHook = entity instanceof GrapplingHookEntity
                || (owner != null && (
                owner.getMainHandItem().is(ModItems.GRAPPLING_HOOK.get()) ||
                owner.getOffhandItem().is(ModItems.GRAPPLING_HOOK.get())));

        // Capture world positions for billboarding
        hookWorldPos = entity.position();
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (cam != null) {
            cameraPos = cam.position();
        }
    }

    @Override
    public void submit(FishingHookRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (!isGrappleHook) {
            super.submit(state, poseStack, collector, camera);
            return;
        }

        // === GRAPPLING HOOK: custom bobber + chain ===
        poseStack.pushPose();

        // Billboard the bobber toward the camera
        Vec3 toCamera = cameraPos.subtract(hookWorldPos);
        float yaw = (float) Math.atan2(toCamera.x, toCamera.z);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotation(yaw));

        // Render bobber quad (now faces camera)
        collector.submitCustomGeometry(poseStack, GRAPPLE_RENDER_TYPE, (pose, buffer) -> {
            renderBobberQuad(pose, buffer, state.lightCoords);
        });

        poseStack.popPose();

        // Render chain along line path (separate push/pop, no billboard)
        if (state.lineOriginOffset != null) {
            poseStack.pushPose();
            renderChain(state, poseStack, collector);
            poseStack.popPose();
        }
    }

    private void renderBobberQuad(PoseStack.Pose pose, VertexConsumer buffer, int light) {
        float s = 0.35f;
        buffer.addVertex(pose, -s, 0, 0)
                .setColor(0xFFFFFFFF).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 1, 0);
        buffer.addVertex(pose, s, 0, 0)
                .setColor(0xFFFFFFFF).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 1, 0);
        buffer.addVertex(pose, s, s * 2, 0)
                .setColor(0xFFFFFFFF).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 1, 0);
        buffer.addVertex(pose, -s, s * 2, 0)
                .setColor(0xFFFFFFFF).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 1, 0);
    }

    private void renderChain(FishingHookRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        float dx = (float) state.lineOriginOffset.x;
        float dy = (float) state.lineOriginOffset.y;
        float dz = (float) state.lineOriginOffset.z;

        double totalDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (totalDist < 0.1) return;

        // Chain width — visible but not huge (6px at 16px/block scale)
        float chainWidth = 0.1f;

        // One segment per chain link — each link is ~0.5 blocks long
        float linkLength = 0.5f;
        int segments = Math.max(4, (int) (totalDist / linkLength));

        collector.submitCustomGeometry(poseStack, CHAIN_RENDER_TYPE, (pose, buffer) -> {
            for (int i = 0; i < segments; i++) {
                float t0 = (float) i / segments;
                float t1 = (float) (i + 1) / segments;

                float x0 = dx * t0;
                float y0 = computeLineY(dy, t0);
                float z0 = dz * t0;

                float x1 = dx * t1;
                float y1 = computeLineY(dy, t1);
                float z1 = dz * t1;

                // Segment direction
                float sdx = x1 - x0;
                float sdy = y1 - y0;
                float sdz = z1 - z0;

                // Up vector for billboard
                float upX = 0, upY = 1, upZ = 0;

                // Cross product: direction x up = perpendicular (width direction)
                float perpX = sdy * upZ - sdz * upY;
                float perpY = sdz * upX - sdx * upZ;
                float perpZ = sdx * upY - sdy * upX;
                float perpLen = (float) Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);
                if (perpLen > 0.001f) {
                    perpX = perpX / perpLen * chainWidth;
                    perpY = perpY / perpLen * chainWidth;
                    perpZ = perpZ / perpLen * chainWidth;
                } else {
                    // Fallback if segment is vertical
                    perpX = chainWidth;
                    perpY = 0;
                    perpZ = 0;
                }

                // UV tiling — each segment gets the full texture (one chain link per segment)
                float u0 = 0f, u1 = 1f;
                float v0 = 0f, v1 = 1f;

                int light = state.lightCoords;

                buffer.addVertex(pose, x0 - perpX, y0 - perpY, z0 - perpZ)
                        .setColor(0xFFFFFFFF).setUv(u0, v0)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                buffer.addVertex(pose, x0 + perpX, y0 + perpY, z0 + perpZ)
                        .setColor(0xFFFFFFFF).setUv(u1, v0)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                buffer.addVertex(pose, x1 + perpX, y1 + perpY, z1 + perpZ)
                        .setColor(0xFFFFFFFF).setUv(u1, v1)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                buffer.addVertex(pose, x1 - perpX, y1 - perpY, z1 - perpZ)
                        .setColor(0xFFFFFFFF).setUv(u0, v1)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose, 0, 1, 0);
            }
        });
    }

    private float computeLineY(float dy, float t) {
        return dy * t * (t * t + t) * 0.5f + 0.25f;
    }
}
