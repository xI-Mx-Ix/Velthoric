/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.network.synchronization.behavior;

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
 * Consolidated behavior for custom data synchronization between server and client.
 * <p>
 * This behavior provides a lightweight mechanism to synchronize custom user data entries 
 * attached to physics bodies. It follows a delta-based synchronization model:
 * <ul>
 *   <li><b>Server-to-Client (S2C):</b> Synchronizes state changes to tracking players.</li>
 *   <li><b>Client-to-Server (C2S):</b> Allows clients to send authoritative updates for logic 
 *   they control (e.g. input-driven custom data).</li>
 * </ul>
 * <p>
 * To minimize allocations and GC pressure, this system uses thread-local serialization 
 * buffers and data-driven bitmask checks for efficiency.
 *
 * @author xI-Mx-Ix
 */
public class VxSyncBehavior implements VxBehavior {

    /**
     * The default initial size for the thread-local serialization buffers.
     */
    private static final int DEFAULT_BUFFER_SIZE = 1024;

    /**
     * Reusable thread-local buffer to avoid frequent allocations during bulk synchronization.
     */
    private static final ThreadLocal<VxByteBuf> THREAD_LOCAL_BUF = ThreadLocal.withInitial(() -> 
            new VxByteBuf(Unpooled.buffer(DEFAULT_BUFFER_SIZE)));

    /**
     * Set of network IDs of bodies that have dirty C2S data on the client.
     */
    private final IntSet dirtyBodiesC2S = new IntOpenHashSet();

    /**
     * Default constructor.
     */
    public VxSyncBehavior() {
    }

    @Override
    public VxBehaviorId getId() {
        return VxBehaviors.CUSTOM_DATA_SYNC;
    }

    // ================================================================================
    // Client-Side Logic (C2S Outgoing, S2C Incoming)
    // ================================================================================

    /**
     * Marks a body as dirty on the client, notifying the system that its custom data 
     * should be sent to the server in the next tick.
     *
     * @param body The body whose custom data has changed.
     */
    public synchronized void markDirtyC2S(VxBody body) {
        this.dirtyBodiesC2S.add(body.getNetworkId());
    }

    /**
     * Handles the removal of a body on the client by cleaning up sync trackers.
     *
     * @param body The body instance being removed.
     */
    public synchronized void onBodyRemoved(VxBody body) {
        this.dirtyBodiesC2S.remove(body.getNetworkId());
    }

    /**
     * Clears all client-side synchronization state.
     */
    public synchronized void clear() {
        this.dirtyBodiesC2S.clear();
    }

    /**
     * Logic executed during the client game tick. 
     * Scans for dirty bodies, serializes their changes, and dispatches them to the server.
     */
    @Override
    public void onClientTick(VxClientBodyManager manager, VxClientBodyDataStore store) {
        if (dirtyBodiesC2S.isEmpty()) return;

        Map<Integer, byte[]> batchUpdates = new Object2ObjectArrayMap<>();
        VxByteBuf serializationBuffer = THREAD_LOCAL_BUF.get();

        synchronized (this) {
            Iterator<Integer> it = dirtyBodiesC2S.iterator();
            while (it.hasNext()) {
                int netId = it.next();
                Integer index = store.getIndexForNetworkId(netId);

                // Body might have been removed or is no longer tracked
                if (index == null) {
                    it.remove();
                    continue;
                }

                UUID id = store.getIdForIndex(index);
                VxBody body = manager.getBody(id);
                if (body == null) {
                    it.remove();
                    continue;
                }

                serializationBuffer.clear();
                // Serialize only dirty entries for this specific body
                if (body.writeDirtySyncData(serializationBuffer)) {
                    byte[] payload = new byte[serializationBuffer.readableBytes()];
                    serializationBuffer.readBytes(payload);
                    batchUpdates.put(netId, payload);
                }
                it.remove();
            }
        }

        if (!batchUpdates.isEmpty()) {
            VxNetworking.sendToServer(new C2SSynchronizedDataBatchPacket(batchUpdates));
        }
    }

    /**
     * Processes an incoming custom data update from the server.
     *
     * @param manager   The client-side body manager.
     * @param networkId The network ID of the body being updated.
     * @param payload   The raw data containing the synchronized entries.
     */
    public void applyS2CUpdate(VxClientBodyManager manager, int networkId, ByteBuf payload) {
        VxClientBodyDataStore store = manager.getStore();
        Integer index = store.getIndexForNetworkId(networkId);
        if (index == null) return;

        UUID id = store.getIdForIndex(index);
        if (id == null) return;

        VxBody body = manager.getBody(id);
        if (body != null) {
            try {
                // Apply the received entries to the body's synchronized data container
                body.getSynchronizedData().readEntries(new VxByteBuf(payload), body);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to apply S2C synchronized data for body {}", id, e);
            }
        }
    }

    // ================================================================================
    // Server-Side Logic (C2S Incoming, S2C Outgoing)
    // ================================================================================

    /**
     * Processes a synchronization request sent by a client.
     *
     * @param bodyManager The server-side body manager.
     * @param networkId   The network ID of the body.
     * @param payload     The serialized sync data.
     * @param sender      The player who sent the update.
     */
    public void handleC2SUpdate(VxServerBodyManager bodyManager, int networkId, byte[] payload, ServerPlayer sender) {
        VxServerBodyDataStore dataStore = bodyManager.getDataStore();
        UUID bodyId = dataStore.getIdForNetworkId(networkId);
        if (bodyId == null) return;

        VxBody body = bodyManager.getVxBody(bodyId);
        if (body == null) return;

        try {
            VxByteBuf buf = new VxByteBuf(Unpooled.wrappedBuffer(payload));
            // Delegate logic to entries, which might perform authority checks
            body.getSynchronizedData().readEntriesC2S(buf, body, sender);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to process C2S sync for body {} from player {}", 
                    bodyId, sender.getName().getString(), e);
        }
    }

    /**
     * Scans for bodies with dirty synchronized data and broadcasts updates to tracking players.
     * <p>
     * This method is designed to be called from the network thread to offload serialization.
     *
     * @param bodyManager The server-side body manager.
     * @param dispatcher  The network dispatcher for tracker resolution.
     */
    public void broadcastS2CUpdates(VxServerBodyManager bodyManager, VxNetworkDispatcher dispatcher) {
        VxServerBodyDataStore dataStore = bodyManager.getDataStore();
        IntArrayList dirtyIndices = new IntArrayList();

        // 1. Collect indices of bodies with server-side dirty flags
        synchronized (dataStore) {
            for (int i = 0; i < dataStore.getCapacity(); i++) {
                if (dataStore.isCustomDataDirty[i]) {
                    dirtyIndices.add(i);
                    dataStore.isCustomDataDirty[i] = false;
                }
            }
        }

        if (dirtyIndices.isEmpty()) return;

        Map<ServerPlayer, Map<Integer, byte[]>> playerUpdateMap = new Object2ObjectOpenHashMap<>();
        VxByteBuf serializationBuffer = THREAD_LOCAL_BUF.get();

        // 2. Serialize updates once per body and identify interested players
        for (int index : dirtyIndices) {
            UUID id = dataStore.getIdForIndex(index);
            if (id == null) continue;

            VxBody body = bodyManager.getVxBody(id);
            if (body == null) continue;

            serializationBuffer.clear();
            if (body.writeDirtySyncData(serializationBuffer)) {
                byte[] payload = new byte[serializationBuffer.readableBytes()];
                serializationBuffer.readBytes(payload);

                int netId = body.getNetworkId();
                Set<ServerPlayer> trackers = dispatcher.getTrackersForBody(netId);
                
                if (trackers != null && !trackers.isEmpty()) {
                    for (ServerPlayer player : trackers) {
                        playerUpdateMap.computeIfAbsent(player, p -> new Object2ObjectArrayMap<>())
                                .put(netId, payload);
                    }
                }
            }
        }

        // 3. Dispatch batch packets to players on the server thread
        if (!playerUpdateMap.isEmpty()) {
            bodyManager.getPhysicsWorld().getLevel().getServer().execute(() -> 
                playerUpdateMap.forEach((player, data) -> {
                    if (!data.isEmpty()) {
                        VxNetworking.sendToPlayer(player, new S2CSynchronizedDataBatchPacket(data));
                    }
                })
            );
        }
    }
}