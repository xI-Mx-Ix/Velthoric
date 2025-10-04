/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.sphere;

import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.body.VxClientRigidBody;
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxDataSerializers;

import java.util.UUID;

/**
 * Client-side handle for a sphere physics object.
 *
 * @author xI-Mx-Ix
 */
public class SphereClientRigidBody extends VxClientRigidBody {

    public static final VxDataAccessor<Float> DATA_RADIUS = VxDataAccessor.create(SphereClientRigidBody.class, VxDataSerializers.FLOAT);

    public SphereClientRigidBody(UUID id, ResourceLocation typeId, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        super(id, typeId, manager, dataStoreIndex, objectType);
    }

    @Override
    protected void defineSyncData() {
        this.synchronizedData.define(DATA_RADIUS, 0.5f);
    }
}