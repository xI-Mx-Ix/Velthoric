/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.weldtool;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.FixedConstraintSettings;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.operator.Op;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.core.constraint.manager.VxConstraintManager;
import net.xmx.velthoric.core.intersection.raycast.VxHitResult;
import net.xmx.velthoric.core.intersection.raycast.VxRaycaster;
import net.xmx.velthoric.core.intersection.raycast.util.VxRaycastUtil;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.item.tool.VxToolMode;
import net.xmx.velthoric.item.tool.config.VxToolConfig;

/**
 * The tool mode implementation for the Weld Tool.
 * <p>
 * This mode allows players to weld two physics bodies together using a Fixed Constraint.
 * It features "Easy Weld" functionality to align bodies surface-to-surface along primary axes,
 * dynamic particle feedback during the selection process, and persistent collision ignoring
 * management via the {@link net.xmx.velthoric.core.physics.ignore.VxBodyPairIgnoreManager}.
 *
 * @author xI-Mx-Ix
 */
public class VxWeldToolMode extends VxToolMode {

    /**
     * Stores the initial attachment point for a player's weld operation.
     * Maps the Player UUID to the physical context of the first hit.
     */
    private final Map<UUID, WeldAttachmentInfo> startPoints = new ConcurrentHashMap<>();

    /**
     * Registers the configurable properties for this tool mode.
     * Note: Break Force is currently planned for future implementation.
     *
     * @param config The tool configuration registry.
     */
    @Override
    public void registerProperties(VxToolConfig config) {
        config.addBoolean("Easy Weld", false);
        config.addBoolean("No Collide", false);
        // TODO: Implement "Break Force" property support in the physics simulation
    }

    /**
     * Manages transition logic based on player input states.
     * Handles the start/finish cycle of welding and the removal of existing constraints.
     *
     * @param player The operating server player.
     * @param state  The updated action state.
     */
    @Override
    public void setState(ServerPlayer player, ActionState state) {
        ActionState previousState = getState(player);
        super.setState(player, state);

        if (previousState == ActionState.IDLE && state == ActionState.PRIMARY_ACTIVE) {
            handleStart(player);
        } else if (previousState == ActionState.PRIMARY_ACTIVE && state == ActionState.IDLE) {
            handleFinish(player);
        } else if (previousState == ActionState.IDLE && state == ActionState.SECONDARY_ACTIVE) {
            handleRemove(player);
        }
    }

    /**
     * Provides visual feedback during the tool's active state.
     * Displays a particle line between the selection point and the current look target.
     *
     * @param player The operating player.
     * @param config The current tool configuration.
     * @param state  The active action state.
     */
    @Override
    public void onServerTick(ServerPlayer player, VxToolConfig config, ActionState state) {
        Optional<WeldAttachmentInfo> hitOpt = findAttachment(player);

        if (hitOpt.isPresent()) {
            WeldAttachmentInfo info = hitOpt.get();
            RVec3 endPos = info.worldPosition();

            if (state == ActionState.PRIMARY_ACTIVE) {
                WeldAttachmentInfo startInfo = startPoints.get(player.getUUID());
                if (startInfo != null) {
                    RVec3 startPos = getUpdatedStartPos(player, startInfo);
                    drawParticleLine(player, startPos, endPos);
                } else {
                    player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            endPos.xx(), endPos.yy(), endPos.zz(), 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    /**
     * Updates the world-space position of an attachment point relative to its parent body.
     * This ensures feedback lines track correctly if the body is in motion.
     */
    private RVec3 getUpdatedStartPos(ServerPlayer player, WeldAttachmentInfo startInfo) {
        if (startInfo.bodyUUID().equals(VxConstraintManager.WORLD_BODY_ID)) {
            return startInfo.worldPosition();
        }

        VxPhysicsWorld world = VxPhysicsWorld.get(player.level().dimension());
        if (world != null) {
            VxBody startBody = world.getBodyManager().getVxBody(startInfo.bodyUUID());
            if (startBody != null && startBody.getBodyId() != Jolt.cInvalidBodyId) {
                BodyInterface bi = world.getPhysicsSystem().getBodyInterface();
                RMat44 transform = bi.getCenterOfMassTransform(startBody.getBodyId());
                return transform.multiply3x4(startInfo.localPivot());
            }
        }
        return startInfo.worldPosition();
    }

    /**
     * Renders a dashed particle line for visual guidance between two coordinates.
     */
    private void drawParticleLine(ServerPlayer player, RVec3 start, RVec3 end) {
        double distance = Math.sqrt(Math.pow(end.xx() - start.xx(), 2)
                + Math.pow(end.yy() - start.yy(), 2)
                + Math.pow(end.zz() - start.zz(), 2));

        int particles = Math.max(2, (int) (distance * 5));
        double dx = (end.xx() - start.xx()) / particles;
        double dy = (end.yy() - start.yy()) / particles;
        double dz = (end.zz() - start.zz()) / particles;

        for (int i = 0; i <= particles; i++) {
            player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    start.xx() + dx * i, start.yy() + dy * i, start.zz() + dz * i,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Handles the removal of constraints.
     * Optimized to restore collisions without temporary collection allocations.
     */
    private void handleRemove(ServerPlayer player) {
        findAttachment(player).ifPresent(info -> {
            UUID targetUUID = info.bodyUUID();
            if (targetUUID.equals(VxConstraintManager.WORLD_BODY_ID)) return;

            VxPhysicsWorld world = VxPhysicsWorld.get(player.level().dimension());
            if (world == null) return;

            // Purge constraints and reset collision ignores in a single pass
            int removedCount = world.getConstraintManager().purgeConstraintsForBody(targetUUID, partnerId -> {
                world.getBodyPairIgnoreManager().removeIgnorePair(targetUUID, partnerId);
            });

            if (removedCount > 0) {
                RVec3 pos = info.worldPosition();
                ServerLevel level = player.serverLevel();

                level.playSound(null, new BlockPos((int) pos.xx(), (int) pos.yy(), (int) pos.zz()),
                        SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);

                level.sendParticles(ParticleTypes.FLAME, pos.xx(), pos.yy(), pos.zz(), 20, 0.15, 0.15, 0.15, 0.05);
            }
        });
    }

    /**
     * Records the starting anchor for a weld operation.
     */
    private void handleStart(ServerPlayer player) {
        findAttachment(player).ifPresent(info -> {
            startPoints.put(player.getUUID(), info);
            RVec3 pos = info.worldPosition();
            player.level().playSound(null,
                    new BlockPos((int) pos.xx(), (int) pos.yy(), (int) pos.zz()),
                    SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 2.0f);
        });
    }

    /**
     * Finalizes the weld operation by initiating the physical constraint creation.
     */
    private void handleFinish(ServerPlayer player) {
        WeldAttachmentInfo startInfo = startPoints.remove(player.getUUID());
        if (startInfo == null) return;

        findAttachment(player).ifPresent(endInfo -> {
            if (startInfo.bodyUUID().equals(endInfo.bodyUUID()) && !startInfo.bodyUUID().equals(VxConstraintManager.WORLD_BODY_ID)) {
                return;
            }

            VxPhysicsWorld world = VxPhysicsWorld.get(player.level().dimension());
            if (world != null) {
                VxToolConfig config = getConfig(player.getUUID());
                boolean easyWeld = config.getBoolean("Easy Weld");
                boolean noCollide = config.getBoolean("No Collide");

                world.execute(() -> createWeld(player, world, startInfo, endInfo, easyWeld, noCollide));
            }
        });
    }

    /**
     * Constructs the physical weld using a Fixed Constraint and manages optional body alignment.
     * Collision ignoring is handled via the persistent manager.
     * Emits feedback particles at both anchor points, respecting "Easy Weld" position updates.
     */
    private void createWeld(ServerPlayer player, VxPhysicsWorld world, WeldAttachmentInfo startInfo, WeldAttachmentInfo endInfo, boolean easyWeld, boolean noCollide) {
        VxServerBodyManager bodyManager = world.getBodyManager();
        VxConstraintManager constraintManager = world.getConstraintManager();
        BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterface();

        VxBody startBody = bodyManager.getVxBody(startInfo.bodyUUID());
        VxBody endBody = bodyManager.getVxBody(endInfo.bodyUUID());
        int startId = startBody != null ? startBody.getBodyId() : Jolt.cInvalidBodyId;
        int endId = endBody != null ? endBody.getBodyId() : Jolt.cInvalidBodyId;

        // Ensure bodies are awake before constraining
        if (startBody != null) bodyInterface.activateBody(startId);
        if (endBody != null) bodyInterface.activateBody(endId);

        // Compute current world positions for pivots and normals
        Vec3 n1 = startInfo.hitNormal();
        RVec3 pos1 = startInfo.worldPosition();
        if (startId != Jolt.cInvalidBodyId) {
            RMat44 comTransform = bodyInterface.getCenterOfMassTransform(startId);
            pos1 = comTransform.multiply3x4(startInfo.localPivot());
            n1 = comTransform.multiply3x3(startInfo.localNormal());
        }

        Vec3 n2 = endInfo.hitNormal();
        RVec3 pos2 = endInfo.worldPosition();
        if (endId != Jolt.cInvalidBodyId) {
            RMat44 comTransform2 = bodyInterface.getCenterOfMassTransform(endId);
            pos2 = comTransform2.multiply3x4(endInfo.localPivot());
            n2 = comTransform2.multiply3x3(endInfo.localNormal());
        }

        RVec3 constraintAnchor = pos1;

        // Perform Geometric Alignment (Easy Weld)
        if (easyWeld && startBody != null && !startInfo.bodyUUID().equals(VxConstraintManager.WORLD_BODY_ID)) {
            // 1. Align normals
            Vec3 targetNormal = new Vec3(-n2.getX(), -n2.getY(), -n2.getZ());
            Quat qAlign = Quat.sFromTo(n1, targetNormal);
            Quat oldRot = bodyInterface.getRotation(startId);
            Quat newRot = Op.star(qAlign, oldRot).normalized();

            // 2. Compute local tangents for twist alignment
            Vec3 absN = new Vec3(Math.abs(startInfo.localNormal().getX()), Math.abs(startInfo.localNormal().getY()), Math.abs(startInfo.localNormal().getZ()));
            Vec3 localTangent = (absN.getX() > 0.9f) ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);

            float dotN = startInfo.localNormal().dot(localTangent);
            localTangent = new Vec3(
                    localTangent.getX() - startInfo.localNormal().getX() * dotN,
                    localTangent.getY() - startInfo.localNormal().getY() * dotN,
                    localTangent.getZ() - startInfo.localNormal().getZ() * dotN
            ).normalized();

            RMat44 newRotMat = RMat44.sRotation(newRot);
            Vec3 worldTangent = newRotMat.multiply3x3(localTangent);

            // 3. Find closest axis on target body for twist snapping
            Quat targetRot = (endId != Jolt.cInvalidBodyId) ? bodyInterface.getRotation(endId) : Quat.sIdentity();
            RMat44 targetRotMat = RMat44.sRotation(targetRot);

            Vec3[] targetAxes = new Vec3[]{
                    targetRotMat.multiply3x3(new Vec3(1, 0, 0)),
                    targetRotMat.multiply3x3(new Vec3(-1, 0, 0)),
                    targetRotMat.multiply3x3(new Vec3(0, 1, 0)),
                    targetRotMat.multiply3x3(new Vec3(0, -1, 0)),
                    targetRotMat.multiply3x3(new Vec3(0, 0, 1)),
                    targetRotMat.multiply3x3(new Vec3(0, 0, -1))
            };

            Vec3 bestAxis = targetAxes[0];
            float maxDot = -1f;
            for (Vec3 axis : targetAxes) {
                float d = worldTangent.dot(axis);
                if (d > maxDot) {
                    maxDot = d;
                    bestAxis = axis;
                }
            }

            // 4. Project best axis onto target plane and align twist
            float pDot = bestAxis.dot(targetNormal);
            Vec3 projBestAxis = new Vec3(
                    bestAxis.getX() - (targetNormal.getX() * pDot),
                    bestAxis.getY() - (targetNormal.getY() * pDot),
                    bestAxis.getZ() - (targetNormal.getZ() * pDot)
            ).normalized();

            if (!Float.isNaN(projBestAxis.getX()) && projBestAxis.lengthSq() > 0.1f) {
                Quat qTwist = Quat.sFromTo(worldTangent, projBestAxis);
                newRot = Op.star(qTwist, newRot).normalized();
            }

            // 5. Finalize transform and anti-Z-fight offset
            RMat44 finalRotMat = RMat44.sRotation(newRot);
            Vec3 localPivotVec = new Vec3((float) startInfo.localPivot().xx(), (float) startInfo.localPivot().yy(), (float) startInfo.localPivot().zz());
            Vec3 rotatedPivotOffset = finalRotMat.multiply3x3(localPivotVec);

            Vec3 antiZfight = new Vec3(targetNormal.getX() * 0.002f, targetNormal.getY() * 0.002f, targetNormal.getZ() * 0.002f);
            RVec3 targetPos = new RVec3(pos2.xx() + antiZfight.getX(), pos2.yy() + antiZfight.getY(), pos2.zz() + antiZfight.getZ());

            RVec3 newShapeOrigin = new RVec3(targetPos.xx() - rotatedPivotOffset.getX(), targetPos.yy() - rotatedPivotOffset.getY(), targetPos.zz() - rotatedPivotOffset.getZ());

            bodyInterface.setPositionAndRotation(startId, newShapeOrigin, newRot, EActivation.Activate);
            constraintAnchor = targetPos;
        }

        // Apply collision ignoring via the persistent manager
        if (noCollide) {
            world.getBodyPairIgnoreManager().ignorePair(startInfo.bodyUUID(), endInfo.bodyUUID(), true);
        }

        // Initialize and register Jolt constraint
        try (FixedConstraintSettings settings = new FixedConstraintSettings()) {
            settings.setSpace(EConstraintSpace.WorldSpace);
            RVec3 finalStartPos = easyWeld ? constraintAnchor : pos1;
            settings.setPoint1(finalStartPos);
            settings.setPoint2(finalStartPos);

            settings.setAxisX1(new Vec3(1, 0, 0));
            settings.setAxisY1(new Vec3(0, 1, 0));
            settings.setAxisX2(new Vec3(1, 0, 0));
            settings.setAxisY2(new Vec3(0, 1, 0));

            // TODO: Apply Break Force

            constraintManager.createConstraint(settings, startInfo.bodyUUID(), endInfo.bodyUUID());

            // Requirement 1: Visual and audible feedback at BOTH points
            ServerLevel level = player.serverLevel();
            level.playSound(null, new BlockPos((int) pos2.xx(), (int) pos2.yy(), (int) pos2.zz()),
                    SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);

            // Particles at target position
            level.sendParticles(ParticleTypes.FLAME,
                    pos2.xx(), pos2.yy(), pos2.zz(), 20, 0.15, 0.15, 0.15, 0.05);

            // Particles at the starting body's new position
            level.sendParticles(ParticleTypes.FLAME,
                    finalStartPos.xx(), finalStartPos.yy(), finalStartPos.zz(), 20, 0.15, 0.15, 0.15, 0.05);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Performs raycasting against the physics world and the block level to identify
     * valid attachment targets within the player's reach.
     */
    private Optional<WeldAttachmentInfo> findAttachment(ServerPlayer player) {
        VxPhysicsWorld world = VxPhysicsWorld.get(player.level().dimension());
        if (world == null) return Optional.empty();

        double reachDistance = player.isCreative() ? 5.0 : 4.5;
        var from = player.getEyePosition();
        var look = player.getLookAngle();
        var to = from.add(look.scale(reachDistance));

        RVec3 joltFrom = new RVec3((float) from.x, (float) from.y, (float) from.z);
        Vec3 joltLook = new Vec3((float) look.x, (float) look.y, (float) look.z);

        Optional<VxHitResult> physicsHitOpt = VxRaycaster.raycastClosest(world, joltFrom, joltLook, (float) reachDistance);
        Optional<BlockHitResult> blockHitOpt = VxRaycastUtil.raycastBlocks(player.level(), from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);

        double physDistSq = physicsHitOpt.map(h -> Op.minus(h.getPhysicsHit().get().position(), joltFrom).lengthSq()).orElse(Double.MAX_VALUE);
        double blockDistSq = blockHitOpt.map(h -> h.getLocation().distanceToSqr(from)).orElse(Double.MAX_VALUE);

        if (physDistSq < blockDistSq && physicsHitOpt.isPresent()) {
            VxHitResult.PhysicsHit hit = physicsHitOpt.get().getPhysicsHit().get();
            VxBody hitBody = world.getBodyManager().getByJoltBodyId(hit.bodyId());

            if (hitBody != null && hitBody.getBodyId() != 0) {
                BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterface();
                RMat44 inverseTransform = bodyInterface.getCenterOfMassTransform(hitBody.getBodyId()).inversed();
                RVec3 localPivot = inverseTransform.multiply3x4(hit.position());
                Vec3 localNormal = inverseTransform.multiply3x3(hit.hitNormal());
                return Optional.of(new WeldAttachmentInfo(hitBody.getPhysicsId(), hit.position(), hit.hitNormal(), localPivot, localNormal));
            }
        }

        if (blockHitOpt.isPresent()) {
            var bPos = blockHitOpt.get().getLocation();
            RVec3 worldPosition = new RVec3((float) bPos.x, (float) bPos.y, (float) bPos.z);
            Vec3 normal = new Vec3((float) blockHitOpt.get().getDirection().getStepX(),
                    (float) blockHitOpt.get().getDirection().getStepY(),
                    (float) blockHitOpt.get().getDirection().getStepZ());
            return Optional.of(new WeldAttachmentInfo(VxConstraintManager.WORLD_BODY_ID, worldPosition, normal, worldPosition, normal));
        }

        return Optional.empty();
    }

    /**
     * Record holding attachment context for a specific point on a rigid body or the world.
     */
    private record WeldAttachmentInfo(UUID bodyUUID, RVec3 worldPosition, Vec3 hitNormal, RVec3 localPivot, Vec3 localNormal) {
    }
}