package net.xmx.xbullet.physics.object.physicsobject.type.rigid.pcmd;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.RigidPhysicsObject;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.world.pcmd.ICommand;

import java.util.UUID;

public record AddRigidBodyCommand(UUID objectId, boolean shouldBeInitiallyActive) implements ICommand {

    @Override
    public void execute(PhysicsWorld world) {
        ObjectManager objectManager = world.getObjectManager();
        if (world.getPhysicsSystem() == null || objectManager == null) {
            return;
        }

        IPhysicsObject physicsObject = objectManager.getObject(objectId).orElse(null);
        if (physicsObject == null || !(physicsObject instanceof RigidPhysicsObject rigidObject) || rigidObject.isRemoved() || rigidObject.getBodyId() != 0) {
            XBullet.LOGGER.error("AddRigidBodyCommand: Could not find manageable object with ID {} or object is in invalid state for physics initialization.", objectId);
            return;
        }

        try (ShapeSettings shapeSettings = rigidObject.getOrBuildShapeSettings()) {
            if (shapeSettings == null) {
                XBullet.LOGGER.error("getOrBuildShapeSettings() returned null for {}. Aborting add.", rigidObject.getPhysicsId());
                rigidObject.markRemoved();
                return;
            }

            try (ShapeResult shapeResult = shapeSettings.create()) {
                if (shapeResult.hasError()) {
                    XBullet.LOGGER.error("Failed to create shape for {}: {}", rigidObject.getPhysicsId(), shapeResult.getError());
                    rigidObject.markRemoved();
                    return;
                }

                try (ShapeRefC shapeRef = shapeResult.get()) {
                    PhysicsTransform transform = rigidObject.getCurrentTransform();
                    RVec3 position = transform.getTranslation();
                    Quat rotation = transform.getRotation();

                    BodyInterface bodyInterface = world.getBodyInterface();
                    try (BodyCreationSettings settings = new BodyCreationSettings(
                            shapeRef, position, rotation,
                            rigidObject.getMotionType(),
                            rigidObject.getMotionType() == EMotionType.Static ? PhysicsWorld.Layers.STATIC : PhysicsWorld.Layers.DYNAMIC)) {

                        rigidObject.configureBodyCreationSettings(settings);

                        int bodyId = bodyInterface.createAndAddBody(settings, shouldBeInitiallyActive ? EActivation.Activate : EActivation.DontActivate);

                        if (bodyId != Jolt.cInvalidBodyId) {
                            rigidObject.setBodyId(bodyId);
                            rigidObject.confirmPhysicsInitialized();
                            objectManager.linkBodyId(bodyId, rigidObject.getPhysicsId());
                        } else {
                            XBullet.LOGGER.error("Jolt failed to create body for object {}", rigidObject.getPhysicsId());
                            rigidObject.markRemoved();
                        }
                    }
                }
            }
        } catch (Exception e) {
            XBullet.LOGGER.error("An unexpected exception occurred while creating body for {}. Aborting add.", rigidObject.getPhysicsId(), e);
            rigidObject.markRemoved();
        }
    }
}