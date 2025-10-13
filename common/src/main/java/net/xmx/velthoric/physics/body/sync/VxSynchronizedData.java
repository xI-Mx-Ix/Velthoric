/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.sync;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.type.VxBody;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Manages all synchronized data entries for a single physics body instance.
 * Its behavior changes depending on the environment (client or server).
 *
 * @author xI-Mx-Ix
 */
public class VxSynchronizedData {

    private final Int2ObjectMap<Entry<?>> entries = new Int2ObjectOpenHashMap<>();
    private final EnvType environment;
    private boolean isDirty;

    public VxSynchronizedData(EnvType environment) {
        this.environment = environment;
    }

    /**
     * Defines a new synchronized data entry with a default value.
     * @param accessor The accessor for the data.
     * @param defaultValue The initial value.
     * @param <T> The data type.
     */
    public <T> void define(VxDataAccessor<T> accessor, T defaultValue) {
        int id = accessor.getId();
        if (this.entries.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate data accessor id: " + id);
        }
        this.entries.put(id, new Entry<>(accessor, defaultValue));
    }

    /**
     * Retrieves the current value for a given data accessor.
     * @param accessor The key for the data.
     * @param <T> The data type.
     * @return The current value.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(VxDataAccessor<T> accessor) {
        Entry<?> entry = this.entries.get(accessor.getId());
        if (entry == null) {
            throw new IllegalArgumentException("Tried to get unregistered data accessor: " + accessor.getId());
        }
        return (T) entry.getValue();
    }

    /**
     * Sets a new value for a given data accessor.
     * On the server, this will mark the entry as dirty if the value has changed.
     * @param accessor The key for the data.
     * @param value The new value.
     * @param <T> The data type.
     */
    public <T> void set(VxDataAccessor<T> accessor, T value) {
        Entry<T> entry = this.getEntry(accessor);
        if (entry != null && !Objects.equals(value, entry.getValue())) {
            entry.setValue(value);
            if (environment == EnvType.SERVER) {
                entry.setDirty(true);
                this.isDirty = true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> Entry<T> getEntry(VxDataAccessor<T> accessor) {
        return (Entry<T>) this.entries.get(accessor.getId());
    }

    /**
     * @return True if any data entries have been changed on the server since the last sync. Always false on the client.
     */
    public boolean isDirty() {
        return this.isDirty;
    }

    /**
     * Gathers all dirty entries for network synchronization.
     * @return A list of dirty entries, or null if nothing is dirty.
     */
    @Nullable
    public List<Entry<?>> getDirtyEntries() {
        if (!this.isDirty) {
            return null;
        }
        List<Entry<?>> dirtyList = new ArrayList<>();
        for (Entry<?> entry : this.entries.values()) {
            if (entry.isDirty()) {
                dirtyList.add(entry);
            }
        }
        return dirtyList.isEmpty() ? null : dirtyList;
    }

    /**
     * Gathers all defined entries, for initial spawn synchronization.
     * @return A list containing all entries.
     */
    public List<Entry<?>> getAllEntries() {
        return new ArrayList<>(entries.values());
    }


    /**
     * Clears the dirty flag for all entries. Called after data has been sent.
     */
    public void clearDirty() {
        this.isDirty = false;
        for (Entry<?> entry : this.entries.values()) {
            entry.setDirty(false);
        }
    }

    /**
     * Writes a list of entries to a buffer.
     * @param buf The buffer to write to.
     * @param entriesToWrite The list of entries to serialize.
     */
    @SuppressWarnings("unchecked")
    public static void writeEntries(VxByteBuf buf, List<Entry<?>> entriesToWrite) {
        for (Entry<?> entry : entriesToWrite) {
            VxDataAccessor<Object> accessor = (VxDataAccessor<Object>) entry.getAccessor();
            buf.writeVarInt(accessor.getId());
            accessor.getSerializer().write(buf, entry.getValue());
        }
        buf.writeVarInt(255); // End marker
    }

    /**
     * Reads entries from a buffer, applies them to this data store,
     * and calls the on-update hook on the provided body for each entry.
     * @param buf The buffer to read from.
     * @param body The body instance whose data is being updated, used to call the hook.
     */
    public void readEntries(VxByteBuf buf, VxBody body) {
        while (true) {
            int id = buf.readVarInt();
            if (id == 255) { // End marker
                break;
            }
            Entry<?> entry = this.entries.get(id);
            if (entry != null) {
                // 1. Read and apply the data value (existing logic)
                this.readEntry(buf, entry);

                // 2. Call the hook on the body, notifying it of the specific change (new logic)
                body.onSyncedDataUpdated(entry.getAccessor());
            }
        }
    }

    private <T> void readEntry(VxByteBuf buf, Entry<T> entry) {
        T value = entry.getAccessor().getSerializer().read(buf);
        entry.setValue(value);
    }

    /**
     * Represents a single piece of synchronized data.
     * @param <T> The data type.
     */
    public static class Entry<T> {
        private final VxDataAccessor<T> accessor;
        private T value;
        private boolean dirty;

        public Entry(VxDataAccessor<T> accessor, T value) {
            this.accessor = accessor;
            this.value = accessor.getSerializer().copy(value);
        }

        public VxDataAccessor<T> getAccessor() {
            return accessor;
        }

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }

        public boolean isDirty() {
            return dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }
    }
}