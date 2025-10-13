/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.physicsgun.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.xmx.velthoric.item.physicsgun.GrabbedObjectInfo;
import net.xmx.velthoric.item.physicsgun.packet.PhysicsGunSyncPacket;
import net.xmx.velthoric.network.VxPacketHandler;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.raycasting.VxHitResult;
import net.xmx.velthoric.physics.raycasting.VxRaytracing;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PhysicsGunServerManager {

    private static final PhysicsGunServerManager INSTANCE = new PhysicsGunServerManager();
    private final Map<UUID, GrabbedObjectInfo> grabbedObjects = new ConcurrentHashMap<>();
    private final Set<UUID> playersTryingToGrab = ConcurrentHashMap.newKeySet();

    private static final float MIN_DISTANCE = 2.0f;
    private static final float MAX_DISTANCE = 450.0f;

    private PhysicsGunServerManager() {}

    public static PhysicsGunServerManager getInstance() {
        return INSTANCE;
    }

    private static Quat playerRotToQuat(float pitch, float yaw) {
        Quat qPitch = Quat.sRotation(new Vec3(1, 0, 0), (float) Math.toRadians(pitch));
        Quat qYaw = Quat.sRotation(new Vec3(0, 1, 0), (float) Math.toRadians(-yaw));
        return Op.star(qYaw, qPitch);
    }

    public void startGrabAttempt(ServerPlayer player) {
        if (isGrabbing(player)) return;
        if (playersTryingToGrab.add(player.getUUID())) {
            syncStateWithClients();
        }
    }

    public void stopGrabAttempt(ServerPlayer player) {
        boolean changed = playersTryingToGrab.remove(player.getUUID());
        if (isGrabbing(player)) {
            stopGrab(player);
        } else if (changed) {
            syncStateWithClients();
        }
    }

    public boolean isGrabbing(Player player) {
        return grabbedObjects.containsKey(player.getUUID());
    }

    public Map<UUID, GrabbedObjectInfo> getGrabbedObjects() {
        return grabbedObjects;
    }

    public boolean isTryingToGrab(Player player) {
        return playersTryingToGrab.contains(player.getUUID());
    }

    public Set<UUID> getPlayersTryingToGrab() {
        return playersTryingToGrab;
    }

    public void startRotationMode(ServerPlayer player) {
        grabbedObjects.computeIfPresent(player.getUUID(), (uuid, info) -> {
            Quat currentPlayerRotation = playerRotToQuat(player.getXRot(), player.getYRot());
            Quat playerRotationDelta = Op.star(currentPlayerRotation, info.initialPlayerRotation().conjugated());
            Quat syncedBodyRotation = Op.star(playerRotationDelta, info.initialBodyRotation());

            return new GrabbedObjectInfo(
                    info.objectId(), info.bodyId(), info.grabPointLocal(),
                    info.currentDistance(), info.originalAngularDamping(),
                    syncedBodyRotation,
                    currentPlayerRotation,
                    true
            );
        });
    }

    public void stopRotationMode(ServerPlayer player) {
        grabbedObjects.computeIfPresent(player.getUUID(), (uuid, info) -> new GrabbedObjectInfo(
                info.objectId(), info.bodyId(), info.grabPointLocal(),
                info.currentDistance(), info.originalAngularDamping(),
                info.initialBodyRotation(),
                playerRotToQuat(player.getXRot(), player.getYRot()),
                false
        ));
    }

    public void startGrab(ServerPlayer player) {
        var physicsWorld = VxPhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null) return;

        final net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
        final net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();
        final net.minecraft.world.level.Level level = player.level();

        physicsWorld.execute(() -> {
            var rayOrigin = new RVec3((float) eyePos.x(), (float) eyePos.y(), (float) eyePos.z());
            var rayDirection = new Vec3((float) lookVec.x(), (float) lookVec.y(), (float) lookVec.z());

            VxRaytracing.raycastPhysics(level, rayOrigin, rayDirection, MAX_DISTANCE).ifPresent(physicsHitResult -> {
                VxHitResult.PhysicsHit physicsHit = physicsHitResult.getPhysicsHit().orElseThrow();
                VxBody physicsObject = physicsWorld.getObjectManager().getByBodyId(physicsHit.bodyId());
                if (physicsObject == null) return;

                UUID objectId = physicsObject.getPhysicsId();

                var bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();
                if (bodyInterface == null) return;

                bodyInterface.setMotionType(physicsHit.bodyId(), EMotionType.Dynamic, EActivation.Activate);
                bodyInterface.activateBody(physicsHit.bodyId());

                var bodyLockInterface = physicsWorld.getPhysicsSystem().getBodyLockInterface();
                if (bodyLockInterface == null) return;

                try (var lock = new BodyLockWrite(bodyLockInterface, physicsHit.bodyId())) {
                    if (lock.succeededAndIsInBroadPhase() && lock.getBody().isDynamic()) {
                        Body body = lock.getBody();
                        MotionProperties motionProperties = body.getMotionProperties();
                        if (motionProperties == null) return;

                        Vec3 offset = Op.star(rayDirection, physicsHit.hitFraction() * MAX_DISTANCE);
                        RVec3 hitPointWorld = Op.plus(rayOrigin, offset);

                        try (var invBodyTransform = body.getInverseCenterOfMassTransform()) {
                            float originalDamping = motionProperties.getAngularDamping();
                            Vec3 hitPointLocal = Op.star(invBodyTransform, hitPointWorld).toVec3();
                            float grabDistance = (float) Op.minus(rayOrigin, hitPointWorld).length();
                            Quat initialPlayerRot = playerRotToQuat(player.getXRot(), player.getYRot());
                            Quat initialBodyRot = body.getRotation();

                            var info = new GrabbedObjectInfo(
                                    objectId, physicsHit.bodyId(), hitPointLocal,
                                    grabDistance, originalDamping, initialBodyRot,
                                    initialPlayerRot, false
                            );

                            grabbedObjects.put(player.getUUID(), info);
                            playersTryingToGrab.remove(player.getUUID());
                            motionProperties.setAngularDamping(2.0f);
                            body.setAngularVelocity(new Vec3(0, 0, 0));
                            syncStateWithClients();
                        }
                    }
                }
            });
        });
    }

    public void stopGrab(ServerPlayer player) {
        GrabbedObjectInfo info = grabbedObjects.remove(player.getUUID());
        if (info != null) {
            syncStateWithClients();
            var physicsWorld = VxPhysicsWorld.get(player.level().dimension());
            if (physicsWorld != null) {
                physicsWorld.execute(() -> {
                    var bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();
                    var bodyLockInterface = physicsWorld.getPhysicsSystem().getBodyLockInterface();
                    if (bodyInterface != null && bodyLockInterface != null) {
                        try (var lock = new BodyLockWrite(bodyLockInterface, info.bodyId())) {
                            if (lock.succeededAndIsInBroadPhase()) {
                                MotionProperties motionProperties = lock.getBody().getMotionProperties();
                                if (motionProperties != null) {
                                    motionProperties.setAngularDamping(info.originalAngularDamping());
                                }
                            }
                        }
                        bodyInterface.activateBody(info.bodyId());
                    }
                });
            }
        }
    }

    public void freezeObject(ServerPlayer player) {
        GrabbedObjectInfo info = grabbedObjects.get(player.getUUID());
        if (info == null) return;

        stopGrab(player);
        var physicsWorld = VxPhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null) return;

        physicsWorld.execute(() -> {
            var bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();
            if (bodyInterface != null) {
                bodyInterface.setMotionType(info.bodyId(), EMotionType.Static, EActivation.DontActivate);
            }
        });
    }

    public void updateScroll(ServerPlayer player, float scrollDelta) {
        grabbedObjects.computeIfPresent(player.getUUID(), (uuid, info) -> {
            float newDistance = info.currentDistance() + scrollDelta;
            newDistance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, newDistance));
            return new GrabbedObjectInfo(
                    info.objectId(), info.bodyId(), info.grabPointLocal(),
                    newDistance, info.originalAngularDamping(), info.initialBodyRotation(),
                    info.initialPlayerRotation(), info.inRotationMode()
            );
        });
    }

    public void updateRotation(ServerPlayer player, float deltaX, float deltaY) {
        grabbedObjects.computeIfPresent(player.getUUID(), (uuid, info) -> {
            final float SENSITIVITY = 0.003f;
            net.minecraft.world.phys.Vec3 look = player.getLookAngle();
            net.minecraft.world.phys.Vec3 worldUp = new net.minecraft.world.phys.Vec3(0, 1, 0);
            net.minecraft.world.phys.Vec3 right = look.cross(worldUp).normalize();
            if (right.lengthSqr() < 1.0E-7) {
                float yawRad = (float) Math.toRadians(player.getYRot());
                right = new net.minecraft.world.phys.Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
            }

            Vec3 joltUp = new Vec3(0, 1, 0);
            Vec3 joltRight = new Vec3((float) right.x, (float) right.y, (float) right.z);

            Quat rotYaw = Quat.sRotation(joltUp, deltaX * SENSITIVITY);
            Quat rotPitch = Quat.sRotation(joltRight, deltaY * SENSITIVITY);

            Quat manualRot = Op.star(rotYaw, rotPitch);
            Quat newInitialBodyRotation = Op.star(manualRot, info.initialBodyRotation());

            return new GrabbedObjectInfo(
                    info.objectId(), info.bodyId(), info.grabPointLocal(),
                    info.currentDistance(), info.originalAngularDamping(), newInitialBodyRotation,
                    info.initialPlayerRotation(), info.inRotationMode()
            );
        });
    }

    public void serverTick(ServerPlayer player) {
        var info = grabbedObjects.get(player.getUUID());
        if (info == null) return;

        var physicsWorld = VxPhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null) {
            stopGrab(player);
            return;
        }

        final var eyePos = player.getEyePosition();
        final var lookVec = player.getLookAngle();

        final float P_GAIN_LINEAR = 250.0f;
        final float D_GAIN_LINEAR = 25.0f;
        final float P_GAIN_ANGULAR = 150.0f;
        final float D_GAIN_ANGULAR = 15.0f;

        physicsWorld.execute(() -> {
            BodyInterface bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();
            if (bodyInterface == null || !bodyInterface.isAdded(info.bodyId())) return;
            bodyInterface.activateBody(info.bodyId());

            var bodyLockInterface = physicsWorld.getPhysicsSystem().getBodyLockInterface();
            if (bodyLockInterface == null) return;

            try (var lock = new BodyLockWrite(bodyLockInterface, info.bodyId())) {
                if (!lock.succeededAndIsInBroadPhase() || !lock.getBody().isDynamic()) {
                    grabbedObjects.remove(player.getUUID());
                    syncStateWithClients();
                    return;
                }
                Body body = lock.getBody();
                MotionProperties motionProperties = body.getMotionProperties();
                if (motionProperties == null || motionProperties.getInverseMass() == 0.0f) return;
                float mass = 1.0f / motionProperties.getInverseMass();

                try (var comTransform = body.getCenterOfMassTransform()) {
                    var targetPointWorld = new RVec3(
                            eyePos.x + lookVec.x * info.currentDistance(),
                            eyePos.y + lookVec.y * info.currentDistance(),
                            eyePos.z + lookVec.z * info.currentDistance()
                    );
                    var currentGrabPointWorld = Op.star(comTransform, info.grabPointLocal());
                    var positionError = Op.minus(targetPointWorld, currentGrabPointWorld);
                    Vec3 currentVelocity = body.getLinearVelocity();
                    Vec3 desiredAcceleration = Op.minus(Op.star(positionError.toVec3(), P_GAIN_LINEAR), Op.star(currentVelocity, D_GAIN_LINEAR));
                    Vec3 force = Op.star(desiredAcceleration, mass);
                    body.addForce(force);
                }

                Quat targetBodyRotation;
                if (info.inRotationMode()) {
                    targetBodyRotation = info.initialBodyRotation();
                } else {
                    Quat currentPlayerRotation = playerRotToQuat(player.getXRot(), player.getYRot());
                    Quat playerRotationDelta = Op.star(currentPlayerRotation, info.initialPlayerRotation().conjugated());
                    targetBodyRotation = Op.star(playerRotationDelta, info.initialBodyRotation());
                }

                Quat currentBodyRotation = body.getRotation();
                Quat errorQuat = Op.star(targetBodyRotation, currentBodyRotation.conjugated());
                if (errorQuat.getW() < 0.0f) {
                    errorQuat.set(-errorQuat.getX(), -errorQuat.getY(), -errorQuat.getZ(), -errorQuat.getW());
                }

                Vec3 rotationError = new Vec3(errorQuat.getX(), errorQuat.getY(), errorQuat.getZ());
                Vec3 currentAngularVelocity = body.getAngularVelocity();
                Vec3 desiredAngularAccel = Op.minus(Op.star(rotationError, P_GAIN_ANGULAR), Op.star(currentAngularVelocity, D_GAIN_ANGULAR));
                Quat invBodyRot = currentBodyRotation.conjugated();
                Vec3 desiredAngularAccelLocal = Op.star(invBodyRot, desiredAngularAccel);
                Vec3 invInertiaDiag = motionProperties.getInverseInertiaDiagonal();
                float ix = invInertiaDiag.getX() == 0f ? 0f : 1f / invInertiaDiag.getX();
                float iy = invInertiaDiag.getY() == 0f ? 0f : 1f / invInertiaDiag.getY();
                float iz = invInertiaDiag.getZ() == 0f ? 0f : 1f / invInertiaDiag.getZ();
                Vec3 inertiaDiag = new Vec3(ix, iy, iz);
                Quat inertiaRotation = motionProperties.getInertiaRotation();
                Quat invInertiaRotation = inertiaRotation.conjugated();
                Vec3 accelInInertiaSpace = Op.star(invInertiaRotation, desiredAngularAccelLocal);
                accelInInertiaSpace.setX(accelInInertiaSpace.getX() * inertiaDiag.getX());
                accelInInertiaSpace.setY(accelInInertiaSpace.getY() * inertiaDiag.getY());
                accelInInertiaSpace.setZ(accelInInertiaSpace.getZ() * inertiaDiag.getZ());
                Vec3 torqueInInertiaSpace = accelInInertiaSpace;
                Vec3 torqueLocal = Op.star(inertiaRotation, torqueInInertiaSpace);
                Vec3 torqueWorld = Op.star(currentBodyRotation, torqueLocal);
                body.addTorque(torqueWorld);
            }
        });
    }

    public void syncStateWithClients() {
        Map<UUID, PhysicsGunClientManager.ClientGrabData> clientGrabData = grabbedObjects.entrySet().stream()
                .collect(Collectors.toConcurrentMap(
                        Map.Entry::getKey,
                        entry -> {
                            GrabbedObjectInfo info = entry.getValue();
                            net.minecraft.world.phys.Vec3 localHitPoint = new net.minecraft.world.phys.Vec3(
                                    info.grabPointLocal().getX(),
                                    info.grabPointLocal().getY(),
                                    info.grabPointLocal().getZ()
                            );
                            return new PhysicsGunClientManager.ClientGrabData(info.objectId(), localHitPoint);
                        }
                ));
        VxPacketHandler.sendToAll(new PhysicsGunSyncPacket(clientGrabData, playersTryingToGrab));
    }

    public void syncStateForNewPlayer(ServerPlayer player) {
        Map<UUID, PhysicsGunClientManager.ClientGrabData> clientGrabData = grabbedObjects.entrySet().stream()
                .collect(Collectors.toConcurrentMap(
                        Map.Entry::getKey,
                        entry -> {
                            GrabbedObjectInfo info = entry.getValue();
                            net.minecraft.world.phys.Vec3 localHitPoint = new net.minecraft.world.phys.Vec3(
                                    info.grabPointLocal().getX(),
                                    info.grabPointLocal().getY(),
                                    info.grabPointLocal().getZ()
                            );
                            return new PhysicsGunClientManager.ClientGrabData(info.objectId(), localHitPoint);
                        }
                ));
        VxPacketHandler.sendToPlayer(new PhysicsGunSyncPacket(clientGrabData, playersTryingToGrab), player);
    }
}
