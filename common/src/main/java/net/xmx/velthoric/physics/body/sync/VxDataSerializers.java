/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.sync;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.ESpringMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.ragdoll.VxBodyPart;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A registry holding standard implementations of {@link VxDataSerializer} for common data types.
 *
 * @author xI-Mx-Ix
 */
public final class VxDataSerializers {
    private static final Int2ObjectMap<VxDataSerializer<?>> REGISTRY = new Int2ObjectOpenHashMap<>();
    private static int nextId = 0;

    public static final VxDataSerializer<Byte> BYTE = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, Byte value) {
            buf.writeByte(value);
        }

        @Override
        public Byte read(VxByteBuf buf) {
            return buf.readByte();
        }

        @Override
        public Byte copy(Byte value) {
            return value;
        }
    });

    public static final VxDataSerializer<Integer> INTEGER = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, Integer value) {
            buf.writeVarInt(value);
        }

        @Override
        public Integer read(VxByteBuf buf) {
            return buf.readVarInt();
        }

        @Override
        public Integer copy(Integer value) {
            return value;
        }
    });

    public static final VxDataSerializer<Float> FLOAT = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, Float value) {
            buf.writeFloat(value);
        }

        @Override
        public Float read(VxByteBuf buf) {
            return buf.readFloat();
        }

        @Override
        public Float copy(Float value) {
            return value;
        }
    });

    public static final VxDataSerializer<Boolean> BOOLEAN = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, Boolean value) {
            buf.writeBoolean(value);
        }

        @Override
        public Boolean read(VxByteBuf buf) {
            return buf.readBoolean();
        }

        @Override
        public Boolean copy(Boolean value) {
            return value;
        }
    });

    public static final VxDataSerializer<RVec3> RVEC3 = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, RVec3 value) {
            buf.writeRVec3(value);
        }

        @Override
        public RVec3 read(VxByteBuf buf) {
            return buf.readRVec3();
        }

        @Override
        public RVec3 copy(RVec3 value) {
            return new RVec3(value);
        }
    });

    public static final VxDataSerializer<Quat> QUAT = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, Quat value) {
            buf.writeQuat(value);
        }

        @Override
        public Quat read(VxByteBuf buf) {
            return buf.readQuat();
        }

        @Override
        public Quat copy(Quat value) {
            return new Quat(value);
        }
    });

    public static final VxDataSerializer<Vec3> VEC3 = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, Vec3 value) {
            buf.writeJoltVec3(value);
        }

        @Override
        public Vec3 read(VxByteBuf buf) {
            return buf.readJoltVec3();
        }

        @Override
        public Vec3 copy(Vec3 value) {
            return new Vec3(value);
        }
    });

    public static final VxDataSerializer<Vec4> VEC4 = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, Vec4 value) {
            buf.writeVec4(value);
        }

        @Override
        public Vec4 read(VxByteBuf buf) {
            return buf.readVec4();
        }

        @Override
        public Vec4 copy(Vec4 value) {
            return new Vec4(value);
        }
    });

    public static final VxDataSerializer<Float2> FLOAT2 = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, Float2 value) {
            buf.writeFloat2(value);
        }

        @Override
        public Float2 read(VxByteBuf buf) {
            return buf.readFloat2();
        }

        @Override
        public Float2 copy(Float2 value) {
            return new Float2(value);
        }
    });

    public static final VxDataSerializer<Float3> FLOAT3 = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, Float3 value) {
            buf.writeFloat3(value);
        }

        @Override
        public Float3 read(VxByteBuf buf) {
            return buf.readFloat3();
        }

        @Override
        public Float3 copy(Float3 value) {
            return new Float3(value);
        }
    });

    public static final VxDataSerializer<UVec4> UVEC4 = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, UVec4 value) {
            buf.writeUVec4(value);
        }

        @Override
        public UVec4 read(VxByteBuf buf) {
            return buf.readUVec4();
        }

        @Override
        public UVec4 copy(UVec4 value) {
            return new UVec4(value);
        }
    });

    public static final VxDataSerializer<Plane> PLANE = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, Plane value) {
            buf.writePlane(value);
        }

        @Override
        public Plane read(VxByteBuf buf) {
            return buf.readPlane();
        }

        @Override
        public Plane copy(Plane value) {
            return new Plane(value);
        }
    });

    public static final VxDataSerializer<Color> COLOR = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, Color value) {
            buf.writeColor(value);
        }

        @Override
        public Color read(VxByteBuf buf) {
            return buf.readColor();
        }

        @Override
        public Color copy(Color value) {
            return new Color(value);
        }
    });

    public static final VxDataSerializer<VertexList> VERTEX_LIST = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, VertexList value) {
            buf.writeVertexList(value);
        }

        @Override
        public VertexList read(VxByteBuf buf) {
            return buf.readVertexList();
        }

        @Override
        public VertexList copy(VertexList value) {
            VertexList copy = new VertexList();
            int size = value.size();
            copy.resize(size);
            for (int i = 0; i < size; i++) {
                copy.set(i, new Float3(value.get(i)));
            }
            return copy;
        }
    });

    public static final VxDataSerializer<List<WheelSettingsWv>> WHEEL_SETTINGS_LIST = register(new VxDataSerializer<>() {

        private final VxDataSerializer<WheelSettingsWv> WHEEL_SETTINGS_WV_MANUAL = new VxDataSerializer<>() {
            @Override
            public void write(VxByteBuf buf, WheelSettingsWv value) {

                buf.writeJoltVec3(value.getPosition());
                buf.writeFloat(value.getRadius());
                buf.writeFloat(value.getWidth());
                buf.writeJoltVec3(value.getSuspensionDirection());
                buf.writeFloat(value.getSuspensionMinLength());
                buf.writeFloat(value.getSuspensionMaxLength());
                buf.writeFloat(value.getSuspensionPreloadLength());
                buf.writeJoltVec3(value.getSuspensionForcePoint());
                buf.writeBoolean(value.getEnableSuspensionForcePoint());
                buf.writeJoltVec3(value.getSteeringAxis());
                buf.writeJoltVec3(value.getWheelUp());
                buf.writeJoltVec3(value.getWheelForward());

                SpringSettings spring = value.getSuspensionSpring();
                buf.writeVarInt(spring.getMode().ordinal());
                buf.writeFloat(spring.getFrequency());
                buf.writeFloat(spring.getDamping());
                buf.writeBoolean(spring.hasStiffness());
                if (spring.hasStiffness()) {
                    buf.writeFloat(spring.getStiffness());
                }

                buf.writeFloat(value.getMaxSteerAngle());
                buf.writeFloat(value.getMaxBrakeTorque());
                buf.writeFloat(value.getMaxHandBrakeTorque());
            }

            @Override
            public WheelSettingsWv read(VxByteBuf buf) {
                WheelSettingsWv settings = new WheelSettingsWv();

                settings.setPosition(buf.readJoltVec3());
                settings.setRadius(buf.readFloat());
                settings.setWidth(buf.readFloat());
                settings.setSuspensionDirection(buf.readJoltVec3());
                settings.setSuspensionMinLength(buf.readFloat());
                settings.setSuspensionMaxLength(buf.readFloat());
                settings.setSuspensionPreloadLength(buf.readFloat());
                settings.setSuspensionForcePoint(buf.readJoltVec3());
                settings.setEnableSuspensionForcePoint(buf.readBoolean());
                settings.setSteeringAxis(buf.readJoltVec3());
                settings.setWheelUp(buf.readJoltVec3());
                settings.setWheelForward(buf.readJoltVec3());

                SpringSettings spring = settings.getSuspensionSpring();
                spring.setMode(ESpringMode.values()[buf.readVarInt()]);
                spring.setFrequency(buf.readFloat());
                spring.setDamping(buf.readFloat());
                if (buf.readBoolean()) {
                    spring.setStiffness(buf.readFloat());
                }

                settings.setMaxSteerAngle(buf.readFloat());
                settings.setMaxBrakeTorque(buf.readFloat());
                settings.setMaxHandBrakeTorque(buf.readFloat());

                return settings;
            }

            @Override
            public WheelSettingsWv copy(WheelSettingsWv value) {

                return new WheelSettingsWv(value);
            }
        };

        @Override
        public void write(VxByteBuf buf, List<WheelSettingsWv> value) {
            buf.writeVarInt(value.size());
            for (WheelSettingsWv settings : value) {
                WHEEL_SETTINGS_WV_MANUAL.write(buf, settings);
            }
        }

        @Override
        public List<WheelSettingsWv> read(VxByteBuf buf) {
            int size = buf.readVarInt();
            List<WheelSettingsWv> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(WHEEL_SETTINGS_WV_MANUAL.read(buf));
            }
            return list;
        }

        @Override
        public List<WheelSettingsWv> copy(List<WheelSettingsWv> value) {
            return value.stream()
                    .map(WHEEL_SETTINGS_WV_MANUAL::copy)
                    .collect(Collectors.toList());
        }
    });

    public static final VxDataSerializer<String> STRING = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, String value) {
            buf.writeUtf(value);
        }

        @Override
        public String read(VxByteBuf buf) {
            return buf.readUtf();
        }

        @Override
        public String copy(String value) {
            return value;
        }
    });

    public static final VxDataSerializer<UUID> UUID = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, UUID value) {
            buf.writeUUID(value);
        }

        @Override
        public UUID read(VxByteBuf buf) {
            return buf.readUUID();
        }

        @Override
        public UUID copy(UUID value) {
            return value;
        }
    });

    public static final VxDataSerializer<VxBodyPart> BODY_PART = register(new VxDataSerializer<>() {
        @Override
        public void write(VxByteBuf buf, VxBodyPart value) {
            buf.writeEnum(value);
        }

        @Override
        public VxBodyPart read(VxByteBuf buf) {
            return buf.readEnum(VxBodyPart.class);
        }

        @Override
        public VxBodyPart copy(VxBodyPart value) {
            return value;
        }
    });


    private VxDataSerializers() {
    }

    private static <T> VxDataSerializer<T> register(VxDataSerializer<T> serializer) {
        REGISTRY.put(nextId++, serializer);
        return serializer;
    }

    public static VxDataSerializer<?> get(int id) {
        return REGISTRY.get(id);
    }
}