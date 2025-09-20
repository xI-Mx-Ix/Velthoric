/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.joltjni;

import com.github.stephengold.joltjni.Body;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Mixin accessor interface to expose the native static method for getting a body's ID
 * directly from its virtual address without creating a Java wrapper object.
 */
@Mixin(value = Body.class, remap = false)
public interface BodyAccessor {

    /**
     * Invokes the private native static method {@code getId(long bodyVa)}.
     *
     * @param bodyVa The virtual address of the Jolt body.
     * @return The integer ID of the body.
     */
    @Invoker(value = "getId", remap = false)
    static int velthoric_getId(long bodyVa) {
        // This will be implemented by the Mixin processor.
        throw new AssertionError();
    }
}