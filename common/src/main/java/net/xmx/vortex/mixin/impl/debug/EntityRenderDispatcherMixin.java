package net.xmx.vortex.mixin.impl.debug;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.xmx.vortex.mixin.util.IEntityRenderDispatcherAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin implements IEntityRenderDispatcherAccessor {

    @Shadow private boolean renderHitBoxes;

    @Override
    public boolean isRenderHitBoxes() {
        return this.renderHitBoxes;
    }
}
