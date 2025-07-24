package net.xmx.vortex.item.physicsgun.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3; // NEU
import net.minecraftforge.network.NetworkEvent;
import net.xmx.vortex.item.physicsgun.manager.PhysicsGunClientManager;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

public class PhysicsGunStatePacket {

    private final UUID playerUuid;
    @Nullable
    private final UUID objectUuid;
    @Nullable
    private final Vec3 localHitPoint;

    public PhysicsGunStatePacket(UUID playerUuid, @Nullable UUID objectUuid, @Nullable Vec3 localHitPoint) {
        this.playerUuid = playerUuid;
        this.objectUuid = objectUuid;
        this.localHitPoint = localHitPoint;
    }

    public static void encode(PhysicsGunStatePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerUuid);
        buf.writeBoolean(msg.objectUuid != null);
        if (msg.objectUuid != null) {
            buf.writeUUID(msg.objectUuid);
            buf.writeDouble(msg.localHitPoint.x());
            buf.writeDouble(msg.localHitPoint.y());
            buf.writeDouble(msg.localHitPoint.z());
        }
    }

    public static PhysicsGunStatePacket decode(FriendlyByteBuf buf) {
        UUID playerUuid = buf.readUUID();
        UUID objectUuid = null;
        Vec3 localHitPoint = null;
        if (buf.readBoolean()) {
            objectUuid = buf.readUUID();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            localHitPoint = new Vec3(x, y, z);
        }
        return new PhysicsGunStatePacket(playerUuid, objectUuid, localHitPoint);
    }

    public static void handle(PhysicsGunStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            PhysicsGunClientManager.getInstance().updateGrabState(msg.playerUuid, msg.objectUuid, msg.localHitPoint);
        });
        ctx.get().setPacketHandled(true);
    }
}