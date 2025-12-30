/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.synchronization.manager;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.network.VxPacketHandler;
import net.xmx.velthoric.physics.body.manager.VxServerBodyDataStore;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.body.network.internal.VxNetworkDispatcher;
import net.xmx.velthoric.physics.body.network.synchronization.packet.S2CSynchronizedDataBatchPacket;
import net.xmx.velthoric.physics.body.type.VxBody;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages server-side synchronization tasks.
 * <p>
 * This class validates incoming Client-to-Server (C2S) data updates against authority rules
 * and handles the distribution of Server-to-Client (S2C) synchronized data updates to
 * relevant players.
 *
 * @author xI-Mx-Ix
 */
public class VxServerSyncManager {

    private final VxBodyManager bodyManager;
    private final VxServerBodyDataStore dataStore;

    // A reusable thread-local buffer for serialization on the network thread.
    private static final ThreadLocal<VxByteBuf> THREAD_LOCAL_BYTE_BUF = ThreadLocal.withInitial(() -> new VxByteBuf(Unpooled.buffer(1024)));

    public VxServerSyncManager(VxBodyManager bodyManager) {
        this.bodyManager = bodyManager;
        this.dataStore = bodyManager.getDataStore();
    }

    /**
     * Processes a batch of synchronized data updates from a client.
     * This method validates that the client has authority over the data it is trying to change.
     *
     * @param networkId The network ID of the body being updated.
     * @param data      The raw byte array containing the update data.
     * @param player    The player who sent the update.
     */
    public void processClientUpdate(int networkId, byte[] data, ServerPlayer player) {
        // Resolve the body ID from the network ID using the DataStore reverse lookup
        UUID bodyId = dataStore.getIdForNetworkId(networkId);

        if (bodyId == null) {
            return; // Body not found or network ID invalid
        }

        VxBody body = bodyManager.getVxBody(bodyId);
        if (body == null) {
            return;
        }

        try {
            VxByteBuf buf = new VxByteBuf(Unpooled.wrappedBuffer(data));
            // Delegate to SynchronizedData to parse and strict-check authority (Anti-Cheat)
            body.getSynchronizedData().readEntriesC2S(buf, body, player);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to process C2S sync for body {} from player {}", bodyId, player.getName().getString(), e);
        }
    }

    /**
     * Scans for bodies with dirty custom synchronized data, serializes it, and sends it to tracking players.
     * This allows {@link VxBody} instances to send arbitrary data to clients when their state changes.
     * <p>
     * This method is called from the {@link VxNetworkDispatcher}'s dedicated thread.
     *
     * @param dispatcher The network dispatcher instance used to resolve player visibility.
     */
    public void sendSynchronizedDataUpdates(VxNetworkDispatcher dispatcher) {
        IntArrayList dirtyDataIndices = new IntArrayList();

        // Atomically capture dirty indices to minimize lock time on the DataStore
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
        VxByteBuf buffer = THREAD_LOCAL_BYTE_BUF.get(); // Use thread-local buffer

        for (int dirtyIndex : dirtyDataIndices) {
            UUID bodyId = dataStore.getIdForIndex(dirtyIndex);
            if (bodyId == null) continue;

            VxBody body = bodyManager.getVxBody(bodyId);
            if (body == null) continue;

            buffer.clear(); // Clear buffer for reuse

            // Serialize the dirty data
            if (body.writeDirtySyncData(buffer)) {
                byte[] data = new byte[buffer.readableBytes()];
                buffer.readBytes(data);

                // Get players tracking this body
                Set<ServerPlayer> trackers = dispatcher.getTrackersForBody(body.getNetworkId());
                if (trackers != null) {
                    for (ServerPlayer player : trackers) {
                        updatesByPlayer.computeIfAbsent(player, k -> new Object2ObjectArrayMap<>()).put(body.getNetworkId(), data);
                    }
                }
            }
        }

        // Dispatch packets on the main server executor to ensure thread safety with the networking stack
        if (!updatesByPlayer.isEmpty()) {
            bodyManager.getPhysicsWorld().getLevel().getServer().execute(() -> updatesByPlayer.forEach((player, dataMap) -> {
                if (!dataMap.isEmpty()) {
                    VxPacketHandler.sendToPlayer(new S2CSynchronizedDataBatchPacket(dataMap), player);
                }
            }));
        }
    }
}