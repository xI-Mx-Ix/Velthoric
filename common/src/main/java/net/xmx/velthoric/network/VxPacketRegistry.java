/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.network;

import dev.architectury.networking.NetworkManager;
import net.xmx.velthoric.item.physicsgun.packet.VxPhysicsGunActionPacket;
import net.xmx.velthoric.item.physicsgun.packet.VxPhysicsGunSyncPacket;
import net.xmx.velthoric.item.tool.packet.VxToolActionPacket;
import net.xmx.velthoric.item.tool.packet.VxToolConfigPacket;
import net.xmx.velthoric.physics.body.network.internal.packet.S2CRemoveBodyBatchPacket;
import net.xmx.velthoric.physics.body.network.internal.packet.S2CSpawnBodyBatchPacket;
import net.xmx.velthoric.physics.body.network.internal.packet.S2CUpdateBodyStateBatchPacket;
import net.xmx.velthoric.physics.body.network.internal.packet.S2CUpdateVerticesBatchPacket;
import net.xmx.velthoric.physics.body.network.synchronization.packet.C2SSynchronizedDataBatchPacket;
import net.xmx.velthoric.physics.body.network.synchronization.packet.S2CSynchronizedDataBatchPacket;
import net.xmx.velthoric.bridge.mounting.input.C2SMountInputPacket;
import net.xmx.velthoric.physics.vehicle.part.packet.C2SPartInteractPacket;

/**
 * Central registry that lists all network packets used by the application.
 * <p>
 * This class serves as the single source of truth for packet definitions. It delegates
 * the technical registration process to the {@link VxPacketHandler}, ensuring that
 * the list of packets remains consistent regardless of the underlying network implementation.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class VxPacketRegistry {

    /**
     * Iterates through all available packet classes and passes them to the packet handler for registration.
     * <p>
     * This method defines the essential metadata for each packet:
     * <ul>
     *     <li>The packet class type.</li>
     *     <li>The unique network channel name.</li>
     *     <li>The encoder and decoder functions.</li>
     *     <li>The execution handler.</li>
     *     <li>The intended network direction (Client-to-Server or Server-to-Client).</li>
     * </ul>
     * This method must be called during the initialization phase of the mod.
     */
    public static void registerPackets() {
        // --- Client to Server Packets (C2S) ---
        // These packets are sent by the client and handled on the server.

        VxPacketHandler.registerPacket(
                C2SPartInteractPacket.class,
                "part_interact",
                C2SPartInteractPacket::encode,
                C2SPartInteractPacket::decode,
                C2SPartInteractPacket::handle,
                NetworkManager.Side.C2S
        );

        VxPacketHandler.registerPacket(
                C2SMountInputPacket.class,
                "mount_input",
                C2SMountInputPacket::encode,
                C2SMountInputPacket::decode,
                C2SMountInputPacket::handle,
                NetworkManager.Side.C2S
        );

        VxPacketHandler.registerPacket(
                VxPhysicsGunActionPacket.class,
                "physics_gun_action",
                VxPhysicsGunActionPacket::encode,
                VxPhysicsGunActionPacket::decode,
                VxPhysicsGunActionPacket::handle,
                NetworkManager.Side.C2S
        );

        VxPacketHandler.registerPacket(
                VxToolActionPacket.class,
                "tool_action",
                VxToolActionPacket::encode,
                VxToolActionPacket::decode,
                VxToolActionPacket::handle,
                NetworkManager.Side.C2S
        );

        VxPacketHandler.registerPacket(
                VxToolConfigPacket.class,
                "tool_config",
                VxToolConfigPacket::encode,
                VxToolConfigPacket::decode,
                VxToolConfigPacket::handle,
                NetworkManager.Side.C2S
        );

        VxPacketHandler.registerPacket(
                C2SSynchronizedDataBatchPacket.class,
                "sync_data_batch_c2s",
                C2SSynchronizedDataBatchPacket::encode,
                C2SSynchronizedDataBatchPacket::decode,
                C2SSynchronizedDataBatchPacket::handle,
                NetworkManager.Side.C2S
        );

        // --- Server to Client Packets (S2C) ---
        // These packets are sent by the server and handled on the client.

        VxPacketHandler.registerPacket(
                S2CSynchronizedDataBatchPacket.class,
                "sync_data_batch_s2c",
                S2CSynchronizedDataBatchPacket::encode,
                S2CSynchronizedDataBatchPacket::decode,
                S2CSynchronizedDataBatchPacket::handle,
                NetworkManager.Side.S2C
        );

        VxPacketHandler.registerPacket(
                S2CSpawnBodyBatchPacket.class,
                "spawn_body_batch",
                S2CSpawnBodyBatchPacket::encode,
                S2CSpawnBodyBatchPacket::decode,
                S2CSpawnBodyBatchPacket::handle,
                NetworkManager.Side.S2C
        );

        VxPacketHandler.registerPacket(
                S2CRemoveBodyBatchPacket.class,
                "remove_body_batch",
                S2CRemoveBodyBatchPacket::encode,
                S2CRemoveBodyBatchPacket::decode,
                S2CRemoveBodyBatchPacket::handle,
                NetworkManager.Side.S2C
        );

        VxPacketHandler.registerPacket(
                S2CUpdateBodyStateBatchPacket.class,
                "update_body_state_batch",
                S2CUpdateBodyStateBatchPacket::encode,
                S2CUpdateBodyStateBatchPacket::decode,
                S2CUpdateBodyStateBatchPacket::handle,
                NetworkManager.Side.S2C
        );

        VxPacketHandler.registerPacket(
                S2CUpdateVerticesBatchPacket.class,
                "update_vertices_batch",
                S2CUpdateVerticesBatchPacket::encode,
                S2CUpdateVerticesBatchPacket::decode,
                S2CUpdateVerticesBatchPacket::handle,
                NetworkManager.Side.S2C
        );

        VxPacketHandler.registerPacket(
                VxPhysicsGunSyncPacket.class,
                "physics_gun_sync",
                VxPhysicsGunSyncPacket::encode,
                VxPhysicsGunSyncPacket::decode,
                VxPhysicsGunSyncPacket::handle,
                NetworkManager.Side.S2C
        );
    }
}