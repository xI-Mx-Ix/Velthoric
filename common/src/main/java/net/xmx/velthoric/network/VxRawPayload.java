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
 * @param identifier The specific payload type identifier (C2S or S2C).
 * @author xI-Mx-Ix
 */
public record VxRawPayload(ByteBuf data, Type<VxRawPayload> identifier) implements CustomPacketPayload {

    public static final Type<VxRawPayload> TYPE_C2S = new Type<>(ResourceLocation.fromNamespaceAndPath("velthoric", "c2s"));
    public static final Type<VxRawPayload> TYPE_S2C = new Type<>(ResourceLocation.fromNamespaceAndPath("velthoric", "s2c"));

    /**
     * A zero-copy StreamCodec that slices the buffer instead of copying it.
     * Specific for Client-to-Server communication.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, VxRawPayload> CODEC_C2S = createCodec(TYPE_C2S);

    /**
     * A zero-copy StreamCodec that slices the buffer instead of copying it.
     * Specific for Server-to-Client communication.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, VxRawPayload> CODEC_S2C = createCodec(TYPE_S2C);

    /**
     * Internal helper to create a codec for a specific payload type.
     *
     * @param type The payload type to be assigned to decoded packets.
     * @return A stream codec for VxRawPayload.
     */
    private static StreamCodec<RegistryFriendlyByteBuf, VxRawPayload> createCodec(Type<VxRawPayload> type) {
        return StreamCodec.of(
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
                    return new VxRawPayload(buf.readBytes(readable), type);
                }
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return identifier;
    }
}