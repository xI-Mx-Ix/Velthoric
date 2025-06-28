package net.xmx.xbullet.builtin.rope;

import net.xmx.xbullet.physics.object.softphysicsobject.properties.SoftPhysicsObjectProperties;

public class RopePhysicsProperties {

    public static SoftPhysicsObjectProperties ropeProperties = SoftPhysicsObjectProperties.builder()
            .linearDamping(0.2f)
            .numIterations(10)
            .buoyancyFactor(0.6f)
            .build();
}