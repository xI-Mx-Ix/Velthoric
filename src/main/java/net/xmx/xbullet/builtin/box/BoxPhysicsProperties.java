package net.xmx.xbullet.builtin.box;

import net.xmx.xbullet.physics.object.rigidphysicsobject.properties.RigidPhysicsObjectProperties;

public class BoxPhysicsProperties {

    public static RigidPhysicsObjectProperties boxProperties = RigidPhysicsObjectProperties.builder()
            .mass(4000.0f)
            .friction(0.7f)
            .restitution(0.3f)
            .linearDamping(0.3f)
            .angularDamping(0.3f)
            .gravityFactor(1.0f)
            .buoyancyFactor(1.6f)
            .build();
}