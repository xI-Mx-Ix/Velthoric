package net.xmx.vortex.physics.object.riding.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.xmx.vortex.math.VxTransform;
import com.github.stephengold.joltjni.RVec3;

public interface IVortexCamera {
    void vortex_setupWithPhysicsObject(
            BlockGetter level,
            Entity entity,
            boolean thirdPerson,
            boolean thirdPersonReverse,
            float partialTicks,
            VxTransform physicsObjectTransform,
            RVec3 localPlayerPosition
    );
}