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

public class SyncAllPhysicsObjectsPacket {

    private final List<PhysicsObjectState> decodedStates;
    private final FriendlyByteBuf buffer;

    public SyncAllPhysicsObjectsPacket(List<Integer> indices, VxObjectDataStore dataStore) {
        this.decodedStates = null;
        this.buffer = new FriendlyByteBuf(Unpooled.buffer(indices.size() * 80));
        encode(this.buffer, indices, dataStore);
    }

    public SyncAllPhysicsObjectsPacket(FriendlyByteBuf buf) {
        this.buffer = null;
        this.decodedStates = decode(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBytes(this.buffer);
    }

    private static void encode(FriendlyByteBuf buf, List<Integer> indices, VxObjectDataStore dataStore) {
        buf.writeVarInt(indices.size());
        for (int index : indices) {
            UUID id = dataStore.getIdForIndex(index);
            if (id == null) continue;

            buf.writeUUID(id);
            buf.writeLong(dataStore.lastUpdateTimestamp[index]);
            boolean isActive = dataStore.isActive[index];
            buf.writeBoolean(isActive);
            buf.writeEnum(dataStore.bodyType[index]);

            buf.writeDouble(dataStore.posX[index]);
            buf.writeDouble(dataStore.posY[index]);
            buf.writeDouble(dataStore.posZ[index]);
            buf.writeFloat(dataStore.rotX[index]);
            buf.writeFloat(dataStore.rotY[index]);
            buf.writeFloat(dataStore.rotZ[index]);
            buf.writeFloat(dataStore.rotW[index]);

            if (isActive) {

                buf.writeFloat(dataStore.velX[index]);
                buf.writeFloat(dataStore.velY[index]);
                buf.writeFloat(dataStore.velZ[index]);

                buf.writeFloat(dataStore.angVelX[index]);
                buf.writeFloat(dataStore.angVelY[index]);
                buf.writeFloat(dataStore.angVelZ[index]);

                if (dataStore.bodyType[index] == EBodyType.SoftBody) {
                    float[] vertices = dataStore.vertexData[index];
                    boolean hasVertices = vertices != null && vertices.length > 0;
                    buf.writeBoolean(hasVertices);
                    if (hasVertices) {
                        buf.writeVarInt(vertices.length);
                        for (float v : vertices) {
                            buf.writeFloat(v);
                        }
                    }
                }
            }
        }
    }

    private static List<PhysicsObjectState> decode(FriendlyByteBuf buf) {
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
}