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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Manages the technical registration, encoding, and transmission of network packets.
 * <p>
 * This implementation wraps standard packet objects (POJOs) into {@link CustomPacketPayload} instances
 * to comply with modern networking APIs. It uses {@link StreamCodec} for serialization.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class VxPacketHandler {

    private static final String CHANNEL_NAMESPACE = "velthoric";

    /**
     * An internal wrapper record that adapts a standard object into a {@link CustomPacketPayload}.
     * This is necessary to bridge simple packet classes with the payload-based network system.
     *
     * @param message The actual packet data object.
     * @param type    The payload type definition associated with this packet.
     * @param <T>     The type of the inner packet message.
     */
    private record PacketWrapper<T>(T message, CustomPacketPayload.Type<PacketWrapper<T>> type) implements CustomPacketPayload {
        @Override
        public Type<PacketWrapper<T>> type() {
            return type;
        }
    }

    /**
     * Internal record to store the payload type definition for a registered packet class.
     * Used to look up the type when creating a new payload for sending.
     *
     * @param type The {@link CustomPacketPayload.Type} associated with the packet wrapper.
     */
    private record PacketInfo<T>(CustomPacketPayload.Type<PacketWrapper<T>> type) {}

    /**
     * A map storing the registration info for every registered packet class.
     */
    private static final Map<Class<?>, PacketInfo<?>> PACKETS = new HashMap<>();

    /**
     * Registers a single packet type with the network manager using the Payload API.
     * <p>
     * This method automatically generates a {@link StreamCodec} that delegates to the provided
     * encoder and decoder functions, wrapping the result in a {@link PacketWrapper}.
     * It handles the registration of both the payload type and the receiver logic depending on the side.
     * </p>
     *
     * @param type    The class of the packet object.
     * @param name    The unique path name used to build the packet's {@link ResourceLocation}.
     * @param encoder The method to write the packet data into a buffer.
     * @param decoder The method to read the packet data from a buffer.
     * @param handler The method to execute when the packet is received.
     * @param side    The logical side (Client or Server) that is expected to <b>receive</b> this packet.
     * @param <T>     The type of the packet.
     */
    public static <T> void registerPacket(Class<T> type,
                                          String name,
                                          BiConsumer<T, FriendlyByteBuf> encoder,
                                          Function<FriendlyByteBuf, T> decoder,
                                          BiConsumer<T, Supplier<NetworkManager.PacketContext>> handler,
                                          NetworkManager.Side side) {
        
        ResourceLocation id = ResourceLocation.tryBuild(CHANNEL_NAMESPACE, name);
        CustomPacketPayload.Type<PacketWrapper<T>> payloadType = new CustomPacketPayload.Type<>(id);

        // Store the payload type for outgoing messages
        PACKETS.put(type, new PacketInfo<>(payloadType));

        // Create a StreamCodec that adapts the RegistryFriendlyByteBuf to our legacy encoder/decoder logic
        StreamCodec<RegistryFriendlyByteBuf, PacketWrapper<T>> codec = StreamCodec.of(
                (buf, wrapper) -> encoder.accept(wrapper.message(), buf),
                (buf) -> new PacketWrapper<>(decoder.apply(buf), payloadType)
        );

        // Create the receiver that unwraps the payload and calls the handler
        NetworkManager.NetworkReceiver<PacketWrapper<T>> receiver = (wrapper, context) -> {
            handler.accept(wrapper.message(), () -> context);
        };

        // Register based on side logic
        if (side == NetworkManager.Side.C2S) {
            // Client to Server: Always register the receiver on the server.
            NetworkManager.registerReceiver(side, payloadType, codec, receiver);
        } else {
            // Server to Client:
            if (Platform.getEnv() == EnvType.CLIENT) {
                // Clients register the full receiver to handle the packet.
                NetworkManager.registerReceiver(side, payloadType, codec, receiver);
            } else {
                // Dedicated servers must still register the Payload Type to be able to SEND the packet,
                // even if they never receive it.
                NetworkManager.registerS2CPayloadType(payloadType, codec);
            }
        }
    }

    /**
     * Retrieves the cached registration info for a given packet object.
     *
     * @param message The packet instance.
     * @param <MSG>   The type of the packet.
     * @return The {@link PacketInfo} containing the payload type.
     * @throws NullPointerException if the packet type has not been registered.
     */
    @SuppressWarnings("unchecked")
    private static <MSG> PacketInfo<MSG> getPacketInfo(MSG message) {
        PacketInfo<MSG> info = (PacketInfo<MSG>) PACKETS.get(message.getClass());
        return Objects.requireNonNull(info, "Unregistered packet type: " + message.getClass().getName());
    }

    /**
     * Sends a packet from the client to the server.
     *
     * @param message The packet object to send.
     * @param <MSG>   The type of the packet.
     */
    public static <MSG> void sendToServer(MSG message) {
        PacketInfo<MSG> info = getPacketInfo(message);
        // Wrap message in payload before sending
        NetworkManager.sendToServer(new PacketWrapper<>(message, info.type()));
    }

    /**
     * Sends a packet from the server to a specific player.
     *
     * @param message The packet object to send.
     * @param player  The target server player.
     * @param <MSG>   The type of the packet.
     */
    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        PacketInfo<MSG> info = getPacketInfo(message);
        NetworkManager.sendToPlayer(player, new PacketWrapper<>(message, info.type()));
    }

    /**
     * Sends a packet to all players currently in a specific dimension (Level).
     *
     * @param message      The packet object to send.
     * @param dimensionKey The resource key of the target dimension.
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
     * Sends a packet to all players currently connected to the server.
     *
     * @param message The packet object to send.
     * @param <MSG>   The type of the packet.
     */
    public static <MSG> void sendToAll(MSG message) {
        if (GameInstance.getServer() != null) {
            PacketInfo<MSG> info = getPacketInfo(message);
            NetworkManager.sendToPlayers(GameInstance.getServer().getPlayerList().getPlayers(), new PacketWrapper<>(message, info.type()));
        }
    }
}