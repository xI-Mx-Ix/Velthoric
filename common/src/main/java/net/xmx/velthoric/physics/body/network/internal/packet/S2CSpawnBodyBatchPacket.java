/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.internal.packet;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.network.VxPacketUtils;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.network.internal.VxSpawnData;
import net.xmx.velthoric.physics.world.VxClientPhysicsWorld;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * A compressed binary stream packet for spawning multiple bodies at once.
 * <p>
 * This eliminates the need for individual packet headers or Java object overhead,
 * utilizing a flat memory layout for maximum throughput.
 *
 * @author xI-Mx-Ix
 */
public class S2CSpawnBodyBatchPacket {

    /**
     * Number of bodies encoded in the payload.
     */
    private final int count;

    /**
     * Compressed binary stream of spawn data.
     */
    private final byte[] compressedPayload;

    /**
     * @param count             Number of bodies.
     * @param compressedPayload Compressed Zstd blob.
     */
    public S2CSpawnBodyBatchPacket(int count, byte[] compressedPayload) {
        this.count = count;
        this.compressedPayload = compressedPayload;
    }

    /**
     * Encodes raw packet data.
     */
    public static void encode(S2CSpawnBodyBatchPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.count);
        buf.writeVarInt(msg.compressedPayload.length);
        buf.writeByteArray(msg.compressedPayload);
    }

    /**
     * Decodes and initializes the batch.
     */
    public static S2CSpawnBodyBatchPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        int len = buf.readVarInt();
        return new S2CSpawnBodyBatchPacket(count, buf.readByteArray(len));
    }

    /**
     * Handles decompression and sequentially spawns bodies from the stream.
     */
    public static void handle(S2CSpawnBodyBatchPacket msg, Supplier<NetworkManager.PacketContext> ctx) {
        ctx.get().queue(() -> {
            try {
                int size = (int) com.github.luben.zstd.Zstd.decompressedSize(msg.compressedPayload);
                byte[] raw = VxPacketUtils.decompress(msg.compressedPayload, size);
                VxByteBuf buf = new VxByteBuf(Unpooled.wrappedBuffer(raw));
                VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();

                try {
                    for (int i = 0; i < msg.count; i++) {
                        VxSpawnData.readAndSpawn(buf, manager);
                    }
                } finally {
                    buf.release();
                }
            } catch (IOException e) {
                throw new RuntimeException("Decompression failure during body spawning", e);
            }
        });
    }
}