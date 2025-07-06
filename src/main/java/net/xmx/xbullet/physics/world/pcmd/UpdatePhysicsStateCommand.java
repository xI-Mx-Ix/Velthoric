package net.xmx.xbullet.physics.world.pcmd;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BodyLockInterface;
import com.github.stephengold.joltjni.PhysicsSystem;
import net.xmx.xbullet.physics.world.PhysicsWorld;

public record UpdatePhysicsStateCommand(long timestampNanos) implements ICommand {

    @Override
    public void execute(PhysicsWorld world) {
        PhysicsSystem system = world.getPhysicsSystem();
        if (system == null) {
            return;
        }

        BodyInterface bodyInterface = system.getBodyInterface();
        BodyLockInterface lockInterface = world.getBodyLockInterface();
        if (lockInterface == null) {
            return;
        }

        world.getObjectManager().parallelUpdateAndDispatch(this.timestampNanos, bodyInterface, lockInterface);
    }
}