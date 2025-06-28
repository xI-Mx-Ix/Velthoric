package net.xmx.xbullet.physics.core.pcmd;

import net.xmx.xbullet.physics.core.PhysicsWorld;

public interface ICommand {
    void execute(PhysicsWorld world);
}