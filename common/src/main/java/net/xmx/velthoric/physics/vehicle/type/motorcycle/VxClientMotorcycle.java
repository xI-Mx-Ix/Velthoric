/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.type.motorcycle;

import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.vehicle.VxClientVehicle;

import java.util.UUID;

/**
 * Client-side representation of a motorcycle. Handles rendering of the chassis and wheels.
 *
 * @author xI-Mx-Ix
 */
public class VxClientMotorcycle extends VxClientVehicle {

    public VxClientMotorcycle(UUID id, ResourceLocation typeId, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        super(id, typeId, manager, dataStoreIndex, objectType);
    }

    @Override
    protected void defineSyncData() {
        super.defineSyncData();
        this.synchronizedData.define(VxMotorcycle.DATA_CHASSIS_HALF_EXTENTS, new Vec3());
    }

    public Vec3 getChassisHalfExtents() {
        return this.getSyncData(VxMotorcycle.DATA_CHASSIS_HALF_EXTENTS);
    }
}