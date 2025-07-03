package net.xmx.xbullet.physics.object.softphysicsobject.pcmd;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.world.pcmd.ICommand;

public record AddSoftBodyCommand(SoftPhysicsObject physicsObject, boolean shouldBeInitiallyActive) implements ICommand {

    public static void queue(PhysicsWorld physicsWorld, SoftPhysicsObject object, boolean activate) {
        physicsWorld.queueCommand(new AddSoftBodyCommand(object, activate));
    }

    @Override
    public void execute(PhysicsWorld world) {
        ObjectManager objectManager = world.getObjectManager();
        BodyInterface bodyInterface = world.getBodyInterface();
        if (world.getPhysicsSystem() == null || bodyInterface == null || objectManager == null || physicsObject.isRemoved() || physicsObject.getBodyId() != 0) {
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

                int bodyId = bodyInterface.createAndAddSoftBody(settings, EActivation.Activate);

                if (bodyId != 0 && bodyId != Jolt.cInvalidBodyId) {
                    physicsObject.setBodyId(bodyId);
                    objectManager.linkBodyId(bodyId, physicsObject.getPhysicsId());

                    if (!shouldBeInitiallyActive) {
                        bodyInterface.deactivateBody(bodyId);
                    }
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