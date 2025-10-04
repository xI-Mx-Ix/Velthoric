/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.box;

import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.body.VxClientRigidBody;
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxDataSerializers;

import java.util.UUID;

/**
 * Client-side handle for a simple box physics object.
 *
 * @author xI-Mx-Ix
 */
public class BoxClientRigidBody extends VxClientRigidBody {

    public static final VxDataAccessor<Vec3> DATA_HALF_EXTENTS = VxDataAccessor.create(BoxClientRigidBody.class, VxDataSerializers.VEC3);
    public static final VxDataAccessor<Integer> DATA_COLOR_ORDINAL = VxDataAccessor.create(BoxClientRigidBody.class, VxDataSerializers.INTEGER);

    public BoxClientRigidBody(UUID id, ResourceLocation typeId, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        super(id, typeId, manager, dataStoreIndex, objectType);
    }

    @Override
    protected void defineSyncData() {
        this.synchronizedData.define(DATA_HALF_EXTENTS, new Vec3(0.5f, 0.5f, 0.5f));
        this.synchronizedData.define(DATA_COLOR_ORDINAL, BoxColor.RED.ordinal());
    }
}