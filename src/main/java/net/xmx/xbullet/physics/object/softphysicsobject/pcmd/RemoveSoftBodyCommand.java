package net.xmx.xbullet.physics.object.softphysicsobject.pcmd;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.Jolt;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.world.pcmd.ICommand;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import java.util.UUID;

public record RemoveSoftBodyCommand(UUID objectId, int bodyId) implements ICommand {

    public static void queue(PhysicsWorld physicsWorld, UUID objectId, int bodyId) {
        physicsWorld.queueCommand(new RemoveSoftBodyCommand(objectId, bodyId));
    }

    @Override
    public void execute(PhysicsWorld world) {
        ObjectManager objectManager = world.getObjectManager();
        if (world.getPhysicsSystem() == null || bodyId == 0 || bodyId == Jolt.cInvalidBodyId) {
            return;
        }

        BodyInterface bodyInterface = world.getBodyInterface();
        if (bodyInterface != null && bodyInterface.isAdded(bodyId)) {
            bodyInterface.removeBody(bodyId);
            bodyInterface.destroyBody(bodyId);
        }

        objectManager.unlinkBodyId(bodyId);
    }
}