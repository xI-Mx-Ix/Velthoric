/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.joltjni;

import com.github.stephengold.joltjni.ContactListener;
import com.github.stephengold.joltjni.ContactListenerList;
import com.github.stephengold.joltjni.PhysicsSystem;
import net.xmx.velthoric.init.VxMainClass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PhysicsSystem.class)
public abstract class PhysicsSystemMixin {

    @Shadow(remap = false)
    private ContactListener contactListener;

    @Inject(
            method = "setContactListener(Lcom/github/stephengold/joltjni/ContactListener;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void velthoric_preventContactListenerOverwrite(ContactListener listener, CallbackInfo ci) {

        if (this.contactListener instanceof ContactListenerList) {

            VxMainClass.LOGGER.error("""

            ================================ VELTHORIC API VIOLATION ================================
            A mod attempted to directly call 'PhysicsSystem.setContactListener()'.
            This action is blocked by Velthoric to ensure compatibility between multiple mods.

            ACTION BLOCKED: The call was ignored and the listener was NOT registered.

            SOLUTION FOR DEVELOPERS:
            To register a contact listener, please use the official Velthoric API:
            'VxPhysicsWorld.addContactListener(your_listener_instance)'

            This ensures that your listener is added to the central list without overwriting others.

            Offending Listener Class: {}
            =========================================================================================
            """, listener != null ? listener.getClass().getName() : "null");

            ci.cancel();
        }

    }
}