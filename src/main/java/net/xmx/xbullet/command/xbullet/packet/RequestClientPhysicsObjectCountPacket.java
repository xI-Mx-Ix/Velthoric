package net.xmx.xbullet.command.xbullet.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.network.NetworkHandler;
import net.xmx.xbullet.physics.object.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.physicsobject.client.ClientPhysicsObjectManager;

import java.util.function.Supplier;

public class RequestClientPhysicsObjectCountPacket {

    public RequestClientPhysicsObjectCountPacket() {}

    public static void encode(RequestClientPhysicsObjectCountPacket msg, FriendlyByteBuf buf) {}

    public static RequestClientPhysicsObjectCountPacket decode(FriendlyByteBuf buf) {
        return new RequestClientPhysicsObjectCountPacket();
    }

    public static void handle(RequestClientPhysicsObjectCountPacket msg, Supplier<NetworkEvent.Context> ctx) {

        ctx.get().enqueueWork(() -> {
            if (!ctx.get().getDirection().getReceptionSide().isClient()) {
                XBullet.LOGGER.warn("RequestClientPhysicsObjectCountPacket fälschlicherweise auf dem Server empfangen!");
                return;
            }

            XBullet.LOGGER.debug("Client received RequestClientPhysicsObjectCountPacket.");

            ClientPhysicsObjectManager manager = ClientPhysicsObjectManager.getInstance();

            long rigidCount = manager.getAllObjectData().stream()
                    .filter(data -> data.getObjectType() == EObjectType.RIGID_BODY)
                    .count();

            long softCount = manager.getAllObjectData().stream()
                    .filter(data -> data.getObjectType() == EObjectType.SOFT_BODY)
                    .count();

            XBullet.LOGGER.debug("Client counted {} rigid bodies and {} soft bodies.", rigidCount, softCount);

            NetworkHandler.CHANNEL.sendToServer(new ClientPhysicsObjectCountResponsePacket((int) rigidCount, (int) softCount));
            XBullet.LOGGER.debug("Client sent ClientPhysicsObjectCountResponsePacket with counts: rigid={}, soft={}", rigidCount, softCount);
        });
        ctx.get().setPacketHandled(true);
    }
}