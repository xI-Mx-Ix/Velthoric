/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.cloth;

import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.body.VxClientSoftBody;
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxDataSerializers;

import java.util.UUID;

/**
 * Client-side handle for a cloth physics object.
 *
 * @author xI-Mx-Ix
 */
public class ClothClientSoftBody extends VxClientSoftBody {

    public static final VxDataAccessor<Integer> DATA_WIDTH_SEGMENTS = VxDataAccessor.create(ClothClientSoftBody.class, VxDataSerializers.INTEGER);
    public static final VxDataAccessor<Integer> DATA_HEIGHT_SEGMENTS = VxDataAccessor.create(ClothClientSoftBody.class, VxDataSerializers.INTEGER);

    public ClothClientSoftBody(UUID id, ResourceLocation typeId, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        super(id, typeId, manager, dataStoreIndex, objectType);
    }

    @Override
    protected void defineSyncData() {
        this.synchronizedData.define(DATA_WIDTH_SEGMENTS, 15);
        this.synchronizedData.define(DATA_HEIGHT_SEGMENTS, 15);
    }
}