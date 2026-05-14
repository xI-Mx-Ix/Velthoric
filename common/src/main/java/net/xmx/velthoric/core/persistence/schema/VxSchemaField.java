/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.persistence.schema;

import net.xmx.velthoric.network.VxByteBuf;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Represents a single defined field within a {@link VxSchema}.
 * This class capsules the logic of when and how to serialize specific data properties,
 * completely avoiding expensive Reflection in favor of direct method references (BiConsumers).
 *
 * @param <T> The object type this field operates on.
 * @author xI-Mx-Ix
 */
public class VxSchemaField<T> {

    /**
     * The unique numeric ID of the field inside its schema.
     */
    private final short id;

    /**
     * A human-readable name useful for debugging and error logging.
     */
    private final String name;

    /**
     * The binary data type defining the field's size and serialization behavior.
     */
    private final VxFieldType type;

    /**
     * A condition evaluating if the field should be serialized.
     */
    private final Predicate<T> condition;

    /**
     * The functional interface responsible for writing the data to the buffer.
     */
    private final BiConsumer<T, VxByteBuf> writer;

    /**
     * The functional interface responsible for reading data from the buffer and applying it to the object.
     */
    private final BiConsumer<T, VxByteBuf> reader;

    /**
     * Constructs a new schema field.
     *
     * @param id        The unique identifier for this field in the schema.
     * @param name      A human-readable name for the field.
     * @param type      The binary data type of the field.
     * @param condition A condition that must be true for this field to be written (e.g., to skip default values).
     * @param writer    The function that writes the field's data to a buffer.
     * @param reader    The function that reads the field's data from a buffer and applies it to the object.
     */
    public VxSchemaField(short id, String name, VxFieldType type, Predicate<T> condition, BiConsumer<T, VxByteBuf> writer, BiConsumer<T, VxByteBuf> reader) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.condition = condition;
        this.writer = writer;
        this.reader = reader;
    }

    /**
     * Retrieves the unique numeric field ID.
     *
     * @return The 16-bit field ID.
     */
    public short getId() {
        return id;
    }

    /**
     * Retrieves the human-readable name of the field.
     *
     * @return The name string.
     */
    public String getName() {
        return name;
    }

    /**
     * Retrieves the precise data type of the field.
     *
     * @return The field type configuration.
     */
    public VxFieldType getType() {
        return type;
    }

    /**
     * Evaluates whether this field should be serialized for the given object.
     * By dynamically skipping fields containing default values (e.g., zero velocity),
     * the storage size is drastically optimized.
     *
     * @param object The specific object instance to evaluate.
     * @return True if the field contains non-default data and should be saved.
     */
    public boolean shouldWrite(T object) {
        return condition.test(object);
    }

    /**
     * Executes the writer consumer to append the field data into the buffer.
     *
     * @param object The object to extract the data from.
     * @param buf    The target buffer to write into.
     */
    public void write(T object, VxByteBuf buf) {
        writer.accept(object, buf);
    }

    /**
     * Executes the reader consumer to apply data from the buffer into the object.
     *
     * @param object The target object to mutate.
     * @param buf    The source buffer to read from.
     */
    public void read(T object, VxByteBuf buf) {
        reader.accept(object, buf);
    }
}