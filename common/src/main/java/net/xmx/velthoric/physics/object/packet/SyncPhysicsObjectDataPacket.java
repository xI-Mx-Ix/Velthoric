package net.xmx.velthoric.physics.object.packet;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.client.ClientObjectDataManager;

import java.util.UUID;
import java.util.function.Supplier;

public class SyncPhysicsObjectDataPacket {

    private final UUID id;
    private final byte[] data;

    public SyncPhysicsObjectDataPacket(VxAbstractBody obj) {
        this.id = obj.getPhysicsId();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        obj.writeCreationData(buf);
        this.data = new byte[buf.readableBytes()];
        buf.readBytes(this.data);
        buf.release();
    }

    public SyncPhysicsObjectDataPacket(FriendlyByteBuf buf) {
        this.id = buf.readUUID();
        this.data = buf.readByteArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeByteArray(data);
    }

    public static void handle(SyncPhysicsObjectDataPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ClientObjectDataManager manager = ClientObjectDataManager.getInstance();
            manager.updateCustomObjectData(msg.id, Unpooled.wrappedBuffer(msg.data));
        });
    }
}