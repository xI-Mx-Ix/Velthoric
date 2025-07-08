package net.xmx.xbullet.builtin.sphere;

import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.properties.RigidPhysicsObjectProperties;

public class SpherePhysicsProperties {

    public static RigidPhysicsObjectProperties sphereProperties = RigidPhysicsObjectProperties.builder()
            .mass(40.0f)
            .friction(0.38f)
            .restitution(0.7f)
            .linearDamping(0.06f)
            .angularDamping(0.2f)
            .gravityFactor(1.0f)
            .buoyancyFactor(1.6f)
            .motionType(EMotionType.Dynamic)
            .build();
}