package net.xmx.xbullet.physics.object.rigidphysicsobject.pcmd;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.Jolt;
import net.xmx.xbullet.physics.core.pcmd.ICommand;
import net.xmx.xbullet.physics.core.PhysicsWorld;
import java.util.UUID;

public record RemoveRigidBodyCommand(UUID objectId, int bodyId) implements ICommand {

    public static void queue(PhysicsWorld physicsWorld, UUID objectId, int bodyId) {
        physicsWorld.queueCommand(new RemoveRigidBodyCommand(objectId, bodyId));
    }

    @Override
    public void execute(PhysicsWorld world) {
        if (world.getPhysicsSystem() == null || bodyId == 0 || bodyId == Jolt.cInvalidBodyId) {
            return;
        }

        BodyInterface bodyInterface = world.getBodyInterface();
        if (bodyInterface != null && bodyInterface.isAdded(bodyId)) {
            bodyInterface.removeBody(bodyId);
            bodyInterface.destroyBody(bodyId);
        }

        world.getPhysicsObjectsMap().remove(objectId);
        world.getBodyIds().remove(objectId);
        world.getBodyIdToUuidMap().remove(bodyId);
        world.getSyncedTransforms().remove(objectId);
        world.getSyncedLinearVelocities().remove(objectId);
        world.getSyncedAngularVelocities().remove(objectId);
        world.getSyncedActiveStates().remove(objectId);
        world.getSyncedStateTimestampsNanos().remove(objectId);
    }
}