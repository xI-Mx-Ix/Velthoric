/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.ragdoll.body;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.TaperedCapsuleShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.core.network.synchronization.accessor.VxServerAccessor;
import net.xmx.velthoric.core.physics.VxPhysicsLayers;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.core.body.registry.VxBodyType;
import net.xmx.velthoric.core.network.synchronization.VxDataSerializers;
import net.xmx.velthoric.core.network.synchronization.VxSynchronizedData;
import net.xmx.velthoric.core.body.type.VxRigidBody;
import net.xmx.velthoric.core.body.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.core.ragdoll.VxBodyPart;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * Represents a single rigid body part of a ragdoll, such as a head, torso, or limb.
 * It stores data about its type, dimensions, and the skin texture it should use for rendering.
 *
 * @author xI-Mx-Ix
 */
public class VxBodyPartRigidBody extends VxRigidBody {

    public static final VxServerAccessor<Vec3> DATA_HALF_EXTENTS = VxServerAccessor.create(VxBodyPartRigidBody.class, VxDataSerializers.VEC3);
    public static final VxServerAccessor<VxBodyPart> DATA_BODY_PART = VxServerAccessor.create(VxBodyPartRigidBody.class, VxDataSerializers.BODY_PART);
    public static final VxServerAccessor<String> DATA_SKIN_ID = VxServerAccessor.create(VxBodyPartRigidBody.class, VxDataSerializers.STRING);

    /**
     * Server-side constructor.
     */
    public VxBodyPartRigidBody(VxBodyType<VxBodyPartRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Client-side constructor.
     */
    @Environment(EnvType.CLIENT)
    public VxBodyPartRigidBody(VxBodyType<VxBodyPartRigidBody> type, UUID id) {
        super(type, id);
    }

    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        builder.define(DATA_HALF_EXTENTS, new Vec3(0.25f, 0.25f, 0.25f));
        builder.define(DATA_BODY_PART, VxBodyPart.TORSO);
        builder.define(DATA_SKIN_ID, "");
    }

    @Override
    public int createJoltBody(VxRigidBodyFactory factory) {
        VxBodyPart partType = get(DATA_BODY_PART);
        Vec3 fullSize = partType.getSize();

        ShapeSettings shapeSettings;

        // We select the shape based on the body part to reduce stiffness.
        // Capsules and rounded boxes prevent snagging that occurs with sharp edges.
        switch (partType) {
            case TORSO: {
                // The torso remains a box, but with a generous convex radius to strongly round off the edges.
                Vec3 halfExtents = new Vec3(fullSize.getX() * 0.5f, fullSize.getY() * 0.5f, fullSize.getZ() * 0.5f);
                float convexRadius = 0.1f;
                shapeSettings = new BoxShapeSettings(halfExtents, convexRadius);
                break;
            }
            case HEAD:
            case LEFT_ARM:
            case RIGHT_ARM:
            case LEFT_LEG:
            case RIGHT_LEG:
            default: {
                // For limbs and the head, we use a TaperedCapsuleShape.
                // It has no edges and ensures very soft, fluid collision behavior.
                float halfHeight = fullSize.getY() / 2.0f;

                // The radius is calculated from the average of the width (X) and depth (Z).
                float baseRadius = (fullSize.getX() + fullSize.getZ()) / 4.0f;

                float topRadius = baseRadius;
                float bottomRadius = baseRadius;

                // Optional: Add a slight taper for arms and legs to make them look more natural.
                if (partType == VxBodyPart.LEFT_ARM || partType == VxBodyPart.RIGHT_ARM || partType == VxBodyPart.LEFT_LEG || partType == VxBodyPart.RIGHT_LEG) {
                    topRadius = baseRadius * 1.1f;    // Slightly wider at the top (e.g., shoulder/thigh)
                    bottomRadius = baseRadius * 0.9f; // Slightly narrower at the bottom (e.g., hand/foot)
                }

                // The capsule shape must have a minimum height. We subtract the largest radius from half the height.
                float capsuleHalfHeight = Math.max(0.01f, halfHeight - Math.max(topRadius, bottomRadius));

                shapeSettings = new TaperedCapsuleShapeSettings(capsuleHalfHeight, topRadius, bottomRadius);
                break;
            }
        }

        try (BodyCreationSettings bcs = new BodyCreationSettings()) {
            bcs.setMotionType(EMotionType.Dynamic);
            bcs.setObjectLayer(VxPhysicsLayers.MOVING);
            bcs.setFriction(0.5f);
            bcs.setRestitution(0.3f);
            return factory.create(shapeSettings, bcs);
        } finally {
            shapeSettings.close();
        }
    }


    @Override
    public void writePersistenceData(VxByteBuf buf) {
        super.writePersistenceData(buf);
        VxDataSerializers.VEC3.write(buf, get(DATA_HALF_EXTENTS));
        VxDataSerializers.BODY_PART.write(buf, get(DATA_BODY_PART));
        VxDataSerializers.STRING.write(buf, get(DATA_SKIN_ID));
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        super.readPersistenceData(buf);
        setServerData(DATA_HALF_EXTENTS, VxDataSerializers.VEC3.read(buf));
        setServerData(DATA_BODY_PART, VxDataSerializers.BODY_PART.read(buf));
        setServerData(DATA_SKIN_ID, VxDataSerializers.STRING.read(buf));
    }
}