/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior;

import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.network.VxByteBuf;

/**
 * A pair of callbacks for reading and writing type-specific persistence data.
 * <p>
 * Each body type registers its persistence handler on
 * {@link net.xmx.velthoric.core.body.registry.VxBodyType.Builder#persistence}.
 *
 * @author xI-Mx-Ix
 */
public interface VxPersistenceHandler {

    /**
     * A no-op handler for body types that have no custom persistence data.
     */
    VxPersistenceHandler EMPTY = new VxPersistenceHandler() {
        @Override
        public void write(VxBody body, VxByteBuf buf) {}
        @Override
        public void read(VxBody body, VxByteBuf buf) {}
    };

    /**
     * Writes type-specific persistence data to the buffer.
     *
     * @param body The body instance.
     * @param buf  The buffer to write to.
     */
    void write(VxBody body, VxByteBuf buf);

    /**
     * Reads type-specific persistence data from the buffer and applies it to the body.
     *
     * @param body The body instance.
     * @param buf  The buffer to read from.
     */
    void read(VxBody body, VxByteBuf buf);

    /**
     * Creates a persistence handler from separate read/write lambdas.
     *
     * @param writer The write function.
     * @param reader The read function.
     * @return A new persistence handler.
     */
    static VxPersistenceHandler of(Writer writer, Reader reader) {
        return new VxPersistenceHandler() {
            @Override
            public void write(VxBody body, VxByteBuf buf) { writer.write(body, buf); }
            @Override
            public void read(VxBody body, VxByteBuf buf) { reader.read(body, buf); }
        };
    }

    @FunctionalInterface
    interface Writer {
        void write(VxBody body, VxByteBuf buf);
    }

    @FunctionalInterface
    interface Reader {
        void read(VxBody body, VxByteBuf buf);
    }
}