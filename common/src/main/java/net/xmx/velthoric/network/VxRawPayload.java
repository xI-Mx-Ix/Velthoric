/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A raw container payload that transports direct Netty ByteBufs through the Minecraft networking system.
 * <p>
 * This bypasses the object-heavy serialization of standard payloads by treating the
 * entire packet content as a single opaque blob of bytes.
 * </p>
 *
 * @param data The raw Netty buffer containing the packet ID and payload.
 * @author xI-Mx-Ix
 */
public record VxRawPayload(ByteBuf data) implements CustomPacketPayload {

    public static final Type<VxRawPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("velthoric", "network"));

    /**
     * A zero-copy StreamCodec that slices the buffer instead of copying it.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, VxRawPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                // Write the raw Netty buffer directly into the output
                buf.writeBytes(payload.data);
                // Release the buffer as ownership is transferred to the network stack
                payload.data.release();
            },
            (buf) -> {
                // Create a slice of the remaining readable bytes.
                // This does NOT copy the memory, making it extremely efficient.
                int readable = buf.readableBytes();
                return new VxRawPayload(buf.readBytes(readable));
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}