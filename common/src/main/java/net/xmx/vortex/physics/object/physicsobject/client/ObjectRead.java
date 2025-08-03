package net.xmx.vortex.physics.object.physicsobject.client;

import com.github.stephengold.joltjni.RVec3;
import net.xmx.vortex.physics.object.physicsobject.EObjectType;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.RenderData;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.RigidPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.type.soft.SoftPhysicsObject;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A temporary, read-only handle for a client-side physics object.
 * This class is designed to be used with a try-with-resources statement to ensure
 * it is always released back to the pool.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * try (ObjectRead objRead = ObjectRead.get(objectId)) {
 *     if (objRead.isValid()) {
 *         RenderData data = objRead.getRenderData(partialTicks);
 *         // ... use the data
 *     }
 * }
 * }</pre>
 */
public final class ObjectRead implements Closeable {

    private static final ConcurrentLinkedQueue<ObjectRead> POOL = new ConcurrentLinkedQueue<>();
    private static final ClientObjectDataManager dataManager = ClientObjectDataManager.getInstance();

    private UUID objectId;
    private boolean isValid = false;

    private ObjectRead() {
        // Private constructor to enforce pooling
    }

    /**
     * Acquires a read handle for the specified object ID from the pool.
     * This handle must be closed (ideally using a try-with-resources block) to be returned to the pool.
     *
     * @param objectId The UUID of the object to read.
     * @return A configured {@code ObjectRead} instance.
     */
    public static ObjectRead get(UUID objectId) {
        ObjectRead reader = POOL.poll();
        if (reader == null) {
            reader = new ObjectRead();
        }
        reader.setup(objectId);
        return reader;
    }

    /**
     * Releases this instance back to the pool for reuse.
     * This is called automatically when the object is used within a try-with-resources statement.
     */
    @Override
    public void close() {
        this.reset();
        POOL.offer(this);
    }

    /**
     * Prepares the handle for use with a specific object ID.
     */
    private void setup(UUID id) {
        this.objectId = id;
        this.isValid = dataManager.hasObject(id);
    }

    /**
     * Resets the handle's state.
     */
    private void reset() {
        this.objectId = null;
        this.isValid = false;
    }

    /**
     * Checks if the handle points to an object that existed at the moment of acquisition.
     *
     * @return {@code true} if the object is valid and its data can be accessed, {@code false} otherwise.
     */
    public boolean isValid() {
        return isValid;
    }

    /**
     * Retrieves the interpolated rendering data for the object.
     *
     * @param partialTicks The fraction of the current tick that has passed.
     * @return The {@link RenderData} for the object, or {@code null} if the handle is invalid.
     */
    @Nullable
    public RenderData getRenderData(float partialTicks) {
        if (!isValid) return null;
        return dataManager.getRenderData(objectId, partialTicks);
    }

    /**
     * Retrieves the last known (most recent) position of the object.
     *
     * @return The latest {@link RVec3} position, or {@code null} if the handle is invalid.
     */
    @Nullable
    public RVec3 getLatestPosition() {
        if (!isValid) return null;
        return dataManager.getLatestPosition(objectId);
    }

    /**
     * Retrieves the type of the physics object.
     *
     * @return The {@link EObjectType}, or {@code null} if the handle is invalid.
     */
    @Nullable
    public EObjectType getObjectType() {
        if (!isValid) return null;
        return dataManager.getObjectType(objectId);
    }

    /**
     * Retrieves the renderer for a rigid body object.
     *
     * @return The {@link RigidPhysicsObject.Renderer}, or {@code null} if the handle is invalid or the object is not a rigid body.
     */
    @Nullable
    public RigidPhysicsObject.Renderer getRigidRenderer() {
        if (!isValid) return null;
        return dataManager.getRigidRenderer(objectId);
    }

    /**
     * Retrieves the renderer for a soft body object.
     *
     * @return The {@link SoftPhysicsObject.Renderer}, or {@code null} if the handle is invalid or the object is not a soft body.
     */
    @Nullable
    public SoftPhysicsObject.Renderer getSoftRenderer() {
        if (!isValid) return null;
        return dataManager.getSoftRenderer(objectId);
    }

    /**
     * Retrieves the custom data associated with the object.
     * The returned buffer is a read-only view to prevent accidental modification.
     *
     * @return A read-only {@link ByteBuffer} with the custom data, or {@code null} if the handle is invalid or no custom data exists.
     */
    @Nullable
    public ByteBuffer getCustomData() {
        if (!isValid) return null;
        ByteBuffer original = dataManager.getCustomData(objectId);
        return (original != null) ? original.asReadOnlyBuffer() : null;
    }
}