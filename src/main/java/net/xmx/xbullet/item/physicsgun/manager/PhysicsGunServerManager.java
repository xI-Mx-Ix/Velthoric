package net.xmx.xbullet.item.physicsgun.manager;

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
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import net.xmx.xbullet.item.physicsgun.GrabbedObjectInfo;
import net.xmx.xbullet.item.physicsgun.packet.PhysicsGunStatePacket;
import net.xmx.xbullet.network.NetworkHandler;
import net.xmx.xbullet.physics.object.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.physicsobject.pcmd.DeactivateBodyCommand;
import net.xmx.xbullet.physics.object.raycast.PhysicsRaytracing;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
        playersTryingToGrab.add(player.getUUID());
    }

    public void stopGrabAttempt(ServerPlayer player) {
        playersTryingToGrab.remove(player.getUUID());
        stopGrab(player);
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

    public void startGrab(ServerPlayer player) {
        var physicsWorld = PhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null) return;

        final var eyePos = player.getEyePosition();
        final var lookVec = player.getLookAngle();
        final Level level = player.level();

        physicsWorld.execute(() -> {
            var rayOrigin = new RVec3(eyePos.x, eyePos.y, eyePos.z);
            var rayDirection = new Vec3((float) lookVec.x, (float) lookVec.y, (float) lookVec.z);

            PhysicsRaytracing.rayCastPhysics(level, rayOrigin, rayDirection, MAX_DISTANCE).ifPresent(physicsHit -> {
                Optional<IPhysicsObject> physicsObjectOpt = physicsWorld.getObjectManager().getObjectByBodyId(physicsHit.getBodyId());
                if (physicsObjectOpt.isEmpty()) return;

                IPhysicsObject physicsObject = physicsObjectOpt.get();
                UUID objectId = physicsObject.getPhysicsId();

                var bodyInterface = physicsWorld.getBodyInterface();
                if (bodyInterface == null) return;

                bodyInterface.activateBody(physicsHit.getBodyId());

                var bodyLockInterface = physicsWorld.getBodyLockInterface();
                if (bodyLockInterface == null) return;

                try (var lock = new BodyLockWrite(bodyLockInterface, physicsHit.getBodyId())) {
                    if (lock.succeededAndIsInBroadPhase() && lock.getBody().isDynamic()) {
                        Body body = lock.getBody();
                        MotionProperties motionProperties = body.getMotionProperties();
                        if (motionProperties == null) return;

                        RVec3 hitPointWorld = physicsHit.calculateHitPoint(rayOrigin, rayDirection, MAX_DISTANCE);

                        try (var invBodyTransform = body.getInverseCenterOfMassTransform()) {
                            float originalDamping = motionProperties.getAngularDamping();
                            Vec3 hitPointLocal = Op.star(invBodyTransform, hitPointWorld).toVec3();
                            float grabDistance = (float) Op.minus(rayOrigin, hitPointWorld).length();
                            Quat initialPlayerRot = playerRotToQuat(player.getXRot(), player.getYRot());
                            Quat initialBodyRot = body.getRotation();

                            var info = new GrabbedObjectInfo(
                                    objectId,
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

                            // Send packet to all clients that a player started grabbing an object
                            NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(),
                                    new PhysicsGunStatePacket(player.getUUID(), objectId));
                        }
                    }
                }
            });
        });
    }

    public void freezeObject(ServerPlayer player) {
        stopGrab(player);
        var physicsWorld = PhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null) return;

        final var eyePos = player.getEyePosition();
        final var lookVec = player.getLookAngle();
        final Level level = player.level();

        physicsWorld.execute(() -> {
            var rayOrigin = new RVec3(eyePos.x, eyePos.y, eyePos.z);
            var rayDirection = new Vec3((float) lookVec.x, (float) lookVec.y, (float) lookVec.z);

            PhysicsRaytracing.rayCastPhysics(level, rayOrigin, rayDirection, MAX_DISTANCE).ifPresent(physicsHit -> {
                new DeactivateBodyCommand(physicsHit.getBodyId()).execute(physicsWorld);
            });
        });
    }

    public void stopGrab(ServerPlayer player) {
        playersTryingToGrab.remove(player.getUUID());
        GrabbedObjectInfo info = grabbedObjects.remove(player.getUUID());

        if (info != null) {

            NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(),
                    new PhysicsGunStatePacket(player.getUUID(), null));

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
                    info.objectId(),
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
        if (info == null) return;

        var physicsWorld = PhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null) {
            stopGrab(player);
            return;
        }

        final var eyePos = player.getEyePosition();
        final var lookVec = player.getLookAngle();

        physicsWorld.execute(() -> {
            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            if (bodyInterface == null) return;
            if (bodyInterface.isAdded(info.bodyId())) {
                bodyInterface.activateBody(info.bodyId());
            }

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

                    float velocityScale = 5.0f;
                    var desiredVelocity = Op.star(positionError.toVec3(), velocityScale);
                    float maxVel = 150.0f;
                    if (desiredVelocity.lengthSq() > maxVel * maxVel) {
                        desiredVelocity.normalizeInPlace();
                        desiredVelocity.scaleInPlace(maxVel);
                    }
                    body.setLinearVelocity(desiredVelocity);
                }

                Quat currentPlayerRotation = playerRotToQuat(player.getXRot(), player.getYRot());
                Quat playerRotationDelta = Op.star(currentPlayerRotation, info.initialPlayerRotation().conjugated());
                Quat targetBodyRotation = Op.star(playerRotationDelta, info.initialBodyRotation());
                var currentBodyRotation = body.getRotation();
                var errorQuat = Op.star(targetBodyRotation, currentBodyRotation.conjugated());
                if (errorQuat.getW() < 0.0f) {
                    errorQuat.set(-errorQuat.getX(), -errorQuat.getY(), -errorQuat.getZ(), -errorQuat.getW());
                }

                float angularVelocityScale = 3.0f;
                var desiredAngularVelocity = Op.star(new Vec3(errorQuat.getX(), errorQuat.getY(), errorQuat.getZ()), angularVelocityScale);
                float maxAngularVel = 25.0f;
                if (desiredAngularVelocity.lengthSq() > maxAngularVel * maxAngularVel) {
                    desiredAngularVelocity.normalizeInPlace();
                    desiredAngularVelocity.scaleInPlace(maxAngularVel);
                }
                body.setAngularVelocity(desiredAngularVelocity);
            }
        });
    }
}