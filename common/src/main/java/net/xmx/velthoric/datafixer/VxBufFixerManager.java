/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.datafixer;

import com.google.common.collect.ImmutableMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.xmx.velthoric.network.VxByteBuf;
import java.util.List;

/**
 * Manages and applies a chain of {@link VxIBufFixer} to update a {@link VxByteBuf}
 * from an old schema version to the current one. This manager is designed to
 * minimize intermediate buffer allocations for efficient processing.
 *
 * @author xI-Mx-Ix
 */
public class VxBufFixerManager {

    private final ImmutableMap<Integer, VxIBufFixer> fixers;

    /**
     * Constructs a manager with a given list of fixers.
     *
     * @param fixerList The list of fixers to manage.
     * @throws IllegalArgumentException if multiple fixers are registered for the same input version.
     */
    public VxBufFixerManager(List<VxIBufFixer> fixerList) {
        ImmutableMap.Builder<Integer, VxIBufFixer> builder = ImmutableMap.builder();
        for (VxIBufFixer fixer : fixerList) {
            builder.put(fixer.getFromVersion(), fixer);
        }
        this.fixers = builder.build();
    }

    /**
     * Processes a {@link VxByteBuf}, applying all necessary fixes to bring it from
     * its saved schema version up to the current schema version.
     * <p>
     * <b>Buffer Lifecycle Contract:</b> This method takes full ownership of the provided
     * {@code data} buffer and is guaranteed to release it. The returned buffer is newly
     * allocated (if fixes were applied) and its ownership is transferred to the caller.
     * If no fixes are needed, the original buffer is returned, and its ownership is
     * simply transferred back to the caller.
     *
     * @param savedSchemaVersion The schema version of the data in the buffer.
     * @param data The buffer containing the data to be processed.
     * @return A buffer containing the data in the current schema format.
     */
    public VxByteBuf process(int savedSchemaVersion, VxByteBuf data) {
        int targetSchemaVersion = VxDataVersionRegistry.getCurrentSchemaVersion();

        if (savedSchemaVersion > targetSchemaVersion) {
            data.release();
            throw new IllegalArgumentException("Cannot process data from a future version. Saved: " + savedSchemaVersion + ", Current: " + targetSchemaVersion);
        }

        if (savedSchemaVersion == targetSchemaVersion) {
            return data; // No fixes needed. Ownership is transferred back to the caller.
        }

        VxByteBuf currentData = data;
        for (int version = savedSchemaVersion; version < targetSchemaVersion; version++) {
            VxIBufFixer fixer = fixers.get(version);
            if (fixer == null) {
                currentData.release();
                throw new IllegalStateException("Missing data fixer to upgrade from schema version " + version + " to " + (version + 1));
            }

            // Allocate a new raw buffer for the output of this step and wrap it.
            ByteBuf nextRawData = Unpooled.buffer(currentData.readableBytes() + 32);
            VxByteBuf nextData = new VxByteBuf(nextRawData);
            fixer.apply(currentData, nextData);

            // Release the buffer from the previous step.
            currentData.release();
            currentData = nextData;
        }

        return currentData;
    }
}