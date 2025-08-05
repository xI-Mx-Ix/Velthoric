package net.xmx.vortex.physics.object.physicsobject.client;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import dev.architectury.event.events.client.ClientTickEvent;
import io.netty.buffer.ByteBuf;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.event.api.VxClientPlayerNetworkEvent;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.EObjectType;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.InterpolationStateContainer;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.RenderData;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectState;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectStatePool;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.RigidPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.type.soft.SoftPhysicsObject;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public class ClientObjectDataManager {

    private static final ClientObjectDataManager INSTANCE = new ClientObjectDataManager();

    private final Map<UUID, InterpolationStateContainer> stateContainers = new ConcurrentHashMap<>();
    private final Map<UUID, EObjectType> objectTypes = new ConcurrentHashMap<>();
    private final Map<UUID, String> typeIdentifiers = new ConcurrentHashMap<>();
    private final Map<UUID, RigidPhysicsObject.Renderer> rigidRenderers = new ConcurrentHashMap<>();
    private final Map<UUID, SoftPhysicsObject.Renderer> softRenderers = new ConcurrentHashMap<>();
    private final Map<UUID, ByteBuffer> customDataMap = new ConcurrentHashMap<>();

    private final Map<String, Supplier<RigidPhysicsObject.Renderer>> rigidRendererFactories = new ConcurrentHashMap<>();
    private final Map<String, Supplier<SoftPhysicsObject.Renderer>> softRendererFactories = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<List<PhysicsObjectState>> stateUpdateQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Vec3> vec3Pool = new ArrayDeque<>();
    private final VxTransform tempTransform = new VxTransform();

    private ClientObjectDataManager() {}

    public static ClientObjectDataManager getInstance() {
        return INSTANCE;
    }

    private Vec3 acquireVec3() {
        return vec3Pool.isEmpty() ? new Vec3() : vec3Pool.poll();
    }

    private void releaseVec3(Vec3 vec) {
        if (vec != null && vec3Pool.size() < 16) {
            vec3Pool.offer(vec);
        }
    }

    public void registerRigidRendererFactory(String identifier, Supplier<RigidPhysicsObject.Renderer> factory) {
        rigidRendererFactories.put(identifier, factory);
    }

    public void registerSoftRendererFactory(String identifier, Supplier<SoftPhysicsObject.Renderer> factory) {
        softRendererFactories.put(identifier, factory);
    }

    public void scheduleStatesForUpdate(List<PhysicsObjectState> states) {
        stateUpdateQueue.offer(states);
    }

    private void processStateUpdates() {
        List<PhysicsObjectState> states;
        while ((states = stateUpdateQueue.poll()) != null) {
            for (PhysicsObjectState state : states) {
                updateObjectState(state);
                PhysicsObjectStatePool.release(state);
            }
        }
    }

    public void spawnObject(UUID id, String typeId, EObjectType objType, FriendlyByteBuf data, long serverTimestamp) {
        if (stateContainers.containsKey(id)) {
            return;
        }

        objectTypes.put(id, objType);
        typeIdentifiers.put(id, typeId);

        InterpolationStateContainer container = new InterpolationStateContainer();
        stateContainers.put(id, container);
        tempTransform.fromBuffer(data);

        Vec3 linVel = acquireVec3();
        Vec3 angVel = acquireVec3();

        try {
            if (objType == EObjectType.RIGID_BODY) {
                Supplier<RigidPhysicsObject.Renderer> factory = rigidRendererFactories.get(typeId);
                if (factory != null) rigidRenderers.put(id, factory.get());
                else VxMainClass.LOGGER.warn("Client: No renderer factory for rigid body type '{}'.", typeId);

                if (data.readableBytes() >= 24) {
                    linVel.set(data.readFloat(), data.readFloat(), data.readFloat());
                    angVel.set(data.readFloat(), data.readFloat(), data.readFloat());
                } else {
                    linVel.loadZero();
                    angVel.loadZero();
                }
                container.addState(serverTimestamp, tempTransform, linVel, angVel, null, true);

            } else if (objType == EObjectType.SOFT_BODY) {
                Supplier<SoftPhysicsObject.Renderer> factory = softRendererFactories.get(typeId);
                if (factory != null) softRenderers.put(id, factory.get());
                else VxMainClass.LOGGER.warn("Client: No renderer factory for soft body type '{}'.", typeId);

                linVel.loadZero();
                angVel.loadZero();
                container.addState(serverTimestamp, tempTransform, linVel, angVel, null, true);
            }

            if (data.readableBytes() > 0) {
                ByteBuffer offHeapBuffer = ByteBuffer.allocateDirect(data.readableBytes());
                data.readBytes(offHeapBuffer);
                offHeapBuffer.flip();
                customDataMap.put(id, offHeapBuffer);
            }
        } finally {
            releaseVec3(linVel);
            releaseVec3(angVel);
        }
    }

    private void updateObjectState(PhysicsObjectState state) {
        InterpolationStateContainer container = stateContainers.get(state.getId());
        if (container != null && state.getTransform() != null) {
            container.addState(
                    state.getTimestamp(),
                    state.getTransform(),
                    state.getLinearVelocity(),
                    state.getAngularVelocity(),
                    state.getSoftBodyVertices(),
                    state.isActive()
            );
        }
    }

    public void updateCustomObjectData(UUID id, ByteBuf data) {
        ByteBuffer offHeapBuffer = ByteBuffer.allocateDirect(data.readableBytes());
        data.readBytes(offHeapBuffer);
        offHeapBuffer.flip();
        customDataMap.put(id, offHeapBuffer);
    }

    public void removeObject(UUID id) {
        InterpolationStateContainer container = stateContainers.remove(id);
        if (container != null) {
            container.release();
        }
        objectTypes.remove(id);
        typeIdentifiers.remove(id);
        rigidRenderers.remove(id);
        softRenderers.remove(id);
        customDataMap.remove(id);
    }

    public void clearAll() {
        stateUpdateQueue.clear();
        stateContainers.values().forEach(InterpolationStateContainer::release);
        stateContainers.clear();
        objectTypes.clear();
        typeIdentifiers.clear();
        rigidRenderers.clear();
        softRenderers.clear();
        customDataMap.clear();
    }

    @Nullable
    public RenderData getRenderData(UUID id, float partialTicks) {
        InterpolationStateContainer container = stateContainers.get(id);
        if (container == null) return null;
        return container.getInterpolatedState(partialTicks);
    }

    @Nullable
    public RVec3 getLatestPosition(UUID id) {
        InterpolationStateContainer container = stateContainers.get(id);
        if (container == null) return null;
        return container.getLastKnownPosition();
    }

    @Nullable
    public EObjectType getObjectType(UUID id) {
        return objectTypes.get(id);
    }

    @Nullable
    public RigidPhysicsObject.Renderer getRigidRenderer(UUID id) {
        return rigidRenderers.get(id);
    }

    @Nullable
    public SoftPhysicsObject.Renderer getSoftRenderer(UUID id) {
        return softRenderers.get(id);
    }

    @Nullable
    public ByteBuffer getCustomData(UUID id) {
        return customDataMap.get(id);
    }

    public Collection<UUID> getAllObjectIds() {
        return stateContainers.keySet();
    }

    public static void registerEvents() {
        ClientTickEvent.CLIENT_PRE.register(client -> INSTANCE.processStateUpdates());
        VxClientPlayerNetworkEvent.LoggingOut.EVENT.register(event -> INSTANCE.clearAll());
    }

    public boolean hasObject(UUID id) {
        return stateContainers.containsKey(id);
    }

    public int getRegisteredRigidRendererFactoryCount() {
        return rigidRendererFactories.size();
    }

    public int getRegisteredSoftRendererFactoryCount() {
        return softRendererFactories.size();
    }

    public int getTotalNodeCount() {
        return stateContainers.entrySet().stream()
                .filter(entry -> objectTypes.get(entry.getKey()) == EObjectType.SOFT_BODY)
                .mapToInt(entry -> {
                    InterpolationStateContainer container = entry.getValue();
                    if (container == null) return 0;
                    float[] vertices = container.getLatestVertexData();
                    return (vertices != null) ? vertices.length / 3 : 0;
                })
                .sum();
    }
}