package net.xmx.xbullet.physics.object.riding.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.riding.ClientRidingCache;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

public class MountPhysicsObjectPacket {

    private final UUID riderUUID;
    private final boolean isMounting;
    @Nullable private final UUID physicsObjectId;
    @Nullable private final PhysicsTransform relativeSeatTransform;

    public MountPhysicsObjectPacket(UUID riderUUID, UUID physicsObjectId, PhysicsTransform relativeSeatTransform) {
        this.riderUUID = riderUUID;
        this.isMounting = true;
        this.physicsObjectId = physicsObjectId;
        this.relativeSeatTransform = relativeSeatTransform;
    }

    public MountPhysicsObjectPacket(UUID riderUUID) {
        this.riderUUID = riderUUID;
        this.isMounting = false;
        this.physicsObjectId = null;
        this.relativeSeatTransform = null;
    }

    public static void encode(MountPhysicsObjectPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.riderUUID);
        buf.writeBoolean(msg.isMounting);
        if (msg.isMounting) {
            buf.writeUUID(msg.physicsObjectId);
            CompoundTag transformTag = new CompoundTag();
            msg.relativeSeatTransform.toNbt(transformTag);
            buf.writeNbt(transformTag);
        }
    }

    public static MountPhysicsObjectPacket decode(FriendlyByteBuf buf) {
        UUID riderUUID = buf.readUUID();
        boolean isMounting = buf.readBoolean();
        if (isMounting) {
            UUID physicsObjectId = buf.readUUID();
            CompoundTag transformTag = buf.readNbt();
            PhysicsTransform transform = PhysicsTransform.createFromNbt(transformTag);
            return new MountPhysicsObjectPacket(riderUUID, physicsObjectId, transform);
        } else {
            return new MountPhysicsObjectPacket(riderUUID);
        }
    }

    public static void handle(MountPhysicsObjectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.isMounting) {
                ClientRidingCache.startRiding(msg.riderUUID, msg.physicsObjectId, msg.relativeSeatTransform);
            } else {
                ClientRidingCache.stopRiding(msg.riderUUID);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}