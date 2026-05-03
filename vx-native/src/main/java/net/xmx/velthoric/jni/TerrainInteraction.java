/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.jni;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The {@code TerrainInteraction} class serves as the JNI (Java Native Interface) bridge
 * for Velthoric's high-performance terrain interaction system.
 * <p>
 * This class facilitates the registration of material-specific interaction properties
 * (such as breaking thresholds and transformation triggers) and provides mechanisms
 * to retrieve interaction events that occur during the native physics simulation.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class TerrainInteraction {

    /**
     * Defines the type of physical interaction event that occurred.
     */
    public enum InteractionType {
        /**
         * Visual particles triggered by sliding friction.
         */
        PARTICLE_SLIDE(0),
        /**
         * Destruction of a fragile block (e.g., Glass, Ice).
         */
        BLOCK_BREAK(1),
        /**
         * Transformation of a block into another type (e.g., Grass to Dirt).
         */
        BLOCK_TRANSFORM(2),
        /**
         * Physical interaction with a block (e.g., Doors, Gates).
         */
        BLOCK_INTERACT(3);

        /**
         * The native integer identifier for the interaction type.
         */
        public final int id;

        InteractionType(int id) {
            this.id = id;
        }

        /**
         * Resolves an {@code InteractionType} from a native integer ID.
         *
         * @param id The native ID.
         * @return The matching enum constant, or {@code PARTICLE_SLIDE} as fallback.
         */
        public static InteractionType fromId(int id) {
            for (InteractionType type : values()) if (type.id == id) return type;
            return PARTICLE_SLIDE;
        }
    }

    /**
     * Configuration structure for a material's interaction properties.
     * This class maps directly to the native {@code MaterialConfig} C++ struct (16 bytes).
     */
    public static class MaterialConfig {
        /**
         * The unique material ID (0-65535).
         */
        public int matId;
        /**
         * Whether the material can be physically destroyed.
         */
        public boolean isFragile;
        /**
         * Whether the material is eligible for terrain transformation.
         */
        public boolean isTransformable;
        /**
         * Whether sliding against this material spawns particles.
         */
        public boolean spawnsParticles;
        /**
         * The physical force threshold for destruction.
         */
        public float breakThreshold;
        /**
         * Whether the material responds to physical impact (doors, gates).
         */
        public boolean isInteractable;

        /**
         * Serializes this configuration into a direct {@link ByteBuffer} for native transfer.
         * <p>
         * Ensures correct byte alignment:
         * - Int (4 bytes)
         * - 3x Bool (3 bytes)
         * - Padding (1 byte)
         * - Float (4 bytes)
         * - Bool (1 byte)
         * - Padding (3 bytes)
         * Total: 16 bytes.
         * </p>
         *
         * @param buffer The target buffer in native byte order.
         */
        public void write(ByteBuffer buffer) {
            buffer.putInt(matId);
            buffer.put((byte) (isFragile ? 1 : 0));
            buffer.put((byte) (isTransformable ? 1 : 0));
            buffer.put((byte) (spawnsParticles ? 1 : 0));
            buffer.put((byte) 0); // Padding to align float to 4 bytes
            buffer.putFloat(breakThreshold);
            buffer.put((byte) (isInteractable ? 1 : 0));
            buffer.put((byte) 0); // Padding
            buffer.put((byte) 0); // Padding
            buffer.put((byte) 0); // Padding to align struct size to 16 bytes
        }

        /**
         * Total size of the native {@code MaterialConfig} struct in bytes.
         */
        public static final int SIZE = 16;
    }

    /**
     * Total size of the native InteractionEvent struct in bytes (aligned).
     */
    public static final int EVENT_SIZE = 48;

    /**
     * Transfers a batch of material configurations to the native physics engine.
     *
     * @param configs An array of material configurations.
     */
    public static void registerMaterials(MaterialConfig[] configs) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(configs.length * MaterialConfig.SIZE).order(ByteOrder.nativeOrder());
        for (MaterialConfig config : configs) {
            config.write(buffer);
        }
        nRegisterInteractionMaterials(buffer, configs.length);
    }

    /**
     * The maximum number of interaction events to retrieve in a single batch.
     */
    private static final int MAX_BATCH = 4096;

    /**
     * Pre-allocated direct byte buffer used for high-performance, zero-copy
     * event flushing from the native layer.
     */
    private static final ByteBuffer FLUSH_BUFFER = ByteBuffer.allocateDirect(MAX_BATCH * EVENT_SIZE).order(ByteOrder.nativeOrder());

    /**
     * Functional interface for zero-allocation event processing.
     */
    @FunctionalInterface
    public interface InteractionCallback {
        void onEvent(InteractionType type, int materialId, float x1, float y1, float z1, float x2, float y2, float z2, float strength, int subShapeId, int terrainBodyId);
    }

    /**
     * Flushes and processes native interaction events without object allocations.
     *
     * @param callback The handler to be called for each event.
     * @return The number of events processed.
     */
    public static int processEvents(InteractionCallback callback) {
        FLUSH_BUFFER.clear();
        int count = nFlushEvents(FLUSH_BUFFER, MAX_BATCH);
        if (count <= 0) return 0;

        for (int i = 0; i < count; i++) {
            InteractionType type = InteractionType.values()[FLUSH_BUFFER.getInt()];
            int matId = FLUSH_BUFFER.getInt();
            float x1 = FLUSH_BUFFER.getFloat();
            float y1 = FLUSH_BUFFER.getFloat();
            float z1 = FLUSH_BUFFER.getFloat();
            float x2 = FLUSH_BUFFER.getFloat();
            float y2 = FLUSH_BUFFER.getFloat();
            float z2 = FLUSH_BUFFER.getFloat();
            float strength = FLUSH_BUFFER.getFloat();
            int subShapeId = FLUSH_BUFFER.getInt();
            int terrainBodyId = FLUSH_BUFFER.getInt();
            FLUSH_BUFFER.getInt(); // padding

            callback.onEvent(type, matId, x1, y1, z1, x2, y2, z2, strength, subShapeId, terrainBodyId);
        }
        return count;
    }

    /**
     * JNI Endpoint: Registers interaction-specific material data.
     *
     * @param buffer Direct buffer containing MaterialConfig structs.
     * @param count  Number of configs in the buffer.
     */
    public static native void nRegisterInteractionMaterials(ByteBuffer buffer, int count);

    /**
     * JNI Endpoint: Flushes the interaction event queue into Java memory.
     *
     * @param buffer   Target direct buffer for event data.
     * @param maxCount Maximum number of events to retrieve.
     * @return The number of events actually written.
     */
    public static native int nFlushEvents(ByteBuffer buffer, int maxCount);
}