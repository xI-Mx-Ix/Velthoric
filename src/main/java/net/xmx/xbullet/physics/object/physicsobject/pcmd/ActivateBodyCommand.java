package net.xmx.xbullet.physics.object.physicsobject.pcmd;

import com.github.stephengold.joltjni.BodyInterface;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.world.pcmd.ICommand;

public record ActivateBodyCommand(int bodyId) implements ICommand {

    @Override
    public void execute(PhysicsWorld world) {
        if (bodyId == 0) {
            return;
        }

        BodyInterface bodyInterface = world.getBodyInterface();
        if (bodyInterface != null && bodyInterface.isAdded(bodyId)) {
            bodyInterface.activateBody(bodyId);
        }
    }
}