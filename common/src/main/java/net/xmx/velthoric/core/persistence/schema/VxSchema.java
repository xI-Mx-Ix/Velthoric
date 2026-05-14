/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.persistence.schema;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * A highly performant, flexible Tag-Length-Value (TLV) schema for data persistence.
 * <p>
 * This system provides NBT-like flexibility (allowing fields to be added, removed, or reordered
 * without breaking backward compatibility) while maintaining the raw speed and low GC pressure
 * of direct buffer writing. Unknown fields are safely skipped, and unchanged default values
 * can be omitted to save space.
 *
 * @param <T> The type of object managed by this schema.
 * @author xI-Mx-Ix
 */
public class VxSchema<T> {

    /**
     * A reserved field ID indicating the end of the schema payload.
     * It ensures the parser knows exactly when an object's properties have finished.
     */
    public static final short END_OF_SCHEMA = 0;

    /**
     * An internal registry mapping the unique field IDs to their respective definitions.
     */
    private final Map<Short, VxSchemaField<T>> fields = new HashMap<>();

    /**
     * Registers a new field within the schema architecture.
     * <p>
     * Every field added via this method becomes a fully supported TLV node. The schema will
     * automatically manage its writing, length-prefixing (if variable), and dynamic loading.
     *
     * @param id        The unique identifier for this field (must be greater than 0).
     * @param name      A human-readable name for the field (used for debugging purposes).
     * @param type      The binary data type of the field defining its fixed or dynamic length.
     * @param condition A condition evaluating if the field should be written (e.g. to skip default values).
     * @param writer    The consumer function that extracts data from the object and writes it.
     * @param reader    The consumer function that reads data and applies it back to the object.
     * @throws IllegalArgumentException if the ID is 0 or already registered.
     */
    public void register(short id, String name, VxFieldType type, Predicate<T> condition, BiConsumer<T, VxByteBuf> writer, BiConsumer<T, VxByteBuf> reader) {
        if (id == END_OF_SCHEMA) {
            throw new IllegalArgumentException("Field ID 0 is reserved for END_OF_SCHEMA.");
        }
        if (fields.containsKey(id)) {
            throw new IllegalArgumentException("Field ID " + id + " is already registered.");
        }
        fields.put(id, new VxSchemaField<>(id, name, type, condition, writer, reader));
    }

    /**
     * Serializes an object into the provided buffer using the dynamic TLV encoding.
     * <p>
     * Iterates through all registered fields, checks their specific conditions, and
     * appends their ID, type byte, and payload length (if variable) before pushing the data.
     * Finally, it appends the {@link #END_OF_SCHEMA} termination marker.
     *
     * @param object The object to extract and serialize state from.
     * @param buf    The target buffer to write the encoded TLV payload into.
     */
    public void serialize(T object, VxByteBuf buf) {
        for (VxSchemaField<T> field : fields.values()) {
            if (!field.shouldWrite(object)) continue;

            buf.writeShort(field.getId());
            buf.writeByte(field.getType().getId());

            if (field.getType().isVariableLength()) {
                // Use a temporary buffer to calculate the exact payload length for variable fields
                ByteBuf tempBuf = ByteBufAllocator.DEFAULT.ioBuffer();
                try {
                    VxByteBuf tempVxBuf = new VxByteBuf(tempBuf);
                    field.write(object, tempVxBuf);

                    int length = tempBuf.readableBytes();
                    buf.writeInt(length);
                    buf.writeBytes(tempBuf);
                } finally {
                    tempBuf.release();
                }
            } else {
                field.write(object, buf);
            }
        }
        // Write the termination marker
        buf.writeShort(END_OF_SCHEMA);
    }

    /**
     * Deserializes data from the buffer and applies it to the given object.
     * <p>
     * This method dynamically reads tags and lengths. If an unknown or mismatched field
     * is encountered (e.g. from an older version or removed mod), it is safely and rapidly
     * skipped using the embedded lengths without triggering object instantiation.
     *
     * @param object The target object to populate with deserialized data.
     * @param buf    The source buffer containing the encoded TLV payload.
     */
    public void deserialize(T object, VxByteBuf buf) {
        while (buf.isReadable()) {
            short id = buf.readShort();
            if (id == END_OF_SCHEMA) break;

            byte typeId = buf.readByte();
            VxFieldType type = VxFieldType.fromId(typeId);

            if (type == null) {
                VxMainClass.LOGGER.warn("Encountered unknown field type {} in schema. Aborting deserialization for this object.", typeId);
                return;
            }

            int length = -1;
            if (type.isVariableLength()) {
                length = buf.readInt();
            }

            VxSchemaField<T> field = fields.get(id);

            if (field != null && field.getType() == type) {
                if (type.isVariableLength()) {
                    // Create a strict slice to prevent the reader from reading past its bounds
                    ByteBuf sliced = buf.readBytes(length);
                    try {
                        VxByteBuf slicedVxBuf = new VxByteBuf(sliced);
                        field.read(object, slicedVxBuf);
                    } finally {
                        sliced.release();
                    }
                } else {
                    field.read(object, buf);
                }
            } else {
                // Skip unknown or mismatched fields to maintain forward compatibility
                if (type.isVariableLength()) {
                    buf.skipBytes(length);
                } else {
                    buf.skipBytes(type.getFixedLength());
                }
            }
        }
    }
}