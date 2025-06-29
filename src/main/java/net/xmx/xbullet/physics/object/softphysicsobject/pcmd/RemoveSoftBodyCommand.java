package net.xmx.xbullet.physics.object.softphysicsobject.pcmd;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.Jolt;
import net.xmx.xbullet.physics.physicsworld.pcmd.ICommand;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;
import java.util.UUID;

public record RemoveSoftBodyCommand(UUID objectId, int bodyId) implements ICommand {

    public static void queue(PhysicsWorld physicsWorld, UUID objectId, int bodyId) {
        physicsWorld.queueCommand(new RemoveSoftBodyCommand(objectId, bodyId));
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
        world.getSyncedSoftBodyVertexData().remove(objectId);
        world.getSyncedActiveStates().remove(objectId);
        world.getSyncedStateTimestampsNanos().remove(objectId);
    }
}