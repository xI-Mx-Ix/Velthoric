package net.xmx.vortex.physics.object.riding;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.vortex.init.registry.EntityRegistry;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.UUID;

public class RidingManager {

    private final VxPhysicsWorld world;
    private final Object2ObjectMap<UUID, UUID> playerToObjectIdMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, ServerPlayer> objectIdToPlayerMap = new Object2ObjectOpenHashMap<>();

    public RidingManager(VxPhysicsWorld world) {
        this.world = world;
    }

    public void startRiding(ServerPlayer player, IPhysicsObject object) {
        if (player.level().isClientSide() || !(object instanceof Rideable rideable)) {
            return;
        }
        if (isRiding(player) || isBeingRidden(object.getPhysicsId())) {
            return;
        }

        RidingProxyEntity proxy = new RidingProxyEntity(EntityRegistry.RIDING_PROXY.get(), player.level());
        com.github.stephengold.joltjni.Vec3 rideOffsetJolt = rideable.getRidePositionOffset();
        Vector3f rideOffsetJoml = new Vector3f(rideOffsetJolt.getX(), rideOffsetJolt.getY(), rideOffsetJolt.getZ());
        proxy.setFollowInfo(object.getPhysicsId(), rideOffsetJoml);

        var initialTransform = object.getCurrentTransform();
        var initialPos = initialTransform.getTranslation();
        var initialRot = initialTransform.getRotation();
        Quaternionf initialQuat = new Quaternionf(initialRot.getX(), initialRot.getY(), initialRot.getZ(), initialRot.getW());
        Vector3f eulerAngles = new Vector3f();
        initialQuat.getEulerAnglesXYZ(eulerAngles);

        proxy.setPos(initialPos.x(), initialPos.y(), initialPos.z());
        proxy.setYRot((float) Math.toDegrees(eulerAngles.y));

        rideable.onStartRiding(player, proxy);
        world.getLevel().addFreshEntity(proxy);
        player.startRiding(proxy, true);

        playerToObjectIdMap.put(player.getUUID(), object.getPhysicsId());
        objectIdToPlayerMap.put(object.getPhysicsId(), player);
    }

    public void stopRiding(ServerPlayer player) {
        if (!isRiding(player)) {
            return;
        }
        UUID physicsId = playerToObjectIdMap.remove(player.getUUID());
        if (physicsId != null) {
            objectIdToPlayerMap.remove(physicsId);
            world.getObjectManager().getObject(physicsId).ifPresent(object -> {
                if (object instanceof Rideable rideable) {
                    rideable.onStopRiding(player);
                    RidingProxyEntity proxy = rideable.getRidingProxy();
                    if (proxy != null && !proxy.isRemoved()) {
                        proxy.discard();
                    }
                }
            });
        }
        if (player.getVehicle() instanceof RidingProxyEntity) {
            player.stopRiding();
        }
    }

    public void tick() {
        Iterator<ServerPlayer> iterator = objectIdToPlayerMap.values().iterator();
        while (iterator.hasNext()) {
            ServerPlayer rider = iterator.next();
            if (rider.isRemoved() || !rider.isPassenger() || !(rider.getVehicle() instanceof RidingProxyEntity)) {
                UUID physicsId = playerToObjectIdMap.remove(rider.getUUID());
                if (physicsId != null) {
                    iterator.remove();
                }
            } else {
                UUID objectId = playerToObjectIdMap.get(rider.getUUID());
                if (objectId != null) {
                    world.getObjectManager().getObject(objectId).ifPresent(physObject -> {
                        var trans = physObject.getCurrentTransform();
                        var pos = trans.getTranslation();
                        var rot = trans.getRotation();

                        Quaternionf jomlQuat = new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW());
                        Vector3f eulerAngles = new Vector3f();
                        jomlQuat.getEulerAnglesXYZ(eulerAngles);
                        float yawDegrees = (float) Math.toDegrees(eulerAngles.y);

                        rider.getVehicle().setPos(pos.x(), pos.y(), pos.z());
                        rider.getVehicle().setYRot(yawDegrees);
                    });
                }
            }
        }
    }

    // TODO: Register Event
    public void onPlayerDisconnect(ServerPlayer player) {
        if (isRiding(player)) {
            stopRiding(player);
        }
    }

    public boolean isRiding(ServerPlayer player) {
        return playerToObjectIdMap.containsKey(player.getUUID());
    }

    public boolean isBeingRidden(UUID objectId) {
        return objectIdToPlayerMap.containsKey(objectId);
    }
}