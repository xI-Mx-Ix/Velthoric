package net.xmx.velthoric.mixin.impl.entity_collision;

import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.physics.entity_collision.EntityAttachmentData;
import net.xmx.velthoric.physics.entity_collision.IEntityAttachmentData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityAttachmentMixin implements IEntityAttachmentData {
    @Unique
    private final EntityAttachmentData attachmentData = new EntityAttachmentData();

    @Override
    public EntityAttachmentData getAttachmentData() {
        return attachmentData;
    }
}