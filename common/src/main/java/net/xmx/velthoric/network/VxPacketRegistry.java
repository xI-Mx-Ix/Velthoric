/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.network;

import dev.architectury.networking.NetworkManager;
import net.xmx.velthoric.core.mounting.input.C2SMountInputPacket;
import net.xmx.velthoric.item.physicsgun.packet.VxPhysicsGunActionPacket;
import net.xmx.velthoric.item.physicsgun.packet.VxPhysicsGunSyncPacket;
import net.xmx.velthoric.item.tool.packet.VxToolActionPacket;
import net.xmx.velthoric.item.tool.packet.VxToolConfigPacket;
import net.xmx.velthoric.core.network.internal.packet.S2CRemoveBodyBatchPacket;
import net.xmx.velthoric.core.network.internal.packet.S2CSpawnBodyBatchPacket;
import net.xmx.velthoric.core.network.internal.packet.S2CUpdateBodyStateBatchPacket;
import net.xmx.velthoric.core.network.internal.packet.S2CUpdateVerticesBatchPacket;
import net.xmx.velthoric.core.network.synchronization.packet.C2SSynchronizedDataBatchPacket;
import net.xmx.velthoric.core.network.synchronization.packet.S2CSynchronizedDataBatchPacket;
import net.xmx.velthoric.core.vehicle.part.packet.C2SPartInteractPacket;

import java.util.function.Function;

/**
 * The central registry that lists all network packets used by Velthoric.
 * <p>
 * This class is responsible for initializing the networking subsystem and
 * assigning unique byte IDs to each packet class per network side.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class VxPacketRegistry {

    private static int c2sId = 0;
    private static int s2cId = 0;

    /**
     * Initializes the {@link VxNetworking} system and registers all packet types.
     * <p>
     * This method iterates through all available packet classes and registers them
     * with a side-specific unique ID and their decoder reference.
     * </p>
     * <p>
     * <b>Note:</b> This method must be called during the common initialization phase
     * of the mod (e.g., inside <code>onInitialize</code> or the common setup event).
     * </p>
     */
    public static void registerPackets() {
        // 1. Initialize the base networking channel (The "Tunnel")
        VxNetworking.init();

        // ---------------------------------------------------------
        // Client -> Server Packets (C2S)
        // IDs are managed independently for the C2S namespace.
        // ---------------------------------------------------------

        registerC2S(C2SPartInteractPacket.class, C2SPartInteractPacket::decode);
        registerC2S(C2SMountInputPacket.class, C2SMountInputPacket::decode);
        registerC2S(VxPhysicsGunActionPacket.class, VxPhysicsGunActionPacket::decode);
        registerC2S(VxToolActionPacket.class, VxToolActionPacket::decode);
        registerC2S(VxToolConfigPacket.class, VxToolConfigPacket::decode);
        registerC2S(C2SSynchronizedDataBatchPacket.class, C2SSynchronizedDataBatchPacket::decode);

        // ---------------------------------------------------------
        // Server -> Client Packets (S2C)
        // IDs are managed independently for the S2C namespace.
        // ---------------------------------------------------------

        registerS2C(S2CSynchronizedDataBatchPacket.class, S2CSynchronizedDataBatchPacket::decode);
        registerS2C(S2CSpawnBodyBatchPacket.class, S2CSpawnBodyBatchPacket::decode);
        registerS2C(S2CRemoveBodyBatchPacket.class, S2CRemoveBodyBatchPacket::decode);
        registerS2C(S2CUpdateBodyStateBatchPacket.class, S2CUpdateBodyStateBatchPacket::decode);
        registerS2C(S2CUpdateVerticesBatchPacket.class, S2CUpdateVerticesBatchPacket::decode);
        registerS2C(VxPhysicsGunSyncPacket.class, VxPhysicsGunSyncPacket::decode);
    }

    /**
     * Internal helper to register a Client-to-Server packet with an automated ID.
     */
    private static <T extends IVxNetPacket> void registerC2S(Class<T> clazz, Function<VxByteBuf, T> decoder) {
        VxNetworking.register(NetworkManager.Side.C2S, c2sId++, clazz, decoder);
    }

    /**
     * Internal helper to register a Server-to-Client packet with an automated ID.
     */
    private static <T extends IVxNetPacket> void registerS2C(Class<T> clazz, Function<VxByteBuf, T> decoder) {
        VxNetworking.register(NetworkManager.Side.S2C, s2cId++, clazz, decoder);
    }
}