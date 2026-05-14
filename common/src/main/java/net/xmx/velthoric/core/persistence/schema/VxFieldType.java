/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.persistence.schema;

/**
 * Defines the available data types for schema-based persistence.
 * Each type explicitly declares its fixed byte length, allowing the deserializer
 * to quickly skip unknown or obsolete fields without constructing objects.
 *
 * @author xI-Mx-Ix
 */
public enum VxFieldType {
    /**
     * A single 8-bit byte. Length: 1 byte.
     */
    BYTE(1, 1),
    /**
     * A 16-bit short integer. Length: 2 bytes.
     */
    SHORT(2, 2),
    /**
     * A 32-bit integer. Length: 4 bytes.
     */
    INT(3, 4),
    /**
     * A 64-bit long integer. Length: 8 bytes.
     */
    LONG(4, 8),
    /**
     * A 32-bit floating point number. Length: 4 bytes.
     */
    FLOAT(5, 4),
    /**
     * A 64-bit floating point number. Length: 8 bytes.
     */
    DOUBLE(6, 8),
    /**
     * A boolean value, stored internally as a byte. Length: 1 byte.
     */
    BOOLEAN(7, 1),
    /**
     * A Universally Unique Identifier. Length: 16 bytes.
     */
    UUID(8, 16),
    /**
     * A precise 3D vector (X, Y, Z as doubles). Length: 24 bytes.
     */
    RVEC3(9, 24),
    /**
     * A standard 3D vector (X, Y, Z as floats). Length: 12 bytes.
     */
    VEC3F(10, 12),
    /**
     * A mathematical quaternion (X, Y, Z, W as floats). Length: 16 bytes.
     */
    QUATERNION(11, 16),
    /**
     * A variable-length UTF-8 encoded string. Length: Dynamic.
     */
    STRING(12, -1),
    /**
     * A variable-length array of raw bytes. Length: Dynamic.
     */
    BYTES(13, -1),
    /**
     * A serialized Jolt physics collision shape. Length: Dynamic.
     */
    SHAPE(14, -1),
    /**
     * A variable-length compound schema block. Length: Dynamic.
     */
    COMPOUND(15, -1);

    /**
     * The unique byte identifier written to the storage file to identify the field type.
     */
    private final byte id;

    /**
     * The strict size of this data type in bytes, or -1 if the length is dynamic.
     */
    private final int fixedLength;

    /**
     * Constructs a new field type definition.
     *
     * @param id          The byte identifier written into the schema payload.
     * @param fixedLength The exact size in bytes, or -1 for variable length types.
     */
    VxFieldType(int id, int fixedLength) {
        this.id = (byte) id;
        this.fixedLength = fixedLength;
    }

    /**
     * Retrieves the unique byte identifier of this type.
     * This ID is used natively in the TLV serialization structure.
     *
     * @return The byte identifier.
     */
    public byte getId() {
        return id;
    }

    /**
     * Retrieves the length in bytes if fixed, or -1 if variable.
     *
     * @return The fixed length or -1.
     */
    public int getFixedLength() {
        return fixedLength;
    }

    /**
     * Determines whether the field has a variable length.
     * Variable length fields are prefixed with a 32-bit length integer in the payload.
     *
     * @return True if variable length, false otherwise.
     */
    public boolean isVariableLength() {
        return fixedLength == -1;
    }

    /**
     * Retrieves a field type by its ID.
     * This method searches the available types and returns the matching enumeration instance.
     *
     * @param id The byte identifier read from the persistence format.
     * @return The corresponding field type, or null if the ID is unknown.
     */
    public static VxFieldType fromId(byte id) {
        for (VxFieldType type : values()) {
            if (type.id == id) return type;
        }
        return null;
    }
}