package net.xmx.velthoric.network;

import dev.architectury.networking.NetworkChannel;
import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.GameInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.item.boxthrower.packet.BoxThrowerActionPacket;
import net.xmx.velthoric.item.magnetizer.packet.MagnetizerActionPacket;
import net.xmx.velthoric.item.physicsgun.packet.*;
import net.xmx.velthoric.physics.object.packet.batch.RemovePhysicsObjectBatchPacket;
import net.xmx.velthoric.physics.object.packet.batch.SpawnPhysicsObjectBatchPacket;
import net.xmx.velthoric.physics.object.packet.batch.SyncAllPhysicsObjectsPacket;
import net.xmx.velthoric.physics.object.packet.SyncPhysicsObjectDataPacket;
import net.xmx.velthoric.physics.raycasting.click.packet.VxClickPacket;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class NetworkHandler {

    public static final NetworkChannel CHANNEL = NetworkChannel.create(
            ResourceLocation.tryBuild("velthoric", "main_channel")
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
                PhysicsGunSyncPacket.class,
                PhysicsGunSyncPacket::encode,
                PhysicsGunSyncPacket::decode,
                PhysicsGunSyncPacket::handle
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
                VxClickPacket.class,
                VxClickPacket::encode,
                VxClickPacket::decode,
                VxClickPacket::handle
        );

        registerPacket(
                RemovePhysicsObjectBatchPacket.class,
                RemovePhysicsObjectBatchPacket::encode,
                RemovePhysicsObjectBatchPacket::new,
                RemovePhysicsObjectBatchPacket::handle
        );

        registerPacket(
                SpawnPhysicsObjectBatchPacket.class,
                SpawnPhysicsObjectBatchPacket::encode,
                SpawnPhysicsObjectBatchPacket::new,
                SpawnPhysicsObjectBatchPacket::handle
        );

        registerPacket(
                BoxThrowerActionPacket.class,
                BoxThrowerActionPacket::encode,
                BoxThrowerActionPacket::decode,
                BoxThrowerActionPacket::handle
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