/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.internal.packet;

import com.github.luben.zstd.Zstd;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.xmx.velthoric.network.IVxNetPacket;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.world.VxClientPhysicsWorld;

import java.nio.ByteBuffer;

/**
 * A network packet for batched removal of physics bodies.
 * <p>
 * This packet uses Zstd compression on the network ID list to minimize bandwidth
 * during mass-deletion events (e.g., chunk unloading or large-scale physics cleanup).
 *
 * @author xI-Mx-Ix
 */
public class S2CRemoveBodyBatchPacket implements IVxNetPacket {

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
     * Encodes the IDs into a compressed binary blob using direct buffers.
     */
    @Override
    public void encode(VxByteBuf buf) {
        // Compress IDs on the fly into the VxByteBuf.
        // Step 1: Write IDs to a temporary heap buffer (fastest for simple ints) or direct.
        // Using heap buffer here because encoding logic for var-int logic is simpler.
        ByteBuf raw = Unpooled.directBuffer(this.networkIds.size() * 4);
        try {
            for (int id : this.networkIds) raw.writeInt(id);

            // Step 2: Prepare compression
            ByteBuffer src = raw.nioBuffer();
            int max = (int) Zstd.compressBound(raw.readableBytes());
            ByteBuf comp = Unpooled.directBuffer(max); // Temporary heap buffer for output

            try {
                ByteBuffer dst = comp.nioBuffer(0, max);
                // Level 1 is sufficient for integer lists
                long len = Zstd.compressDirectByteBuffer(dst, 0, max, src, 0, raw.readableBytes(), 1);
                comp.writerIndex((int) len);

                buf.writeVarInt(comp.readableBytes());
                buf.writeBytes(comp);
            } finally {
                comp.release();
            }
        } finally {
            raw.release();
        }
    }

    /**
     * Decodes and decompresses the removal list.
     */
    public static S2CRemoveBodyBatchPacket decode(VxByteBuf buf) {
        int len = buf.readVarInt();
        ByteBuf comp = buf.readBytes(len);
        try {
            ByteBuffer src = comp.nioBuffer();
            long origSize = Zstd.decompressedSize(src);

            // Check valid size
            if (Zstd.isError(origSize)) throw new RuntimeException("Zstd error: " + Zstd.getErrorName(origSize));

            ByteBuf raw = Unpooled.directBuffer((int) origSize);
            try {
                ByteBuffer dst = raw.nioBuffer(0, (int) origSize);
                Zstd.decompressDirectByteBuffer(dst, 0, (int) origSize, src, 0, src.remaining());
                raw.writerIndex((int) origSize);

                IntList ids = new IntArrayList(raw.readableBytes() / 4);
                while (raw.isReadable()) {
                    ids.add(raw.readInt());
                }
                return new S2CRemoveBodyBatchPacket(ids);
            } finally {
                raw.release();
            }
        } finally {
            comp.release();
        }
    }

    /**
     * Handles the removals on the client thread.
     */
    @Override
    public void handle(NetworkManager.PacketContext context) {
        context.queue(() -> {
            VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();
            for (int id : this.networkIds) manager.removeBody(id);
        });
    }
}