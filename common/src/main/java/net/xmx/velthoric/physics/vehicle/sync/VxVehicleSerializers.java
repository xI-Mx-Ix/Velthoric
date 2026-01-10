/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.sync;

import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.network.synchronization.VxDataSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds custom serializers used by the vehicle synchronization system.
 *
 * @author xI-Mx-Ix
 */
public class VxVehicleSerializers {

    /**
     * Serializes a list of {@link VxVehicleWheelState} objects.
     * <p>
     * This implementation writes the size of the list followed by the raw float components
     * of each wheel state. It avoids overhead by not writing full object headers.
     */
    public static final VxDataSerializer<List<VxVehicleWheelState>> WHEEL_STATES = new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, List<VxVehicleWheelState> value) {
            buf.writeVarInt(value.size());
            for (VxVehicleWheelState state : value) {
                buf.writeFloat(state.rotation());
                buf.writeFloat(state.steer());
                buf.writeFloat(state.suspension());
            }
        }

        @Override
        public List<VxVehicleWheelState> read(VxByteBuf buf) {
            int size = buf.readVarInt();
            List<VxVehicleWheelState> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                float rotation = buf.readFloat();
                float steer = buf.readFloat();
                float suspension = buf.readFloat();
                list.add(new VxVehicleWheelState(rotation, steer, suspension));
            }
            return list;
        }

        @Override
        public List<VxVehicleWheelState> copy(List<VxVehicleWheelState> value) {
            // Records are immutable, so we can just create a shallow copy of the list structure.
            return new ArrayList<>(value);
        }
    };

    // Private constructor to prevent instantiation.
    private VxVehicleSerializers() {}
}