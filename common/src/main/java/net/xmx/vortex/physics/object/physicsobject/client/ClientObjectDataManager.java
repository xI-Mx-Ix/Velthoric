package net.xmx.vortex.physics.object.physicsobject.client;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import dev.architectury.event.events.client.ClientTickEvent;
import io.netty.buffer.ByteBuf;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.event.api.VxClientPlayerNetworkEvent;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.InterpolationFrame;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.InterpolationStateContainer;
import net.xmx.vortex.physics.object.physicsobject.client.time.VxClientClock;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectState;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectStatePool;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.VxRigidBody;
import net.xmx.vortex.physics.object.physicsobject.type.soft.VxSoftBody;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public class ClientObjectDataManager {

    private static final ClientObjectDataManager INSTANCE = new ClientObjectDataManager();
    private static final long INTERPOLATION_DELAY_NANOS = 100_000_000L;

    private long clockOffsetNanos = 0L;
    private boolean isClockOffsetInitialized = false;

    private final Map<UUID, InterpolationStateContainer> stateContainers = new ConcurrentHashMap<>();
    private final Map<UUID, InterpolationFrame> renderFrames = new ConcurrentHashMap<>();
    private final Map<UUID, EBodyType> objectTypes = new ConcurrentHashMap<>();
    private final Map<UUID, String> typeIdentifiers = new ConcurrentHashMap<>();
    private final Map<UUID, VxRigidBody.Renderer> rigidRenderers = new ConcurrentHashMap<>();
    private final Map<UUID, VxSoftBody.Renderer> softRenderers = new ConcurrentHashMap<>();
    private final Map<UUID, ByteBuffer> customDataMap = new ConcurrentHashMap<>();
    private final Map<String, Supplier<VxRigidBody.Renderer>> rigidRendererFactories = new ConcurrentHashMap<>();
    private final Map<String, Supplier<VxSoftBody.Renderer>> softRendererFactories = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<List<PhysicsObjectState>> stateUpdateQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Vec3> vec3Pool = new ConcurrentLinkedQueue<>();
    private final VxTransform tempTransform = new VxTransform();
    private final List<Long> clockOffsetSamples = Collections.synchronizedList(new ArrayList<>());

    private ClientObjectDataManager() {}

    public static ClientObjectDataManager getInstance() {
        return INSTANCE;
    }

    private Vec3 acquireVec3() {
        Vec3 v = vec3Pool.poll();
        return v != null ? v : new Vec3();
    }

    private void releaseVec3(Vec3 vec) {
        if (vec != null) {
            vec3Pool.offer(vec);
        }
    }

    public void registerRigidRendererFactory(String identifier, Supplier<VxRigidBody.Renderer> factory) {
        rigidRendererFactories.put(identifier, factory);
    }

    public void registerSoftRendererFactory(String identifier, Supplier<VxSoftBody.Renderer> factory) {
        softRendererFactories.put(identifier, factory);
    }

    public void scheduleStatesForUpdate(List<PhysicsObjectState> states) {
        stateUpdateQueue.offer(states);
    }

    private void processStateUpdates() {
        List<PhysicsObjectState> states;
        while ((states = stateUpdateQueue.poll()) != null) {
            long clientReceiptTime = VxClientClock.getInstance().getGameTimeNanos();
            for (PhysicsObjectState state : states) {
                this.clockOffsetSamples.add(state.getTimestamp() - clientReceiptTime);
                updateObjectState(state);
                PhysicsObjectStatePool.release(state);
            }
        }
    }

    private void synchronizeClock() {
        if (clockOffsetSamples.isEmpty()) return;

        long averageOffset;
        synchronized (clockOffsetSamples) {
            long sum = 0;
            for (long sample : clockOffsetSamples) {
                sum += sample;
            }
            averageOffset = sum / clockOffsetSamples.size();
            clockOffsetSamples.clear();
        }

        if (!isClockOffsetInitialized) {
            this.clockOffsetNanos = averageOffset;
            this.isClockOffsetInitialized = true;
        } else {
            double smoothingFactor = 0.05;
            this.clockOffsetNanos = (long) (this.clockOffsetNanos * (1.0 - smoothingFactor) + averageOffset * smoothingFactor);
        }
    }

    private void updateInterpolationTargets() {
        if (!isClockOffsetInitialized) return;

        long estimatedServerTime = VxClientClock.getInstance().getGameTimeNanos() + this.clockOffsetNanos;
        long renderTimestamp = estimatedServerTime - INTERPOLATION_DELAY_NANOS;

        for (Map.Entry<UUID, InterpolationStateContainer> entry : stateContainers.entrySet()) {
            UUID id = entry.getKey();
            InterpolationStateContainer container = entry.getValue();
            InterpolationFrame frame = renderFrames.computeIfAbsent(id, k -> new InterpolationFrame());

            frame.previous.set(frame.current);

            container.getInterpolatedState(renderTimestamp, frame.current);

            if (!frame.isInitialized) {
                frame.previous.set(frame.current);
                frame.isInitialized = true;
            }
        }
    }

    public void spawnObject(UUID id, String typeId, EBodyType objType, FriendlyByteBuf data, long serverTimestamp) {
        stateContainers.computeIfAbsent(id, key -> {
            renderFrames.put(id, new InterpolationFrame());
            objectTypes.put(id, objType);
            typeIdentifiers.put(id, typeId);

            InterpolationStateContainer container = new InterpolationStateContainer();
            tempTransform.fromBuffer(data);

            Vec3 linVel = acquireVec3();
            Vec3 angVel = acquireVec3();

            try {
                if (objType == EBodyType.RigidBody) {
                    Supplier<VxRigidBody.Renderer> factory = rigidRendererFactories.get(typeId);
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
                } else if (objType == EBodyType.SoftBody) {
                    Supplier<VxSoftBody.Renderer> factory = softRendererFactories.get(typeId);
                    if (factory != null) softRenderers.put(id, factory.get());
                    else VxMainClass.LOGGER.warn("Client: No renderer factory for soft body type '{}'.", typeId);

                    linVel.loadZero();
                    angVel.loadZero();
                    container.addState(serverTimestamp, tempTransform, linVel, angVel, null, true);
                }

                long initialOffset = serverTimestamp - VxClientClock.getInstance().getGameTimeNanos();
                this.clockOffsetSamples.add(initialOffset);
                if (!isClockOffsetInitialized) {
                    synchronizeClock();
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
            return container;
        });
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
        renderFrames.remove(id);
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
        renderFrames.clear();
        objectTypes.clear();
        typeIdentifiers.clear();
        rigidRenderers.clear();
        softRenderers.clear();
        customDataMap.clear();
        isClockOffsetInitialized = false;
        clockOffsetNanos = 0L;
        clockOffsetSamples.clear();
    }

    @Nullable
    public InterpolationFrame getInterpolationFrame(UUID id) {
        return renderFrames.get(id);
    }

    @Nullable
    public RVec3 getLatestPosition(UUID id) {
        InterpolationStateContainer container = stateContainers.get(id);
        return (container != null) ? container.getLastKnownPosition() : null;
    }

    @Nullable
    public EBodyType getObjectType(UUID id) {
        return objectTypes.get(id);
    }

    @Nullable
    public VxRigidBody.Renderer getRigidRenderer(UUID id) {
        return rigidRenderers.get(id);
    }

    @Nullable
    public VxSoftBody.Renderer getSoftRenderer(UUID id) {
        return softRenderers.get(id);
    }

    @Nullable
    public ByteBuffer getCustomData(UUID id) {
        return customDataMap.get(id);
    }

    public Collection<UUID> getAllObjectIds() {
        return Collections.unmodifiableCollection(stateContainers.keySet());
    }

    public int getRegisteredRigidRendererFactoryCount() {
        return rigidRendererFactories.size();
    }

    public int getRegisteredSoftRendererFactoryCount() {
        return softRendererFactories.size();
    }

    public int getTotalNodeCount() {
        int total = 0;
        for (UUID id : objectTypes.keySet()) {
            if (objectTypes.get(id) == EBodyType.SoftBody) {
                InterpolationFrame frame = renderFrames.get(id);
                if (frame != null && frame.current.vertexData != null) {
                    total += frame.current.vertexData.length / 3;
                }
            }
        }
        return total;
    }

    public static void registerEvents() {
        ClientTickEvent.CLIENT_PRE.register(client -> {
            INSTANCE.processStateUpdates();
            INSTANCE.synchronizeClock();
            INSTANCE.updateInterpolationTargets();
        });
        VxClientPlayerNetworkEvent.LoggingOut.EVENT.register(event -> INSTANCE.clearAll());
    }

    public boolean hasObject(UUID id) {
        return stateContainers.containsKey(id);
    }
}