package net.xmx.xbullet.item.physicsgun;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.xbullet.physics.object.global.PhysicsRaytracing;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicsGunManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final PhysicsGunManager INSTANCE = new PhysicsGunManager();
    private final Map<UUID, GrabbedObjectInfo> grabbedObjects = new ConcurrentHashMap<>();

    private static final float POSITIONAL_SPRING_CONSTANT = 400000.0f;
    private static final float POSITIONAL_DAMPING_FACTOR = 40000.0f;
    private static final float ROTATIONAL_SPRING_CONSTANT = 20000.0f;
    private static final float ROTATIONAL_DAMPING_FACTOR = 2000.0f;
    private static final float MAX_LINEAR_FORCE = 1000000.0f;
    private static final float MAX_ANGULAR_TORQUE = 10000.0f;
    private static final float MIN_DISTANCE = 1.5f;
    private static final float MAX_DISTANCE = 25.0f;

    private PhysicsGunManager() {}

    public static PhysicsGunManager getInstance() {
        return INSTANCE;
    }

    private record GrabbedObjectInfo(
            int bodyId,
            Vec3 grabPointLocal,
            float currentDistance,
            float originalGravityFactor,
            float originalAngularDamping,
            Vec3 originalAngularVelocity,
            Quat lastPlayerLookRotation
    ) {}

    public void startGrab(ServerPlayer player) {
        if (grabbedObjects.containsKey(player.getUUID())) return;

        var physicsWorld = PhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null) return;

        final var eyePos = player.getEyePosition();
        final var lookVec = player.getLookAngle();
        final var level = player.level();

        physicsWorld.execute(() -> {
            var rayOrigin = new RVec3(eyePos.x, eyePos.y, eyePos.z);
            var rayDirection = new Vec3((float) lookVec.x, (float) lookVec.y, (float) lookVec.z);

            PhysicsRaytracing.rayCastPhysics(level, rayOrigin, rayDirection, MAX_DISTANCE).ifPresent(physicsHit -> {
                var bodyInterface = physicsWorld.getBodyInterface();
                var bodyLockInterface = physicsWorld.getBodyLockInterface();
                if (bodyInterface == null || bodyLockInterface == null) return;

                boolean grabSuccessful = false;
                try (var lock = new BodyLockWrite(bodyLockInterface, physicsHit.getBodyId())) {
                    if (lock.succeededAndIsInBroadPhase() && lock.getBody().isDynamic()) {
                        Body body = lock.getBody();
                        MotionProperties motionProperties = body.getMotionProperties();
                        if (motionProperties != null) {
                            RVec3 hitPointWorld = physicsHit.calculateHitPoint(rayOrigin, rayDirection, MAX_DISTANCE);

                            try (var invBodyTransform = body.getInverseCenterOfMassTransform()) {
                                float originalGravity = motionProperties.getGravityFactor();
                                float originalDamping = motionProperties.getAngularDamping();
                                Vec3 originalVelocity = body.getAngularVelocity();

                                var hitPointLocal = Op.star(invBodyTransform, hitPointWorld).toVec3();
                                var grabDistance = (float) Op.minus(rayOrigin, hitPointWorld).length();
                                var playerLookRotation = Quat.sFromTo(new Vec3(0, 0, -1), rayDirection);

                                var info = new GrabbedObjectInfo(
                                        physicsHit.getBodyId(),
                                        hitPointLocal,
                                        grabDistance,
                                        originalGravity,
                                        originalDamping,
                                        originalVelocity,
                                        playerLookRotation
                                );

                                grabbedObjects.put(player.getUUID(), info);
                                grabSuccessful = true;

                                motionProperties.setGravityFactor(0f);
                                motionProperties.setAngularDamping(1.0f);
                                body.setAngularVelocity(new Vec3(0, 0, 0));
                            }
                        }
                    }
                }

                if (grabSuccessful) {
                    bodyInterface.activateBody(physicsHit.getBodyId());
                }
            });
        });
    }

    public void stopGrab(ServerPlayer player) {
        GrabbedObjectInfo info = grabbedObjects.remove(player.getUUID());
        if (info != null) {
            var physicsWorld = PhysicsWorld.get(player.level().dimension());
            if (physicsWorld != null) {
                physicsWorld.execute(() -> {
                    var bodyInterface = physicsWorld.getBodyInterface();
                    if (bodyInterface != null) {
                        var bodyLockInterface = physicsWorld.getBodyLockInterface();
                        try (var lock = new BodyLockWrite(bodyLockInterface, info.bodyId())) {
                            if (lock.succeededAndIsInBroadPhase()) {
                                Body body = lock.getBody();
                                MotionProperties motionProperties = body.getMotionProperties();
                                if (motionProperties != null) {
                                    motionProperties.setGravityFactor(info.originalGravityFactor());
                                    motionProperties.setAngularDamping(info.originalAngularDamping());
                                }
                                body.setAngularVelocity(info.originalAngularVelocity());
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
            float newDistance = info.currentDistance() + scrollDelta * 0.75f;
            newDistance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, newDistance));
            return new GrabbedObjectInfo(
                    info.bodyId(),
                    info.grabPointLocal(),
                    newDistance,
                    info.originalGravityFactor(),
                    info.originalAngularDamping(),
                    info.originalAngularVelocity(),
                    info.lastPlayerLookRotation()
            );
        });
    }

    public void serverTick(ServerPlayer player) {
        var info = grabbedObjects.get(player.getUUID());
        if (info == null) return;

        var physicsWorld = PhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null) {
            stopGrab(player);
            return;
        }

        final var eyePos = player.getEyePosition();
        final var lookVec = player.getLookAngle();

        physicsWorld.execute(() -> {
            var bodyLockInterface = physicsWorld.getBodyLockInterface();
            if (bodyLockInterface == null) return;

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
                    var linearVelocity = body.getLinearVelocity();
                    var angularVelocity = body.getAngularVelocity();
                    var comPos = body.getCenterOfMassPosition();
                    var r = Op.minus(currentGrabPointWorld, comPos);
                    var angularComponent = r.toVec3().cross(angularVelocity);
                    var pointVelocity = Op.plus(linearVelocity, angularComponent);
                    var force = Op.minus(
                            Op.star(positionError.toVec3(), POSITIONAL_SPRING_CONSTANT),
                            Op.star(pointVelocity, POSITIONAL_DAMPING_FACTOR)
                    );
                    if (force.lengthSq() > MAX_LINEAR_FORCE * MAX_LINEAR_FORCE) {
                        force.normalizeInPlace();
                        force.scaleInPlace(MAX_LINEAR_FORCE);
                    }
                    body.addForce(force);
                }

                var joltLookVec = new Vec3((float) lookVec.x, (float) lookVec.y, (float) lookVec.z);
                var currentPlayerLookRotation = Quat.sFromTo(new Vec3(0, 0, -1), joltLookVec);
                var rotationDelta = Op.star(currentPlayerLookRotation, info.lastPlayerLookRotation().conjugated());
                var currentBodyRotation = body.getRotation();
                var targetBodyRotation = Op.star(rotationDelta, currentBodyRotation);
                var errorQuat = Op.star(targetBodyRotation, currentBodyRotation.conjugated());
                var rotationAxis = new Vec3(errorQuat.getX(), errorQuat.getY(), errorQuat.getZ());
                float angle = rotationAxis.length();
                if (angle > 0) {
                    rotationAxis.scaleInPlace(1.0f / angle);
                }
                float timeStep = 1.0f / 20.0f;
                var desiredAngularVelocity = Op.star(rotationAxis, angle / timeStep);
                var currentAngularVelocity = body.getAngularVelocity();
                var angularVelocityError = Op.minus(desiredAngularVelocity, currentAngularVelocity);
                var torque = Op.star(angularVelocityError, ROTATIONAL_SPRING_CONSTANT);
                if (torque.lengthSq() > MAX_ANGULAR_TORQUE * MAX_ANGULAR_TORQUE) {
                    torque.normalizeInPlace();
                    torque.scaleInPlace(MAX_ANGULAR_TORQUE);
                }
                body.addTorque(torque);

                grabbedObjects.computeIfPresent(player.getUUID(), (uuid, oldInfo) ->
                        new GrabbedObjectInfo(
                                oldInfo.bodyId(), oldInfo.grabPointLocal(), oldInfo.currentDistance(),
                                oldInfo.originalGravityFactor(), oldInfo.originalAngularDamping(),
                                oldInfo.originalAngularVelocity(),
                                currentPlayerLookRotation
                        )
                );
            }
        });
    }
}