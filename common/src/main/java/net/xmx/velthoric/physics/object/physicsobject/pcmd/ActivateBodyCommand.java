package net.xmx.velthoric.physics.object.physicsobject.pcmd;

import com.github.stephengold.joltjni.BodyInterface;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.physics.world.pcmd.ICommand;

public record ActivateBodyCommand(int bodyId) implements ICommand {

    @Override
    public void execute(VxPhysicsWorld world) {
        if (bodyId == 0) {
            return;
        }

        BodyInterface bodyInterface = world.getBodyInterface();
        if (bodyInterface != null && bodyInterface.isAdded(bodyId)) {
            bodyInterface.activateBody(bodyId);
        }
    }
}