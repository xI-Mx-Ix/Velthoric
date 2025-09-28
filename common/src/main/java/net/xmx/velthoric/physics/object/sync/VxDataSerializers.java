/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.sync;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.xmx.velthoric.network.VxByteBuf;

/**
 * A registry holding standard implementations of {@link VxDataSerializer} for common data types.
 *
 * @author xI-Mx-Ix
 */
public final class VxDataSerializers {
    private static final Int2ObjectMap<VxDataSerializer<?>> REGISTRY = new Int2ObjectOpenHashMap<>();
    private static int nextId = 0;

    public static final VxDataSerializer<Byte> BYTE = register(new VxDataSerializer<>() {
        @Override public void write(VxByteBuf buf, Byte value) { buf.writeByte(value); }
        @Override public Byte read(VxByteBuf buf) { return buf.readByte(); }
        @Override public Byte copy(Byte value) { return value; }
    });

    public static final VxDataSerializer<Integer> INTEGER = register(new VxDataSerializer<>() {
        @Override public void write(VxByteBuf buf, Integer value) { buf.writeVarInt(value); }
        @Override public Integer read(VxByteBuf buf) { return buf.readVarInt(); }
        @Override public Integer copy(Integer value) { return value; }
    });

    public static final VxDataSerializer<Float> FLOAT = register(new VxDataSerializer<>() {
        @Override public void write(VxByteBuf buf, Float value) { buf.writeFloat(value); }
        @Override public Float read(VxByteBuf buf) { return buf.readFloat(); }
        @Override public Float copy(Float value) { return value; }
    });

    public static final VxDataSerializer<Boolean> BOOLEAN = register(new VxDataSerializer<>() {
        @Override public void write(VxByteBuf buf, Boolean value) { buf.writeBoolean(value); }
        @Override public Boolean read(VxByteBuf buf) { return buf.readBoolean(); }
        @Override public Boolean copy(Boolean value) { return value; }
    });

    public static final VxDataSerializer<RVec3> RVEC3 = register(new VxDataSerializer<>() {
        @Override public void write(VxByteBuf buf, RVec3 value) { buf.writeRVec3(value); }
        @Override public RVec3 read(VxByteBuf buf) { return buf.readRVec3(); }
        @Override public RVec3 copy(RVec3 value) { return new RVec3(value); }
    });

    public static final VxDataSerializer<Quat> QUAT = register(new VxDataSerializer<>() {
        @Override public void write(VxByteBuf buf, Quat value) { buf.writeQuat(value); }
        @Override public Quat read(VxByteBuf buf) { return buf.readQuat(); }
        @Override public Quat copy(Quat value) { return new Quat(value); }
    });

    private VxDataSerializers() {}

    private static <T> VxDataSerializer<T> register(VxDataSerializer<T> serializer) {
        REGISTRY.put(nextId++, serializer);
        return serializer;
    }

    public static VxDataSerializer<?> get(int id) {
        return REGISTRY.get(id);
    }
}