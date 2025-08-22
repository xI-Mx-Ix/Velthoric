package net.xmx.velthoric.physics.object.client;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.xmx.velthoric.physics.object.client.interpolation.InterpolationFrame;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ObjectRead implements Closeable {

    private static final ConcurrentLinkedQueue<ObjectRead> POOL = new ConcurrentLinkedQueue<>();
    private static final ClientObjectDataManager dataManager = ClientObjectDataManager.getInstance();

    private UUID objectId;
    private boolean isValid = false;

    private ObjectRead() {
    }

    public static ObjectRead get(UUID objectId) {
        ObjectRead reader = POOL.poll();
        if (reader == null) {
            reader = new ObjectRead();
        }
        reader.setup(objectId);
        return reader;
    }

    @Override
    public void close() {
        this.reset();
        POOL.offer(this);
    }

    private void setup(UUID id) {
        this.objectId = id;
        this.isValid = dataManager.hasObject(id);
    }

    private void reset() {
        this.objectId = null;
        this.isValid = false;
    }

    public boolean isValid() {
        return isValid;
    }

    @Nullable
    public InterpolationFrame getInterpolationFrame() {
        if (!isValid) return null;
        return dataManager.getInterpolationFrame(objectId);
    }

    @Nullable
    public RVec3 getLatestPosition() {
        if (!isValid) return null;
        return dataManager.getLatestPosition(objectId);
    }

    @Nullable
    public EBodyType getObjectType() {
        if (!isValid) return null;
        return dataManager.getObjectType(objectId);
    }

    @Nullable
    public VxRigidBody.Renderer getRigidRenderer() {
        if (!isValid) return null;
        return dataManager.getRigidRenderer(objectId);
    }

    @Nullable
    public VxSoftBody.Renderer getSoftRenderer() {
        if (!isValid) return null;
        return dataManager.getSoftRenderer(objectId);
    }

    @Nullable
    public ByteBuffer getCustomData() {
        if (!isValid) return null;
        ByteBuffer original = dataManager.getCustomData(objectId);
        return (original != null) ? original.asReadOnlyBuffer() : null;
    }
}