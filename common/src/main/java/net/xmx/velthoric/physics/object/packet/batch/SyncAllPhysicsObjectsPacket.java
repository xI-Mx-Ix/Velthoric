package net.xmx.velthoric.physics.object.packet.batch;

import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.manager.VxObjectStore;
import net.xmx.velthoric.physics.object.state.PhysicsObjectState;
import net.xmx.velthoric.physics.object.state.PhysicsObjectStatePool;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class SyncAllPhysicsObjectsPacket {

    private final List<PhysicsObjectState> decodedStates;

    private final FriendlyByteBuf dataBuffer;

    public SyncAllPhysicsObjectsPacket(List<Integer> indices, VxObjectStore dataStore) {
        this.decodedStates = null;
        this.dataBuffer = buildPacketBuffer(indices, dataStore);
    }

    public SyncAllPhysicsObjectsPacket(FriendlyByteBuf buf) {
        this.dataBuffer = null;
        this.decodedStates = decode(buf);
    }

    private static FriendlyByteBuf buildPacketBuffer(List<Integer> indices, VxObjectStore dataStore) {
        FriendlyByteBuf tempPayloadBuffer = new FriendlyByteBuf(Unpooled.buffer());
        int validObjectCount = 0;

        for (int index : indices) {
            UUID id = dataStore.getIdForIndex(index);

            if (id == null) {
                continue;
            }

            validObjectCount++;
            tempPayloadBuffer.writeUUID(id);
            tempPayloadBuffer.writeLong(dataStore.lastUpdateTimestamp[index]);
            boolean isActive = dataStore.isActive[index];
            tempPayloadBuffer.writeBoolean(isActive);
            tempPayloadBuffer.writeEnum(dataStore.bodyType[index]);

            tempPayloadBuffer.writeDouble(dataStore.posX[index]);
            tempPayloadBuffer.writeDouble(dataStore.posY[index]);
            tempPayloadBuffer.writeDouble(dataStore.posZ[index]);
            tempPayloadBuffer.writeFloat(dataStore.rotX[index]);
            tempPayloadBuffer.writeFloat(dataStore.rotY[index]);
            tempPayloadBuffer.writeFloat(dataStore.rotZ[index]);
            tempPayloadBuffer.writeFloat(dataStore.rotW[index]);

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

    public void encode(FriendlyByteBuf buf) {

        if (this.dataBuffer != null && this.dataBuffer.isReadable()) {
            buf.writeBytes(this.dataBuffer);
        }
    }

    private static List<PhysicsObjectState> decode(FriendlyByteBuf buf) {

        if (!buf.isReadable()) {
            return new ArrayList<>();
        }
        int size = buf.readVarInt();
        List<PhysicsObjectState> states = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
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

    public static void handle(SyncAllPhysicsObjectsPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            if (msg.decodedStates != null && !msg.decodedStates.isEmpty()) {
                VxClientObjectManager.getInstance().scheduleStatesForUpdate(msg.decodedStates);
            }
        });
    }

    public boolean hasData() {
        return this.dataBuffer != null;
    }

    public void release() {
        if (this.dataBuffer != null && this.dataBuffer.refCnt() > 0) {
            this.dataBuffer.release();
        }
    }
}