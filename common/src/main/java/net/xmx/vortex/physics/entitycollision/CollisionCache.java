package net.xmx.vortex.physics.entitycollision;

import com.github.stephengold.joltjni.*;

final class CollisionCache {
        final ClosestHitCastShapeCollector collector = new ClosestHitCastShapeCollector();
        final ShapeCastSettings shapeCastSettings = new ShapeCastSettings();
        final BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
        final ObjectLayerFilter olFilter = new ObjectLayerFilter();
        final ShapeFilter shapeFilter = new ShapeFilter();
        final SingleBodyFilter bodyFilter = new SingleBodyFilter();
        final Vec3 motionVec = new Vec3();
        final RVec3 startPosVec = new RVec3();
        final Quat identityQuat = Quat.sIdentity();
        final RMat44 startTransform = new RMat44();
        final Vec3 entityShapeScale = new Vec3(1f, 1f, 1f);
    }