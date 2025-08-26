package net.xmx.velthoric.physics.object.packet.batch;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.physics.object.client.ClientObjectDataManager;
import net.xmx.velthoric.physics.object.packet.SpawnData;

import java.util.List;
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
}