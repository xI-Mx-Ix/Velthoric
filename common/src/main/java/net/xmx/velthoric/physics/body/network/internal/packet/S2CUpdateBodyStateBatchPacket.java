/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.internal.packet;

import com.github.luben.zstd.Zstd;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.physics.body.client.VxClientBodyDataStore;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.world.VxClientPhysicsWorld;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * A compressed raw-data packet for synchronizing high-frequency body state updates.
 * <p>
 * <b>Zero-Allocation / Zero-Copy Design:</b>
 * On the server, this packet simply holds a reference to a Pooled Direct ByteBuf containing Zstd-compressed data.
 * On the client, the data is decompressed directly from the network buffer into a reusable thread-local buffer,
 * and then applied directly to the Structure-of-Arrays data store.
 * <p>
 * This eliminates the creation of thousands of temporary objects (float[], Packet objects, wrappers) per tick.
 *
 * @author xI-Mx-Ix
 */
public class S2CUpdateBodyStateBatchPacket {

    /**
     * ThreadLocal buffer for decompression on the client to avoid repeated allocations.
     * Sized at 512KB to handle dense chunk updates.
     */
    private static final ThreadLocal<ByteBuffer> DECOMPRESSION_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(512 * 1024));

    /**
     * The compressed binary payload. On Server, this is a Pooled Direct Buffer. On Client, it's a slice of the network buffer.
     */
    private final ByteBuf data;

    /**
     * Server-side constructor wrapping a pre-built compressed payload.
     * Takes ownership of the passed ByteBuf (it will be released after encoding).
     *
     * @param data The compressed Zstd data blob.
     */
    public S2CUpdateBodyStateBatchPacket(ByteBuf data) {
        this.data = data;
    }

    /**
     * Encodes the packet into the network buffer.
     * Writes the length prefix followed by the compressed bytes.
     * Releases the buffer after writing.
     *
     * @param msg The packet instance.
     * @param buf The output buffer.
     */
    public static void encode(S2CUpdateBodyStateBatchPacket msg, FriendlyByteBuf buf) {
        try {
            int length = msg.data.readableBytes();
            buf.writeVarInt(length);
            buf.writeBytes(msg.data);
        } finally {
            // Important: Release the pooled buffer after writing to the wire.
            msg.data.release();
        }
    }

    /**
     * Decodes the packet from the network buffer.
     * Does NOT allocate a byte array, but returns a retained slice/copy in a ByteBuf.
     *
     * @param buf The input buffer.
     * @return A populated packet instance.
     */
    public static S2CUpdateBodyStateBatchPacket decode(FriendlyByteBuf buf) {
        int length = buf.readVarInt();
        // Read into a new ByteBuf. This makes a copy from the underlying buffer,
        // which is unavoidable with FriendlyByteBuf if we want the data to survive the handler scope.
        ByteBuf copied = buf.readBytes(length);
        return new S2CUpdateBodyStateBatchPacket(copied);
    }

    /**
     * Processes the decoded packet on the client's main thread and updates the data store.
     * Uses Zero-Copy decompression directly into the DataStore arrays.
     *
     * @param msg The packet message.
     * @param ctx The packet context.
     */
    public static void handle(S2CUpdateBodyStateBatchPacket msg, Supplier<NetworkManager.PacketContext> ctx) {
        ctx.get().queue(() -> {
            try {
                VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();
                VxClientBodyDataStore store = manager.getStore();

                // 1. Prepare Decompression
                // Obtain NIO buffer from Netty ByteBuf without copying
                ByteBuffer compressedNio = msg.data.nioBuffer();

                // Determine required size for the output buffer
                long uncompressedSize = Zstd.decompressedSize(compressedNio);

                // Acquire and resize thread-local buffer if necessary
                ByteBuffer targetBuf = DECOMPRESSION_BUFFER.get();
                if (targetBuf.capacity() < uncompressedSize) {
                    targetBuf = ByteBuffer.allocateDirect((int) uncompressedSize);
                    DECOMPRESSION_BUFFER.set(targetBuf);
                }

                // Reset buffer state before writing
                targetBuf.clear();

                // 2. Decompress (Direct Memory -> Direct Memory)
                // Writes the raw physics data directly into the reusable buffer
                Zstd.decompressDirectByteBuffer(targetBuf, 0, (int) uncompressedSize, compressedNio, 0, compressedNio.remaining());

                // Set the limit to the actual data size so the wrapped ByteBuf behaves correctly
                targetBuf.position(0);
                targetBuf.limit((int) uncompressedSize);

                // 3. Read directly from the decompressed buffer
                // Wrapping it in a ByteBuf allows easy reading of primitives without manual offsets
                ByteBuf db = Unpooled.wrappedBuffer(targetBuf);

                int count = db.readInt();
                long timestamp = db.readLong();
                long chunkPosLong = db.readLong();

                ChunkPos cp = new ChunkPos(chunkPosLong);
                double baseX = cp.getMinBlockX();
                double baseY = -64.0; // Standard world bottom height
                double baseZ = cp.getMinBlockZ();

                manager.addClockSyncSample(timestamp - manager.getClock().getGameTimeNanos());

                // 4. Update Data Store (Zero Object Allocation)
                for (int i = 0; i < count; i++) {
                    int netId = db.readInt();
                    Integer idx = store.getIndexForNetworkId(netId);

                    // If the body is not tracked locally (e.g., desync or unloaded), skip the data stream
                    // to maintain correct buffer offsets for subsequent bodies.
                    if (idx == null) {
                        db.skipBytes(12); // Position (3 floats * 4 bytes)
                        db.skipBytes(16); // Rotation (4 floats * 4 bytes)

                        boolean isActive = db.readBoolean(); // Must read the activity flag
                        if (isActive) {
                            db.skipBytes(12); // Velocity (3 floats * 4 bytes)
                        }
                        continue;
                    }

                    int index = idx; // Unbox index once

                    // Cycle history states (current -> old)
                    store.state0_timestamp[index] = store.state1_timestamp[index];
                    store.state0_posX[index] = store.state1_posX[index];
                    store.state0_posY[index] = store.state1_posY[index];
                    store.state0_posZ[index] = store.state1_posZ[index];
                    store.state0_rotX[index] = store.state1_rotX[index];
                    store.state0_rotY[index] = store.state1_rotY[index];
                    store.state0_rotZ[index] = store.state1_rotZ[index];
                    store.state0_rotW[index] = store.state1_rotW[index];
                    store.state0_isActive[index] = store.state1_isActive[index];

                    // Read New State into state1
                    store.state1_timestamp[index] = timestamp;
                    store.state1_posX[index] = baseX + db.readFloat();
                    store.state1_posY[index] = baseY + db.readFloat();
                    store.state1_posZ[index] = baseZ + db.readFloat();
                    store.state1_rotX[index] = db.readFloat();
                    store.state1_rotY[index] = db.readFloat();
                    store.state1_rotZ[index] = db.readFloat();
                    store.state1_rotW[index] = db.readFloat();

                    boolean active = db.readBoolean();
                    store.state1_isActive[index] = active;

                    if (active) {
                        store.state1_velX[index] = db.readFloat();
                        store.state1_velY[index] = db.readFloat();
                        store.state1_velZ[index] = db.readFloat();
                    }

                    // Update culling position for renderer frustum checks
                    if (store.lastKnownPosition[index] != null) {
                        store.lastKnownPosition[index].set(store.state1_posX[index], store.state1_posY[index], store.state1_posZ[index]);
                    }
                }

            } finally {
                // Always release the pooled network buffer on client side
                msg.data.release();
            }
        });
    }
}