/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.sync;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.sync.accessor.VxClientAccessor;
import net.xmx.velthoric.physics.body.sync.accessor.VxDataAccessor;
import net.xmx.velthoric.physics.body.sync.accessor.VxServerAccessor;
import net.xmx.velthoric.physics.body.type.VxBody;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Manages all synchronized data entries for a single physics body instance.
 * It holds the internal map of values and handles reading/writing from network buffers.
 *
 * @author xI-Mx-Ix
 */
public class VxSynchronizedData {

    private final Int2ObjectMap<Entry<?>> entries;
    private boolean isDirty;

    private VxSynchronizedData(Int2ObjectMap<Entry<?>> entries) {
        this.entries = entries;
    }

    /**
     * Retrieves the current value for a given data accessor.
     * Reading is allowed on both client and server for any accessor type.
     *
     * @param accessor The key for the data.
     * @param <T>      The data type.
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
     * Internal set method.
     * Access is restricted to package-private to force usage of {@link VxBody}'s type-safe methods.
     *
     * @param accessor The key for the data.
     * @param value    The new value.
     * @param <T>      The data type.
     */
    public <T> void set(VxDataAccessor<T> accessor, T value) {
        Entry<T> entry = this.getEntry(accessor);
        if (entry != null && !Objects.equals(value, entry.getValue())) {
            entry.setValue(value);
            entry.setDirty(true);
            this.isDirty = true;
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> Entry<T> getEntry(VxDataAccessor<T> accessor) {
        return (Entry<T>) this.entries.get(accessor.getId());
    }

    /**
     * @return True if any data entries have changed and need synchronization.
     */
    public boolean isDirty() {
        return this.isDirty;
    }

    /**
     * Gathers all dirty entries for network synchronization.
     *
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
     *
     * @return A list containing all entries.
     */
    public List<Entry<?>> getAllEntries() {
        return new ArrayList<>(entries.values());
    }

    /**
     * Clears the dirty flag for all entries. Called after data has been successfully written to a packet.
     */
    public void clearDirty() {
        this.isDirty = false;
        for (Entry<?> entry : this.entries.values()) {
            entry.setDirty(false);
        }
    }

    /**
     * Writes a list of entries to a buffer.
     *
     * @param buf            The buffer to write to.
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
     * Dispatches the update event to the specific method in VxBody based on the accessor type.
     */
    private void dispatchUpdate(VxBody body, VxDataAccessor<?> accessor) {
        if (accessor instanceof VxServerAccessor<?> serverAccessor) {
            body.onSyncedDataUpdated(serverAccessor);
        } else if (accessor instanceof VxClientAccessor<?> clientAccessor) {
            body.onSyncedDataUpdated(clientAccessor);
        }
    }

    /**
     * Reads entries from a buffer sent by the server (S2C).
     * This method blindly trusts the source (the server) and applies all updates.
     *
     * @param buf  The buffer to read from.
     * @param body The body instance.
     */
    public void readEntries(VxByteBuf buf, VxBody body) {
        while (true) {
            int id = buf.readVarInt();
            if (id == 255) { // End marker
                break;
            }
            Entry<?> entry = this.entries.get(id);
            if (entry != null) {
                this.readEntryInternal(buf, entry);
                this.dispatchUpdate(body, entry.getAccessor());
            }
        }
    }

    /**
     * Reads entries from a buffer sent by a client (C2S).
     * Validates that the client has authority ({@link VxSyncMode#CLIENT}) for each entry.
     * If a client tries to update SERVER-authoritative data, a warning is logged and the update is ignored.
     *
     * @param buf    The buffer to read from.
     * @param body   The body instance.
     * @param player The player sending the update.
     */
    @SuppressWarnings("unchecked")
    public void readEntriesC2S(VxByteBuf buf, VxBody body, ServerPlayer player) {
        while (true) {
            int id = buf.readVarInt();
            if (id == 255) {
                break;
            }
            Entry<?> entry = this.entries.get(id);
            if (entry != null) {
                // Must read value to advance buffer regardless of authority
                Object newValue = entry.getAccessor().getSerializer().read(buf);

                if (entry.getAccessor().getMode() == VxSyncMode.CLIENT) {
                    // Client allowed to update: Apply and mark dirty so it replicates to OTHER clients
                    if (!Objects.equals(newValue, entry.getValue())) {
                        ((Entry<Object>) entry).setValue(newValue);
                        entry.setDirty(true);
                        this.isDirty = true;
                        this.dispatchUpdate(body, entry.getAccessor());
                    }
                } else {
                    // Client NOT allowed: Log warning (Anti-Cheat)
                    VxMainClass.LOGGER.warn("Player {} tried to manipulate SERVER-authoritative data (ID: {}) on Body {}",
                            player.getName().getString(), id, body.getPhysicsId());
                }
            } else {
                throw new IllegalStateException("Unknown data ID received in C2S packet: " + id);
            }
        }

        // If we applied valid changes, notify the manager to sync to other clients
        if (this.isDirty && body.getPhysicsWorld() != null) {
            body.getPhysicsWorld().getBodyManager().markCustomDataDirty(body);
        }
    }

    /**
     * Internal helper to read a single entry's value.
     */
    private <T> void readEntryInternal(VxByteBuf buf, Entry<T> entry) {
        T value = entry.getAccessor().getSerializer().read(buf);
        entry.setValue(value);
    }

    /**
     * A builder for creating {@link VxSynchronizedData} instances.
     * It collects data definitions before constructing the final immutable map.
     */
    public static class Builder {
        private final Int2ObjectMap<Entry<?>> entries = new Int2ObjectOpenHashMap<>();

        /**
         * Defines a new synchronized data entry with a default value.
         *
         * @param accessor     The accessor for the data.
         * @param defaultValue The initial value.
         * @param <T>          The data type.
         * @return This builder instance for chaining.
         */
        public <T> Builder define(VxDataAccessor<T> accessor, T defaultValue) {
            int id = accessor.getId();
            if (this.entries.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate data accessor id: " + id);
            }
            this.entries.put(id, new Entry<>(accessor, defaultValue));
            return this;
        }

        /**
         * Builds the {@link VxSynchronizedData} instance.
         *
         * @return A new {@link VxSynchronizedData} instance.
         */
        public VxSynchronizedData build() {
            return new VxSynchronizedData(new Int2ObjectOpenHashMap<>(this.entries));
        }
    }

    /**
     * Represents a single piece of synchronized data.
     *
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