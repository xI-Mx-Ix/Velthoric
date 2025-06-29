package net.xmx.xbullet.item.physicsgun.server;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.xmx.xbullet.init.registry.ItemRegistry;
import net.xmx.xbullet.item.physicsgun.GrabbedObjectInfo;
import net.xmx.xbullet.item.physicsgun.PhysicsGunServerRaycastHandler;
import net.xmx.xbullet.item.physicsgun.packet.S2CConfirmGrabPacket;
import net.xmx.xbullet.network.NetworkHandler;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManagerRegistry;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicsGunServerHandler {

    private static final ConcurrentHashMap<UUID, GrabbedObjectInfo> HELD_OBJECTS = new ConcurrentHashMap<>();

    private static final float MIN_DISTANCE = 1.5f;
    private static final float MAX_DISTANCE = 300.0f;
    private static final float FORCE_STRENGTH = 600f;
    private static final float DAMPING_FACTOR = 50f;
    private static final float TORQUE_STRENGTH = 50f;
    private static final float TORQUE_DAMPING = 15f;

    public static void handleGrabRequest(ServerPlayer player) {
        if (HELD_OBJECTS.containsKey(player.getUUID())) return;

        Optional<PhysicsGunServerRaycastHandler.HitResult> hitOpt = PhysicsGunServerRaycastHandler.performRaycast(player);
        if (hitOpt.isEmpty()) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CConfirmGrabPacket(false));
            return;
        }

        PhysicsGunServerRaycastHandler.HitResult hitResult = hitOpt.get();
        IPhysicsObject pco = hitResult.physicsObject();
        com.github.stephengold.joltjni.Vec3 hitPoint = hitResult.hitPoint();

        PhysicsObjectManager manager = PhysicsObjectManagerRegistry.getInstance().getManagerForLevel(player.serverLevel());
        manager.getPhysicsWorld().execute(() -> {
            net.minecraft.world.phys.Vec3 mcEyePos = player.getEyePosition();
            net.minecraft.world.phys.Vec3 mcHitPoint = new net.minecraft.world.phys.Vec3(hitPoint.getX(), hitPoint.getY(), hitPoint.getZ());
            float dist = (float) mcEyePos.distanceTo(mcHitPoint);
            Quat playerRotation = getPlayerLookRotation(player);

            GrabbedObjectInfo info = null;
            if (pco instanceof RigidPhysicsObject rpo) {
                info = new GrabbedObjectInfo(rpo, hitPoint.toRVec3(), playerRotation, dist);
            } else if (pco instanceof SoftPhysicsObject spo) {
                try (BodyLockRead lock = new BodyLockRead(manager.getPhysicsWorld().getBodyLockInterface(), spo.getBodyId())) {
                    if (lock.succeeded()) {
                        int nodeId = PhysicsGunServerRaycastHandler.findClosestNode(lock.getBody(), hitPoint);
                        if (nodeId != -1) {
                            info = new GrabbedObjectInfo(spo, nodeId, dist);
                        }
                    }
                }
            }

            final GrabbedObjectInfo finalInfo = info;
            player.getServer().execute(() -> {
                if (finalInfo != null) {
                    HELD_OBJECTS.put(player.getUUID(), finalInfo);
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CConfirmGrabPacket(true));
                } else {
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CConfirmGrabPacket(false));
                }
            });
        });
    }

    public static void handleReleaseRequest(ServerPlayer player) {
        HELD_OBJECTS.remove(player.getUUID());
    }

    public static void handleFreezeRequest(ServerPlayer player) {
        Optional<PhysicsGunServerRaycastHandler.HitResult> hitOpt = PhysicsGunServerRaycastHandler.performRaycast(player);
        if (hitOpt.isEmpty() || !(hitOpt.get().physicsObject() instanceof RigidPhysicsObject rpo)) {
            return;
        }

        PhysicsObjectManager manager = PhysicsObjectManagerRegistry.getInstance().getManagerForLevel(player.serverLevel());
        manager.getPhysicsWorld().execute(() -> {
            try (BodyLockWrite lock = new BodyLockWrite(manager.getPhysicsWorld().getBodyLockInterface(), rpo.getBodyId())) {
                Body body = lock.getBody();
                if (body != null) {
                    boolean isKinematic = body.isKinematic();
                    body.setMotionType(isKinematic ? EMotionType.Dynamic : EMotionType.Kinematic);
                    if (!isKinematic) {
                        player.getServer().execute(() -> HELD_OBJECTS.entrySet().removeIf(entry -> entry.getValue().objectId.equals(rpo.getPhysicsId())));
                    }
                }
            }
        });
    }

    public static void handleScrollUpdate(ServerPlayer player, float scrollDelta) {
        GrabbedObjectInfo info = HELD_OBJECTS.get(player.getUUID());
        if (info != null) {
            info.targetDistance = Mth.clamp(info.targetDistance + scrollDelta, MIN_DISTANCE, MAX_DISTANCE);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        HELD_OBJECTS.forEach((playerUUID, info) -> {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerUUID);
            if (player == null || !player.getMainHandItem().is(ItemRegistry.PHYSICS_GUN.get())) {
                HELD_OBJECTS.remove(playerUUID);
                return;
            }

            PhysicsObjectManager manager = PhysicsObjectManagerRegistry.getInstance().getManagerForLevel(player.serverLevel());
            manager.getPhysicsWorld().execute(() -> {
                if (info.isRigid) {
                    applyForceToRigidBody(player, info, manager.getPhysicsWorld());
                } else {
                    applyForceToSoftBodyNode(player, info, manager.getPhysicsWorld());
                }
            });
        });
    }

    private static void applyForceToRigidBody(ServerPlayer player, GrabbedObjectInfo info, PhysicsWorld physicsWorld) {
        Integer bodyId = physicsWorld.getBodyIds().get(info.objectId);
        if (bodyId == null) {
            player.getServer().execute(() -> HELD_OBJECTS.remove(player.getUUID()));
            return;
        }

        try (BodyLockWrite lock = new BodyLockWrite(physicsWorld.getBodyLockInterface(), bodyId);
             MotionProperties mp = lock.succeeded() ? lock.getBody().getMotionProperties() : null) {

            if (mp == null || mp.getInverseMass() == 0f) {
                player.getServer().execute(() -> HELD_OBJECTS.remove(player.getUUID()));
                return;
            }

            Body body = lock.getBody();
            if (body.isKinematic()) {
                player.getServer().execute(() -> HELD_OBJECTS.remove(player.getUUID()));
                return;
            }

            RVec3 targetGrabPoint = calculateTargetPoint(player, info.targetDistance);
            Quat bodyRotation = body.getRotation();
            com.github.stephengold.joltjni.Vec3 worldGrabOffset = Op.star(bodyRotation, info.localGrabOffset);
            RVec3 targetBodyCenter = Op.minus(targetGrabPoint, worldGrabOffset.toRVec3());

            RVec3 currentPos = body.getCenterOfMassPosition();
            com.github.stephengold.joltjni.Vec3 posError = Op.minus(targetBodyCenter, currentPos).toVec3();

            if (posError.lengthSq() > 400) { // 20 blocks
                player.getServer().execute(() -> HELD_OBJECTS.remove(player.getUUID()));
                return;
            }

            com.github.stephengold.joltjni.Vec3 force = Op.star(posError, FORCE_STRENGTH);
            com.github.stephengold.joltjni.Vec3 dampingForce = Op.star(body.getLinearVelocity(), -DAMPING_FACTOR);
            body.addForce(Op.plus(force, dampingForce));

            Quat playerRot = getPlayerLookRotation(player);
            Quat targetRotation = Op.star(playerRot, info.localRotationOffset);
            Quat rotError = Op.star(targetRotation, body.getRotation().conjugated());
            if (rotError.getW() < 0) {
                rotError.set(-rotError.getX(), -rotError.getY(), -rotError.getZ(), -rotError.getW());
            }

            com.github.stephengold.joltjni.Vec3 torqueAxis = new com.github.stephengold.joltjni.Vec3(rotError.getX(), rotError.getY(), rotError.getZ());
            com.github.stephengold.joltjni.Vec3 torque = Op.star(torqueAxis, TORQUE_STRENGTH);
            com.github.stephengold.joltjni.Vec3 dampingTorque = Op.star(body.getAngularVelocity(), -TORQUE_DAMPING);
            body.addTorque(Op.plus(torque, dampingTorque));
        }
    }

    private static void applyForceToSoftBodyNode(ServerPlayer player, GrabbedObjectInfo info, PhysicsWorld physicsWorld) {
        Integer bodyId = physicsWorld.getBodyIds().get(info.objectId);
        if (bodyId == null) {
            player.getServer().execute(() -> HELD_OBJECTS.remove(player.getUUID()));
            return;
        }

        try (BodyLockWrite lock = new BodyLockWrite(physicsWorld.getBodyLockInterface(), bodyId);
             MotionProperties baseMp = lock.succeeded() ? lock.getBody().getMotionProperties() : null) {

            if (baseMp == null || !lock.getBody().isSoftBody()) {
                player.getServer().execute(() -> HELD_OBJECTS.remove(player.getUUID()));
                return;
            }
            SoftBodyMotionProperties mp = (SoftBodyMotionProperties) baseMp;

            if (info.nodeId >= mp.getVertices().length) {
                player.getServer().execute(() -> HELD_OBJECTS.remove(player.getUUID()));
                return;
            }

            try (SoftBodyVertex vertex = mp.getVertex(info.nodeId)) {
                if (vertex.getInvMass() == 0f) {
                    player.getServer().execute(() -> HELD_OBJECTS.remove(player.getUUID()));
                    return;
                }

                RVec3 targetNodePosR = calculateTargetPoint(player, info.targetDistance);
                com.github.stephengold.joltjni.Vec3 targetNodePos = targetNodePosR.toVec3();
                com.github.stephengold.joltjni.Vec3 posError = Op.minus(targetNodePos, vertex.getPosition());

                com.github.stephengold.joltjni.Vec3 force = Op.star(posError, FORCE_STRENGTH * 0.1f);
                com.github.stephengold.joltjni.Vec3 dampingForce = Op.star(vertex.getVelocity(), -DAMPING_FACTOR * 0.5f);
                com.github.stephengold.joltjni.Vec3 totalForce = Op.plus(force, dampingForce);

                com.github.stephengold.joltjni.Vec3 deltaV = Op.star(totalForce, vertex.getInvMass() * physicsWorld.getFixedTimeStep());
                vertex.setVelocity(Op.plus(vertex.getVelocity(), deltaV));
            }
        }
    }

    private static RVec3 calculateTargetPoint(ServerPlayer player, float distance) {
        net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
        net.minecraft.world.phys.Vec3 lookVec = player.getViewVector(1.0f);
        return new RVec3(
                eyePos.x + lookVec.x * distance,
                eyePos.y + lookVec.y * distance,
                eyePos.z + lookVec.z * distance);
    }

    private static Quat getPlayerLookRotation(ServerPlayer player) {
        float yaw = -player.getYRot() * Mth.DEG_TO_RAD - ((float)Math.PI / 2.0f);
        float pitch = -player.getXRot() * Mth.DEG_TO_RAD;
        return Quat.sEulerAngles(pitch, yaw, 0f);
    }
}