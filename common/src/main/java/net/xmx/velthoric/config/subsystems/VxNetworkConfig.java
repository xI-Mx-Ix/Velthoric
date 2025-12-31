/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.config.subsystems;

import net.xmx.velthoric.config.VxConfigSpec;
import net.xmx.velthoric.config.VxConfigValue;

/**
 * Configuration container for Network Synchronization.
 *
 * @author xI-Mx-Ix
 */
public class VxNetworkConfig {

    public final VxConfigValue<Integer> networkTickRate;
    public final VxConfigValue<Integer> maxStatesPerPacket;
    public final VxConfigValue<Integer> maxVerticesPerPacket;
    public final VxConfigValue<Integer> maxPayloadSize;
    public final VxConfigValue<Integer> maxRemovalsPerPacket;

    public VxNetworkConfig(VxConfigSpec.Builder builder) {
        this.networkTickRate = builder.defineInRange("network_thread_tick_rate_ms", 10, 1, 1000, "Target tick rate for the network sync thread in milliseconds.");
        this.maxStatesPerPacket = builder.defineInRange("max_states_per_packet", 50, 1, 1000, "Max number of body states packed into one update packet.");
        this.maxVerticesPerPacket = builder.defineInRange("max_vertices_per_packet", 50, 1, 1000, "Max number of vertex updates packed into one packet.");
        this.maxPayloadSize = builder.define("max_packet_payload_bytes", 128 * 1024, "Max bytes per packet to prevent disconnects.");
        this.maxRemovalsPerPacket = builder.define("max_removals_per_packet", 512, "Max body removals per packet.");
    }
}