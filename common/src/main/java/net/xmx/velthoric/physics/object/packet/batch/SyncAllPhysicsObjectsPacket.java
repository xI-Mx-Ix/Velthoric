/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.packet.batch;

import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.manager.VxObjectDataStore;
import net.xmx.velthoric.physics.object.state.PhysicsObjectState;
import net.xmx.velthoric.physics.object.state.PhysicsObjectStatePool;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A network packet for synchronizing the state of multiple physics objects at once.
 * This is the primary packet for sending continuous updates (position, rotation, velocity)
 * from the server to the client.
 *
 * @author xI-Mx-Ix
 */
public class SyncAllPhysicsObjectsPacket {

    // On the client, this holds the list of decoded states. Null on the server.
    private final List<PhysicsObjectState> decodedStates;

    // On the server, this holds the raw, encoded packet data. Null on the client.
    private final FriendlyByteBuf dataBuffer;

    /**
     * Server-side constructor. Builds the packet data from a list of object indices.
     *
     * @param indices   The list of indices of dirty objects in the data store.
     * @param dataStore The server's data store to read the states from.
     */
    public SyncAllPhysicsObjectsPacket(List<Integer> indices, VxObjectDataStore dataStore) {
        this.decodedStates = null;
        this.dataBuffer = buildPacketBuffer(indices, dataStore);
    }

    /**
     * Client-side constructor. Decodes the packet data from the network buffer.
     *
     * @param buf The buffer received from the network.
     */
    public SyncAllPhysicsObjectsPacket(FriendlyByteBuf buf) {
        this.dataBuffer = null;
        this.decodedStates = decode(buf);
    }

    /**
     * A static helper method to build the packet's payload on the server.
     *
     * @param indices   The indices of objects to include.
     * @param dataStore The data store to read from.
     * @return A buffer containing the encoded data, or null if no valid objects were processed.
     */
    private static FriendlyByteBuf buildPacketBuffer(List<Integer> indices, VxObjectDataStore dataStore) {
        FriendlyByteBuf tempPayloadBuffer = new FriendlyByteBuf(Unpooled.buffer());
        int validObjectCount = 0;

        for (int index : indices) {
            UUID id = dataStore.getIdForIndex(index);
            if (id == null) continue; // Skip if the object was removed before sending.

            validObjectCount++;
            tempPayloadBuffer.writeUUID(id);
            tempPayloadBuffer.writeLong(dataStore.lastUpdateTimestamp[index]);
            boolean isActive = dataStore.isActive[index];
            tempPayloadBuffer.writeBoolean(isActive);
            tempPayloadBuffer.writeEnum(dataStore.bodyType[index]);

            // Always send transform data.
            tempPayloadBuffer.writeDouble(dataStore.posX[index]);
            tempPayloadBuffer.writeDouble(dataStore.posY[index]);
            tempPayloadBuffer.writeDouble(dataStore.posZ[index]);
            tempPayloadBuffer.writeFloat(dataStore.rotX[index]);
            tempPayloadBuffer.writeFloat(dataStore.rotY[index]);
            tempPayloadBuffer.writeFloat(dataStore.rotZ[index]);
            tempPayloadBuffer.writeFloat(dataStore.rotW[index]);

            // Only send velocity and vertex data if the object is active.
            if (isActive) {
                tempPayloadBuffer.writeFloat(dataStore.velX[index]);
                tempPayloadBuffer.writeFloat(dataStore.velY[index]);
                tempPayloadBuffer.writeFloat(dataStore.velZ[index]);
                tempPayloadBuffer.writeFloat(dataStore.angVelX[index]);
                tempPayloadBuffer.writeFloat(dataStore.angVelY[index]);
                tempPayloadBuffer.writeFloat(dataStore.angVelZ[index]);

                if (dataStore.bodyType[index] == EBodyType.SoftBody) {
                    float[] vertices = dataStore.vertexData[index];
                    boolean hasVertices = vertices != null && vertices.length > 0;
                    tempPayloadBuffer.writeBoolean(hasVertices);
                    if (hasVertices) {
                        tempPayloadBuffer.writeVarInt(vertices.length);
                        for (float v : vertices) {
                            tempPayloadBuffer.writeFloat(v);
                        }
                    }
                }
            }
        }

        // Only create a final buffer if there's actually data to send.
        if (validObjectCount > 0) {
            FriendlyByteBuf finalBuffer = new FriendlyByteBuf(Unpooled.buffer());
            finalBuffer.writeVarInt(validObjectCount);
            finalBuffer.writeBytes(tempPayloadBuffer);
            tempPayloadBuffer.release();
            return finalBuffer;
        } else {
            tempPayloadBuffer.release();
            return null;
        }
    }

    /**
     * Encodes the packet for network transmission.
     *
     * @param buf The target buffer.
     */
    public void encode(FriendlyByteBuf buf) {
        if (this.dataBuffer != null && this.dataBuffer.isReadable()) {
            buf.writeBytes(this.dataBuffer);
        }
    }

    /**
     * A static helper method to decode the packet's payload on the client.
     *
     * @param buf The buffer to read from.
     * @return A list of {@link PhysicsObjectState} objects.
     */
    private static List<PhysicsObjectState> decode(FriendlyByteBuf buf) {
        if (!buf.isReadable()) {
            return new ArrayList<>();
        }
        int size = buf.readVarInt();
        List<PhysicsObjectState> states = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            // Acquire a reusable state object from the pool.
            PhysicsObjectState state = PhysicsObjectStatePool.acquire();

            UUID id = buf.readUUID();
            long timestamp = buf.readLong();
            boolean isActive = buf.readBoolean();
            EBodyType bodyType = buf.readEnum(EBodyType.class);

            VxTransform transform = new VxTransform();
            transform.getTranslation().set(buf.readDouble(), buf.readDouble(), buf.readDouble());
            transform.getRotation().set(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());

            Vec3 linearVelocity = new Vec3();
            Vec3 angularVelocity = new Vec3();
            float[] softBodyVertices = null;

            if (isActive) {
                linearVelocity.set(buf.readFloat(), buf.readFloat(), buf.readFloat());
                angularVelocity.set(buf.readFloat(), buf.readFloat(), buf.readFloat());
                if (bodyType == EBodyType.SoftBody && buf.readBoolean()) {
                    int length = buf.readVarInt();
                    softBodyVertices = new float[length];
                    for (int j = 0; j < length; j++) {
                        softBodyVertices[j] = buf.readFloat();
                    }
                }
            }

            state.from(id, bodyType, transform, linearVelocity, angularVelocity, softBodyVertices, timestamp, isActive);
            states.add(state);
        }
        return states;
    }

    /**
     * Handles the packet on the client side.
     *
     * @param msg            The received packet.
     * @param contextSupplier A supplier for the network context.
     */
    public static void handle(SyncAllPhysicsObjectsPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            // Schedule the decoded states to be processed by the client manager.
            if (msg.decodedStates != null && !msg.decodedStates.isEmpty()) {
                VxClientObjectManager.getInstance().scheduleStatesForUpdate(msg.decodedStates);
            }
        });
    }

    /**
     * @return True if the packet has data to send, false otherwise.
     */
    public boolean hasData() {
        return this.dataBuffer != null;
    }

    /**
     * Releases the underlying data buffer to prevent memory leaks.
     * This must be called on the server after the packet has been sent.
     */
    public void release() {
        if (this.dataBuffer != null && this.dataBuffer.refCnt() > 0) {
            this.dataBuffer.release();
        }
    }
}