package net.xmx.xbullet.physics.physicsworld.pcmd;

import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;

public interface ICommand {
    void execute(PhysicsWorld world);
}