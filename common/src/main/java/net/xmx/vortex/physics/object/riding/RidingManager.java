package net.xmx.vortex.physics.object.riding;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.vortex.init.registry.EntityRegistry;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

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
        proxy.setFollowInfo(object.getPhysicsId(), rideable.getRidePositionOffset());

        proxy.setPos(object.getCurrentTransform().getTranslation().x(),
                object.getCurrentTransform().getTranslation().y(),
                object.getCurrentTransform().getTranslation().z());

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

        UUID physicsId = playerToObjectIdMap.get(player.getUUID());
        if (physicsId != null) {
            world.getObjectManager().getObject(physicsId).ifPresent(object -> {
                if (object instanceof Rideable rideable) {

                    RidingProxyEntity proxy = rideable.getRidingProxy();
                    if (proxy != null && !proxy.isRemoved()) {
                        proxy.discard();
                    }
                    rideable.onStopRiding(player);
                }
            });

            objectIdToPlayerMap.remove(physicsId);
            playerToObjectIdMap.remove(player.getUUID());
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
                iterator.remove();
                stopRiding(rider);
            }
        }
    }

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