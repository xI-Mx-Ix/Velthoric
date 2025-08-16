package net.xmx.vortex.physics.object.physicsobject.packet.batch;

import com.github.stephengold.joltjni.enumerate.EBodyType;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.physics.object.physicsobject.VxAbstractBody;
import net.xmx.vortex.physics.object.physicsobject.client.ClientObjectDataManager;
import net.xmx.vortex.physics.object.physicsobject.type.soft.VxSoftBody;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class SpawnPhysicsObjectBatchPacket {

    private final List<SpawnData> spawnDataList;

    public SpawnPhysicsObjectBatchPacket(List<SpawnData> spawnDataList) {
        this.spawnDataList = spawnDataList;
    }

    public SpawnPhysicsObjectBatchPacket(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.spawnDataList = new ObjectArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.spawnDataList.add(new SpawnData(buf));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(spawnDataList.size());
        for (SpawnData data : spawnDataList) {
            data.encode(buf);
        }
    }

    public static void handle(SpawnPhysicsObjectBatchPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ClientObjectDataManager manager = ClientObjectDataManager.getInstance();
            for (SpawnData data : msg.spawnDataList) {
                FriendlyByteBuf dataBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data.data));
                manager.spawnObject(data.id, data.typeIdentifier, data.objectType, dataBuf, data.timestamp);
                if (dataBuf.refCnt() > 0) {
                    dataBuf.release();
                }
            }
        });
    }

    public static class SpawnData {
        final UUID id;
        final String typeIdentifier;
        final EBodyType objectType;
        final long timestamp;
        final byte[] data;

        public SpawnData(VxAbstractBody obj, long timestamp) {
            this.id = obj.getPhysicsId();
            this.typeIdentifier = obj.getType().getTypeId();
            this.objectType = obj instanceof VxSoftBody ? EBodyType.SoftBody : EBodyType.RigidBody;
            this.timestamp = timestamp;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                obj.getGameTransform().toBuffer(buf);
                obj.writeCreationData(buf);
                this.data = new byte[buf.readableBytes()];
                buf.readBytes(this.data);
            } finally {
                if(buf.refCnt() > 0) {
                    buf.release();
                }
            }
        }

        public SpawnData(FriendlyByteBuf buf) {
            this.id = buf.readUUID();
            this.typeIdentifier = buf.readUtf();
            this.objectType = buf.readEnum(EBodyType.class);
            this.timestamp = buf.readLong();
            this.data = buf.readByteArray();
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(typeIdentifier);
            buf.writeEnum(objectType);
            buf.writeLong(timestamp);
            buf.writeByteArray(data);
        }

        public int estimateSize() {
            return 16 + FriendlyByteBuf.getVarIntSize(typeIdentifier.length()) + typeIdentifier.length() + 4 + 8 + FriendlyByteBuf.getVarIntSize(data.length) + data.length;
        }
    }
}