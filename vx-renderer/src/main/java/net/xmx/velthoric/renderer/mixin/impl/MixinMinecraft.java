/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.mixin.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.xmx.velthoric.renderer.listener.VxReloadListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to inject into the Minecraft class.
 * This is used to register the VxReloadListener directly into the game's
 * resource manager at the end of the client's initialization.
 *
 * @author xI-Mx-Ix
 */
@Mixin(Minecraft.class)
public class MixinMinecraft {

    /**
     * Shadows the resourceManager field from the Minecraft class.
     * This provides direct access to the manager instance where listeners are registered.
     */
    @Shadow @Final private ReloadableResourceManager resourceManager;

    /**
     * Injects a method call at the end of the Minecraft constructor.
     * This is the ideal point to add our resource listener, as all necessary
     * game systems, including the resource manager, have been initialized.
     *
     * @param ci The CallbackInfo object provided by Mixin, required for the injection.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        // Register our custom reload listener to the game's resource manager.
        // This ensures the VxModelCache is cleared on every resource reload.
        this.resourceManager.registerReloadListener(new VxReloadListener());
    }
}