/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.internal.packet;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.network.VxPacketUtils;
import net.xmx.velthoric.physics.body.client.VxClientBodyDataStore;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.world.VxClientPhysicsWorld;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * A compressed raw-data packet for synchronizing high-frequency body state updates.
 * <p>
 * This packet utilizes a chunk-relative binary stream format (Structure of Arrays).
 * On the server, it exists only as a compressed byte array to save heap space.
 * On the client, it is decoded into flat arrays for fast application to the data store.
 *
 * @author xI-Mx-Ix
 */
public class S2CUpdateBodyStateBatchPacket {

    /**
     * The compressed binary payload containing all body states for a specific chunk.
     */
    private final byte[] compressedPayload;

    /**
     * The number of bodies included in this batch. (Decoded on client)
     */
    private int count;

    /**
     * The server-side simulation timestamp for this batch. (Decoded on client)
     */
    private long timestamp;

    /**
     * The coordinate of the chunk these bodies belong to. (Decoded on client)
     */
    private long chunkPosLong;

    /**
     * Array of network IDs corresponding to the bodies in this batch. (Decoded on client)
     */
    private int[] networkIds;

    /**
     * X coordinates relative to the chunk origin. (Decoded on client)
     */
    private float[] relX;
    /**
     * Y coordinates relative to the world bottom. (Decoded on client)
     */
    private float[] relY;
    /**
     * Z coordinates relative to the chunk origin. (Decoded on client)
     */
    private float[] relZ;

    /**
     * Quaternion rotation data. (Decoded on client)
     */
    private float[] rotX, rotY, rotZ, rotW;

    /**
     * Linear velocity data. (Decoded on client)
     */
    private float[] velX, velY, velZ;

    /**
     * Activation states of the bodies. (Decoded on client)
     */
    private boolean[] isActive;

    /**
     * Server-side constructor wrapping a pre-built compressed payload.
     *
     * @param compressedPayload The compressed Zstd data blob.
     */
    public S2CUpdateBodyStateBatchPacket(byte[] compressedPayload) {
        this.compressedPayload = compressedPayload;
    }

    /**
     * Internal client-side constructor for populating decoded data.
     */
    private S2CUpdateBodyStateBatchPacket(int count, long timestamp, long chunkPosLong,
                                          int[] networkIds, float[] relX, float[] relY, float[] relZ,
                                          float[] rotX, float[] rotY, float[] rotZ, float[] rotW,
                                          float[] velX, float[] velY, float[] velZ, boolean[] isActive) {
        this.compressedPayload = null;
        this.count = count;
        this.timestamp = timestamp;
        this.chunkPosLong = chunkPosLong;
        this.networkIds = networkIds;
        this.relX = relX;
        this.relY = relY;
        this.relZ = relZ;
        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;
        this.rotW = rotW;
        this.velX = velX;
        this.velY = velY;
        this.velZ = velZ;
        this.isActive = isActive;
    }

    /**
     * Encodes the packet into the network buffer.
     *
     * @param msg The packet instance.
     * @param buf The output buffer.
     */
    public static void encode(S2CUpdateBodyStateBatchPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.compressedPayload.length);
        buf.writeByteArray(msg.compressedPayload);
    }

    /**
     * Decodes the packet from the network buffer and decompresses the binary stream.
     *
     * @param buf The input buffer.
     * @return A populated packet instance.
     */
    public static S2CUpdateBodyStateBatchPacket decode(FriendlyByteBuf buf) {
        try {
            int length = buf.readVarInt();
            byte[] compressed = buf.readByteArray(length);
            int decompSize = (int) com.github.luben.zstd.Zstd.decompressedSize(compressed);

            byte[] decompressed = VxPacketUtils.decompress(compressed, decompSize);
            FriendlyByteBuf db = new FriendlyByteBuf(Unpooled.wrappedBuffer(decompressed));

            int count = db.readVarInt();
            long timestamp = db.readLong();
            long chunkPosLong = db.readLong();

            int[] netIds = new int[count];
            float[] rx = new float[count], ry = new float[count], rz = new float[count];
            float[] qx = new float[count], qy = new float[count], qz = new float[count], qw = new float[count];
            float[] vx = new float[count], vy = new float[count], vz = new float[count];
            boolean[] active = new boolean[count];

            for (int i = 0; i < count; i++) {
                netIds[i] = db.readVarInt();
                rx[i] = db.readFloat();
                ry[i] = db.readFloat();
                rz[i] = db.readFloat();
                qx[i] = db.readFloat();
                qy[i] = db.readFloat();
                qz[i] = db.readFloat();
                qw[i] = db.readFloat();
                active[i] = db.readBoolean();
                if (active[i]) {
                    vx[i] = db.readFloat();
                    vy[i] = db.readFloat();
                    vz[i] = db.readFloat();
                }
            }
            db.release();
            return new S2CUpdateBodyStateBatchPacket(count, timestamp, chunkPosLong, netIds, rx, ry, rz, qx, qy, qz, qw, vx, vy, vz, active);
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode high-performance state packet", e);
        }
    }

    /**
     * Processes the decoded packet on the client's main thread and updates the data store.
     *
     * @param msg The packet message.
     * @param ctx The packet context.
     */
    public static void handle(S2CUpdateBodyStateBatchPacket msg, Supplier<NetworkManager.PacketContext> ctx) {
        ctx.get().queue(() -> {
            VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();
            VxClientBodyDataStore store = manager.getStore();
            ChunkPos cp = new ChunkPos(msg.chunkPosLong);
            double baseX = cp.getMinBlockX(), baseY = -64.0, baseZ = cp.getMinBlockZ();

            manager.addClockSyncSample(msg.timestamp - manager.getClock().getGameTimeNanos());

            for (int i = 0; i < msg.count; i++) {
                Integer idx = store.getIndexForNetworkId(msg.networkIds[i]);
                if (idx == null) continue;

                store.state0_timestamp[idx] = store.state1_timestamp[idx];
                store.state0_posX[idx] = store.state1_posX[idx];
                store.state0_posY[idx] = store.state1_posY[idx];
                store.state0_posZ[idx] = store.state1_posZ[idx];
                store.state0_rotX[idx] = store.state1_rotX[idx];
                store.state0_rotY[idx] = store.state1_rotY[idx];
                store.state0_rotZ[idx] = store.state1_rotZ[idx];
                store.state0_rotW[idx] = store.state1_rotW[idx];
                store.state0_isActive[idx] = store.state1_isActive[idx];

                store.state1_timestamp[idx] = msg.timestamp;
                store.state1_posX[idx] = baseX + msg.relX[i];
                store.state1_posY[idx] = baseY + msg.relY[i];
                store.state1_posZ[idx] = baseZ + msg.relZ[i];
                store.state1_rotX[idx] = msg.rotX[i];
                store.state1_rotY[idx] = msg.rotY[i];
                store.state1_rotZ[idx] = msg.rotZ[i];
                store.state1_rotW[idx] = msg.rotW[i];
                store.state1_isActive[idx] = msg.isActive[i];

                if (msg.isActive[i]) {
                    store.state1_velX[idx] = msg.velX[i];
                    store.state1_velY[idx] = msg.velY[i];
                    store.state1_velZ[idx] = msg.velZ[i];
                }
                if (store.lastKnownPosition[idx] != null)
                    store.lastKnownPosition[idx].set(store.state1_posX[idx], store.state1_posY[idx], store.state1_posZ[idx]);
            }
        });
    }
}