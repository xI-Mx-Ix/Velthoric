package net.xmx.xbullet.physics.world.pcmd;

import net.xmx.xbullet.physics.world.PhysicsWorld;

public interface ICommand {
    void execute(PhysicsWorld world);
}