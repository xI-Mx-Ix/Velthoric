package net.xmx.xbullet.physics.object.softphysicsobject.pcmd;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.SoftBodyCreationSettings;
import com.github.stephengold.joltjni.SoftBodySharedSettings;
import com.github.stephengold.joltjni.enumerate.EActivation;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.world.pcmd.ICommand;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;

public record AddSoftBodyCommand(SoftPhysicsObject physicsObject) implements ICommand {

    public static void queue(PhysicsWorld physicsWorld, SoftPhysicsObject object) {
        physicsWorld.queueCommand(new AddSoftBodyCommand(object));
    }

    @Override
    public void execute(PhysicsWorld world) {
        PhysicsObjectManager objectManager = world.getObjectManager();
        if (world.getPhysicsSystem() == null || world.getBodyInterface() == null || objectManager == null || physicsObject.isRemoved() || physicsObject.getBodyId() != 0) {
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

                    objectManager.linkBodyId(bodyId, physicsObject.getPhysicsId());
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