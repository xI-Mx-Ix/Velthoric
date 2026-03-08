/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.core.behavior.VxBehavior;
import net.xmx.velthoric.core.behavior.VxBehaviorId;
import net.xmx.velthoric.core.behavior.VxBehaviors;
import net.xmx.velthoric.core.body.client.VxClientBodyDataStore;
import net.xmx.velthoric.core.body.client.VxClientBodyManager;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.network.internal.VxNetworkDispatcher;
import net.xmx.velthoric.core.network.synchronization.packet.C2SSynchronizedDataBatchPacket;
import net.xmx.velthoric.core.network.synchronization.packet.S2CSynchronizedDataBatchPacket;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.network.VxNetworking;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Consolidated behavior for custom data synchronization.
 * <p>
 * This class handles both Server-to-Client (S2C) and Client-to-Server (C2S) synchronization.
 * It is designed to work in both dedicated server environments and integrated singleplayer environments
 * where side-specific logic might share the same behavior instance.
 *
 * @author xI-Mx-Ix
 */
public class VxSyncBehavior implements VxBehavior {

    // --- Serialization Buffers ---
    private static final ThreadLocal<VxByteBuf> THREAD_LOCAL_BUF = ThreadLocal.withInitial(() -> new VxByteBuf(Unpooled.buffer(1024)));

    // --- Client-Side State ---
    private final IntSet dirtyBodies = new IntOpenHashSet();

    /**
     * Default constructor for the consolidated sync behavior.
     */
    public VxSyncBehavior() {
    }

    @Override
    public VxBehaviorId getId() {
        return VxBehaviors.CUSTOM_DATA_SYNC;
    }

    // ================================================================================
    // Client-Side Logic (C2S)
    // ================================================================================

    /**
     * Marks a body as dirty so its custom data is synchronized to the server.
     *
     * @param body The body that changed.
     */
    public synchronized void markBodyDirty(VxBody body) {
        this.dirtyBodies.add(body.getNetworkId());
    }

    /**
     * Cleans up tracking data for a removed body.
     *
     * @param body The body being removed.
     */
    public synchronized void onBodyRemoved(VxBody body) {
        this.dirtyBodies.remove(body.getNetworkId());
    }

    /**
     * Clears all tracked dirty bodies.
     */
    public synchronized void clear() {
        this.dirtyBodies.clear();
    }

    /**
     * Updates synchronized data on a body from a server packet (S2C).
     *
     * @param manager   The client body manager.
     * @param networkId The network ID of the body.
     * @param data      The raw byte buffer containing update data.
     */
    public void handleServerUpdate(VxClientBodyManager manager, int networkId, ByteBuf data) {
        VxClientBodyDataStore store = manager.getStore();
        Integer index = store.getIndexForNetworkId(networkId);
        if (index == null) return;

        UUID id = store.getIdForIndex(index);
        if (id == null) return;

        VxBody body = manager.getBody(id);
        if (body != null) {
            try {
                body.getSynchronizedData().readEntries(new VxByteBuf(data), body);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to read synchronized data for body {}", id, e);
            }
        }
    }

    /**
     * Scans for bodies with dirty custom synchronized data, serializes it, and sends it to the server.
     * <p>
     * This method is called once per client game tick.
     *
     * @param manager The client body manager.
     * @param store   The client data store.
     */
    @Override
    public void onClientTick(VxClientBodyManager manager, VxClientBodyDataStore store) {
        Map<Integer, byte[]> updates = new Object2ObjectArrayMap<>();
        VxByteBuf buffer = THREAD_LOCAL_BUF.get();

        synchronized (this) {
            if (dirtyBodies.isEmpty()) return;

            Iterator<Integer> iterator = dirtyBodies.iterator();
            while (iterator.hasNext()) {
                int networkId = iterator.next();
                Integer index = store.getIndexForNetworkId(networkId);

                if (index == null) {
                    iterator.remove();
                    continue;
                }

                UUID id = store.getIdForIndex(index);
                VxBody body = manager.getBody(id);
                if (body == null) {
                    iterator.remove();
                    continue;
                }

                buffer.clear();
                if (body.writeDirtySyncData(buffer)) {
                    byte[] data = new byte[buffer.readableBytes()];
                    buffer.readBytes(data);
                    updates.put(networkId, data);
                }
                iterator.remove();
            }
        }

        if (!updates.isEmpty()) {
            VxNetworking.sendToServer(new C2SSynchronizedDataBatchPacket(updates));
        }
    }

    // ================================================================================
    // Server-Side Logic (S2C)
    // ================================================================================

    /**
     * Processes a batch of synchronized data updates from a client.
     * This method validates that the client has authority over the data it is trying to change.
     *
     * @param bodyManager The server body manager.
     * @param networkId   The network ID of the body being updated.
     * @param data        The raw byte array containing the update data.
     * @param player      The player who sent the update.
     */
    public void processClientUpdate(VxServerBodyManager bodyManager, int networkId, byte[] data, ServerPlayer player) {
        VxServerBodyDataStore dataStore = bodyManager.getDataStore();
        UUID bodyId = dataStore.getIdForNetworkId(networkId);

        if (bodyId == null) return;

        VxBody body = bodyManager.getVxBody(bodyId);
        if (body == null) return;

        try {
            VxByteBuf buf = new VxByteBuf(Unpooled.wrappedBuffer(data));
            body.getSynchronizedData().readEntriesC2S(buf, body, player);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to process C2S sync for body {} from player {}", bodyId, player.getName().getString(), e);
        }
    }

    /**
     * Scans for bodies with dirty custom synchronized data, serializes it, and sends it to tracking players.
     * <p>
     * This method is called from the {@link VxNetworkDispatcher}'s dedicated thread.
     *
     * @param bodyManager The server body manager.
     * @param dispatcher  The network dispatcher instance used to resolve player visibility.
     */
    public void sendSynchronizedDataUpdates(VxServerBodyManager bodyManager, VxNetworkDispatcher dispatcher) {
        VxServerBodyDataStore dataStore = bodyManager.getDataStore();
        IntArrayList dirtyDataIndices = new IntArrayList();

        synchronized (dataStore) {
            for (int i = 0; i < dataStore.getCapacity(); i++) {
                if (dataStore.isCustomDataDirty[i]) {
                    dirtyDataIndices.add(i);
                    dataStore.isCustomDataDirty[i] = false;
                }
            }
        }

        if (dirtyDataIndices.isEmpty()) return;

        Map<ServerPlayer, Map<Integer, byte[]>> updatesByPlayer = new Object2ObjectOpenHashMap<>();
        VxByteBuf buffer = THREAD_LOCAL_BUF.get();

        for (int dirtyIndex : dirtyDataIndices) {
            UUID bodyId = dataStore.getIdForIndex(dirtyIndex);
            if (bodyId == null) continue;

            VxBody body = bodyManager.getVxBody(bodyId);
            if (body == null) continue;

            buffer.clear();
            if (body.writeDirtySyncData(buffer)) {
                byte[] data = new byte[buffer.readableBytes()];
                buffer.readBytes(data);

                Set<ServerPlayer> trackers = dispatcher.getTrackersForBody(body.getNetworkId());
                if (trackers != null) {
                    for (ServerPlayer player : trackers) {
                        updatesByPlayer.computeIfAbsent(player, k -> new Object2ObjectArrayMap<>()).put(body.getNetworkId(), data);
                    }
                }
            }
        }

        if (!updatesByPlayer.isEmpty()) {
            bodyManager.getPhysicsWorld().getLevel().getServer().execute(() -> updatesByPlayer.forEach((player, dataMap) -> {
                if (!dataMap.isEmpty()) {
                    VxNetworking.sendToPlayer(player, new S2CSynchronizedDataBatchPacket(dataMap));
                }
            }));
        }
    }
}