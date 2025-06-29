package net.xmx.xbullet.physics.object.rigidphysicsobject.pcmd;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.physicsworld.pcmd.ICommand;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;

public record AddRigidBodyCommand(RigidPhysicsObject physicsObject) implements ICommand {

    public static void queue(PhysicsWorld physicsWorld, RigidPhysicsObject object) {
        physicsWorld.queueCommand(new AddRigidBodyCommand(object));
    }

    @Override
    public void execute(PhysicsWorld world) {
        if (world.getPhysicsSystem() == null || physicsObject.isRemoved() || physicsObject.getBodyId() != 0) {
            return;
        }

        try (ShapeSettings shapeSettings = physicsObject.getOrBuildShapeSettings()) {

            if (shapeSettings == null) {
                XBullet.LOGGER.error("createShapeSettings() returned null for {}. Aborting add.", physicsObject.getPhysicsId());
                physicsObject.markRemoved();
                return;
            }

            try (ShapeResult shapeResult = shapeSettings.create()) {

                if (shapeResult.hasError()) {
                    XBullet.LOGGER.error("Failed to create shape for {}: {}", physicsObject.getPhysicsId(), shapeResult.getError());
                    physicsObject.markRemoved();
                    return;
                }

                try (ShapeRefC shapeRef = shapeResult.get()) {

                    RVec3 position = physicsObject.getCurrentTransform().getTranslation();
                    Quat rotation = physicsObject.getCurrentTransform().getRotation();

                    try (BodyCreationSettings settings = new BodyCreationSettings(

                            shapeRef,
                            position,
                            rotation,
                            physicsObject.getMotionType(),
                            physicsObject.getMotionType() == com.github.stephengold.joltjni.enumerate.EMotionType.Static
                                    ? PhysicsWorld.Layers.STATIC : PhysicsWorld.Layers.DYNAMIC
                    )) {


                        physicsObject.configureBodyCreationSettings(settings);

                        int bodyId = world.getBodyInterface().createAndAddBody(settings, EActivation.Activate);

                        if (bodyId != 0 && bodyId != Jolt.cInvalidBodyId) {
                            physicsObject.setBodyId(bodyId);
                            world.getBodyIds().put(physicsObject.getPhysicsId(), bodyId);
                            world.getBodyIdToUuidMap().put(bodyId, physicsObject.getPhysicsId());
                            world.getPhysicsObjectsMap().put(physicsObject.getPhysicsId(), physicsObject);
                            world.getSyncedActiveStates().put(physicsObject.getPhysicsId(), true);
                        } else {
                            XBullet.LOGGER.error("Jolt failed to create body for object {}", physicsObject.getPhysicsId());
                            physicsObject.markRemoved();
                        }
                    }
                }
            }
        } catch (Exception e) {
            XBullet.LOGGER.error("An unexpected exception occurred while creating body for {}. Aborting add.", physicsObject.getPhysicsId(), e);
            physicsObject.markRemoved();
        }
    }
}