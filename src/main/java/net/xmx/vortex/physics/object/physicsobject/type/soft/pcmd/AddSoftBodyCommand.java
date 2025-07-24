package net.xmx.vortex.physics.object.physicsobject.type.soft.pcmd;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.object.physicsobject.type.soft.SoftPhysicsObject;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import net.xmx.vortex.physics.world.pcmd.ICommand;

import java.util.UUID;

public record AddSoftBodyCommand(UUID objectId, boolean shouldBeInitiallyActive) implements ICommand {

    @Override
    public void execute(VxPhysicsWorld world) {
        VxObjectManager objectManager = world.getObjectManager();
        BodyInterface bodyInterface = world.getBodyInterface();
        if (world.getPhysicsSystem() == null || bodyInterface == null || objectManager == null) {
            return;
        }

        IPhysicsObject physicsObject = objectManager.getObject(objectId).orElse(null);
        if (physicsObject == null || !(physicsObject instanceof SoftPhysicsObject softObject) || softObject.isRemoved() || softObject.getBodyId() != 0) {
            VxMainClass.LOGGER.error("AddSoftBodyCommand: Could not find manageable object with ID {} or object is in invalid state for physics initialization.", objectId);
            return;
        }

        try {
            SoftBodySharedSettings sharedSettings = softObject.getOrBuildSharedSettings();
            if (sharedSettings == null) {
                VxMainClass.LOGGER.error("Failed to create SoftBodySharedSettings for {}. Aborting add.", softObject.getPhysicsId());
                softObject.markRemoved();
                return;
            }

            try (SoftBodyCreationSettings settings = new SoftBodyCreationSettings()) {
                settings.setSettings(sharedSettings);
                settings.setPosition(softObject.getCurrentTransform().getTranslation());
                settings.setRotation(softObject.getCurrentTransform().getRotation());
                settings.setObjectLayer(VxPhysicsWorld.Layers.DYNAMIC);
                softObject.configureSoftBodyCreationSettings(settings);

                int bodyId = bodyInterface.createAndAddSoftBody(settings, shouldBeInitiallyActive ? EActivation.Activate : EActivation.DontActivate);

                if (bodyId != Jolt.cInvalidBodyId) {
                    softObject.setBodyId(bodyId);
                    softObject.confirmPhysicsInitialized();
                    objectManager.linkBodyId(bodyId, softObject.getPhysicsId());
                } else {
                    VxMainClass.LOGGER.error("Jolt failed to create soft body for object {}", softObject.getPhysicsId());
                    softObject.markRemoved();
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during SoftBody creation for {}. Aborting add.", softObject.getPhysicsId(), e);
            softObject.markRemoved();
        }
    }
}