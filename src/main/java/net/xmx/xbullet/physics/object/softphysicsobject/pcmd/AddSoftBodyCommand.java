package net.xmx.xbullet.physics.object.softphysicsobject.pcmd;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.SoftBodyCreationSettings;
import com.github.stephengold.joltjni.SoftBodySharedSettings;
import com.github.stephengold.joltjni.enumerate.EActivation;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;
import net.xmx.xbullet.physics.physicsworld.pcmd.ICommand;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;

public record AddSoftBodyCommand(SoftPhysicsObject physicsObject) implements ICommand {

    public static void queue(PhysicsWorld physicsWorld, SoftPhysicsObject object) {
        physicsWorld.queueCommand(new AddSoftBodyCommand(object));
    }

    @Override
    public void execute(PhysicsWorld world) {
        if (world.getPhysicsSystem() == null || world.getBodyInterface() == null || physicsObject.isRemoved() || physicsObject.getBodyId() != 0) {
            return;
        }

        try {
            SoftBodySharedSettings sharedSettings = physicsObject.getOrBuildSharedSettings();
            if (sharedSettings == null) {
                XBullet.LOGGER.error("Failed to create SoftBodySharedSettings for {}. Aborting add.", physicsObject.getPhysicsId());
                physicsObject.markRemoved();
                return;
            }

            try (SoftBodyCreationSettings settings = new SoftBodyCreationSettings()) {
                settings.setSettings(sharedSettings);
                settings.setPosition(physicsObject.getCurrentTransform().getTranslation());
                settings.setRotation(physicsObject.getCurrentTransform().getRotation());

                settings.setObjectLayer(PhysicsWorld.Layers.DYNAMIC);

                physicsObject.configureSoftBodyCreationSettings(settings);

                int bodyId = world.getBodyInterface().createAndAddSoftBody(settings, EActivation.Activate);

                if (bodyId != 0 && bodyId != Jolt.cInvalidBodyId) {
                    physicsObject.setBodyId(bodyId);
                    world.getBodyIds().put(physicsObject.getPhysicsId(), bodyId);
                    world.getBodyIdToUuidMap().put(bodyId, physicsObject.getPhysicsId());
                    world.getPhysicsObjectsMap().put(physicsObject.getPhysicsId(), physicsObject);
                    world.getSyncedActiveStates().put(physicsObject.getPhysicsId(), true);
                } else {
                    XBullet.LOGGER.error("Jolt failed to create soft body for object {}", physicsObject.getPhysicsId());
                    physicsObject.markRemoved();
                }
            }
        } catch (Exception e) {
            XBullet.LOGGER.error("Exception during SoftBody creation for {}. Aborting add.", physicsObject.getPhysicsId(), e);
            physicsObject.markRemoved();
        }
    }
}