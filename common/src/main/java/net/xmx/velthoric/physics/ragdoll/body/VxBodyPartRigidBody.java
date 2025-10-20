/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.ragdoll.body;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.TaperedCapsuleShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.natives.VxLayers;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.sync.VxDataAccessor;
import net.xmx.velthoric.physics.body.sync.VxDataSerializers;
import net.xmx.velthoric.physics.body.sync.VxSynchronizedData;
import net.xmx.velthoric.physics.body.type.VxRigidBody;
import net.xmx.velthoric.physics.body.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.ragdoll.VxBodyPart;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * Represents a single rigid body part of a ragdoll, such as a head, torso, or limb.
 * It stores data about its type, dimensions, and the skin texture it should use for rendering.
 *
 * @author xI-Mx-Ix
 */
public class VxBodyPartRigidBody extends VxRigidBody {

    public static final VxDataAccessor<Vec3> DATA_HALF_EXTENTS = VxDataAccessor.create(VxBodyPartRigidBody.class, VxDataSerializers.VEC3);
    public static final VxDataAccessor<VxBodyPart> DATA_BODY_PART = VxDataAccessor.create(VxBodyPartRigidBody.class, VxDataSerializers.BODY_PART);
    public static final VxDataAccessor<String> DATA_SKIN_ID = VxDataAccessor.create(VxBodyPartRigidBody.class, VxDataSerializers.STRING);

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
        VxBodyPart partType = getSyncData(DATA_BODY_PART);
        Vec3 fullSize = partType.getSize();

        ShapeSettings shapeSettings;

        // Wir wählen die Form basierend auf dem Körperteil aus, um die Steifheit zu reduzieren.
        // Kapseln und abgerundete Boxen verhindern das Verhaken, das bei scharfen Kanten auftritt.
        switch (partType) {
            case TORSO: {
                // Der Torso bleibt eine Box, aber mit einem großzügigen konvexen Radius, um die Kanten stark abzurunden.
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
                // Für Gliedmaßen und den Kopf verwenden wir eine TaperedCapsuleShape.
                // Sie hat keine Kanten und sorgt für ein sehr weiches, flüssiges Kollisionsverhalten.
                float halfHeight = fullSize.getY() / 2.0f;

                // Der Radius wird aus dem Durchschnitt der Breite (X) und Tiefe (Z) berechnet.
                float baseRadius = (fullSize.getX() + fullSize.getZ()) / 4.0f;

                float topRadius = baseRadius;
                float bottomRadius = baseRadius;

                // Optional: Fügen Sie eine leichte Verjüngung für Arme und Beine hinzu, um sie natürlicher aussehen zu lassen.
                if (partType == VxBodyPart.LEFT_ARM || partType == VxBodyPart.RIGHT_ARM || partType == VxBodyPart.LEFT_LEG || partType == VxBodyPart.RIGHT_LEG) {
                    topRadius = baseRadius * 1.1f;    // Oben etwas breiter (z.B. Schulter/Oberschenkel)
                    bottomRadius = baseRadius * 0.9f; // Unten etwas schmaler (z.B. Hand/Fuß)
                }

                // Die Kapselform muss eine minimale Höhe haben. Wir ziehen den größten Radius von der halben Höhe ab.
                float capsuleHalfHeight = Math.max(0.01f, halfHeight - Math.max(topRadius, bottomRadius));

                shapeSettings = new TaperedCapsuleShapeSettings(capsuleHalfHeight, topRadius, bottomRadius);
                break;
            }
        }

        try (BodyCreationSettings bcs = new BodyCreationSettings()) {
            bcs.setMotionType(EMotionType.Dynamic);
            bcs.setObjectLayer(VxLayers.DYNAMIC);
            bcs.setFriction(0.5f);
            bcs.setRestitution(0.3f);
            return factory.create(shapeSettings, bcs);
        } finally {
            // Wichtig: Die erstellten ShapeSettings müssen geschlossen werden, um Speicherlecks zu vermeiden.
            if (shapeSettings != null) {
                shapeSettings.close();
            }
        }
    }


    @Override
    public void writePersistenceData(VxByteBuf buf) {
        super.writePersistenceData(buf);
        VxDataSerializers.VEC3.write(buf, getSyncData(DATA_HALF_EXTENTS));
        VxDataSerializers.BODY_PART.write(buf, getSyncData(DATA_BODY_PART));
        VxDataSerializers.STRING.write(buf, getSyncData(DATA_SKIN_ID));
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        super.readPersistenceData(buf);
        // Hinweis: DATA_HALF_EXTENTS wird hier gelesen, aber in createJoltBody nicht mehr direkt verwendet,
        // da die Größe jetzt aus VxBodyPart kommt. Dies könnte man refaktorisieren, aber es funktioniert auch so.
        setSyncData(DATA_HALF_EXTENTS, VxDataSerializers.VEC3.read(buf));
        setSyncData(DATA_BODY_PART, VxDataSerializers.BODY_PART.read(buf));
        setSyncData(DATA_SKIN_ID, VxDataSerializers.STRING.read(buf));
    }
}