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
        BLOCK_TRANSFORM(2);

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
     * This class maps directly to the native {@code MaterialConfig} C++ struct (12 bytes).
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
     * Data structure representing an interaction event triggered in the native physics world.
     * This class maps directly to the native {@code InteractionEvent} C++ struct (32 bytes).
     */
    public static class InteractionEvent {
        /**
         * The category of interaction.
         */
        public InteractionType type;
        /**
         * The material ID involved in the interaction.
         */
        public int materialId;
        /**
         * World X-coordinate of the interaction point.
         */
        public float x;
        /**
         * World Y-coordinate of the interaction point.
         */
        public float y;
        /**
         * World Z-coordinate of the interaction point.
         */
        public float z;
        /**
         * The physical strength/intensity of the event.
         */
        public float strength;
        /**
         * The internal Jolt sub-shape identifier.
         */
        public int subShapeId;
        /**
         * The ID of the terrain rigid body.
         */
        public int terrainBodyId;

        /**
         * Deserializes an event from a native {@link ByteBuffer}.
         *
         * @param buffer The source buffer.
         * @return A newly initialized {@code InteractionEvent}.
         */
        public static InteractionEvent read(ByteBuffer buffer) {
            InteractionEvent e = new InteractionEvent();
            e.type = InteractionType.fromId(buffer.getInt());
            e.materialId = buffer.getInt();
            e.x = buffer.getFloat();
            e.y = buffer.getFloat();
            e.z = buffer.getFloat();
            e.strength = buffer.getFloat();
            e.subShapeId = buffer.getInt();
            e.terrainBodyId = buffer.getInt();
            return e;
        }

        /**
         * Total size of the native {@code InteractionEvent} struct in bytes.
         */
        public static final int SIZE = 32;
    }

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
     * Retrieves and clears the queue of interaction events from the native layer.
     *
     * @param maxCount The maximum number of events to retrieve in one call.
     * @return An array containing all flushed interaction events.
     */
    public static InteractionEvent[] flushEvents(int maxCount) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(maxCount * InteractionEvent.SIZE).order(ByteOrder.nativeOrder());
        int count = nFlushEvents(buffer, maxCount);
        InteractionEvent[] events = new InteractionEvent[count];
        for (int i = 0; i < count; i++) {
            events[i] = InteractionEvent.read(buffer);
        }
        return events;
    }

    /**
     * Native call to register interaction properties.
     *
     * @param buffer Direct ByteBuffer containing {@code MaterialConfig} structs.
     * @param count  Number of configurations in the buffer.
     */
    private static native void nRegisterInteractionMaterials(ByteBuffer buffer, int count);

    /**
     * Native call to drain the interaction event queue.
     *
     * @param buffer   Direct ByteBuffer to store {@code InteractionEvent} structs.
     * @param maxCount Capacity of the buffer.
     * @return The actual number of events written into the buffer.
     */
    private static native int nFlushEvents(ByteBuffer buffer, int maxCount);
}