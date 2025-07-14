package net.xmx.xbullet.physics.object.raycast.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.physics.object.raycast.PhysicsClickManager;

import java.util.function.Supplier;

public record PhysicsClickPacket(
        float rayOriginX, float rayOriginY, float rayOriginZ,
        float rayDirectionX, float rayDirectionY, float rayDirectionZ,
        boolean isRightClick) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(rayOriginX);
        buffer.writeFloat(rayOriginY);
        buffer.writeFloat(rayOriginZ);
        buffer.writeFloat(rayDirectionX);
        buffer.writeFloat(rayDirectionY);
        buffer.writeFloat(rayDirectionZ);
        buffer.writeBoolean(isRightClick);
    }

    public static PhysicsClickPacket decode(FriendlyByteBuf buffer) {
        return new PhysicsClickPacket(
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readBoolean()
        );
    }

    public static void handle(PhysicsClickPacket msg, Supplier<NetworkEvent.Context> ctx) {
        PhysicsClickManager.processClick(msg, ctx);
    }
}