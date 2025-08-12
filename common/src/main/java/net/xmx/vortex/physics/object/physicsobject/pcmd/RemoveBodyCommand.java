package net.xmx.vortex.physics.object.physicsobject.pcmd;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.Jolt;
import net.xmx.vortex.physics.object.physicsobject.VxAbstractBody;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import net.xmx.vortex.physics.world.pcmd.ICommand;

public record RemoveBodyCommand(VxAbstractBody body) implements ICommand {

    @Override
    public void execute(VxPhysicsWorld world) {
        if (body == null) return;

        int bodyId = body.getBodyId();
        if (bodyId == 0 || bodyId == Jolt.cInvalidBodyId) {
            return;
        }

        BodyInterface bodyInterface = world.getBodyInterface();
        if (bodyInterface != null && bodyInterface.isAdded(bodyId)) {
            bodyInterface.removeBody(bodyId);
            bodyInterface.destroyBody(bodyId);
        }

        world.getObjectManager().getObjectContainer().unlinkBodyId(bodyId);
        body.setBodyId(0);
    }
}