/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.architectury.utils.GameInstance;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Central networking controller for Velthoric.
 * <p>
 * This class serves as the core communication bridge between the mod and the underlying
 * networking API. Instead of registering dozens of separate
 * packet types, it manages a single "Raw Payload" channel.
 * </p>
 * <p>
 * <b>Architecture:</b>
 * <ul>
 *     <li>All packets are serialized into a single {@link VxRawPayload}.</li>
 *     <li>The first byte of the payload determines the Packet ID.</li>
 *     <li>The ID is resolved based on the receiving side (Env.SERVER = C2S, Env.CLIENT = S2C).</li>
 *     <li>The payload is wrapped in a {@link VxByteBuf} for convenient reading of custom types.</li>
 * </ul>
 *
 * @author xI-Mx-Ix
 */
public class VxNetworking {

    /**
     * Maps a byte ID to a specific packet decoder function for packets received by the Server (C2S).
     */
    private static final Byte2ObjectMap<Function<VxByteBuf, IVxNetPacket>> C2S_DECODERS = new Byte2ObjectOpenHashMap<>();

    /**
     * Maps a byte ID to a specific packet decoder function for packets received by the Client (S2C).
     */
    private static final Byte2ObjectMap<Function<VxByteBuf, IVxNetPacket>> S2C_DECODERS = new Byte2ObjectOpenHashMap<>();

    /**
     * Maps a packet class type to its assigned byte ID.
     * Used during sending to determine the header byte.
     */
    private static final Map<Class<? extends IVxNetPacket>, Byte> PACKET_TO_ID = new HashMap<>();

    /**
     * Initializes the networking system.
     * <p>
     * This method registers the single {@link VxRawPayload} type with the Architectury
     * NetworkManager. It must be called during the mod's initialization phase (common init).
     * </p>
     */
    public static void init() {
        // Register the raw payload receiver for the client side (S2C)
        if (Platform.getEnv() == EnvType.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.Side.S2C, VxRawPayload.TYPE, VxRawPayload.CODEC, VxNetworking::handlePacket);
        }

        // Register the raw payload receiver for the server side (C2S)
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, VxRawPayload.TYPE, VxRawPayload.CODEC, VxNetworking::handlePacket);

        // For dedicated servers (or general compliance), we must register the S2C payload type
        // so that the server knows it is capable of sending this payload type to clients.
        if (Platform.getEnv() == EnvType.SERVER) {
            NetworkManager.registerS2CPayloadType(VxRawPayload.TYPE, VxRawPayload.CODEC);
        }
    }

    /**
     * Registers a packet implementation with the network system.
     *
     * @param side    The network side that receives this packet.
     * @param id      The unique byte ID (0-255) for this packet on the given side.
     * @param clazz   The class of the packet.
     * @param decoder The function used to decode the packet from a {@link VxByteBuf}.
     * @param <T>     The type of the packet, must implement {@link IVxNetPacket}.
     * @throws IllegalArgumentException if the ID is already registered for the specified side.
     */
    @SuppressWarnings("unchecked")
    public static <T extends IVxNetPacket> void register(NetworkManager.Side side, int id, Class<T> clazz, Function<VxByteBuf, T> decoder) {
        byte byteId = (byte) id;
        Byte2ObjectMap<Function<VxByteBuf, IVxNetPacket>> targetMap = (side == NetworkManager.Side.C2S) ? C2S_DECODERS : S2C_DECODERS;

        if (targetMap.containsKey(byteId)) {
            throw new IllegalArgumentException("Duplicate packet ID registered for side " + side + ": " + id);
        }

        targetMap.put(byteId, (Function<VxByteBuf, IVxNetPacket>) decoder);
        PACKET_TO_ID.put(clazz, byteId);
    }

    /**
     * The central handler for all incoming raw payloads.
     * <p>
     * This method:
     * <ol>
     *     <li>Extracts the raw Netty buffer.</li>
     *     <li>Identifies the receiving map by checking the environment (Server = C2S, Client = S2C).</li>
     *     <li>Reads the first byte (Packet ID).</li>
     *     <li>Finds the corresponding decoder.</li>
     *     <li>Wraps the buffer in {@link VxByteBuf}.</li>
     *     <li>Decodes the packet and delegates execution to {@link IVxNetPacket#handle}.</li>
     * </ol>
     *
     * @param payload The raw payload container from Minecraft.
     * @param context The execution context (containing player, level, thread executor).
     */
    private static void handlePacket(VxRawPayload payload, NetworkManager.PacketContext context) {
        ByteBuf rawData = payload.data();
        try {
            // Ensure there is data to read
            if (!rawData.isReadable()) {
                return;
            }

            // 1. Identify Map by Environment
            // If environment is SERVER, we received a C2S packet.
            // If environment is CLIENT, we received an S2C packet.
            Byte2ObjectMap<Function<VxByteBuf, IVxNetPacket>> targetMap = (context.getEnvironment() == Env.SERVER) ? C2S_DECODERS : S2C_DECODERS;

            // 2. Read Packet ID
            byte id = rawData.readByte();

            // 3. Lookup Decoder
            Function<VxByteBuf, IVxNetPacket> decoder = targetMap.get(id);

            if (decoder == null) {
                return;
            }

            // 4. Wrap Buffer
            VxByteBuf buf = new VxByteBuf(rawData);

            // 5. Decode & Handle
            IVxNetPacket packet = decoder.apply(buf);
            packet.handle(context);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 6. Cleanup
            if (rawData.refCnt() > 0) {
                rawData.release();
            }
        }
    }

    /**
     * Internal helper to create a ByteBuf containing the packet ID and encoded data.
     *
     * @param packet The packet to serialize.
     * @return A new Netty ByteBuf containing [ID][Data].
     * @throws IllegalStateException if the packet class is not registered.
     */
    private static ByteBuf createBuffer(IVxNetPacket packet) {
        Byte id = PACKET_TO_ID.get(packet.getClass());
        if (id == null) {
            throw new IllegalStateException("Attempted to send unregistered packet: " + packet.getClass().getName());
        }

        // Allocate a buffer. Unpooled is fine here; Netty/MC handles the rest.
        ByteBuf buffer = Unpooled.buffer();

        // Write ID
        buffer.writeByte(id);

        // Wrap and Encode
        VxByteBuf vxBuf = new VxByteBuf(buffer);
        packet.encode(vxBuf);

        return buffer;
    }

    // ============================================================================================
    // Sending Methods
    // ============================================================================================

    /**
     * Sends a packet from the Client to the Server.
     *
     * @param packet The packet to send.
     */
    public static void sendToServer(IVxNetPacket packet) {
        ByteBuf buf = createBuffer(packet);
        if (NetworkManager.canServerReceive(VxRawPayload.TYPE)) {
            NetworkManager.sendToServer(new VxRawPayload(buf));
        } else {
            // Prevent memory leaks if the packet cannot be sent
            buf.release();
        }
    }

    /**
     * Sends a packet from the Server to a specific Player.
     *
     * @param player The target player.
     * @param packet The packet to send.
     */
    public static void sendToPlayer(ServerPlayer player, IVxNetPacket packet) {
        ByteBuf buf = createBuffer(packet);
        if (NetworkManager.canPlayerReceive(player, VxRawPayload.TYPE)) {
            NetworkManager.sendToPlayer(player, new VxRawPayload(buf));
        } else {
            buf.release();
        }
    }

    /**
     * Sends a packet from the Server to all connected players.
     *
     * @param packet The packet to send.
     */
    public static void sendToAll(IVxNetPacket packet) {
        if (GameInstance.getServer() == null) return;

        ByteBuf buf = createBuffer(packet);
        // Note: Architectury's sendToPlayers usually handles retained duplicates internally,
        // but since we wrap a raw buffer in a record, the record creation is cheap.
        NetworkManager.sendToPlayers(
                GameInstance.getServer().getPlayerList().getPlayers(),
                new VxRawPayload(buf)
        );
    }

    /**
     * Sends a packet to all players in a specific dimension (Level).
     *
     * @param dimension The resource key of the target dimension.
     * @param packet    The packet to send.
     */
    public static void sendToDimension(ResourceKey<Level> dimension, IVxNetPacket packet) {
        if (GameInstance.getServer() == null) return;

        ServerLevel level = GameInstance.getServer().getLevel(dimension);
        if (level != null) {
            ByteBuf buf = createBuffer(packet);
            NetworkManager.sendToPlayers(level.players(), new VxRawPayload(buf));
        }
    }
}