/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.pcmd;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.Jolt;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.physics.world.pcmd.ICommand;

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

        world.getObjectManager().unlinkBodyId(bodyId);
        body.setBodyId(0);
    }
}
