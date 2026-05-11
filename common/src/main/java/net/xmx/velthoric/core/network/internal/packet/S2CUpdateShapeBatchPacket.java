/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.network.internal.packet;

import com.github.luben.zstd.Zstd;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.xmx.velthoric.network.IVxNetPacket;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.body.client.VxClientBodyManager;
import net.xmx.velthoric.core.body.client.VxClientBodyDataStore;
import net.xmx.velthoric.core.body.shape.VxCollisionShape;
import net.xmx.velthoric.core.body.shape.VxShapeCodec;

import java.nio.ByteBuffer;

/**
 * A compressed binary batch packet for updating collision shapes on the client.
 * <p>
 * Follows the same zero-allocation pattern as {@link S2CUpdateVerticesBatchPacket}:
 * Zstd-compressed direct memory, decompressed into a thread-local buffer on the client.
 *
 * @author xI-Mx-Ix
 */
public class S2CUpdateShapeBatchPacket implements IVxNetPacket {

    private static final ThreadLocal<ByteBuffer> DECOMPRESSION_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(256 * 1024));

    private final ByteBuf data;

    /**
     * @param data Pre-compressed shape data stream (Direct ByteBuf).
     */
    public S2CUpdateShapeBatchPacket(ByteBuf data) {
        this.data = data;
    }

    /**
     * Decodes the packet from the network buffer.
     */
    public static S2CUpdateShapeBatchPacket decode(VxByteBuf buf) {
        int len = buf.readVarInt();
        return new S2CUpdateShapeBatchPacket(buf.readBytes(len));
    }

    /**
     * Encodes the compressed blob into the network buffer.
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
     * Decompresses and applies shape updates to client-side bodies.
     */
    @Override
    public void handle(NetworkManager.PacketContext context) {
        context.queue(() -> {
            try {
                VxClientBodyManager manager = VxClientBodyManager.getInstance();
                VxClientBodyDataStore store = manager.getStore();

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
                VxByteBuf wrapped = new VxByteBuf(db);

                try {
                    int count = wrapped.readVarInt();
                    wrapped.readLong(); // chunkPosLong (skipped, addressed via body network IDs)

                    for (int i = 0; i < count; i++) {
                        int netId = wrapped.readVarInt();
                        boolean hasShape = wrapped.readBoolean();

                        if (hasShape) {
                            VxCollisionShape shape = VxShapeCodec.read(wrapped);

                            // Find the client body and assign the deserialized shape
                            Integer idx = store.getIndexForNetworkId(netId);
                            if (idx != null) {
                                VxBody body = store.clientCurrent().bodies[idx];
                                if (body != null) {
                                    body.setShape(shape);
                                }
                            }
                        }
                    }
                } finally {
                    wrapped.release();
                }
            } finally {
                this.data.release();
            }
        });
    }
}