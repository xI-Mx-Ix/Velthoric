/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.boxthrower;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.builtin.VxRegisteredObjects;
import net.xmx.velthoric.builtin.box.BoxColor;
import net.xmx.velthoric.builtin.box.BoxRigidBody;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class BoxThrowerManager {

    private static final BoxThrowerManager INSTANCE = new BoxThrowerManager();
    private final Map<UUID, Boolean> shootingPlayers = new ConcurrentHashMap<>();

    private static final int BOXES_PER_TICK = 5;
    private static final float SHOOT_SPEED = 40f;
    private static final float SPAWN_OFFSET = 1.5f;
    private static final float MIN_BOX_SIZE = 0.4f;
    private static final float MAX_BOX_SIZE = 1.2f;

    private BoxThrowerManager() {}

    public static BoxThrowerManager getInstance() {
        return INSTANCE;
    }

    public void startShooting(ServerPlayer player) {
        shootingPlayers.put(player.getUUID(), true);
    }

    public void stopShooting(ServerPlayer player) {
        shootingPlayers.remove(player.getUUID());
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

        physicsWorld.execute(() -> {
            for (int i = 0; i < BOXES_PER_TICK; i++) {
                spawnAndLaunchSingleBox(player, physicsWorld);
            }
        });
    }

    private void spawnAndLaunchSingleBox(ServerPlayer player, VxPhysicsWorld physicsWorld) {
        net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
        net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();
        var random = ThreadLocalRandom.current();

        net.minecraft.world.phys.Vec3 spawnPosMc = eyePos.add(lookVec.scale(SPAWN_OFFSET));
        VxTransform transform = new VxTransform(new RVec3((float)spawnPosMc.x, (float)spawnPosMc.y, (float)spawnPosMc.z), Quat.sIdentity());

        float randomWidth = random.nextFloat(MIN_BOX_SIZE, MAX_BOX_SIZE);
        float randomHeight = random.nextFloat(MIN_BOX_SIZE, MAX_BOX_SIZE);
        float randomDepth = random.nextFloat(MIN_BOX_SIZE, MAX_BOX_SIZE);

        Vec3 halfExtents = new Vec3(randomWidth / 2.0f, randomHeight / 2.0f, randomDepth / 2.0f);

        Vec3 launchVelocity = new Vec3(
                (float) lookVec.x,
                (float) lookVec.y,
                (float) lookVec.z
        );
        launchVelocity.scaleInPlace(SHOOT_SPEED);

        VxObjectManager manager = physicsWorld.getObjectManager();

        BoxRigidBody spawnedObject = manager.createRigidBody(
                VxRegisteredObjects.BOX,
                transform,
                box -> {
                    box.setHalfExtents(halfExtents);
                    box.setColor(BoxColor.getRandom());
                }
        );

        if (spawnedObject != null) {
            var bodyId = spawnedObject.getInternalBody().getBodyId();
            var bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();
            bodyInterface.activateBody(bodyId);
            bodyInterface.setLinearVelocity(bodyId, launchVelocity);
        }
    }
}