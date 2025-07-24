package net.xmx.vortex.builtin.rope;

import net.xmx.vortex.physics.object.physicsobject.type.soft.properties.SoftPhysicsObjectProperties;

public class RopePhysicsProperties {

    public static SoftPhysicsObjectProperties ropeProperties = SoftPhysicsObjectProperties.builder()
            .linearDamping(0.2f)
            .numIterations(10)
            .buoyancyFactor(0.6f)
            .build();
}