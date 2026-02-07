/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.internal.packet;

import com.github.luben.zstd.Zstd;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.xmx.velthoric.network.IVxNetPacket;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.world.VxClientPhysicsWorld;

import java.nio.ByteBuffer;

/**
 * A compressed binary batch packet for updating soft body vertex data.
 * <p>
 * Handles vertex data updates efficiently using direct memory buffers and Zstd compression,
 * avoiding object overhead for high-frequency soft body deformation updates.
 *
 * @author xI-Mx-Ix
 */
public class S2CUpdateVerticesBatchPacket implements IVxNetPacket {

    private static final ThreadLocal<ByteBuffer> DECOMPRESSION_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(1024 * 1024));

    private final ByteBuf data;

    /**
     * @param data Pre-compressed vertex data stream (Direct ByteBuf).
     */
    public S2CUpdateVerticesBatchPacket(ByteBuf data) {
        this.data = data;
    }

    /**
     * Decodes the packet.
     */
    public static S2CUpdateVerticesBatchPacket decode(VxByteBuf buf) {
        int len = buf.readVarInt();
        return new S2CUpdateVerticesBatchPacket(buf.readBytes(len));
    }

    /**
     * Encodes the compressed blob.
     */
    @Override
    public void encode(VxByteBuf buf) {
        try {
            buf.writeVarInt(this.data.readableBytes());
            buf.writeBytes(this.data);
        } finally {
            this.data.release();
        }
    }

    /**
     * Decompresses and updates the vertex arrays in the client data store.
     */
    @Override
    public void handle(NetworkManager.PacketContext context) {
        context.queue(() -> {
            try {
                VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();

                ByteBuffer compressedNio = this.data.nioBuffer();
                long uncompressedSize = Zstd.decompressedSize(compressedNio);

                ByteBuffer targetBuf = DECOMPRESSION_BUFFER.get();
                if (targetBuf.capacity() < uncompressedSize) {
                    targetBuf = ByteBuffer.allocateDirect((int) uncompressedSize);
                    DECOMPRESSION_BUFFER.set(targetBuf);
                }
                targetBuf.clear();
                targetBuf.limit((int) uncompressedSize);

                Zstd.decompressDirectByteBuffer(targetBuf, 0, (int) uncompressedSize, compressedNio, 0, compressedNio.remaining());

                ByteBuf db = Unpooled.wrappedBuffer(targetBuf);

                int count = db.readInt();
                db.readLong(); // chunkPosLong (skipped, handled via body IDs)

                for (int i = 0; i < count; i++) {
                    int netId = db.readInt();
                    if (db.readBoolean()) {
                        int vLen = db.readInt();
                        // Here we still need an array because the DataStore currently stores float[].
                        // Ideally the DataStore should use a flat FloatBuffer for soft bodies,
                        // but strictly following the current DataStore API, we allocate the array here.
                        float[] verts = new float[vLen];
                        for (int k = 0; k < vLen; k++) verts[k] = db.readFloat();

                        Integer index = manager.getStore().getIndexForNetworkId(netId);
                        if (index != null) manager.getStore().state1_vertexData[index] = verts;
                    }
                }
            } finally {
                this.data.release();
            }
        });
    }
}