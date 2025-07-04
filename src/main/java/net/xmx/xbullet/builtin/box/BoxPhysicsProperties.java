package net.xmx.xbullet.builtin.box;

import net.xmx.xbullet.physics.object.rigidphysicsobject.properties.RigidPhysicsObjectProperties;

public class BoxPhysicsProperties {

    public static RigidPhysicsObjectProperties boxProperties = RigidPhysicsObjectProperties.builder()
            .mass(400.0f)
            .friction(0.5f)
            .restitution(0.4f)
            .linearDamping(0.1f)
            .angularDamping(0.1f)
            .gravityFactor(1.0f)
            .buoyancyFactor(1.6f)
            .build();
}