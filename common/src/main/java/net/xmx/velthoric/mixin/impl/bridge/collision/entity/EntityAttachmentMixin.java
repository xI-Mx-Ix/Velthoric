/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.bridge.collision.entity;

import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.bridge.collision.entity.IVxEntityAttachmentData;
import net.xmx.velthoric.bridge.collision.entity.VxEntityAttachmentData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin that implements the {@link IVxEntityAttachmentData} interface on the Entity class.
 * This stores the state required to track which physics body an entity is currently standing on.
 *
 * @author xI-Mx-Ix
 */
@Mixin(Entity.class)
public abstract class EntityAttachmentMixin implements IVxEntityAttachmentData {

    @Unique
    private final VxEntityAttachmentData attachmentData = new VxEntityAttachmentData();

    @Override
    public VxEntityAttachmentData getAttachmentData() {
        return attachmentData;
    }
}