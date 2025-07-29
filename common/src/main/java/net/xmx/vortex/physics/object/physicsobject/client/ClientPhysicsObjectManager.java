package net.xmx.vortex.physics.object.physicsobject.client;

import com.github.stephengold.joltjni.Vec3;
import dev.architectury.event.events.client.ClientTickEvent;
import io.netty.buffer.ByteBuf;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.event.api.VxClientPlayerNetworkEvent;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.EObjectType;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectState;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectStatePool;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.RigidPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.client.ClientRigidPhysicsObjectData;
import net.xmx.vortex.physics.object.physicsobject.type.soft.SoftPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.type.soft.client.ClientSoftPhysicsObjectData;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public class ClientPhysicsObjectManager {
    private static ClientPhysicsObjectManager instance;

    private final Map<UUID, ClientPhysicsObjectData> allObjects = new ConcurrentHashMap<>();
    private final Map<String, Supplier<RigidPhysicsObject.Renderer>> rigidRendererFactories = new ConcurrentHashMap<>();
    private final Map<String, Supplier<SoftPhysicsObject.Renderer>> softRendererFactories = new ConcurrentHashMap<>();
    private final Queue<List<PhysicsObjectState>> stateUpdateQueue = new ConcurrentLinkedQueue<>();

    private final ByteBuffer offHeapBuffer = ByteBuffer.allocateDirect(8);
    private long lastSecondTime = System.currentTimeMillis();

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

        int current = offHeapBuffer.getInt(0);
        offHeapBuffer.putInt(0, current + 1);

        long now = System.currentTimeMillis();
        if (now - lastSecondTime >= 1000) {
            int count = offHeapBuffer.getInt(0);
            offHeapBuffer.putInt(4, count);
            offHeapBuffer.putInt(0, 0);
            lastSecondTime = now;
        }
    }


    void processStateUpdates() {
        List<PhysicsObjectState> states;
        while ((states = stateUpdateQueue.poll()) != null) {
            for (PhysicsObjectState state : states) {
                updateObject(
                        state.getId(),
                        state.getEObjectType(),
                        state.getTransform(),
                        state.getLinearVelocity(),
                        state.getAngularVelocity(),
                        state.getSoftBodyVertices(),
                        state.getTimestamp(),
                        state.isActive()
                );
                PhysicsObjectStatePool.release(state);
            }
        }
    }

    public void spawnObject(UUID id, String typeIdentifier, EObjectType objectType, FriendlyByteBuf data, long serverTimestamp) {
        if (allObjects.containsKey(id)) {
            return;
        }

        ClientPhysicsObjectData clientData = new ClientPhysicsObjectData(id, typeIdentifier, objectType);

        if (objectType == EObjectType.RIGID_BODY) {
            Supplier<RigidPhysicsObject.Renderer> factory = rigidRendererFactories.get(typeIdentifier);
            RigidPhysicsObject.Renderer renderer = (factory != null) ? factory.get() : null;
            if (renderer == null) VxMainClass.LOGGER.warn("Client: No renderer found for rigid body type '{}'.", typeIdentifier);

            ClientRigidPhysicsObjectData rigidData = new ClientRigidPhysicsObjectData(id, renderer, serverTimestamp);
            rigidData.readData(data);
            clientData.setRigidData(rigidData);

        } else if (objectType == EObjectType.SOFT_BODY) {
            Supplier<SoftPhysicsObject.Renderer> factory = softRendererFactories.get(typeIdentifier);
            SoftPhysicsObject.Renderer renderer = (factory != null) ? factory.get() : null;
            if (renderer == null) VxMainClass.LOGGER.warn("Client: No renderer found for soft body type '{}'.", typeIdentifier);

            ClientSoftPhysicsObjectData softData = new ClientSoftPhysicsObjectData(id, renderer, serverTimestamp);
            softData.readData(data);
            clientData.setSoftData(softData);
        }

        allObjects.put(id, clientData);
    }

    public void updateObject(UUID id, EObjectType objectType, @Nullable VxTransform transform, @Nullable Vec3 linearVel, @Nullable Vec3 angVel, @Nullable float[] vertices, long serverTimestamp, boolean isActive) {
        ClientPhysicsObjectData data = allObjects.get(id);
        if (data == null) return;

        if (objectType == EObjectType.RIGID_BODY && data.getRigidData() != null) {
            data.getRigidData().updateTransformFromServer(transform, linearVel, angVel, serverTimestamp, isActive);
        } else if (objectType == EObjectType.SOFT_BODY && data.getSoftData() != null) {
            data.getSoftData().updateDataFromServer(transform, linearVel, angVel, vertices, serverTimestamp, isActive);
        }
    }

    public void updateObjectData(UUID id, ByteBuf data) {
        ClientPhysicsObjectData clientData = allObjects.get(id);
        if (clientData != null) {
            clientData.updateData(data);
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

    public static void registerEvents() {
        ClientTickEvent.CLIENT_PRE.register(ClientPhysicsObjectManager::onClientTick);
        VxClientPlayerNetworkEvent.LoggingOut.EVENT.register(ClientPhysicsObjectManager::onClientDisconnect);
    }

    private static void onClientTick(Minecraft client) {
        if (instance != null) {
            instance.processStateUpdates();
        }
    }

    private static void onClientDisconnect(VxClientPlayerNetworkEvent.LoggingOut event) {
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

    public int getStateUpdatesPerSecond() {
        return offHeapBuffer.getInt(4);
    }

}