package net.xmx.xbullet.physics.object.rigidphysicsobject.pcmd;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.world.pcmd.ICommand;

public record AddRigidBodyCommand(RigidPhysicsObject physicsObject, boolean shouldBeInitiallyActive) implements ICommand {

    public static void queue(PhysicsWorld physicsWorld, RigidPhysicsObject object, boolean activate) {
        physicsWorld.queueCommand(new AddRigidBodyCommand(object, activate));
    }

    @Override
    public void execute(PhysicsWorld world) {
        ObjectManager objectManager = world.getObjectManager();
        if (world.getPhysicsSystem() == null || objectManager == null || physicsObject.isRemoved() || physicsObject.getBodyId() != 0) {
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
                    BodyInterface bodyInterface = world.getBodyInterface();

                    try (BodyCreationSettings settings = new BodyCreationSettings()) {
                        settings.setShape(shapeRef);
                        settings.setPosition(position);
                        settings.setRotation(rotation);

                        settings.setMotionType(physicsObject.getMotionType());
                        if (physicsObject.getMotionType() == EMotionType.Static) {
                            settings.setObjectLayer(PhysicsWorld.Layers.STATIC);
                        } else {
                            settings.setObjectLayer(PhysicsWorld.Layers.DYNAMIC);
                        }

                        physicsObject.configureBodyCreationSettings(settings);

                        int bodyId = bodyInterface.createAndAddBody(settings, EActivation.Activate);

                        if (bodyId != 0 && bodyId != Jolt.cInvalidBodyId) {
                            physicsObject.setBodyId(bodyId);
                            objectManager.linkBodyId(bodyId, physicsObject.getPhysicsId());

                            if (!shouldBeInitiallyActive) {
                                bodyInterface.deactivateBody(bodyId);
                            }

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