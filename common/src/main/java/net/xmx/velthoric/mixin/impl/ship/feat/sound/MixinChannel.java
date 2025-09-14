/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.feat.sound;

import com.mojang.blaze3d.audio.Channel;
import net.xmx.velthoric.mixin.util.ship.sound.IVxHasOpenALVelocity;
import org.joml.Vector3d;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Channel.class)
public class MixinChannel implements IVxHasOpenALVelocity {

    @Shadow @Final private int source;

    @Override
    public void velthoric$setVelocity(Vector3d velocity) {
        AL10.alSource3f(this.source, AL10.AL_VELOCITY, (float) velocity.x, (float) velocity.y, (float) velocity.z);
    }
}