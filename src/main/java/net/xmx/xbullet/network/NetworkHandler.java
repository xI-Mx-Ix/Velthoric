package net.xmx.xbullet.network;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.command.xbullet.packet.ClientPhysicsObjectCountResponsePacket;
import net.xmx.xbullet.command.xbullet.packet.RequestClientPhysicsObjectCountPacket;
import net.xmx.xbullet.debug.drawer.packet.DebugRenderDataPacket;
import net.xmx.xbullet.item.physicsgun.packet.PhysicsGunActionPacket;
import net.xmx.xbullet.item.rope.packet.PacketCreateRope;
import net.xmx.xbullet.physics.object.global.click.PhysicsClickPacket;
import net.xmx.xbullet.physics.object.global.physicsobject.packet.*;
import net.xmx.xbullet.physics.object.riding.packet.DismountRequestPacket;
import net.xmx.xbullet.physics.object.riding.packet.MountPhysicsObjectPacket;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("xbullet", "main_channel"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private static <T> void registerPacket(Class<T> type,
                                           BiConsumer<T, FriendlyByteBuf> encoder,
                                           Function<FriendlyByteBuf, T> decoder,
                                           BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(packetId++, type, encoder, decoder, handler);
    }

    public static void register() {


        registerPacket(
                PacketCreateRope.class,
                PacketCreateRope::encode,
                PacketCreateRope::decode,
                PacketCreateRope::handle
        );

        registerPacket(
                DebugRenderDataPacket.class,
                DebugRenderDataPacket::encode,
                DebugRenderDataPacket::decode,
                DebugRenderDataPacket::handle
        );

        registerPacket(
                MountPhysicsObjectPacket.class,
                MountPhysicsObjectPacket::encode,
                MountPhysicsObjectPacket::decode,
                MountPhysicsObjectPacket::handle
        );

        registerPacket(
                SyncPhysicsObjectNbtPacket.class,
                SyncPhysicsObjectNbtPacket::encode,
                SyncPhysicsObjectNbtPacket::new,
                SyncPhysicsObjectNbtPacket::handle
        );

        registerPacket(
                DismountRequestPacket.class,
                DismountRequestPacket::encode,
                DismountRequestPacket::decode,
                DismountRequestPacket::handle
        );

        registerPacket(
                SyncAllPhysicsObjectsPacket.class,
                SyncAllPhysicsObjectsPacket::encode,
                SyncAllPhysicsObjectsPacket::new,
                SyncAllPhysicsObjectsPacket::handle
        );

        registerPacket(
                PhysicsGunActionPacket.class,
                PhysicsGunActionPacket::encode,
                PhysicsGunActionPacket::decode,
                PhysicsGunActionPacket::handle
        );

        registerPacket(
                PhysicsClickPacket.class,
                PhysicsClickPacket::encode,
                PhysicsClickPacket::decode,
                PhysicsClickPacket::handle
        );


        registerPacket(
                RequestClientPhysicsObjectCountPacket.class,
                RequestClientPhysicsObjectCountPacket::encode,
                RequestClientPhysicsObjectCountPacket::decode,
                RequestClientPhysicsObjectCountPacket::handle
        );

        registerPacket(
                RemovePhysicsObjectPacket.class,
                RemovePhysicsObjectPacket::encode,
                RemovePhysicsObjectPacket::new,
                RemovePhysicsObjectPacket::handle
        );

        registerPacket(SpawnPhysicsObjectPacket.class,
                SpawnPhysicsObjectPacket::encode,
                SpawnPhysicsObjectPacket::new,
                SpawnPhysicsObjectPacket::handle
        );


        registerPacket(
                ClientPhysicsObjectCountResponsePacket.class,
                ClientPhysicsObjectCountResponsePacket::write,
                ClientPhysicsObjectCountResponsePacket::read,
                ClientPhysicsObjectCountResponsePacket.Handler::handle
        );
    }

    public static <MSG> void sendToServer(MSG message) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static <MSG> void sendToDimension(MSG message, ResourceKey<Level> dimensionKey) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dimensionKey), message);
    }

    public static <MSG> void sendToAll(MSG msg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }
}