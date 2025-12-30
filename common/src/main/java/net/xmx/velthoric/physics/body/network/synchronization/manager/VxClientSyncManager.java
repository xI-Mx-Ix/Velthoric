/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.synchronization.manager;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.network.VxPacketHandler;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.network.synchronization.packet.C2SSynchronizedDataBatchPacket;
import net.xmx.velthoric.physics.body.type.VxBody;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the synchronization of custom data on the client side.
 * - Collects CLIENT-authoritative changes and batches them to the server.
 * - Applies SERVER-authoritative updates received from the server.
 *
 * @author xI-Mx-Ix
 */
public class VxClientSyncManager {

    private final VxClientBodyManager bodyManager;
    
    // Set of bodies that have dirty CLIENT-authoritative data needing to be sent to server
    private final Set<VxBody> dirtyBodiesC2S = ConcurrentHashMap.newKeySet();

    // Reusable buffer for packet construction to avoid allocation
    private final ThreadLocal<VxByteBuf> packetBuffer = ThreadLocal.withInitial(() -> new VxByteBuf(Unpooled.buffer(1024)));

    public VxClientSyncManager(VxClientBodyManager bodyManager) {
        this.bodyManager = bodyManager;
    }

    /**
     * Marks a body as having dirty client-authoritative data.
     * Queues it for the next C2S sync packet.
     *
     * @param body The body that changed.
     */
    public void markBodyDirty(VxBody body) {
        this.dirtyBodiesC2S.add(body);
    }

    /**
     * Called when a body is removed from the client to ensure we don't try to sync it.
     */
    public void onBodyRemoved(VxBody body) {
        this.dirtyBodiesC2S.remove(body);
    }

    /**
     * Clears all tracking data. Called on disconnect.
     */
    public void clear() {
        this.dirtyBodiesC2S.clear();
    }

    /**
     * Updates synchronized data on a body from a server packet (S2C).
     *
     * @param networkId The network ID of the body.
     * @param data      The data buffer.
     */
    public void handleServerUpdate(int networkId, ByteBuf data) {
        Integer index = bodyManager.getStore().getIndexForNetworkId(networkId);
        if (index == null) return;

        UUID id = bodyManager.getStore().getIdForIndex(index);
        if (id == null) return;

        VxBody body = bodyManager.getBody(id);
        if (body != null) {
            try {
                // S2C updates are trusted (Server Authority)
                body.getSynchronizedData().readEntries(new VxByteBuf(data), body);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to read synchronized data for body {}", id, e);
            }
        }
    }

    /**
     * Called every client tick. Checks for dirty bodies and sends updates to the server.
     */
    public void tick() {
        if (dirtyBodiesC2S.isEmpty()) return;

        Map<Integer, byte[]> updates = new Object2ObjectArrayMap<>();
        VxByteBuf buffer = packetBuffer.get();

        Iterator<VxBody> iterator = dirtyBodiesC2S.iterator();
        while (iterator.hasNext()) {
            VxBody body = iterator.next();
            
            // Check if body is still valid and tracked
            if (body.getDataStoreIndex() == -1) {
                iterator.remove();
                continue;
            }

            buffer.clear();
            if (body.writeDirtySyncData(buffer)) {
                byte[] data = new byte[buffer.readableBytes()];
                buffer.readBytes(data);
                updates.put(body.getNetworkId(), data);
            }
            // Always remove from set after processing
            iterator.remove();
        }

        if (!updates.isEmpty()) {
            VxPacketHandler.sendToServer(new C2SSynchronizedDataBatchPacket(updates));
        }
    }
}