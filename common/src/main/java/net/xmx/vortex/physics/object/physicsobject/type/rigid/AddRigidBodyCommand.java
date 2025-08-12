package net.xmx.vortex.physics.object.physicsobject.type.rigid;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import net.xmx.vortex.physics.world.pcmd.ICommand;

public record AddRigidBodyCommand(VxRigidBody rigidBody) implements ICommand {

    @Override
    public void execute(VxPhysicsWorld world) {
        if (rigidBody == null || rigidBody.getBodyId() != 0) {
            return;
        }

        try (ShapeSettings shapeSettings = rigidBody.createShapeSettings()) {
            if (shapeSettings == null) {
                VxMainClass.LOGGER.error("createShapeSettings() returned null for {}.", rigidBody.getPhysicsId());
                return;
            }

            try (ShapeResult shapeResult = shapeSettings.create()) {
                if (shapeResult.hasError()) {
                    VxMainClass.LOGGER.error("Failed to create shape for {}: {}", rigidBody.getPhysicsId(), shapeResult.getError());
                    return;
                }

                try (ShapeRefC shapeRef = shapeResult.get();
                     BodyCreationSettings settings = rigidBody.createBodyCreationSettings(shapeRef)) {

                    BodyInterface bodyInterface = world.getBodyInterface();
                    int bodyId = bodyInterface.createAndAddBody(settings, EActivation.Activate);

                    if (bodyId != Jolt.cInvalidBodyId) {
                        rigidBody.setBodyId(bodyId);
                        world.getObjectManager().getObjectContainer().add(rigidBody);
                        world.getObjectManager().getObjectContainer().linkBodyId(bodyId, rigidBody.getPhysicsId());
                    } else {
                        VxMainClass.LOGGER.error("Jolt failed to create body for object {}", rigidBody.getPhysicsId());
                    }
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("An unexpected exception occurred while creating body for {}.", rigidBody.getPhysicsId(), e);
        }
    }
}