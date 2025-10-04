/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.rope;

import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.body.VxClientSoftBody;
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxDataSerializers;

import java.util.UUID;

/**
 * Client-side handle for a rope physics object.
 *
 * @author xI-Mx-Ix
 */
public class RopeClientSoftBody extends VxClientSoftBody {

    public static final VxDataAccessor<Float> DATA_ROPE_RADIUS = VxDataAccessor.create(RopeClientSoftBody.class, VxDataSerializers.FLOAT);

    public RopeClientSoftBody(UUID id, ResourceLocation typeId, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        super(id, typeId, manager, dataStoreIndex, objectType);
    }

    @Override
    protected void defineSyncData() {
        this.synchronizedData.define(DATA_ROPE_RADIUS, 0.1f);
    }
}