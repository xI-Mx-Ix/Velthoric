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
import net.xmx.velthoric.physics.body.network.internal.VxSpawnData;
import net.xmx.velthoric.physics.world.VxClientPhysicsWorld;

import java.nio.ByteBuffer;

/**
 * A compressed binary stream packet for spawning multiple bodies at once.
 * <p>
 * This eliminates the need for individual packet headers or Java object overhead,
 * utilizing a flat memory layout for maximum throughput during chunk loading.
 *
 * @author xI-Mx-Ix
 */
public class S2CSpawnBodyBatchPacket implements IVxNetPacket {

    private static final ThreadLocal<ByteBuffer> DECOMPRESSION_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(128 * 1024));

    /**
     * Number of bodies encoded in the payload.
     */
    private final int count;

    /**
     * Compressed binary stream of spawn data (Direct ByteBuf).
     */
    private final ByteBuf data;

    /**
     * @param count Number of bodies.
     * @param data  Compressed Zstd blob (ByteBuf).
     */
    public S2CSpawnBodyBatchPacket(int count, ByteBuf data) {
        this.count = count;
        this.data = data;
    }

    /**
     * Encodes raw packet data.
     */
    @Override
    public void encode(VxByteBuf buf) {
        try {
            buf.writeVarInt(this.count);
            buf.writeVarInt(this.data.readableBytes());
            buf.writeBytes(this.data);
        } finally {
            this.data.release();
        }
    }

    /**
     * Decodes and initializes the batch.
     */
    public static S2CSpawnBodyBatchPacket decode(VxByteBuf buf) {
        int count = buf.readVarInt();
        int len = buf.readVarInt();
        return new S2CSpawnBodyBatchPacket(count, buf.readBytes(len));
    }

    /**
     * Handles decompression and sequentially spawns bodies from the stream.
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

                // Wrap in VxByteBuf to use the readAndSpawn helper
                ByteBuf db = Unpooled.wrappedBuffer(targetBuf);
                VxByteBuf wrapped = new VxByteBuf(db);

                try {
                    for (int i = 0; i < this.count; i++) {
                        VxSpawnData.readAndSpawn(wrapped, manager);
                    }
                } finally {
                    wrapped.release(); // Just wrapper, underlying buffer is thread local
                }

            } finally {
                this.data.release();
            }
        });
    }
}