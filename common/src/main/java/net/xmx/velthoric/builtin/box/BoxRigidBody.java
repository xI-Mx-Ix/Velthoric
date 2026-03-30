/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.box;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.Vec3Arg;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.core.body.VxBodyType;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.body.factory.VxRigidBodyFactory;
import net.xmx.velthoric.core.network.synchronization.VxDataSerializers;
import net.xmx.velthoric.core.network.synchronization.VxSynchronizedData;
import net.xmx.velthoric.core.network.synchronization.accessor.VxServerAccessor;
import net.xmx.velthoric.core.physics.VxPhysicsLayers;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.network.VxByteBuf;

import java.util.UUID;

/**
 * A simple physics body with a box shape.
 *
 * @author xI-Mx-Ix
 */
public class BoxRigidBody extends VxBody {

    public static final VxServerAccessor<Vec3Arg> DATA_HALF_EXTENTS = VxServerAccessor.create(BoxRigidBody.class, VxDataSerializers.VEC3);
    public static final VxServerAccessor<Integer> DATA_COLOR_ORDINAL = VxServerAccessor.create(BoxRigidBody.class, VxDataSerializers.INTEGER);

    /**
     * Server-side constructor.
     */
    public BoxRigidBody(VxBodyType type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Client-side constructor.
     */
    @Environment(EnvType.CLIENT)
    public BoxRigidBody(VxBodyType type, UUID id) {
        super(type, id);
    }

    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        builder.define(DATA_HALF_EXTENTS, new Vec3(0.5f, 0.5f, 0.5f));
        builder.define(DATA_COLOR_ORDINAL, BoxColor.RED.ordinal());
    }

    public void setHalfExtents(Vec3Arg halfExtents) {
        this.setServerData(DATA_HALF_EXTENTS, halfExtents);
    }

    public Vec3Arg getHalfExtents() {
        return get(DATA_HALF_EXTENTS);
    }

    public void setColor(BoxColor color) {
        this.setServerData(DATA_COLOR_ORDINAL, color.ordinal());
    }

    public BoxColor getColor() {
        int ordinal = get(DATA_COLOR_ORDINAL);
        return (ordinal >= 0 && ordinal < BoxColor.values().length) ? BoxColor.values()[ordinal] : BoxColor.RED;
    }

    /**
     * Creates the Jolt rigid body. Used as the {@code VxJoltRigidProvider} for this type.
     */
    public static int createJoltBody(VxBody body, VxRigidBodyFactory factory) {
        try (
                ShapeSettings shapeSettings = new BoxShapeSettings(body.get(DATA_HALF_EXTENTS));
                BodyCreationSettings bcs = new BodyCreationSettings()
        ) {
            bcs.setMotionType(EMotionType.Dynamic);
            bcs.setObjectLayer(VxPhysicsLayers.MOVING);
            bcs.setRestitution(0.01f);
            return factory.create(shapeSettings, bcs);
        }
    }

    /**
     * Writes type-specific persistence data.
     */
    public static void writePersistence(VxBody body, VxByteBuf buf) {
        Vec3Arg halfExtents = body.get(DATA_HALF_EXTENTS);
        buf.writeJoltVec3(halfExtents);
        buf.writeInt(body.get(DATA_COLOR_ORDINAL));
    }

    /**
     * Reads type-specific persistence data.
     */
    public static void readPersistence(VxBody body, VxByteBuf buf) {
        body.setServerData(DATA_HALF_EXTENTS, buf.readJoltVec3());
        body.setServerData(DATA_COLOR_ORDINAL, buf.readInt());
    }
}