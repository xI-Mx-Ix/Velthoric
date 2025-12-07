/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.GameInstance;
import net.fabricmc.api.EnvType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.item.physicsgun.packet.VxPhysicsGunActionPacket;
import net.xmx.velthoric.item.physicsgun.packet.VxPhysicsGunSyncPacket;
import net.xmx.velthoric.item.tool.packet.VxToolActionPacket;
import net.xmx.velthoric.item.tool.packet.VxToolConfigPacket;
import net.xmx.velthoric.physics.body.packet.batch.*;
import net.xmx.velthoric.physics.mounting.input.C2SMountInputPacket;
import net.xmx.velthoric.physics.mounting.request.C2SRequestMountPacket;
import net.xmx.velthoric.physics.vehicle.sync.S2CVehicleDataBatchPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Handles the registration and sending of all network packets for the mod.
 * This class bridges existing packet logic with the modern CustomPacketPayload API
 * and Architectury's NetworkManager.
 *
 * @author xI-Mx-Ix
 */
public class VxPacketHandler {

    private static final String CHANNEL_NAMESPACE = "velthoric";

    /**
     * Internal wrapper to adapt existing POJO packets to the CustomPacketPayload API.
     *
     * @param <T> The type of the inner packet message.
     */
    private record PacketWrapper<T>(T message, CustomPacketPayload.Type<PacketWrapper<T>> type) implements CustomPacketPayload {
        @Override
        public Type<PacketWrapper<T>> type() {
            return type;
        }
    }

    /**
     * Internal record to store configuration about a registered packet type.
     */
    private record PacketInfo<T>(CustomPacketPayload.Type<PacketWrapper<T>> type) {}

    private static final Map<Class<?>, PacketInfo<?>> PACKETS = new HashMap<>();

    /**
     * Registers a packet type with the network manager for a specific direction.
     * Automatically creates a StreamCodec and PacketPayloadWrapper to bridge logic to the new API.
     *
     * @param type    The class of the packet.
     * @param name    The unique name for the packet, used as the path in its ResourceLocation.
     * @param encoder The method to encode the packet into a byte buffer.
     * @param decoder The method to decode the packet from a byte buffer.
     * @param handler The method to handle the received packet.
     * @param side    The network side that receives this packet (e.g., C2S for actions, S2C for syncs).
     * @param <T>     The type of the packet.
     */
    private static <T> void registerPacket(Class<T> type,
                                           String name,
                                           BiConsumer<T, FriendlyByteBuf> encoder,
                                           Function<FriendlyByteBuf, T> decoder,
                                           BiConsumer<T, Supplier<NetworkManager.PacketContext>> handler,
                                           NetworkManager.Side side) {
        ResourceLocation id = ResourceLocation.tryBuild(CHANNEL_NAMESPACE, name);
        CustomPacketPayload.Type<PacketWrapper<T>> payloadType = new CustomPacketPayload.Type<>(id);

        // Store the packet info for looking up the Type later when sending
        PACKETS.put(type, new PacketInfo<>(payloadType));

        // Create a codec that delegates to the provided legacy encoder/decoder.
        StreamCodec<RegistryFriendlyByteBuf, PacketWrapper<T>> codec = StreamCodec.of(
                (buf, wrapper) -> encoder.accept(wrapper.message(), buf),
                (buf) -> new PacketWrapper<>(decoder.apply(buf), payloadType)
        );

        // Create a receiver that unwraps the payload and calls the legacy handler.
        NetworkManager.NetworkReceiver<PacketWrapper<T>> receiver = (wrapper, context) -> {
            handler.accept(wrapper.message(), () -> context);
        };

        // Logic to safely register packets across NeoForge and Fabric (Server & Client).
        if (side == NetworkManager.Side.C2S) {
            // Client -> Server packets are received on the server.
            // This is safe to register everywhere.
            NetworkManager.registerReceiver(side, payloadType, codec, receiver);
        } else {
            // Server -> Client packets (S2C).
            if (Platform.getEnv() == EnvType.CLIENT) {
                // The Client receives these packets, so we register the full receiver.
                NetworkManager.registerReceiver(side, payloadType, codec, receiver);
            } else {
                // The Server SENDS these packets.
                // It does NOT receive them, so we cannot register a receiver (causes crash on Fabric).
                // HOWEVER, we MUST register the Payload Type, otherwise the server won't have the Codec to encode the packet.
                NetworkManager.registerS2CPayloadType(payloadType, codec);
            }
        }
    }

    /**
     * Registers all packets used by the mod.
     * This method must be called during mod initialization.
     */
    public static void register() {
        // --- Client to Server Packets ---

        registerPacket(
                C2SRequestMountPacket.class,
                "request_mount",
                C2SRequestMountPacket::encode,
                C2SRequestMountPacket::decode,
                C2SRequestMountPacket::handle,
                NetworkManager.Side.C2S
        );

        registerPacket(
                C2SMountInputPacket.class,
                "mount_input",
                C2SMountInputPacket::encode,
                C2SMountInputPacket::decode,
                C2SMountInputPacket::handle,
                NetworkManager.Side.C2S
        );

        registerPacket(
                VxPhysicsGunActionPacket.class,
                "physics_gun_action",
                VxPhysicsGunActionPacket::encode,
                VxPhysicsGunActionPacket::decode,
                VxPhysicsGunActionPacket::handle,
                NetworkManager.Side.C2S
        );

        registerPacket(
                VxToolActionPacket.class,
                "tool_action",
                VxToolActionPacket::encode,
                VxToolActionPacket::decode,
                VxToolActionPacket::handle,
                NetworkManager.Side.C2S
        );

        registerPacket(
                VxToolConfigPacket.class,
                "tool_config",
                VxToolConfigPacket::encode,
                VxToolConfigPacket::decode,
                VxToolConfigPacket::handle,
                NetworkManager.Side.C2S
        );

        // --- Server to Client Packets ---

        registerPacket(
                S2CSynchronizedDataBatchPacket.class,
                "sync_data_batch",
                S2CSynchronizedDataBatchPacket::encode,
                S2CSynchronizedDataBatchPacket::decode,
                S2CSynchronizedDataBatchPacket::handle,
                NetworkManager.Side.S2C
        );

        registerPacket(
                S2CVehicleDataBatchPacket.class,
                "vehicle_state",
                S2CVehicleDataBatchPacket::encode,
                S2CVehicleDataBatchPacket::decode,
                S2CVehicleDataBatchPacket::handle,
                NetworkManager.Side.S2C
        );

        registerPacket(
                S2CSpawnBodyBatchPacket.class,
                "spawn_body_batch",
                S2CSpawnBodyBatchPacket::encode,
                S2CSpawnBodyBatchPacket::decode,
                S2CSpawnBodyBatchPacket::handle,
                NetworkManager.Side.S2C
        );

        registerPacket(
                S2CRemoveBodyBatchPacket.class,
                "remove_body_batch",
                S2CRemoveBodyBatchPacket::encode,
                S2CRemoveBodyBatchPacket::decode,
                S2CRemoveBodyBatchPacket::handle,
                NetworkManager.Side.S2C
        );

        registerPacket(
                S2CUpdateBodyStateBatchPacket.class,
                "update_body_state_batch",
                S2CUpdateBodyStateBatchPacket::encode,
                S2CUpdateBodyStateBatchPacket::decode,
                S2CUpdateBodyStateBatchPacket::handle,
                NetworkManager.Side.S2C
        );

        registerPacket(
                S2CUpdateVerticesBatchPacket.class,
                "update_vertices_batch",
                S2CUpdateVerticesBatchPacket::encode,
                S2CUpdateVerticesBatchPacket::decode,
                S2CUpdateVerticesBatchPacket::handle,
                NetworkManager.Side.S2C
        );

        registerPacket(
                VxPhysicsGunSyncPacket.class,
                "physics_gun_sync",
                VxPhysicsGunSyncPacket::encode,
                VxPhysicsGunSyncPacket::decode,
                VxPhysicsGunSyncPacket::handle,
                NetworkManager.Side.S2C
        );
    }

    /**
     * Retrieves the stored PacketInfo for a given message object.
     *
     * @param message The packet message instance.
     * @return The PacketInfo associated with the message's class.
     * @throws NullPointerException if the packet has not been registered.
     */
    @SuppressWarnings("unchecked")
    private static <MSG> PacketInfo<MSG> getPacketInfo(MSG message) {
        PacketInfo<MSG> info = (PacketInfo<MSG>) PACKETS.get(message.getClass());
        return Objects.requireNonNull(info, "Unregistered packet type: " + message.getClass().getName());
    }

    /**
     * Sends a packet from the client to the server.
     *
     * @param message The packet to send.
     * @param <MSG>   The type of the packet.
     */
    public static <MSG> void sendToServer(MSG message) {
        PacketInfo<MSG> info = getPacketInfo(message);
        // Wrap the message into the Payload wrapper and send it via Architectury
        NetworkManager.sendToServer(new PacketWrapper<>(message, info.type()));
    }

    /**
     * Sends a packet from the server to a specific player.
     *
     * @param message The packet to send.
     * @param player  The player to send the packet to.
     * @param <MSG>   The type of the packet.
     */
    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        PacketInfo<MSG> info = getPacketInfo(message);
        NetworkManager.sendToPlayer(player, new PacketWrapper<>(message, info.type()));
    }

    /**
     * Sends a packet to all players in a specific dimension.
     *
     * @param message      The packet to send.
     * @param dimensionKey The key of the dimension to send the packet to.
     * @param <MSG>        The type of the packet.
     */
    public static <MSG> void sendToDimension(MSG message, ResourceKey<Level> dimensionKey) {
        if (GameInstance.getServer() != null) {
            ServerLevel level = GameInstance.getServer().getLevel(dimensionKey);
            if (level != null) {
                PacketInfo<MSG> info = getPacketInfo(message);
                NetworkManager.sendToPlayers(level.players(), new PacketWrapper<>(message, info.type()));
            }
        }
    }

    /**
     * Sends a packet to all players currently on the server.
     *
     * @param message The packet to send.
     * @param <MSG>   The type of the packet.
     */
    public static <MSG> void sendToAll(MSG message) {
        if (GameInstance.getServer() != null) {
            PacketInfo<MSG> info = getPacketInfo(message);
            NetworkManager.sendToPlayers(GameInstance.getServer().getPlayerList().getPlayers(), new PacketWrapper<>(message, info.type()));
        }
    }
}