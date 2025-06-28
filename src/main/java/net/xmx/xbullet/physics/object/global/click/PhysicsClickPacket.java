package net.xmx.xbullet.physics.object.global.click;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Ein Netzwerkpaket, das einen Klick des Spielers (links oder rechts) sendet,
 * um mit physikalischen Objekten zu interagieren.
 * Die Verarbeitungslogik wird vollständig an den PhysicsClickManager delegiert.
 */
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

    /**
     * Behandelt das Paket, indem die gesamte Logik an den PhysicsClickManager delegiert wird.
     */
    public static void handle(PhysicsClickPacket msg, Supplier<NetworkEvent.Context> ctx) {
        PhysicsClickManager.processClick(msg, ctx);
    }
}