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

    /**
     * The target update frequency of the dedicated network thread.
     * Lower values result in smoother movement but higher server CPU usage.
     */
    public final VxConfigValue<Integer> networkTickRate;

    /**
     * The maximum size in bytes for a single packet payload (specifically for spawns).
     * Used to split huge chunks into multiple spawn packets to avoid kicking clients
     * due to protocol buffer overflows (MTU/Netty limits).
     */
    public final VxConfigValue<Integer> maxPayloadSize;

    public VxNetworkConfig(VxConfigSpec.Builder builder) {
        this.networkTickRate = builder.defineInRange("network_thread_tick_rate_ms", 10, 1, 1000,
                "Target tick rate for the network sync thread in milliseconds.");

        this.maxPayloadSize = builder.define("max_packet_payload_bytes", 128 * 1024,
                "Max bytes per packet (approx 128KB) to prevent client disconnects during mass spawns.");
    }
}