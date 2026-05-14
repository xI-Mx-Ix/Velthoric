/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.persistence.impl.body;

import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.core.behavior.impl.VxSoftPhysicsBehavior;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.body.server.VxServerBodyDataContainer;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.shape.VxCollisionShape;
import net.xmx.velthoric.core.body.shape.VxShapeCodec;
import net.xmx.velthoric.core.persistence.schema.VxFieldType;
import net.xmx.velthoric.core.persistence.schema.VxSchema;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A utility class for serializing and deserializing {@link VxBody} objects using a flexible,
 * high-performance TLV (Tag-Length-Value) schema.
 * <p>
 * This codec translates the state of a physics body (including transform, velocity, and
 * custom data) into a byte representation for storage, and vice versa. Deserialization
 * produces a {@link VxSerializedBodyData} record, which acts as an intermediary
 * data-transfer object for reconstructing the body.
 *
 * @author xI-Mx-Ix
 */
public final class VxBodyCodec {

    /**
     * The schema definition for physics bodies.
     * This defines all persistent properties and their exact layout.
     */
    public static final VxSchema<VxBody> SCHEMA = new VxSchema<>();

    static {
        // Field 1: Position
        SCHEMA.register((short) 1, "position", VxFieldType.RVEC3,
                body -> body.getPhysicsWorld() != null && body.getDataStoreIndex() != -1,
                (body, buf) -> {
                    VxServerBodyDataContainer c = body.getPhysicsWorld().getBodyManager().getDataStore().serverCurrent();
                    int idx = body.getDataStoreIndex();
                    buf.writeDouble(c.posX[idx]);
                    buf.writeDouble(c.posY[idx]);
                    buf.writeDouble(c.posZ[idx]);
                },
                (body, buf) -> {
                    if (body.getPhysicsWorld() != null && body.getDataStoreIndex() != -1) {
                        VxServerBodyDataContainer c = body.getPhysicsWorld().getBodyManager().getDataStore().serverCurrent();
                        int idx = body.getDataStoreIndex();
                        c.posX[idx] = buf.readDouble();
                        c.posY[idx] = buf.readDouble();
                        c.posZ[idx] = buf.readDouble();
                    } else {
                        buf.skipBytes(24);
                    }
                }
        );

        // Field 2: Rotation
        SCHEMA.register((short) 2, "rotation", VxFieldType.QUATERNION,
                body -> body.getPhysicsWorld() != null && body.getDataStoreIndex() != -1,
                (body, buf) -> {
                    VxServerBodyDataContainer c = body.getPhysicsWorld().getBodyManager().getDataStore().serverCurrent();
                    int idx = body.getDataStoreIndex();
                    buf.writeFloat(c.rotX[idx]);
                    buf.writeFloat(c.rotY[idx]);
                    buf.writeFloat(c.rotZ[idx]);
                    buf.writeFloat(c.rotW[idx]);
                },
                (body, buf) -> {
                    if (body.getPhysicsWorld() != null && body.getDataStoreIndex() != -1) {
                        VxServerBodyDataContainer c = body.getPhysicsWorld().getBodyManager().getDataStore().serverCurrent();
                        int idx = body.getDataStoreIndex();
                        c.rotX[idx] = buf.readFloat();
                        c.rotY[idx] = buf.readFloat();
                        c.rotZ[idx] = buf.readFloat();
                        c.rotW[idx] = buf.readFloat();
                    } else {
                        buf.skipBytes(16);
                    }
                }
        );

        // Field 3: Linear Velocity
        SCHEMA.register((short) 3, "linear_velocity", VxFieldType.VEC3F,
                body -> {
                    if (body.getPhysicsWorld() == null || body.getDataStoreIndex() == -1) return false;
                    VxServerBodyDataContainer c = body.getPhysicsWorld().getBodyManager().getDataStore().serverCurrent();
                    int idx = body.getDataStoreIndex();
                    return c.velX[idx] != 0 || c.velY[idx] != 0 || c.velZ[idx] != 0;
                },
                (body, buf) -> {
                    VxServerBodyDataContainer c = body.getPhysicsWorld().getBodyManager().getDataStore().serverCurrent();
                    int idx = body.getDataStoreIndex();
                    buf.writeFloat(c.velX[idx]);
                    buf.writeFloat(c.velY[idx]);
                    buf.writeFloat(c.velZ[idx]);
                },
                (body, buf) -> {
                    if (body.getPhysicsWorld() != null && body.getDataStoreIndex() != -1) {
                        VxServerBodyDataContainer c = body.getPhysicsWorld().getBodyManager().getDataStore().serverCurrent();
                        int idx = body.getDataStoreIndex();
                        c.velX[idx] = buf.readFloat();
                        c.velY[idx] = buf.readFloat();
                        c.velZ[idx] = buf.readFloat();
                    } else {
                        buf.skipBytes(12);
                    }
                }
        );

        // Field 4: Angular Velocity
        SCHEMA.register((short) 4, "angular_velocity", VxFieldType.VEC3F,
                body -> {
                    if (body.getPhysicsWorld() == null || body.getDataStoreIndex() == -1) return false;
                    VxServerBodyDataContainer c = body.getPhysicsWorld().getBodyManager().getDataStore().serverCurrent();
                    int idx = body.getDataStoreIndex();
                    return c.angVelX[idx] != 0 || c.angVelY[idx] != 0 || c.angVelZ[idx] != 0;
                },
                (body, buf) -> {
                    VxServerBodyDataContainer c = body.getPhysicsWorld().getBodyManager().getDataStore().serverCurrent();
                    int idx = body.getDataStoreIndex();
                    buf.writeFloat(c.angVelX[idx]);
                    buf.writeFloat(c.angVelY[idx]);
                    buf.writeFloat(c.angVelZ[idx]);
                },
                (body, buf) -> {
                    if (body.getPhysicsWorld() != null && body.getDataStoreIndex() != -1) {
                        VxServerBodyDataContainer c = body.getPhysicsWorld().getBodyManager().getDataStore().serverCurrent();
                        int idx = body.getDataStoreIndex();
                        c.angVelX[idx] = buf.readFloat();
                        c.angVelY[idx] = buf.readFloat();
                        c.angVelZ[idx] = buf.readFloat();
                    } else {
                        buf.skipBytes(12);
                    }
                }
        );

        // Field 5: Motion Type
        SCHEMA.register((short) 5, "motion_type", VxFieldType.BYTE,
                body -> body.getMotionType() != null && body.getMotionType() != EMotionType.Dynamic,
                (body, buf) -> buf.writeByte(body.getMotionType().ordinal()),
                (body, buf) -> {
                    int ordinal = buf.readByte();
                    body.setMotionType(EMotionType.values()[Math.max(0, Math.min(ordinal, EMotionType.values().length - 1))]);
                }
        );

        // Field 6: Activation
        SCHEMA.register((short) 6, "activation", VxFieldType.BYTE,
                body -> body.getActivation() != null && body.getActivation() != EActivation.DontActivate,
                (body, buf) -> buf.writeByte(body.getActivation().ordinal()),
                (body, buf) -> {
                    int ordinal = buf.readByte();
                    body.setActivation(EActivation.values()[Math.max(0, Math.min(ordinal, EActivation.values().length - 1))]);
                }
        );

        // Field 7: Behavior Bits
        SCHEMA.register((short) 7, "behaviors", VxFieldType.LONG,
                body -> body.getPhysicsWorld() != null && body.getDataStoreIndex() != -1,
                (body, buf) -> {
                    VxServerBodyDataContainer c = body.getPhysicsWorld().getBodyManager().getDataStore().serverCurrent();
                    buf.writeLong(c.behaviorBits[body.getDataStoreIndex()]);
                },
                (body, buf) -> {
                    if (body.getPhysicsWorld() != null && body.getDataStoreIndex() != -1) {
                        VxServerBodyDataContainer c = body.getPhysicsWorld().getBodyManager().getDataStore().serverCurrent();
                        c.behaviorBits[body.getDataStoreIndex()] = buf.readLong();
                    } else {
                        buf.skipBytes(8);
                    }
                }
        );

        // Field 8: Soft Body Vertices
        SCHEMA.register((short) 8, "soft_body_vertices", VxFieldType.BYTES,
                body -> {
                    if (body.getPhysicsWorld() == null || body.getDataStoreIndex() == -1) return false;
                    VxServerBodyDataStore store = body.getPhysicsWorld().getBodyManager().getDataStore();
                    long bits = store.serverCurrent().behaviorBits[body.getDataStoreIndex()];
                    return VxSoftPhysicsBehavior.ID.isSet(bits) && body.getPhysicsWorld().getBodyManager().retrieveSoftBodyVertices(body) != null;
                },
                (body, buf) -> {
                    float[] vertices = body.getPhysicsWorld().getBodyManager().retrieveSoftBodyVertices(body);
                    buf.writeInt(vertices.length);
                    for (float val : vertices) buf.writeFloat(val);
                },
                (body, buf) -> {
                    int vertexCount = buf.readInt();
                    if (vertexCount > 0) {
                        float[] vertices = new float[vertexCount];
                        for (int i = 0; i < vertexCount; i++) vertices[i] = buf.readFloat();
                        if (body.getPhysicsWorld() != null) {
                            body.getPhysicsWorld().getBodyManager().updateSoftBodyVertices(body, vertices);
                        }
                    }
                }
        );

        // Field 9: Collision Shape
        SCHEMA.register((short) 9, "shape", VxFieldType.SHAPE,
                body -> body.getShape() != null,
                (body, buf) -> VxShapeCodec.write(buf, body.getShape()),
                (body, buf) -> {
                    try {
                        VxCollisionShape shape = VxShapeCodec.read(buf);
                        body.setShape(shape);
                    } catch (Exception e) {
                        VxMainClass.LOGGER.warn("Failed to read shape for body {}: {}", body.getPhysicsId(), e.getMessage());
                    }
                }
        );

        // Field 10: Custom Type Data
        SCHEMA.register((short) 10, "type_data", VxFieldType.BYTES,
                body -> true, // Let the specific handler decide if it actually writes anything
                (body, buf) -> body.getType().getPersistenceHandler().write(body, buf),
                (body, buf) -> body.getType().getPersistenceHandler().read(body, buf)
        );
    }

    private VxBodyCodec() {
    }

    /**
     * Serializes a {@link VxBody} and its current physics state into a buffer.
     * <p>
     * <b>Format:</b>
     * <ul>
     *     <li>UUID (16 bytes)</li>
     *     <li>Type ID (UTF String)</li>
     *     <li>Data Length (4 bytes)</li>
     *     <li>Body Data (Schema Payload)</li>
     * </ul>
     *
     * @param body The physics body to serialize.
     * @param buf  The buffer to write the serialized data into.
     */
    public static void serialize(VxBody body, VxByteBuf buf) {
        buf.writeUUID(body.getPhysicsId());
        buf.writeUtf(body.getType().getTypeId().toString());

        // We use a temporary buffer to capture the variable-length schema payload.
        // This allows us to prefix the length for fast zero-copy loading later.
        ByteBuf tempBuf = ByteBufAllocator.DEFAULT.ioBuffer();
        try {
            VxByteBuf tempVxBuf = new VxByteBuf(tempBuf);
            SCHEMA.serialize(body, tempVxBuf);

            int length = tempBuf.readableBytes();
            if (length <= 2) { // Only END_OF_SCHEMA tag present
                return; // Do not write completely empty bodies
            }

            // Write length prefix
            buf.writeInt(length);
            // Write the actual payload
            buf.writeBytes(tempBuf);
        } finally {
            tempBuf.release();
        }
    }

    /**
     * Deserializes the identity header from the buffer and provides a sliced payload.
     *
     * @param buf The buffer to read the serialized data from.
     * @return A {@link VxSerializedBodyData} record, or null if deserialization fails.
     */
    @Nullable
    public static VxSerializedBodyData deserialize(VxByteBuf buf) {
        try {
            UUID id = buf.readUUID();
            ResourceLocation typeId = ResourceLocation.tryParse(buf.readUtf());

            // Read the length of the internal data block
            int dataLength = buf.readInt();

            // Safety check to prevent reading past buffer bounds
            if (buf.readableBytes() < dataLength) {
                VxMainClass.LOGGER.error("Malformed body data for ID {}: expected {} bytes, but only {} remain.", id, dataLength, buf.readableBytes());
                return null;
            }

            // Slice exactly the payload belonging to this body.
            VxByteBuf bodyData = new VxByteBuf(buf.readBytes(dataLength));

            return new VxSerializedBodyData(typeId, id, bodyData);

        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to deserialize physics body header", e);
            return null;
        }
    }

    /**
     * Deserializes and restores the internal state of the body from storage using the schema.
     *
     * @param body The body to deserialize into.
     * @param buf  The buffer to read from.
     */
    public static void readInternalPersistenceData(VxBody body, VxByteBuf buf) {
        try {
            SCHEMA.deserialize(body, buf);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to process schema for body {}: {}", body.getPhysicsId(), e.getMessage());
        }
    }
}