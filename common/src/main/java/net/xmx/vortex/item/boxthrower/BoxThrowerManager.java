package net.xmx.vortex.item.boxthrower;

import com.github.stephengold.joltjni.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.xmx.vortex.builtin.VxRegisteredObjects;
import net.xmx.vortex.builtin.box.BoxRigidPhysicsObject;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class BoxThrowerManager {

    private static final BoxThrowerManager INSTANCE = new BoxThrowerManager();
    private final Map<UUID, Boolean> shootingPlayers = new ConcurrentHashMap<>();

    private static final int BOXES_PER_TICK = 10;
    private static final float SPREAD_AMOUNT = 0.8f;

    private static final float BOX_SIZE = 0.75f;
    private static final float SHOOT_SPEED = 10f;
    private static final float SPAWN_OFFSET = 1.5f;

    private BoxThrowerManager() {}

    public static BoxThrowerManager getInstance() {
        return INSTANCE;
    }

    public void startShooting(ServerPlayer player) {
        shootingPlayers.put(player.getUUID(), true);
    }

    public void stopShooting(ServerPlayer player) {
        shootingPlayers.put(player.getUUID(), false);
    }

    public boolean isShooting(ServerPlayer player) {
        return shootingPlayers.getOrDefault(player.getUUID(), false);
    }

    public void serverTick(ServerPlayer player) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            stopShooting(player);
            return;
        }

        for (int i = 0; i < BOXES_PER_TICK; i++) {
            spawnSingleBox(player, physicsWorld);
        }
    }

    private void spawnSingleBox(ServerPlayer player, VxPhysicsWorld physicsWorld) {

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        var random = ThreadLocalRandom.current();

        double randomOffsetX = (random.nextDouble() - 0.5) * SPREAD_AMOUNT;
        double randomOffsetY = (random.nextDouble() - 0.5) * SPREAD_AMOUNT;
        double randomOffsetZ = (random.nextDouble() - 0.5) * SPREAD_AMOUNT;

        Vec3 spawnPosMc = eyePos.add(lookVec.scale(SPAWN_OFFSET))
                .add(randomOffsetX, randomOffsetY, randomOffsetZ);

        float halfExtent = BOX_SIZE / 2.0f;
        com.github.stephengold.joltjni.Vec3 halfExtents = new com.github.stephengold.joltjni.Vec3(halfExtent, halfExtent, halfExtent);

        VxObjectManager manager = physicsWorld.getObjectManager();
        VxTransform transform = new VxTransform(new RVec3(spawnPosMc.x, spawnPosMc.y, spawnPosMc.z), Quat.sIdentity());

        Optional<BoxRigidPhysicsObject> spawnedObjectOpt = manager.createRigidBody(
                VxRegisteredObjects.BOX,
                transform,
                box -> box.setHalfExtents(halfExtents)
        );

        spawnedObjectOpt.ifPresent(spawnedObject -> {
            physicsWorld.execute(() -> {
                int bodyId = spawnedObject.getBodyId();
                PhysicsSystem physicsSystem = physicsWorld.getPhysicsSystem();
                if (physicsSystem == null) return;

                physicsSystem.getBodyInterface().activateBody(bodyId);

                try (var lock = new BodyLockWrite(physicsSystem.getBodyLockInterface(), bodyId)) {
                    if (lock.succeededAndIsInBroadPhase()) {
                        Body body = lock.getBody();
                        if (body.isDynamic()) {

                            Vec3 spreadVec = new Vec3(
                                    (random.nextDouble() - 0.5),
                                    (random.nextDouble() - 0.5),
                                    (random.nextDouble() - 0.5)
                            ).scale(SPREAD_AMOUNT * 0.2);

                            Vec3 finalLookVec = lookVec.add(spreadVec).normalize();

                            com.github.stephengold.joltjni.Vec3 velocity = new com.github.stephengold.joltjni.Vec3(
                                    (float) finalLookVec.x,
                                    (float) finalLookVec.y,
                                    (float) finalLookVec.z
                            );

                            velocity.scaleInPlace(SHOOT_SPEED);

                            body.setLinearVelocity(velocity);
                        }
                    }
                }
            });
        });
    }
}