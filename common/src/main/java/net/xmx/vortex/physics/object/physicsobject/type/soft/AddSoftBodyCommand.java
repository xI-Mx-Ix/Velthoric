package net.xmx.vortex.physics.object.physicsobject.type.soft;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import net.xmx.vortex.physics.world.pcmd.ICommand;

public record AddSoftBodyCommand(VxSoftBody softBody) implements ICommand {

    @Override
    public void execute(VxPhysicsWorld world) {
        if (softBody == null || softBody.getBodyId() != 0) {
            return;
        }

        try (SoftBodySharedSettings sharedSettings = softBody.createSoftBodySharedSettings()) {
            if (sharedSettings == null) {
                VxMainClass.LOGGER.error("Failed to create SoftBodySharedSettings for {}.", softBody.getPhysicsId());
                return;
            }

            try (SoftBodyCreationSettings settings = softBody.createSoftBodyCreationSettings(sharedSettings)) {
                BodyInterface bodyInterface = world.getBodyInterface();
                int bodyId = bodyInterface.createAndAddSoftBody(settings, EActivation.Activate);

                if (bodyId != Jolt.cInvalidBodyId) {
                    softBody.setBodyId(bodyId);
                    world.getObjectManager().getObjectContainer().add(softBody);
                    world.getObjectManager().getObjectContainer().linkBodyId(bodyId, softBody.getPhysicsId());
                } else {
                    VxMainClass.LOGGER.error("Jolt failed to create soft body for object {}", softBody.getPhysicsId());
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during SoftBody creation for {}.", softBody.getPhysicsId(), e);
        }
    }
}