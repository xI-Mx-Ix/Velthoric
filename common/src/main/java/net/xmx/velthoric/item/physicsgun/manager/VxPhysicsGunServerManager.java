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
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.intersection.raycast.VxHitResult;
import net.xmx.velthoric.core.intersection.raycast.VxRaycaster;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.item.physicsgun.VxGrabbedBodyInfo;
import net.xmx.velthoric.item.physicsgun.packet.VxPhysicsGunSyncPacket;
import net.xmx.velthoric.network.VxNetworking;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the server-side logic and physical simulation for the Physics Gun.
 * <p>
 * This manager handles the lifecycle of "grabbing" physics bodies, applying
 * P-D (Proportional-Derivative) controllers to maintain position and orientation,
 * and synchronizing the state between the server and all connected clients.
 * It utilizes the revolutionized Jolt-only raycasting system for high performance.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsGunServerManager {

    /**
     * Singleton instance of the manager.
     */
    private static final VxPhysicsGunServerManager INSTANCE = new VxPhysicsGunServerManager();

    /**
     * Maps player UUIDs to information about the physics body they are currently holding.
     */
    private final Map<UUID, VxGrabbedBodyInfo> grabbedBodies = new ConcurrentHashMap<>();

    /**
     * Tracks players who are currently attempting to grab an object (e.g., holding the key but haven't hit anything yet).
     */
    private final Set<UUID> playersTryingToGrab = ConcurrentHashMap.newKeySet();

    /**
     * The minimum distance an object can be held from the player.
     */
    private static final float MIN_DISTANCE = 2.0f;

    /**
     * The maximum reach of the Physics Gun raycast.
     */
    private static final float MAX_DISTANCE = 450.0f;

    /**
     * Private constructor to enforce Singleton pattern.
     */
    private VxPhysicsGunServerManager() {
    }

    /**
     * Accessor for the singleton instance.
     *
     * @return The global instance of VxPhysicsGunServerManager.
     */
    public static VxPhysicsGunServerManager getInstance() {
        return INSTANCE;
    }

    /**
     * Converts Minecraft player rotation (pitch/yaw) into a Jolt Quaternion.
     *
     * @param pitch The player's X rotation.
     * @param yaw   The player's Y rotation.
     * @return A normalized Jolt Quaternion representing the view direction.
     */
    private static Quat playerRotToQuat(float pitch, float yaw) {
        // Create rotation around X-axis for pitch
        Quat qPitch = Quat.sRotation(new Vec3(1, 0, 0), (float) Math.toRadians(pitch));
        // Create rotation around Y-axis for yaw (inverted to match Jolt/Minecraft coordinate delta)
        Quat qYaw = Quat.sRotation(new Vec3(0, 1, 0), (float) Math.toRadians(-yaw));
        // Combine rotations: Yaw * Pitch
        return Op.star(qYaw, qPitch);
    }

    /**
     * Marks a player as attempting to grab a physics body.
     *
     * @param player The player initiating the grab attempt.
     */
    public void startGrabAttempt(ServerPlayer player) {
        // If already grabbing, ignore new attempt
        if (isGrabbing(player)) return;
        // Add to tracking set and sync state if successful
        if (playersTryingToGrab.add(player.getUUID())) {
            syncStateWithClients();
        }
    }

    /**
     * Stops a player's attempt to grab or releases their currently held body.
     *
     * @param player The player stopping the grab.
     */
    public void stopGrabAttempt(ServerPlayer player) {
        // Remove from the 'trying' set
        boolean changed = playersTryingToGrab.remove(player.getUUID());
        // If they were actively holding a body, release it
        if (isGrabbing(player)) {
            stopGrab(player);
        } else if (changed) {
            // Otherwise, just sync the removal of the attempt state
            syncStateWithClients();
        }
    }

    /**
     * Checks if a player is currently holding a physics body.
     *
     * @param player The player to check.
     * @return True if the player has a grabbed body.
     */
    public boolean isGrabbing(Player player) {
        return grabbedBodies.containsKey(player.getUUID());
    }

    /**
     * Returns the internal map of all grabbed bodies.
     *
     * @return Map of player UUIDs to grab info.
     */
    public Map<UUID, VxGrabbedBodyInfo> getGrabbedBodies() {
        return grabbedBodies;
    }

    /**
     * Checks if a player is in the process of attempting a grab.
     *
     * @param player The player to check.
     * @return True if they are trying to grab.
     */
    public boolean isTryingToGrab(Player player) {
        return playersTryingToGrab.contains(player.getUUID());
    }

    /**
     * Returns the set of players currently attempting a grab.
     *
     * @return Set of UUIDs.
     */
    public Set<UUID> getPlayersTryingToGrab() {
        return playersTryingToGrab;
    }

    /**
     * Activates rotation mode for a held object, locking its initial rotation state relative to the player.
     *
     * @param player The player holding the object.
     */
    public void startRotationMode(ServerPlayer player) {
        grabbedBodies.computeIfPresent(player.getUUID(), (uuid, info) -> {
            // Get current view rotation
            Quat currentPlayerRotation = playerRotToQuat(player.getXRot(), player.getYRot());
            // Calculate delta between current view and initial view when grabbed
            Quat playerRotationDelta = Op.star(currentPlayerRotation, info.initialPlayerRotation().conjugated());
            // Calculate what the current synced rotation should be
            Quat syncedBodyRotation = Op.star(playerRotationDelta, info.initialBodyRotation());

            // Return updated info with rotation mode enabled
            return new VxGrabbedBodyInfo(
                    info.physicsId(), info.bodyId(), info.grabPointLocal(),
                    info.currentDistance(), info.originalAngularDamping(),
                    syncedBodyRotation,
                    currentPlayerRotation,
                    true
            );
        });
    }

    /**
     * Deactivates rotation mode, freezing the relative rotation offset.
     *
     * @param player The player holding the object.
     */
    public void stopRotationMode(ServerPlayer player) {
        grabbedBodies.computeIfPresent(player.getUUID(), (uuid, info) -> new VxGrabbedBodyInfo(
                info.physicsId(), info.bodyId(), info.grabPointLocal(),
                info.currentDistance(), info.originalAngularDamping(),
                info.initialBodyRotation(),
                playerRotToQuat(player.getXRot(), player.getYRot()),
                false
        ));
    }

    /**
     * Initiates the grab logic by performing a raycast and establishing a physical link.
     *
     * @param player The player performing the grab.
     */
    public void startGrab(ServerPlayer player) {
        // Retrieve the physics world for the current dimension
        var physicsWorld = VxPhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null) return;

        // Capture player eye position and look vector
        final var eyePos = player.getEyePosition();
        final var lookVec = player.getLookAngle();

        // Offload raycasting and grab setup to the physics thread
        physicsWorld.execute(() -> {
            // Convert Minecraft vectors to Jolt RVec3/Vec3
            var rayOrigin = new RVec3((float) eyePos.x(), (float) eyePos.y(), (float) eyePos.z());
            var rayDirection = new Vec3((float) lookVec.x(), (float) lookVec.y(), (float) lookVec.z());

            // Perform specialized physics raycast (ignoring terrain)
            VxRaycaster.raycastPhysics(physicsWorld, rayOrigin, rayDirection, MAX_DISTANCE).ifPresent(hitResult -> {
                // Get the raw physics hit data
                VxHitResult.PhysicsHit hit = hitResult.getPhysicsHit().get();

                // Find the corresponding Velthoric body wrapper
                VxBody physicsBody = physicsWorld.getBodyManager().getByJoltBodyId(hit.bodyId());
                if (physicsBody == null) return;

                UUID physicsId = physicsBody.getPhysicsId();
                var bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();
                var bodyLockInterface = physicsWorld.getPhysicsSystem().getBodyLockInterface();

                if (bodyInterface == null || bodyLockInterface == null) return;

                // Ensure the body is dynamic and active
                bodyInterface.setMotionType(hit.bodyId(), EMotionType.Dynamic, EActivation.Activate);

                // Lock the body for writing to initialize state safely
                try (var lock = new BodyLockWrite(bodyLockInterface, hit.bodyId())) {
                    if (lock.succeededAndIsInBroadPhase() && lock.getBody().isDynamic()) {
                        Body body = lock.getBody();
                        MotionProperties motionProperties = body.getMotionProperties();
                        if (motionProperties == null) return;

                        // Precise hit point from the result
                        RVec3 hitPointWorld = hit.position();

                        // Calculate the local hit point relative to the body's center of mass
                        try (var invBodyTransform = body.getInverseCenterOfMassTransform()) {
                            float originalDamping = motionProperties.getAngularDamping();
                            Vec3 hitPointLocal = Op.star(invBodyTransform, hitPointWorld).toVec3();

                            // Calculate current world distance to the hit point
                            float grabDistance = (float) Op.minus(rayOrigin, hitPointWorld).length();

                            // Store initial rotations to calculate offsets during movement
                            Quat initialPlayerRot = playerRotToQuat(player.getXRot(), player.getYRot());
                            Quat initialBodyRot = body.getRotation();

                            // Create the grab information record
                            var info = new VxGrabbedBodyInfo(
                                    physicsId, hit.bodyId(), hitPointLocal,
                                    grabDistance, originalDamping, initialBodyRot,
                                    initialPlayerRot, false
                            );

                            // Store grab and clear the 'attempting' state
                            grabbedBodies.put(player.getUUID(), info);
                            playersTryingToGrab.remove(player.getUUID());

                            // Increase damping to make the body more stable while held
                            motionProperties.setAngularDamping(2.0f);
                            // Zero out existing angular velocity to prevent immediate spinning
                            body.setAngularVelocity(new Vec3(0, 0, 0));

                            // Broadcast update to clients
                            syncStateWithClients();
                        }
                    }
                }
            });
        });
    }

    /**
     * Releases a held body and restores its original physical properties.
     *
     * @param player The player releasing the object.
     */
    public void stopGrab(ServerPlayer player) {
        // Remove from the active grab map
        VxGrabbedBodyInfo info = grabbedBodies.remove(player.getUUID());
        if (info != null) {
            syncStateWithClients();
            var physicsWorld = VxPhysicsWorld.get(player.level().dimension());
            if (physicsWorld != null) {
                // Restore physical state on the physics thread
                physicsWorld.execute(() -> {
                    var bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();
                    var bodyLockInterface = physicsWorld.getPhysicsSystem().getBodyLockInterface();
                    if (bodyInterface != null && bodyLockInterface != null) {
                        try (var lock = new BodyLockWrite(bodyLockInterface, info.bodyId())) {
                            if (lock.succeededAndIsInBroadPhase()) {
                                MotionProperties motionProperties = lock.getBody().getMotionProperties();
                                if (motionProperties != null) {
                                    // Reset damping to what it was before the grab
                                    motionProperties.setAngularDamping(info.originalAngularDamping());
                                }
                            }
                        }
                        // Ensure the body stays active after release to fall/collide
                        bodyInterface.activateBody(info.bodyId());
                    }
                });
            }
        }
    }

    /**
     * Freezes the held body in place by setting its motion type to Static.
     *
     * @param player The player requesting the freeze.
     */
    public void freezeBody(ServerPlayer player) {
        VxGrabbedBodyInfo info = grabbedBodies.get(player.getUUID());
        if (info == null) return;

        // Stop the grab first
        stopGrab(player);
        var physicsWorld = VxPhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null) return;

        // Change motion type to Static on the physics thread
        physicsWorld.execute(() -> {
            var bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();
            if (bodyInterface != null) {
                bodyInterface.setMotionType(info.bodyId(), EMotionType.Static, EActivation.DontActivate);
            }
        });
    }

    /**
     * Adjusts the distance of the held body from the player via scroll input.
     *
     * @param player      The player scrolling.
     * @param scrollDelta The change in distance.
     */
    public void updateScroll(ServerPlayer player, float scrollDelta) {
        grabbedBodies.computeIfPresent(player.getUUID(), (uuid, info) -> {
            float newDistance = info.currentDistance() + scrollDelta;
            // Clamp distance within predefined limits
            newDistance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, newDistance));
            return new VxGrabbedBodyInfo(
                    info.physicsId(), info.bodyId(), info.grabPointLocal(),
                    newDistance, info.originalAngularDamping(), info.initialBodyRotation(),
                    info.initialPlayerRotation(), info.inRotationMode()
            );
        });
    }

    /**
     * Updates the target rotation of a held body based on player mouse movement while in rotation mode.
     *
     * @param player The player rotating.
     * @param deltaX Horizontal rotation delta.
     * @param deltaY Vertical rotation delta.
     */
    public void updateRotation(ServerPlayer player, float deltaX, float deltaY) {
        grabbedBodies.computeIfPresent(player.getUUID(), (uuid, info) -> {
            final float SENSITIVITY = 0.003f;
            net.minecraft.world.phys.Vec3 look = player.getLookAngle();
            net.minecraft.world.phys.Vec3 worldUp = new net.minecraft.world.phys.Vec3(0, 1, 0);
            // Calculate right vector relative to player look
            net.minecraft.world.phys.Vec3 right = look.cross(worldUp).normalize();

            // Handle edge case where look is parallel to up vector
            if (right.lengthSqr() < 1.0E-7) {
                float yawRad = (float) Math.toRadians(player.getYRot());
                right = new net.minecraft.world.phys.Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
            }

            // Convert axes to Jolt
            Vec3 joltUp = new Vec3(0, 1, 0);
            Vec3 joltRight = new Vec3((float) right.x, (float) right.y, (float) right.z);

            // Create incremental rotation quaternions
            Quat rotYaw = Quat.sRotation(joltUp, deltaX * SENSITIVITY);
            Quat rotPitch = Quat.sRotation(joltRight, deltaY * SENSITIVITY);

            // Apply incremental rotation to current target rotation
            Quat manualRot = Op.star(rotYaw, rotPitch);
            Quat newInitialBodyRotation = Op.star(manualRot, info.initialBodyRotation());

            return new VxGrabbedBodyInfo(
                    info.physicsId(), info.bodyId(), info.grabPointLocal(),
                    info.currentDistance(), info.originalAngularDamping(), newInitialBodyRotation,
                    info.initialPlayerRotation(), info.inRotationMode()
            );
        });
    }

    /**
     * Core physics tick logic. Updates forces and torques applied to all grabbed bodies.
     * This method implements a virtual spring-damper system to move objects.
     *
     * @param player The player whose held body is being ticked.
     */
    public void serverTick(ServerPlayer player) {
        // Retrieve grabbed body info
        var info = grabbedBodies.get(player.getUUID());
        if (info == null) return;

        // Ensure physics world exists
        var physicsWorld = VxPhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null) {
            stopGrab(player);
            return;
        }

        // Cache player state for thread-safe access
        final var eyePos = player.getEyePosition();
        final var lookVec = player.getLookAngle();

        // Control parameters for the P-D controllers
        final float P_GAIN_LINEAR = 250.0f;   // Position stiffness
        final float D_GAIN_LINEAR = 25.0f;    // Linear damping
        final float P_GAIN_ANGULAR = 150.0f;  // Rotation stiffness
        final float D_GAIN_ANGULAR = 15.0f;   // Angular damping

        // Perform simulation updates on the physics thread
        physicsWorld.execute(() -> {
            BodyInterface bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();
            // Validate body existence
            if (bodyInterface == null || !bodyInterface.isAdded(info.bodyId())) return;
            // Keep the body active so it doesn't go to sleep while held
            bodyInterface.activateBody(info.bodyId());

            var bodyLockInterface = physicsWorld.getPhysicsSystem().getBodyLockInterface();
            if (bodyLockInterface == null) return;

            // Lock body for simulation steps
            try (var lock = new BodyLockWrite(bodyLockInterface, info.bodyId())) {
                // If body became static or invalid, stop the grab
                if (!lock.succeededAndIsInBroadPhase() || !lock.getBody().isDynamic()) {
                    grabbedBodies.remove(player.getUUID());
                    syncStateWithClients();
                    return;
                }
                Body body = lock.getBody();
                MotionProperties motionProperties = body.getMotionProperties();
                if (motionProperties == null || motionProperties.getInverseMass() == 0.0f) return;

                // --- Linear Force Calculation (Position) ---
                float mass = 1.0f / motionProperties.getInverseMass();

                try (var comTransform = body.getCenterOfMassTransform()) {
                    // Target world position based on player view and current distance
                    var targetPointWorld = new RVec3(
                            eyePos.x + lookVec.x * info.currentDistance(),
                            eyePos.y + lookVec.y * info.currentDistance(),
                            eyePos.z + lookVec.z * info.currentDistance()
                    );

                    // Current world position of the specific grab point
                    var currentGrabPointWorld = Op.star(comTransform, info.grabPointLocal());
                    // Difference vector (Error)
                    var positionError = Op.minus(targetPointWorld, currentGrabPointWorld);

                    // Current velocity to apply damping
                    Vec3 currentVelocity = body.getLinearVelocity();
                    // F = mass * (P * error - D * velocity)
                    Vec3 desiredAcceleration = Op.minus(Op.star(positionError.toVec3(), P_GAIN_LINEAR), Op.star(currentVelocity, D_GAIN_LINEAR));
                    Vec3 force = Op.star(desiredAcceleration, mass);

                    // Apply force at the specific grab point
                    body.addForce(force);
                }

                // --- Angular Torque Calculation (Rotation) ---
                Quat targetBodyRotation;
                if (info.inRotationMode()) {
                    // In manual rotation mode, we use the stored base rotation
                    targetBodyRotation = info.initialBodyRotation();
                } else {
                    // Calculate relative rotation based on player turning
                    Quat currentPlayerRotation = playerRotToQuat(player.getXRot(), player.getYRot());
                    Quat playerRotationDelta = Op.star(currentPlayerRotation, info.initialPlayerRotation().conjugated());
                    targetBodyRotation = Op.star(playerRotationDelta, info.initialBodyRotation());
                }

                Quat currentBodyRotation = body.getRotation();
                // Difference between current and target rotation
                Quat errorQuat = Op.star(targetBodyRotation, currentBodyRotation.conjugated());

                // Ensure the quaternion takes the shortest path
                if (errorQuat.getW() < 0.0f) {
                    errorQuat.set(-errorQuat.getX(), -errorQuat.getY(), -errorQuat.getZ(), -errorQuat.getW());
                }

                // Extract imaginary part as rotation error vector
                Vec3 rotationError = new Vec3(errorQuat.getX(), errorQuat.getY(), errorQuat.getZ());
                Vec3 currentAngularVelocity = body.getAngularVelocity();

                // Alpha = P * rotError - D * angVel
                Vec3 desiredAngularAccel = Op.minus(Op.star(rotationError, P_GAIN_ANGULAR), Op.star(currentAngularVelocity, D_GAIN_ANGULAR));

                // Transform desired acceleration to local space to account for inertia tensor
                Quat invBodyRot = currentBodyRotation.conjugated();
                Vec3 desiredAngularAccelLocal = Op.star(invBodyRot, desiredAngularAccel);

                // Get inertia properties
                Vec3 invInertiaDiag = motionProperties.getInverseInertiaDiagonal();
                float ix = invInertiaDiag.getX() == 0f ? 0f : 1f / invInertiaDiag.getX();
                float iy = invInertiaDiag.getY() == 0f ? 0f : 1f / invInertiaDiag.getY();
                float iz = invInertiaDiag.getZ() == 0f ? 0f : 1f / invInertiaDiag.getZ();
                Vec3 inertiaDiag = new Vec3(ix, iy, iz);

                Quat inertiaRotation = motionProperties.getInertiaRotation();
                Quat invInertiaRotation = inertiaRotation.conjugated();

                // Calculate local torque in inertia space: Torque = Inertia * Accel
                Vec3 accelInInertiaSpace = Op.star(invInertiaRotation, desiredAngularAccelLocal);
                accelInInertiaSpace.setX(accelInInertiaSpace.getX() * inertiaDiag.getX());
                accelInInertiaSpace.setY(accelInInertiaSpace.getY() * inertiaDiag.getY());
                accelInInertiaSpace.setZ(accelInInertiaSpace.getZ() * inertiaDiag.getZ());

                // Transform torque back to world space
                Vec3 torqueLocal = Op.star(inertiaRotation, accelInInertiaSpace);
                Vec3 torqueWorld = Op.star(currentBodyRotation, torqueLocal);

                // Apply final corrective torque
                body.addTorque(torqueWorld);
            }
        });
    }

    /**
     * Synchronizes the current state of all grabbed bodies to all players on the server.
     */
    public void syncStateWithClients() {
        // Build sync data map
        Map<UUID, VxPhysicsGunClientManager.ClientGrabData> clientGrabData = grabbedBodies.entrySet().stream()
                .collect(Collectors.toConcurrentMap(
                        Map.Entry::getKey,
                        entry -> {
                            VxGrabbedBodyInfo info = entry.getValue();
                            // Convert Jolt Vec3 to Minecraft Vec3 for networking
                            net.minecraft.world.phys.Vec3 localHitPoint = new net.minecraft.world.phys.Vec3(
                                    info.grabPointLocal().getX(),
                                    info.grabPointLocal().getY(),
                                    info.grabPointLocal().getZ()
                            );
                            return new VxPhysicsGunClientManager.ClientGrabData(info.physicsId(), localHitPoint);
                        }
                ));
        // Broadcast the packet
        VxNetworking.sendToAll(new VxPhysicsGunSyncPacket(clientGrabData, playersTryingToGrab));
    }

    /**
     * Sends the current state to a specific player (typically upon joining).
     *
     * @param player The player to synchronize.
     */
    public void syncStateForNewPlayer(ServerPlayer player) {
        Map<UUID, VxPhysicsGunClientManager.ClientGrabData> clientGrabData = grabbedBodies.entrySet().stream()
                .collect(Collectors.toConcurrentMap(
                        Map.Entry::getKey,
                        entry -> {
                            VxGrabbedBodyInfo info = entry.getValue();
                            net.minecraft.world.phys.Vec3 localHitPoint = new net.minecraft.world.phys.Vec3(
                                    info.grabPointLocal().getX(),
                                    info.grabPointLocal().getY(),
                                    info.grabPointLocal().getZ()
                            );
                            return new VxPhysicsGunClientManager.ClientGrabData(info.physicsId(), localHitPoint);
                        }
                ));
        VxNetworking.sendToPlayer(player, new VxPhysicsGunSyncPacket(clientGrabData, playersTryingToGrab));
    }
}