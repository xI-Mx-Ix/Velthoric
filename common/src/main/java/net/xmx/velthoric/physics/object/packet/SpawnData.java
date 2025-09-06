package net.xmx.velthoric.physics.object.packet;

import com.github.stephengold.joltjni.enumerate.EBodyType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;

import java.util.UUID;

public class SpawnData {
    public final UUID id;
    public final ResourceLocation typeIdentifier;
    public final EBodyType objectType;
    public final long timestamp;
    public final byte[] data;

    public SpawnData(VxAbstractBody obj, long timestamp) {
        this.id = obj.getPhysicsId();
        this.typeIdentifier = obj.getType().getTypeId();
        this.objectType = obj instanceof VxSoftBody ? EBodyType.SoftBody : EBodyType.RigidBody;
        this.timestamp = timestamp;
        VxByteBuf buf = new VxByteBuf(Unpooled.buffer());
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
        this.typeIdentifier = buf.readResourceLocation();
        this.objectType = buf.readEnum(EBodyType.class);
        this.timestamp = buf.readLong();
        this.data = buf.readByteArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeResourceLocation(typeIdentifier);
        buf.writeEnum(objectType);
        buf.writeLong(timestamp);
        buf.writeByteArray(data);
    }

    public int estimateSize() {
        String typeStr = typeIdentifier.toString();
        return 16 + FriendlyByteBuf.getVarIntSize(typeStr.length()) + typeStr.length() + 4 + 8 + FriendlyByteBuf.getVarIntSize(data.length) + data.length;
    }
}