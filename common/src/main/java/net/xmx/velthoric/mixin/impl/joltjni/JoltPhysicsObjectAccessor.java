/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.joltjni;

import com.github.stephengold.joltjni.JoltPhysicsObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Accessor mixin to expose internal fields of {@link JoltPhysicsObject}.
 * <p>
 * This allows retrieving the native memory address directly, bypassing
 * method resolution issues when shadowing methods from parent classes.
 *
 * @author xI-Mx-Ix
 */
@Mixin(value = JoltPhysicsObject.class, remap = false)
public interface JoltPhysicsObjectAccessor {

    /**
     * Accesses the {@code virtualAddress} field which holds the native pointer.
     *
     * @return The AtomicLong containing the memory address.
     */
    @Accessor("virtualAddress")
    AtomicLong getVirtualAddress();
}