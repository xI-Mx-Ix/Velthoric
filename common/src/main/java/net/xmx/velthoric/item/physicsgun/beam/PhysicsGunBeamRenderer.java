/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.physicsgun.beam;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.event.api.VxRenderEvent;
import net.xmx.velthoric.item.physicsgun.manager.PhysicsGunClientManager;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.object.client.VxClientObjectInterpolator;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PhysicsGunBeamRenderer {

    private static final int BEAM_SEGMENTS = 20;
    private static final float BEAM_WIDTH = 0.15f;
    private static final float BEAM_CURVE_STRENGTH = 0.3f;
    private static final float BEAM_MAX_LENGTH = 100.0f;

    private static final RVec3 INTERPOLATED_POSITION = new RVec3();
    private static final Quat INTERPOLATED_ROTATION = new Quat();

    public static void registerEvents() {
        VxRenderEvent.ClientRenderLevelStageEvent.EVENT.register(PhysicsGunBeamRenderer::onRenderLevelStage);
    }

    public static void onRenderLevelStage(VxRenderEvent.ClientRenderLevelStageEvent event) {
        if (event.getStage() != VxRenderEvent.ClientRenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        Player localPlayer = mc.player;
        if (localPlayer == null || mc.level == null) return;

        var poseStack = event.getPoseStack();
        float partialTicks = event.getPartialTick();

        PhysicsGunClientManager clientManager = PhysicsGunClientManager.getInstance();
        VxClientObjectManager objectManager = VxClientObjectManager.getInstance();
        VxClientObjectDataStore store = objectManager.getStore();
        VxClientObjectInterpolator interpolator = objectManager.getInterpolator();

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        Map<UUID, PhysicsGunClientManager.ClientGrabData> activeGrabs = clientManager.getActiveGrabs();
        for (var entry : activeGrabs.entrySet()) {
            UUID playerUuid = entry.getKey();
            PhysicsGunClientManager.ClientGrabData grabData = entry.getValue();
            UUID objectUuid = grabData.objectUuid();

            Player player = mc.level.getPlayerByUUID(playerUuid);
            if (player == null) continue;

            Integer index = store.getIndexForId(objectUuid);
            VxBody body = objectManager.getObject(objectUuid);

            if (index == null || !store.render_isInitialized[index] || !(body instanceof VxRigidBody)) continue;

            interpolator.interpolateFrame(store, index, partialTicks, INTERPOLATED_POSITION, INTERPOLATED_ROTATION);

            Vec3 startPoint = getGunTipPosition(player, partialTicks);
            RVec3 centerPos = INTERPOLATED_POSITION;
            Quat rotation = INTERPOLATED_ROTATION;
            Vec3 localHitPoint = grabData.localHitPoint();

            com.github.stephengold.joltjni.Vec3 localHitJolt = new com.github.stephengold.joltjni.Vec3(
                    (float) localHitPoint.x(), (float) localHitPoint.y(), (float) localHitPoint.z()
            );

            com.github.stephengold.joltjni.Vec3 rotatedOffset = com.github.stephengold.joltjni.operator.Op.star(rotation, localHitJolt);
            RVec3 endPointJolt = com.github.stephengold.joltjni.operator.Op.plus(centerPos, rotatedOffset);
            Vec3 endPoint = new Vec3(endPointJolt.xx(), endPointJolt.yy(), endPointJolt.zz());

            Vec3 playerLookVec = getPlayerLookVector(player, partialTicks);

            bufferBuilder.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            drawThickCurvedBeam(bufferBuilder, matrix, camPos, startPoint, endPoint, playerLookVec);
            tesselator.end();
        }

        Set<UUID> playersTryingToGrab = clientManager.getPlayersTryingToGrab();
        for (UUID playerUuid : playersTryingToGrab) {
            if (activeGrabs.containsKey(playerUuid)) continue;

            Player player = mc.level.getPlayerByUUID(playerUuid);
            if (player == null) continue;

            Vec3 startPoint = getGunTipPosition(player, partialTicks);
            Vec3 playerLookVec = getPlayerLookVector(player, partialTicks);
            Vec3 traceStart = player.getEyePosition(partialTicks);

            Optional<Vec3> physicsHitPoint = raycastClientPhysicsObjects(traceStart, playerLookVec, BEAM_MAX_LENGTH, store, interpolator, partialTicks);

            Vec3 endPoint;
            if (physicsHitPoint.isPresent()) {
                endPoint = physicsHitPoint.get();
            } else {
                Vec3 traceEnd = traceStart.add(playerLookVec.scale(BEAM_MAX_LENGTH));
                ClipContext clipContext = new ClipContext(traceStart, traceEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
                BlockHitResult blockHitResult = mc.level.clip(clipContext);
                endPoint = (blockHitResult.getType() == HitResult.Type.MISS) ? traceEnd : blockHitResult.getLocation();
            }

            bufferBuilder.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            drawThickCurvedBeam(bufferBuilder, matrix, camPos, startPoint, endPoint, playerLookVec);
            tesselator.end();
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static Optional<Vec3> raycastClientPhysicsObjects(Vec3 rayOrigin, Vec3 rayDirection, float maxDistance, VxClientObjectDataStore store, VxClientObjectInterpolator interpolator, float partialTicks) {
        double closestHitDist = maxDistance;
        Vec3 hitPoint = null;

        final double OBJECT_RADIUS = 1.5;
        final double OBJECT_RADIUS_SQR = OBJECT_RADIUS * OBJECT_RADIUS;

        RVec3 tempPos = new RVec3();
        Quat tempRot = new Quat();

        for (UUID id : store.getAllObjectIds()) {
            Integer i = store.getIndexForId(id);
            if (i == null || !store.render_isInitialized[i]) {
                continue;
            }

            interpolator.interpolateFrame(store, i, partialTicks, tempPos, tempRot);
            Vec3 objectCenter = new Vec3(tempPos.xx(), tempPos.yy(), tempPos.zz());
            Vec3 originToCenter = objectCenter.subtract(rayOrigin);
            double t = originToCenter.dot(rayDirection);

            if (t < 0 || t > closestHitDist) {
                continue;
            }

            double d2 = originToCenter.lengthSqr() - t * t;

            if (d2 < OBJECT_RADIUS_SQR) {
                double t_hit = t - Math.sqrt(OBJECT_RADIUS_SQR - d2);
                if (t_hit < closestHitDist && t_hit >= 0) {
                    closestHitDist = t_hit;
                    hitPoint = rayOrigin.add(rayDirection.scale(closestHitDist));
                }
            }
        }
        return Optional.ofNullable(hitPoint);
    }

    private static void drawThickCurvedBeam(BufferBuilder bufferBuilder, Matrix4f matrix, Vec3 camPos, Vec3 start, Vec3 end, Vec3 playerLookVec) {
        float r = 0.9725f;
        float g = 0.2863f;
        float b = 0.0117f;
        float baseAlpha = 0.83f;

        double distance = start.distanceTo(end);
        Vec3 p0 = start;
        Vec3 p3 = end;
        Vec3 p1 = p0.add(playerLookVec.scale(distance * BEAM_CURVE_STRENGTH));
        Vec3 tangentAtEnd = p0.subtract(p3).normalize();
        Vec3 p2 = p3.add(tangentAtEnd.scale(distance * BEAM_CURVE_STRENGTH));
        Vec3 lastPos = p0;

        for (int i = 0; i <= BEAM_SEGMENTS; i++) {
            float t = (float) i / BEAM_SEGMENTS;
            Vec3 currentPos = getCubicBezierPoint(t, p0, p1, p2, p3);
            Vec3 viewDir = currentPos.subtract(camPos).normalize();
            Vec3 segmentDir = (i == 0) ? p1.subtract(p0).normalize() : currentPos.subtract(lastPos).normalize();
            if (segmentDir.lengthSqr() < 1.0E-6) {
                segmentDir = playerLookVec;
            }
            Vec3 side = segmentDir.cross(viewDir).normalize().scale(BEAM_WIDTH / 2.0);
            bufferBuilder.vertex(matrix, (float) (currentPos.x + side.x), (float) (currentPos.y + side.y), (float) (currentPos.z + side.z)).color(r, g, b, baseAlpha).endVertex();
            bufferBuilder.vertex(matrix, (float) (currentPos.x - side.x), (float) (currentPos.y - side.y), (float) (currentPos.z - side.z)).color(r, g, b, baseAlpha).endVertex();
            lastPos = currentPos;
        }
    }

    private static Vec3 getCubicBezierPoint(float t, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        float u = 1.0f - t;
        float tt = t * t;
        float uu = u * u;
        float uuu = uu * u;
        float ttt = tt * t;
        Vec3 p = p0.scale(uuu);
        p = p.add(p1.scale(3 * uu * t));
        p = p.add(p2.scale(3 * u * tt));
        p = p.add(p3.scale(ttt));
        return p;
    }

    private static Vec3 getPlayerLookVector(Player player, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (player.is(mc.player) && mc.options.getCameraType().isFirstPerson()) {
            Camera camera = mc.gameRenderer.getMainCamera();
            org.joml.Vector3f lookVector = camera.getLookVector();
            return new Vec3(lookVector.x(), lookVector.y(), lookVector.z());
        } else {
            return player.getViewVector(partialTicks);
        }
    }

    private static Vec3 getGunTipPosition(Player player, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (player.is(mc.player) && mc.options.getCameraType().isFirstPerson()) {
            Camera camera = mc.gameRenderer.getMainCamera();
            org.joml.Vector3f lookVector = camera.getLookVector();
            Vec3 camForward = new Vec3(lookVector.x(), lookVector.y(), lookVector.z());
            org.joml.Vector3f upVector = camera.getUpVector();
            Vec3 camUp = new Vec3(upVector.x(), upVector.y(), upVector.z());
            Vec3 camRight = camForward.cross(camUp).normalize();
            return camera.getPosition()
                    .add(camForward.scale(0.5))
                    .add(camRight.scale(0.3))
                    .add(camUp.scale(-0.15));
        } else {
            Vec3 eyePos = player.getEyePosition(partialTicks);
            Vec3 lookVec = player.getViewVector(partialTicks);
            Vec3 upVec = player.getUpVector(partialTicks);
            Vec3 rightVec = lookVec.cross(upVec).normalize();
            return eyePos
                    .add(lookVec.scale(0.3))
                    .add(rightVec.scale(-0.35))
                    .add(upVec.scale(-0.3));
        }
    }
}
