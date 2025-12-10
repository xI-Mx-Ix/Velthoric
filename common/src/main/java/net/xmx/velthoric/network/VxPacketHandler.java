/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.architectury.utils.GameInstance;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
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
 * This implementation utilizes standard {@link FriendlyByteBuf} for direct packet encoding
 * and decoding, mapping packet classes to specific {@link ResourceLocation} channels.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class VxPacketHandler {

    private static final String CHANNEL_NAMESPACE = "velthoric";

    /**
     * Internal record to hold the registration details of a specific packet type.
     *
     * @param id      The unique network identifier for the packet.
     * @param encoder The function used to serialize the packet into a buffer.
     * @param <T>     The type of the packet object.
     */
    private record PacketInfo<T>(ResourceLocation id, BiConsumer<T, FriendlyByteBuf> encoder) {}

    /**
     * A map storing the registration info for every registered packet class.
     * Used to look up the channel ID and encoder when sending a packet.
     */
    private static final Map<Class<?>, PacketInfo<?>> PACKETS = new HashMap<>();

    /**
     * Registers a single packet type with the network manager.
     * <p>
     * This method creates a network receiver that decodes incoming data and delegates it to the handler.
     * It automatically determines whether to register the receiver on the client or server side
     * based on the provided {@code side} parameter.
     * </p>
     *
     * @param type    The class of the packet object.
     * @param name    The unique path name used to build the packet's {@link ResourceLocation}.
     * @param encoder The method to write the packet data into a {@link FriendlyByteBuf}.
     * @param decoder The method to read the packet data from a {@link FriendlyByteBuf}.
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
        
        ResourceLocation packetId = new ResourceLocation(CHANNEL_NAMESPACE, name);
        
        // Cache the packet information for outgoing messages
        PACKETS.put(type, new PacketInfo<>(packetId, encoder));

        // Create the receiver logic which connects the decoder to the handler
        NetworkManager.NetworkReceiver receiver = (buf, context) -> {
            T packet = decoder.apply(buf);
            handler.accept(packet, () -> context);
        };

        // Register the receiver on the appropriate logical side
        if (side == NetworkManager.Side.C2S) {
            // C2S: Packets sent by client, received by server.
            NetworkManager.registerReceiver(NetworkManager.c2s(), packetId, receiver);
        } else {
            // S2C: Packets sent by server, received by client.
            // Only register the receiver if we are physically on a client environment.
            if (Platform.getEnvironment() == Env.CLIENT) {
                NetworkManager.registerReceiver(NetworkManager.s2c(), packetId, receiver);
            }
        }
    }

    /**
     * Retrieves the cached registration info for a given packet object.
     *
     * @param message The packet instance.
     * @param <MSG>   The type of the packet.
     * @return The {@link PacketInfo} containing ID and encoder.
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
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        info.encoder.accept(message, buf);
        NetworkManager.sendToServer(info.id, buf);
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
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        info.encoder.accept(message, buf);
        NetworkManager.sendToPlayer(player, info.id, buf);
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
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                info.encoder.accept(message, buf);
                NetworkManager.sendToPlayers(level.players(), info.id, buf);
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
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            info.encoder.accept(message, buf);
            NetworkManager.sendToPlayers(GameInstance.getServer().getPlayerList().getPlayers(), info.id, buf);
        }
    }
}