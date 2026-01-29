/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.internal.packet;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.network.VxPacketUtils;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.world.VxClientPhysicsWorld;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * A network packet for batched removal of physics bodies.
 * <p>
 * This packet uses Zstd compression on the network ID list to minimize bandwidth
 * during mass-deletion events (e.g., chunk unloading or large-scale physics cleanup).
 *
 * @author xI-Mx-Ix
 */
public class S2CRemoveBodyBatchPacket {

    /**
     * The list of primitive network IDs to be removed.
     */
    private final IntList networkIds;

    /**
     * @param networkIds List of network IDs.
     */
    public S2CRemoveBodyBatchPacket(IntList networkIds) {
        this.networkIds = networkIds;
    }

    /**
     * Encodes the IDs into a compressed binary blob.
     */
    public static void encode(S2CRemoveBodyBatchPacket msg, FriendlyByteBuf buf) {
        FriendlyByteBuf temp = new FriendlyByteBuf(Unpooled.buffer());
        try {
            temp.writeVarInt(msg.networkIds.size());
            for (int id : msg.networkIds) temp.writeVarInt(id);
            byte[] raw = new byte[temp.readableBytes()];
            temp.readBytes(raw);
            byte[] comp = VxPacketUtils.compress(raw);
            buf.writeVarInt(raw.length);
            buf.writeByteArray(comp);
        } catch (IOException e) {
            throw new RuntimeException("Removal batch compression failed", e);
        } finally {
            temp.release();
        }
    }

    /**
     * Decodes and decompresses the removal list.
     */
    public static S2CRemoveBodyBatchPacket decode(FriendlyByteBuf buf) {
        try {
            int uncompSize = buf.readVarInt();
            byte[] comp = buf.readByteArray();
            byte[] raw = VxPacketUtils.decompress(comp, uncompSize);
            FriendlyByteBuf db = new FriendlyByteBuf(Unpooled.wrappedBuffer(raw));
            int size = db.readVarInt();
            IntList ids = new IntArrayList(size);
            for (int i = 0; i < size; i++) ids.add(db.readVarInt());
            db.release();
            return new S2CRemoveBodyBatchPacket(ids);
        } catch (IOException e) {
            throw new RuntimeException("Removal batch decompression failed", e);
        }
    }

    /**
     * Handles the removals on the client thread.
     */
    public static void handle(S2CRemoveBodyBatchPacket msg, Supplier<NetworkManager.PacketContext> ctx) {
        ctx.get().queue(() -> {
            VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();
            for (int id : msg.networkIds) manager.removeBody(id);
        });
    }
}