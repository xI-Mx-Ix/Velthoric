package net.xmx.xbullet.physics.object.global.physicsobject.client;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.global.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.global.physicsobject.state.PhysicsObjectState;
import net.xmx.xbullet.physics.object.global.physicsobject.state.PhysicsObjectStatePool;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.object.rigidphysicsobject.client.ClientRigidPhysicsObjectData;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;
import net.xmx.xbullet.physics.object.softphysicsobject.client.ClientSoftPhysicsObjectData;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public class ClientPhysicsObjectManager {
    private static ClientPhysicsObjectManager instance;

    private final Map<UUID, ClientPhysicsObjectData> allObjects = new ConcurrentHashMap<>();
    private final Map<String, Supplier<RigidPhysicsObject.Renderer>> rigidRendererFactories = new ConcurrentHashMap<>();
    private final Map<String, Supplier<SoftPhysicsObject.Renderer>> softRendererFactories = new ConcurrentHashMap<>();

    private final Queue<List<PhysicsObjectState>> stateUpdateQueue = new ConcurrentLinkedQueue<>();

    private ClientPhysicsObjectManager() {}

    public static ClientPhysicsObjectManager getInstance() {
        if (instance == null) {
            synchronized (ClientPhysicsObjectManager.class) {
                if (instance == null) {
                    instance = new ClientPhysicsObjectManager();
                }
            }
        }
        return instance;
    }

    public void registerRigidRendererFactory(String typeIdentifier, Supplier<RigidPhysicsObject.Renderer> factory) {
        rigidRendererFactories.put(typeIdentifier, factory);
    }

    public void registerSoftRendererFactory(String typeIdentifier, Supplier<SoftPhysicsObject.Renderer> factory) {
        softRendererFactories.put(typeIdentifier, factory);
    }

    public void scheduleStatesForUpdate(List<PhysicsObjectState> states) {
        this.stateUpdateQueue.offer(states);
    }

    private void processStateUpdates() {
        List<PhysicsObjectState> states;
        while ((states = stateUpdateQueue.poll()) != null) {
            for (PhysicsObjectState state : states) {
                updateObject(
                        state.id(),
                        state.objectType(),
                        state.transform(),
                        state.linearVelocity(),
                        state.angularVelocity(),
                        state.softBodyVertices(),
                        null,
                        state.timestamp(),
                        state.isActive()
                );
                PhysicsObjectStatePool.release(state);
            }
        }
    }

    public void spawnObject(UUID id, String typeIdentifier, EObjectType objectType, PhysicsTransform transform, @Nullable float[] vertices, CompoundTag nbt, long serverTimestamp) {
        if (allObjects.containsKey(id)) {
            updateObject(id, objectType, transform, null, null, vertices, nbt, serverTimestamp, true);
            return;
        }

        ClientPhysicsObjectData data = new ClientPhysicsObjectData(id, typeIdentifier, objectType);

        if (objectType == EObjectType.RIGID_BODY) {
            Supplier<RigidPhysicsObject.Renderer> factory = rigidRendererFactories.get(typeIdentifier);
            RigidPhysicsObject.Renderer renderer = (factory != null) ? factory.get() : null;
            if (renderer == null) XBullet.LOGGER.warn("Client: No renderer found for rigid body type '{}'.", typeIdentifier);

            float mass = nbt.getFloat("mass");
            float friction = nbt.getFloat("friction");
            float restitution = nbt.getFloat("restitution");
            float linDamp = nbt.getFloat("linearDamping");
            float angDamp = nbt.getFloat("angularDamping");

            ClientRigidPhysicsObjectData rigidData = new ClientRigidPhysicsObjectData(id, transform, mass, friction, restitution, linDamp, angDamp, renderer, serverTimestamp);

            data.setRigidData(rigidData);

        } else if (objectType == EObjectType.SOFT_BODY) {
            Supplier<SoftPhysicsObject.Renderer> factory = softRendererFactories.get(typeIdentifier);
            SoftPhysicsObject.Renderer renderer = (factory != null) ? factory.get() : null;
            if (renderer == null) XBullet.LOGGER.warn("Client: No renderer found for soft body type '{}'.", typeIdentifier);

            ClientSoftPhysicsObjectData softData = new ClientSoftPhysicsObjectData(id, renderer, serverTimestamp);

            if (vertices != null) {
                softData.updateDataFromServer(transform, null, null, vertices, serverTimestamp, true);
            }

            data.setSoftData(softData);
        }

        data.updateNbt(nbt);

        allObjects.put(id, data);
    }

    public void updateObject(UUID id, EObjectType objectType, @Nullable PhysicsTransform transform, @Nullable Vec3 linearVel, @Nullable Vec3 angVel, @Nullable float[] vertices, @Nullable CompoundTag nbt, long serverTimestamp, boolean isActive) {
        ClientPhysicsObjectData data = allObjects.get(id);
        if (data == null) return;

        if (objectType == EObjectType.RIGID_BODY && data.getRigidData() != null) {
            data.getRigidData().updateTransformFromServer(transform, linearVel, angVel, serverTimestamp, isActive);
            if (nbt != null) data.getRigidData().updateNbt(nbt);
        } else if (objectType == EObjectType.SOFT_BODY && data.getSoftData() != null) {
            data.getSoftData().updateDataFromServer(transform, linearVel, angVel, vertices, serverTimestamp, isActive);
            if (nbt != null) data.getSoftData().updateNbt(nbt);
        }
    }

    public void removeObject(UUID id) {
        ClientPhysicsObjectData data = allObjects.remove(id);
        if (data != null) {
            data.cleanupAndRemove();
        }
    }

    public void clearAll() {
        stateUpdateQueue.clear();
        allObjects.values().forEach(ClientPhysicsObjectData::cleanupAndRemove);
        allObjects.clear();
    }

    @Nullable
    public ClientPhysicsObjectData getObjectData(UUID id) {
        return allObjects.get(id);
    }

    public Collection<ClientPhysicsObjectData> getAllObjectData() {
        return allObjects.values();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {

        if (instance != null) {
            if (event.phase == TickEvent.Phase.START) {

                instance.processStateUpdates();
            } else if (event.phase == TickEvent.Phase.END) {

                instance.allObjects.values().forEach(ClientPhysicsObjectData::updateInterpolation);
            }
        }
    }

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        if (instance != null) {
            instance.clearAll();
        }
    }

    public int getRegisteredRigidRendererFactoryCount() {
        return rigidRendererFactories.size();
    }

    public int getRegisteredSoftRendererFactoryCount() {
        return softRendererFactories.size();
    }

    public int getTotalNodeCount() {
        return getAllObjectData().stream()
                .filter(d -> d.getObjectType() == EObjectType.SOFT_BODY)
                .map(ClientPhysicsObjectData::getSoftData)
                .filter(Objects::nonNull)
                .mapToInt(softData -> {
                    float[] vertices = softData.getLatestVertexData();
                    return (vertices != null) ? vertices.length / 3 : 0;
                })
                .sum();
    }
}