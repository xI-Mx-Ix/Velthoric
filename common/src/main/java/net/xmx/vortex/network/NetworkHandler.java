package net.xmx.vortex.network;

import dev.architectury.networking.NetworkChannel;
import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.GameInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.xmx.vortex.debug.drawer.packet.DebugShapesUpdatePacket;
import net.xmx.vortex.item.magnetizer.packet.MagnetizerActionPacket;
import net.xmx.vortex.item.physicsgun.packet.PhysicsGunActionPacket;
import net.xmx.vortex.item.physicsgun.packet.PhysicsGunStatePacket;
import net.xmx.vortex.item.physicsgun.packet.SyncAllPhysicsGunGrabsPacket;
import net.xmx.vortex.physics.object.physicsobject.packet.RemovePhysicsObjectPacket;
import net.xmx.vortex.physics.object.physicsobject.packet.SpawnPhysicsObjectPacket;
import net.xmx.vortex.physics.object.physicsobject.packet.SyncAllPhysicsObjectsPacket;
import net.xmx.vortex.physics.object.physicsobject.packet.SyncPhysicsObjectDataPacket;
import net.xmx.vortex.physics.object.raycast.packet.PhysicsClickPacket;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class NetworkHandler {

    public static final NetworkChannel CHANNEL = NetworkChannel.create(
            ResourceLocation.tryBuild("vortex", "main_channel")
    );

    private static <T> void registerPacket(Class<T> type,
                                           BiConsumer<T, FriendlyByteBuf> encoder,
                                           Function<FriendlyByteBuf, T> decoder,
                                           BiConsumer<T, Supplier<NetworkManager.PacketContext>> handler) {
        CHANNEL.register(type, encoder, decoder, handler);
    }

    public static void register() {
        registerPacket(
                SyncPhysicsObjectDataPacket.class,
                SyncPhysicsObjectDataPacket::encode,
                SyncPhysicsObjectDataPacket::new,
                SyncPhysicsObjectDataPacket::handle
        );

        registerPacket(
                PhysicsGunStatePacket.class,
                PhysicsGunStatePacket::encode,
                PhysicsGunStatePacket::decode,
                PhysicsGunStatePacket::handle
        );

        registerPacket(
                SyncAllPhysicsGunGrabsPacket.class,
                SyncAllPhysicsGunGrabsPacket::encode,
                SyncAllPhysicsGunGrabsPacket::decode,
                SyncAllPhysicsGunGrabsPacket::handle
        );

        registerPacket(
                DebugShapesUpdatePacket.class,
                DebugShapesUpdatePacket::encode,
                DebugShapesUpdatePacket::new,
                DebugShapesUpdatePacket::handle
        );

        registerPacket(
                SyncAllPhysicsObjectsPacket.class,
                SyncAllPhysicsObjectsPacket::encode,
                SyncAllPhysicsObjectsPacket::new,
                SyncAllPhysicsObjectsPacket::handle
        );

        registerPacket(
                MagnetizerActionPacket.class,
                MagnetizerActionPacket::encode,
                MagnetizerActionPacket::decode,
                MagnetizerActionPacket::handle
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
                RemovePhysicsObjectPacket.class,
                RemovePhysicsObjectPacket::encode,
                RemovePhysicsObjectPacket::new,
                RemovePhysicsObjectPacket::handle
        );

        registerPacket(
                SpawnPhysicsObjectPacket.class,
                SpawnPhysicsObjectPacket::encode,
                SpawnPhysicsObjectPacket::new,
                SpawnPhysicsObjectPacket::handle
        );
    }

    public static <MSG> void sendToServer(MSG message) {
        CHANNEL.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        CHANNEL.sendToPlayer(player, message);
    }

    public static <MSG> void sendToDimension(MSG message, ResourceKey<Level> dimensionKey) {
        if (GameInstance.getServer() != null) {
            ServerLevel level = GameInstance.getServer().getLevel(dimensionKey);
            if (level != null) {
                CHANNEL.sendToPlayers(level.players(), message);
            }
        }
    }

    public static <MSG> void sendToAll(MSG msg) {
        if (GameInstance.getServer() != null) {
            CHANNEL.sendToPlayers(GameInstance.getServer().getPlayerList().getPlayers(), msg);
        }
    }
}