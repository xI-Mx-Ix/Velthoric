package net.xmx.velthoric.physics.object.client;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import dev.architectury.event.events.client.ClientTickEvent;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.event.api.VxClientPlayerNetworkEvent;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.client.time.VxClientClock;
import net.xmx.velthoric.physics.object.state.PhysicsObjectState;
import net.xmx.velthoric.physics.object.state.PhysicsObjectStatePool;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

public class VxClientObjectManager {
    private static final VxClientObjectManager INSTANCE = new VxClientObjectManager();
    private static final long INTERPOLATION_DELAY_NANOS = 100_000_000L;

    private final VxClientObjectStore store = new VxClientObjectStore();
    private final VxClientObjectRegistry registry = new VxClientObjectRegistry();
    private final VxClientObjectInterpolator interpolator = new VxClientObjectInterpolator();
    private final VxClientClock clock = VxClientClock.getInstance();

    private long clockOffsetNanos = 0L;
    private boolean isClockOffsetInitialized = false;
    private final List<Long> clockOffsetSamples = Collections.synchronizedList(new ArrayList<>());
    private final ConcurrentLinkedQueue<List<PhysicsObjectState>> stateUpdateQueue = new ConcurrentLinkedQueue<>();
    private final VxTransform tempTransform = new VxTransform();

    private VxClientObjectManager() {}

    public static VxClientObjectManager getInstance() {
        return INSTANCE;
    }

    public void registerRigidRendererFactory(String identifier, Supplier<VxRigidBody.Renderer> factory) {
        registry.registerRigidRendererFactory(identifier, factory);
    }

    public void registerSoftRendererFactory(String identifier, Supplier<VxSoftBody.Renderer> factory) {
        registry.registerSoftRendererFactory(identifier, factory);
    }

    public void scheduleStatesForUpdate(List<PhysicsObjectState> states) {
        stateUpdateQueue.offer(states);
    }

    private void processStateUpdates() {
        List<PhysicsObjectState> states;
        while ((states = stateUpdateQueue.poll()) != null) {
            long clientReceiptTime = clock.getGameTimeNanos();
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
            averageOffset = clockOffsetSamples.stream().mapToLong(Long::longValue).sum() / clockOffsetSamples.size();
            clockOffsetSamples.clear();
        }
        if (!isClockOffsetInitialized) {
            this.clockOffsetNanos = averageOffset;
            this.isClockOffsetInitialized = true;
        } else {
            this.clockOffsetNanos = (long) (this.clockOffsetNanos * 0.95 + averageOffset * 0.05);
        }
    }

    private void updateObjectState(PhysicsObjectState state) {
        Integer index = store.getIndexForId(state.getId());
        if (index == null) return;

        store.state0_timestamp[index] = store.state1_timestamp[index];
        store.state0_posX[index] = store.state1_posX[index];
        store.state0_posY[index] = store.state1_posY[index];
        store.state0_posZ[index] = store.state1_posZ[index];
        store.state0_rotX[index] = store.state1_rotX[index];
        store.state0_rotY[index] = store.state1_rotY[index];
        store.state0_rotZ[index] = store.state1_rotZ[index];
        store.state0_rotW[index] = store.state1_rotW[index];
        store.state0_vertexData[index] = store.state1_vertexData[index];

        store.state1_timestamp[index] = state.getTimestamp();
        RVec3 pos = state.getTransform().getTranslation();
        store.state1_posX[index] = pos.x();
        store.state1_posY[index] = pos.y();
        store.state1_posZ[index] = pos.z();
        Quat rot = state.getTransform().getRotation();
        store.state1_rotX[index] = rot.getX();
        store.state1_rotY[index] = rot.getY();
        store.state1_rotZ[index] = rot.getZ();
        store.state1_rotW[index] = rot.getW();

        float[] newVertices = state.getSoftBodyVertices();
        if (newVertices != null) {

            store.state1_vertexData[index] = newVertices;
        } else {

            store.state1_vertexData[index] = store.state0_vertexData[index];
        }

        store.lastKnownPosition[index].set(pos);
    }

    public void spawnObject(UUID id, String typeId, EBodyType objType, FriendlyByteBuf data, long serverTimestamp) {
        if (store.hasObject(id)) return;

        int index = store.addObject(id);
        store.objectType[index] = objType;
        tempTransform.fromBuffer(data);

        RVec3 pos = tempTransform.getTranslation();
        Quat rot = tempTransform.getRotation();

        store.state0_timestamp[index] = serverTimestamp;
        store.state1_timestamp[index] = serverTimestamp;
        store.state0_posX[index] = store.state1_posX[index] = pos.x();
        store.state0_posY[index] = store.state1_posY[index] = pos.y();
        store.state0_posZ[index] = store.state1_posZ[index] = pos.z();
        store.state0_rotX[index] = store.state1_rotX[index] = rot.getX();
        store.state0_rotY[index] = store.state1_rotY[index] = rot.getY();
        store.state0_rotZ[index] = store.state1_rotZ[index] = rot.getZ();
        store.state0_rotW[index] = store.state1_rotW[index] = rot.getW();

        store.lastKnownPosition[index].set(pos);

        if (objType == EBodyType.RigidBody) {
            store.renderer[index] = registry.createRigidRenderer(typeId);
        } else if (objType == EBodyType.SoftBody) {
            store.renderer[index] = registry.createSoftRenderer(typeId);

            store.state0_vertexData[index] = null;
            store.state1_vertexData[index] = null;
        }

        if (store.renderer[index] == null) {
            VxMainClass.LOGGER.warn("Client: No renderer for body type '{}'.", typeId);
        }

        long initialOffset = serverTimestamp - clock.getGameTimeNanos();
        this.clockOffsetSamples.add(initialOffset);
        if (!isClockOffsetInitialized) synchronizeClock();

        if (data.readableBytes() > 0) {
            ByteBuffer offHeapBuffer = ByteBuffer.allocateDirect(data.readableBytes());
            data.readBytes(offHeapBuffer);
            offHeapBuffer.flip();
            store.customData[index] = offHeapBuffer;
        }
    }

    public void removeObject(UUID id) {
        store.removeObject(id);
    }

    public void updateCustomObjectData(UUID id, ByteBuf data) {
        Integer index = store.getIndexForId(id);
        if (index == null) return;
        ByteBuffer offHeapBuffer = ByteBuffer.allocateDirect(data.readableBytes());
        data.readBytes(offHeapBuffer);
        offHeapBuffer.flip();
        store.customData[index] = offHeapBuffer;
    }

    public void clearAll() {
        store.clear();
        stateUpdateQueue.clear();
        isClockOffsetInitialized = false;
        clockOffsetNanos = 0L;
        clockOffsetSamples.clear();
    }

    public void clientTick() {
        processStateUpdates();
        synchronizeClock();
        if (isClockOffsetInitialized) {
            long renderTimestamp = clock.getGameTimeNanos() + this.clockOffsetNanos - INTERPOLATION_DELAY_NANOS;
            interpolator.updateInterpolationTargets(store, renderTimestamp);
        }
    }

    public static void registerEvents() {
        ClientTickEvent.CLIENT_PRE.register(client -> INSTANCE.clientTick());
        VxClientPlayerNetworkEvent.LoggingOut.EVENT.register(event -> INSTANCE.clearAll());
    }

    public VxClientObjectStore getStore() {
        return store;
    }

    public VxClientObjectInterpolator getInterpolator() {
        return interpolator;
    }
}