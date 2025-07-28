package net.xmx.vortex.builtin.box;

import net.xmx.vortex.physics.object.physicsobject.type.rigid.properties.RigidPhysicsObjectProperties;

public class BoxPhysicsProperties {

    public static RigidPhysicsObjectProperties boxProperties = RigidPhysicsObjectProperties.builder()
            .mass(40.0f)
            .friction(0.7f)
            .restitution(0.3f)
            .linearDamping(0.3f)
            .angularDamping(0.3f)
            .gravityFactor(1.0f)
            .buoyancyFactor(1.6f)
            .build();
}