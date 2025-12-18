/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.datafixer;

import net.timtaran.interactivemc.physics.network.VxByteBuf;

/**
 * Defines a contract for a data fixer that transforms a {@link VxByteBuf} from one
 * schema version to the next. Implementations of this interface should be stateless.
 *
 * @author xI-Mx-Ix
 */
public interface VxIBufFixer {

    /**
     * The schema version number that this fixer reads as input.
     *
     * @return The input schema version.
     */
    int getFromVersion();

    /**
     * The schema version number that this fixer produces as output.
     *
     * @return The output schema version.
     */
    int getToVersion();

    /**
     * Reads data from the {@code oldData} buffer and writes the transformed data
     * into the {@code newData} buffer.
     * <p>
     * The implementation must not release either buffer, as their lifecycle
     * is managed by the caller (typically {@link VxBufFixerManager}).
     *
     * @param oldData The buffer containing data in the format of {@link #getFromVersion()}.
     * @param newData The buffer to write the new, transformed data into.
     */
    void apply(VxByteBuf oldData, VxByteBuf newData);
}