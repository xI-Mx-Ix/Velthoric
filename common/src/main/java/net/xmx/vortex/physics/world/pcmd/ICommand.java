package net.xmx.vortex.physics.world.pcmd;

import net.xmx.vortex.physics.world.VxPhysicsWorld;

public interface ICommand {
    void execute(VxPhysicsWorld world);
}