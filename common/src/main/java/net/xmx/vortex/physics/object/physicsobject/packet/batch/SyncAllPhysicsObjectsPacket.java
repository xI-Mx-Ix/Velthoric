package net.xmx.vortex.physics.object.physicsobject.packet.batch;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.physics.object.physicsobject.client.ClientObjectDataManager;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectState;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectStatePool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncAllPhysicsObjectsPacket {
    private final byte[] data;

    public SyncAllPhysicsObjectsPacket(List<PhysicsObjectState> objectStates) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(objectStates.size() * 64));
        try {
            buf.writeVarInt(objectStates.size());
            for (PhysicsObjectState state : objectStates) {
                state.encode(buf);
            }
            this.data = new byte[buf.readableBytes()];
            buf.readBytes(this.data);
        } finally {
            buf.release();
        }
    }

    public SyncAllPhysicsObjectsPacket(FriendlyByteBuf buf) {
        this.data = buf.readByteArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByteArray(this.data);
    }

    public static void handle(SyncAllPhysicsObjectsPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        FriendlyByteBuf dataBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(msg.data));
        try {
            int size = dataBuf.readVarInt();
            final List<PhysicsObjectState> objectStates = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                PhysicsObjectState state = PhysicsObjectStatePool.acquire();
                state.decode(dataBuf);
                objectStates.add(state);
            }
            NetworkManager.PacketContext context = contextSupplier.get();
            context.queue(() -> ClientObjectDataManager.getInstance().scheduleStatesForUpdate(objectStates));
        } finally {
            dataBuf.release();
        }
    }
}