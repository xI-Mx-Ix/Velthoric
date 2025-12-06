/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.event.mixin.impl.event.network;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin to expose protected fields from {@link ClientCommonPacketListenerImpl}.
 * <p>
 * This allows other mixins (like the one for ClientPacketListener) to access the network connection
 * which is otherwise protected and hidden in the parent class.
 * </p>
 *
 * @author xI-Mx-Ix
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public interface ClientCommonPacketListenerImplAccessor {

    /**
     * Accessor for the 'connection' field.
     *
     * @return The underlying network connection.
     */
    @Accessor("connection")
    Connection velthoric$getConnection();
}