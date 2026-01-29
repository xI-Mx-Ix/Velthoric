/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.internal.packet;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.network.VxPacketUtils;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.world.VxClientPhysicsWorld;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * A compressed binary batch packet for updating soft body vertex data.
 * <p>
 * This packet is sent on a lower frequency than the transform packet and uses
 * a raw stream to avoid creating thousands of float[] objects on the server.
 *
 * @author xI-Mx-Ix
 */
public class S2CUpdateVerticesBatchPacket {

    /**
     * Compressed binary payload.
     */
    private final byte[] compressedPayload;

    /**
     * @param compressedPayload Pre-compressed vertex data stream.
     */
    public S2CUpdateVerticesBatchPacket(byte[] compressedPayload) {
        this.compressedPayload = compressedPayload;
    }

    /**
     * Encodes the compressed blob.
     */
    public static void encode(S2CUpdateVerticesBatchPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.compressedPayload.length);
        buf.writeByteArray(msg.compressedPayload);
    }

    /**
     * Decodes the packet.
     */
    public static S2CUpdateVerticesBatchPacket decode(FriendlyByteBuf buf) {
        int len = buf.readVarInt();
        return new S2CUpdateVerticesBatchPacket(buf.readByteArray(len));
    }

    /**
     * Decompresses and updates the vertex arrays in the client data store.
     */
    public static void handle(S2CUpdateVerticesBatchPacket msg, Supplier<NetworkManager.PacketContext> ctx) {
        ctx.get().queue(() -> {
            try {
                int size = (int) com.github.luben.zstd.Zstd.decompressedSize(msg.compressedPayload);
                byte[] data = VxPacketUtils.decompress(msg.compressedPayload, size);
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
                VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();

                int count = buf.readVarInt();
                buf.readLong(); // Read and ignore chunk context if not needed for indexing

                for (int i = 0; i < count; i++) {
                    int netId = buf.readVarInt();
                    if (buf.readBoolean()) {
                        int vLen = buf.readVarInt();
                        float[] verts = new float[vLen];
                        for (int k = 0; k < vLen; k++) verts[k] = buf.readFloat();

                        Integer index = manager.getStore().getIndexForNetworkId(netId);
                        if (index != null) manager.getStore().state1_vertexData[index] = verts;
                    }
                }
                buf.release();
            } catch (IOException e) {
                throw new RuntimeException("Vertex batch decompression failed", e);
            }
        });
    }
}