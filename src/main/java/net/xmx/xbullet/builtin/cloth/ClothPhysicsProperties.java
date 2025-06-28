package net.xmx.xbullet.builtin.cloth;

import net.xmx.xbullet.physics.object.softphysicsobject.properties.SoftPhysicsObjectProperties;

public class ClothPhysicsProperties {

    public static SoftPhysicsObjectProperties clothProperties = SoftPhysicsObjectProperties.builder()
            .linearDamping(0.3f)
            .numIterations(10)
            .gravityFactor(1.0f)
            .friction(0.4f)
            .restitution(0.1f)
            .build();
}