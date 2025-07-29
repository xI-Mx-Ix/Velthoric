package net.xmx.vortex.physics.object.physicsobject.type.rigid.pcmd;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.RigidPhysicsObject;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import net.xmx.vortex.physics.world.pcmd.ICommand;

import java.util.UUID;

public record AddRigidBodyCommand(UUID objectId) implements ICommand {

    @Override
    public void execute(VxPhysicsWorld world) {
        VxObjectManager objectManager = world.getObjectManager();
        if (world.getPhysicsSystem() == null || objectManager == null) {
            return;
        }

        IPhysicsObject physicsObject = objectManager.getObject(objectId).orElse(null);
        if (!(physicsObject instanceof RigidPhysicsObject rigidObject) || rigidObject.isRemoved() || rigidObject.getBodyId() != 0) {
            VxMainClass.LOGGER.error("AddRigidBodyCommand: Could not find manageable object with ID {} or object is in invalid state for physics initialization.", objectId);
            return;
        }

        try (ShapeSettings shapeSettings = rigidObject.getOrBuildShapeSettings()) {
            if (shapeSettings == null) {
                VxMainClass.LOGGER.error("getOrBuildShapeSettings() returned null for {}. Aborting add.", rigidObject.getPhysicsId());
                rigidObject.markRemoved();
                return;
            }

            try (ShapeResult shapeResult = shapeSettings.create()) {
                if (shapeResult.hasError()) {
                    VxMainClass.LOGGER.error("Failed to create shape for {}: {}", rigidObject.getPhysicsId(), shapeResult.getError());
                    rigidObject.markRemoved();
                    return;
                }

                try (ShapeRefC shapeRef = shapeResult.get()) {
                    VxTransform transform = rigidObject.getCurrentTransform();
                    RVec3 position = transform.getTranslation();
                    Quat rotation = transform.getRotation();

                    BodyInterface bodyInterface = world.getBodyInterface();
                    try (BodyCreationSettings settings = new BodyCreationSettings(
                            shapeRef, position, rotation,
                            rigidObject.getMotionType(),
                            rigidObject.getMotionType() == EMotionType.Static ? VxPhysicsWorld.Layers.STATIC : VxPhysicsWorld.Layers.DYNAMIC)) {

                        rigidObject.configureBodyCreationSettings(settings);

                        int bodyId = bodyInterface.createAndAddBody(settings, EActivation.DontActivate);

                        if (bodyId != Jolt.cInvalidBodyId) {
                            rigidObject.setBodyId(bodyId);
                            rigidObject.confirmPhysicsInitialized();
                            objectManager.getObjectContainer().linkBodyId(bodyId, rigidObject.getPhysicsId());
                        } else {
                            VxMainClass.LOGGER.error("Jolt failed to create body for object {}", rigidObject.getPhysicsId());
                            rigidObject.markRemoved();
                        }
                    }
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("An unexpected exception occurred while creating body for {}. Aborting add.", rigidObject.getPhysicsId(), e);
            rigidObject.markRemoved();
        }
    }
}