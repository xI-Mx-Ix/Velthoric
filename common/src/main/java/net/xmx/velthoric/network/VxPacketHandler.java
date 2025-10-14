/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
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
import net.xmx.velthoric.item.chaincreator.packet.VxChainCreatorActionPacket;
import net.xmx.velthoric.item.magnetizer.packet.MagnetizerActionPacket;
import net.xmx.velthoric.item.physicsgun.packet.*;
import net.xmx.velthoric.physics.body.packet.batch.*;
import net.xmx.velthoric.physics.mounting.input.C2SMountInputPacket;
import net.xmx.velthoric.physics.mounting.request.C2SRequestMountPacket;
import net.xmx.velthoric.physics.vehicle.sync.S2CUpdateWheelsPacket;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class VxPacketHandler {

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
                VxChainCreatorActionPacket.class,
                VxChainCreatorActionPacket::encode,
                VxChainCreatorActionPacket::decode,
                VxChainCreatorActionPacket::handle
        );

        registerPacket(
                C2SRequestMountPacket.class,
                C2SRequestMountPacket::encode,
                C2SRequestMountPacket::new,
                C2SRequestMountPacket::handle
        );

        registerPacket(
                S2CSynchronizedDataBatchPacket.class,
                S2CSynchronizedDataBatchPacket::encode,
                S2CSynchronizedDataBatchPacket::new,
                S2CSynchronizedDataBatchPacket::handle
        );

        registerPacket(
                C2SMountInputPacket.class,
                C2SMountInputPacket::encode,
                C2SMountInputPacket::new,
                C2SMountInputPacket::handle
        );

        registerPacket(
                S2CUpdateWheelsPacket.class,
                S2CUpdateWheelsPacket::encode,
                S2CUpdateWheelsPacket::new,
                S2CUpdateWheelsPacket::handle
        );

        registerPacket(
                S2CSpawnBodyBatchPacket.class,
                S2CSpawnBodyBatchPacket::encode,
                S2CSpawnBodyBatchPacket::new,
                S2CSpawnBodyBatchPacket::handle
        );

        registerPacket(
                S2CRemoveBodyBatchPacket.class,
                S2CRemoveBodyBatchPacket::encode,
                S2CRemoveBodyBatchPacket::new,
                S2CRemoveBodyBatchPacket::handle
        );

        registerPacket(
                S2CUpdateBodyStateBatchPacket.class,
                S2CUpdateBodyStateBatchPacket::encode,
                S2CUpdateBodyStateBatchPacket::new,
                S2CUpdateBodyStateBatchPacket::handle
        );

        registerPacket(
                S2CUpdateVerticesBatchPacket.class,
                S2CUpdateVerticesBatchPacket::encode,
                S2CUpdateVerticesBatchPacket::new,
                S2CUpdateVerticesBatchPacket::handle
        );

        registerPacket(
                PhysicsGunSyncPacket.class,
                PhysicsGunSyncPacket::encode,
                PhysicsGunSyncPacket::decode,
                PhysicsGunSyncPacket::handle
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
