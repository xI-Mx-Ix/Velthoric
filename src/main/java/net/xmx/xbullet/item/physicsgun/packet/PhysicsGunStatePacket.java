package net.xmx.xbullet.item.physicsgun.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.item.physicsgun.manager.PhysicsGunClientManager;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

public class PhysicsGunStatePacket {

    private final UUID playerUuid;
    @Nullable
    private final UUID objectUuid;

    public PhysicsGunStatePacket(UUID playerUuid, @Nullable UUID objectUuid) {
        this.playerUuid = playerUuid;
        this.objectUuid = objectUuid;
    }

    public static void encode(PhysicsGunStatePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerUuid);
        buf.writeBoolean(msg.objectUuid != null);
        if (msg.objectUuid != null) {
            buf.writeUUID(msg.objectUuid);
        }
    }

    public static PhysicsGunStatePacket decode(FriendlyByteBuf buf) {
        UUID playerUuid = buf.readUUID();
        UUID objectUuid = buf.readBoolean() ? buf.readUUID() : null;
        return new PhysicsGunStatePacket(playerUuid, objectUuid);
    }

    public static void handle(PhysicsGunStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            PhysicsGunClientManager.getInstance().updateGrabState(msg.playerUuid, msg.objectUuid);
        });
        ctx.get().setPacketHandled(true);
    }
}