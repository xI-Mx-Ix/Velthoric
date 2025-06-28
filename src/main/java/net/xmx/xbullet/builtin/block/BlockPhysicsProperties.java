package net.xmx.xbullet.builtin.block;

import net.xmx.xbullet.physics.object.rigidphysicsobject.properties.RigidPhysicsObjectProperties;

public class BlockPhysicsProperties {

    public static RigidPhysicsObjectProperties blockProperties = RigidPhysicsObjectProperties.builder()
            .mass(40.0f)
            .friction(0.38f)
            .restitution(0.6f)
            .linearDamping(0.1f)
            .angularDamping(0.35f)
            .gravityFactor(1.0f)
            .buoyancyFactor(0.6f)
            .build();
}