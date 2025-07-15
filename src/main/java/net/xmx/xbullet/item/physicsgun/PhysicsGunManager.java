package net.xmx.xbullet.item.physicsgun;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BodyLockWrite;
import com.github.stephengold.joltjni.MotionProperties;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.xmx.xbullet.physics.object.physicsobject.pcmd.DeactivateBodyCommand;
import net.xmx.xbullet.physics.object.raycast.PhysicsRaytracing;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicsGunManager {

    private static final PhysicsGunManager INSTANCE = new PhysicsGunManager();
    private final Map<UUID, GrabbedObjectInfo> grabbedObjects = new ConcurrentHashMap<>();
    private final Set<UUID> playersTryingToGrab = ConcurrentHashMap.newKeySet();

    private static final float POSITIONAL_SPRING_CONSTANT = 100_000f;
    private static final float POSITIONAL_DAMPING_FACTOR = 15_000f;

    private static final float ROTATIONAL_SPRING_CONSTANT = 25_000f;
    private static final float ROTATIONAL_DAMPING_FACTOR = 5_000f;

    private static final float MAX_LINEAR_FORCE = 750_000f;
    private static final float MAX_ANGULAR_TORQUE = 1_500_000f;

    private static final float MIN_DISTANCE = 2.0f;
    private static final float MAX_DISTANCE = 450.0f;

    private PhysicsGunManager() {
    }

    public static PhysicsGunManager getInstance() {
        return INSTANCE;
    }

    private record GrabbedObjectInfo(
            int bodyId,
            Vec3 grabPointLocal,
            float currentDistance,
            float originalAngularDamping,
            Quat initialBodyRotation,
            Quat initialPlayerRotation
    ) {
    }

    private static Quat playerRotToQuat(float pitch, float yaw) {
        Quat qPitch = Quat.sRotation(new Vec3(1, 0, 0), (float) Math.toRadians(pitch));
        Quat qYaw = Quat.sRotation(new Vec3(0, 1, 0), (float) Math.toRadians(-yaw));
        return Op.star(qYaw, qPitch);
    }

    public void startGrabAttempt(ServerPlayer player) {
        playersTryingToGrab.add(player.getUUID());
    }

    public void stopGrabAttempt(ServerPlayer player) {
        playersTryingToGrab.remove(player.getUUID());
        stopGrab(player);
    }

    public boolean isGrabbing(Player player) {
        return grabbedObjects.containsKey(player.getUUID());
    }

    public boolean isTryingToGrab(Player player) {
        return playersTryingToGrab.contains(player.getUUID());
    }

    public void startGrab(ServerPlayer player) {
        var physicsWorld = PhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null) {
            return;
        }

        final var eyePos = player.getEyePosition();
        final var lookVec = player.getLookAngle();
        final var level = player.level();

        physicsWorld.execute(() -> {
            var rayOrigin = new RVec3(eyePos.x, eyePos.y, eyePos.z);
            var rayDirection = new Vec3((float) lookVec.x, (float) lookVec.y, (float) lookVec.z);

            PhysicsRaytracing.rayCastPhysics(level, rayOrigin, rayDirection, MAX_DISTANCE).ifPresent(physicsHit -> {
                var bodyInterface = physicsWorld.getBodyInterface();
                if (bodyInterface == null) {
                    return;
                }

                bodyInterface.activateBody(physicsHit.getBodyId());

                var bodyLockInterface = physicsWorld.getBodyLockInterface();
                if (bodyLockInterface == null) {
                    return;
                }

                try (var lock = new BodyLockWrite(bodyLockInterface, physicsHit.getBodyId())) {
                    if (lock.succeededAndIsInBroadPhase() && lock.getBody().isDynamic()) {
                        Body body = lock.getBody();
                        MotionProperties motionProperties = body.getMotionProperties();

                        if (motionProperties != null) {
                            RVec3 hitPointWorld = physicsHit.calculateHitPoint(rayOrigin, rayDirection, MAX_DISTANCE);

                            try (var invBodyTransform = body.getInverseCenterOfMassTransform()) {
                                float originalDamping = motionProperties.getAngularDamping();

                                Vec3 hitPointLocal = Op.star(invBodyTransform, hitPointWorld).toVec3();
                                float grabDistance = (float) Op.minus(rayOrigin, hitPointWorld).length();

                                Quat initialPlayerRot = playerRotToQuat(player.getXRot(), player.getYRot());
                                Quat initialBodyRot = body.getRotation();

                                var info = new GrabbedObjectInfo(
                                        physicsHit.getBodyId(),
                                        hitPointLocal,
                                        grabDistance,
                                        originalDamping,
                                        initialBodyRot,
                                        initialPlayerRot
                                );

                                grabbedObjects.put(player.getUUID(), info);
                                motionProperties.setAngularDamping(2.0f);
                                body.setAngularVelocity(new Vec3(0, 0, 0));
                            }
                        }
                    }
                }
            });
        });
    }

    public void freezeObject(ServerPlayer player) {
        stopGrab(player);

        var physicsWorld = PhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null) {
            return;
        }

        final var eyePos = player.getEyePosition();
        final var lookVec = player.getLookAngle();
        final var level = player.level();

        physicsWorld.execute(() -> {
            var rayOrigin = new RVec3(eyePos.x, eyePos.y, eyePos.z);
            var rayDirection = new Vec3((float) lookVec.x, (float) lookVec.y, (float) lookVec.z);

            PhysicsRaytracing.rayCastPhysics(level, rayOrigin, rayDirection, MAX_DISTANCE).ifPresent(physicsHit -> {
                var bodyId = physicsHit.getBodyId();
                new DeactivateBodyCommand(bodyId).execute(physicsWorld);
            });
        });
    }

    public void stopGrab(ServerPlayer player) {
        playersTryingToGrab.remove(player.getUUID());
        GrabbedObjectInfo info = grabbedObjects.remove(player.getUUID());

        if (info != null) {
            var physicsWorld = PhysicsWorld.get(player.level().dimension());
            if (physicsWorld != null) {
                physicsWorld.execute(() -> {
                    var bodyInterface = physicsWorld.getBodyInterface();
                    var bodyLockInterface = physicsWorld.getBodyLockInterface();

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

    public void updateScroll(ServerPlayer player, float scrollDelta) {
        grabbedObjects.computeIfPresent(player.getUUID(), (uuid, info) -> {
            float newDistance = info.currentDistance() + scrollDelta;
            newDistance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, newDistance));
            return new GrabbedObjectInfo(
                    info.bodyId(),
                    info.grabPointLocal(),
                    newDistance,
                    info.originalAngularDamping(),
                    info.initialBodyRotation(),
                    info.initialPlayerRotation()
            );
        });
    }

    public void serverTick(ServerPlayer player) {
        var info = grabbedObjects.get(player.getUUID());
        if (info == null) {
            return;
        }

        var physicsWorld = PhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null) {
            stopGrab(player);
            return;
        }

        final var eyePos = player.getEyePosition();
        final var lookVec = player.getLookAngle();

        physicsWorld.execute(() -> {
            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            if (bodyInterface == null) {
                return;
            }

            // Sicherstellen, dass das Objekt wach ist, bevor wir es manipulieren
            if (bodyInterface.isAdded(info.bodyId())) {
                bodyInterface.activateBody(info.bodyId());
            }

            var bodyLockInterface = physicsWorld.getBodyLockInterface();
            if (bodyLockInterface == null) {
                return;
            }

            try (var lock = new BodyLockWrite(bodyLockInterface, info.bodyId())) {
                if (!lock.succeededAndIsInBroadPhase()) {
                    grabbedObjects.remove(player.getUUID());
                    return;
                }

                Body body = lock.getBody();

                try (var comTransform = body.getCenterOfMassTransform()) {
                    var targetPointWorld = new RVec3(
                            eyePos.x + lookVec.x * info.currentDistance(),
                            eyePos.y + lookVec.y * info.currentDistance(),
                            eyePos.z + lookVec.z * info.currentDistance()
                    );
                    var currentGrabPointWorld = Op.star(comTransform, info.grabPointLocal());
                    var positionError = Op.minus(targetPointWorld, currentGrabPointWorld);
                    var r = Op.minus(currentGrabPointWorld, body.getCenterOfMassPosition());
                    var pointVelocity = Op.plus(body.getLinearVelocity(), r.toVec3().cross(body.getAngularVelocity()));
                    var springForce = Op.star(positionError.toVec3(), POSITIONAL_SPRING_CONSTANT);
                    var dampingForce = Op.star(pointVelocity, POSITIONAL_DAMPING_FACTOR);
                    var force = Op.minus(springForce, dampingForce);
                    if (force.lengthSq() > MAX_LINEAR_FORCE * MAX_LINEAR_FORCE) {
                        force.normalizeInPlace();
                        force.scaleInPlace(MAX_LINEAR_FORCE);
                    }
                    body.addForce(force);
                }

                Quat currentPlayerRotation = playerRotToQuat(player.getXRot(), player.getYRot());
                Quat playerRotationDelta = Op.star(currentPlayerRotation, info.initialPlayerRotation().conjugated());
                Quat targetBodyRotation = Op.star(playerRotationDelta, info.initialBodyRotation());

                var currentBodyRotation = body.getRotation();
                var errorQuat = Op.star(targetBodyRotation, currentBodyRotation.conjugated());

                if (errorQuat.getW() < 0.0f) {
                    errorQuat.set(-errorQuat.getX(), -errorQuat.getY(), -errorQuat.getZ(), -errorQuat.getW());
                }

                var springTorque = Op.star(new Vec3(errorQuat.getX(), errorQuat.getY(), errorQuat.getZ()), 2.0f * ROTATIONAL_SPRING_CONSTANT);
                var dampingTorque = Op.star(body.getAngularVelocity(), ROTATIONAL_DAMPING_FACTOR);
                var torque = Op.minus(springTorque, dampingTorque);

                if (torque.lengthSq() > MAX_ANGULAR_TORQUE * MAX_ANGULAR_TORQUE) {
                    torque.normalizeInPlace();
                    torque.scaleInPlace(MAX_ANGULAR_TORQUE);
                }
                body.addTorque(torque);
            }
        });
    }
}