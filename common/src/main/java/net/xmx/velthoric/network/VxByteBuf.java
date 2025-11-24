/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.network;

import com.github.stephengold.joltjni.Color;
import com.github.stephengold.joltjni.Float2;
import com.github.stephengold.joltjni.Float3;
import com.github.stephengold.joltjni.Plane;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.UVec4;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.Vec4;
import com.github.stephengold.joltjni.VertexList;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;

/**
 * A custom network buffer extension for Velthoric.
 * <p>
 * This class extends Minecraft's {@link FriendlyByteBuf} to provide direct support
 * for serializing and deserializing Jolt Physics data types (Vectors, Quaternions, etc.)
 * and additional variable-length primitive types.
 *
 * @author xI-Mx-Ix
 */
public class VxByteBuf extends FriendlyByteBuf {

    /**
     * Wraps an existing Netty ByteBuf.
     *
     * @param source The underlying buffer.
     */
    public VxByteBuf(ByteBuf source) {
        super(source);
    }

    /**
     * Reads a Jolt Physics single-precision 3D vector.
     *
     * @return A new {@link Vec3} instance.
     */
    public Vec3 readJoltVec3() {
        float x = this.readFloat();
        float y = this.readFloat();
        float z = this.readFloat();
        return new Vec3(x, y, z);
    }

    /**
     * Writes a Jolt Physics single-precision 3D vector.
     *
     * @param vec The vector to write.
     */
    public void writeJoltVec3(Vec3 vec) {
        this.writeFloat(vec.getX());
        this.writeFloat(vec.getY());
        this.writeFloat(vec.getZ());
    }

    /**
     * Reads a Jolt Physics Quaternion.
     *
     * @return A new {@link Quat} instance (x, y, z, w).
     */
    public Quat readQuat() {
        float x = this.readFloat();
        float y = this.readFloat();
        float z = this.readFloat();
        float w = this.readFloat();
        return new Quat(x, y, z, w);
    }

    /**
     * Writes a Jolt Physics Quaternion.
     *
     * @param quat The quaternion to write.
     */
    public void writeQuat(Quat quat) {
        this.writeFloat(quat.getX());
        this.writeFloat(quat.getY());
        this.writeFloat(quat.getZ());
        this.writeFloat(quat.getW());
    }

    /**
     * Reads a Jolt Physics Float3 (packed 3-component float).
     *
     * @return A new {@link Float3} instance.
     */
    public Float3 readFloat3() {
        float x = this.readFloat();
        float y = this.readFloat();
        float z = this.readFloat();
        return new Float3(x, y, z);
    }

    /**
     * Writes a Jolt Physics Float3.
     *
     * @param f3 The Float3 to write.
     */
    public void writeFloat3(Float3 f3) {
        this.writeFloat(f3.get(0));
        this.writeFloat(f3.get(1));
        this.writeFloat(f3.get(2));
    }

    /**
     * Reads a Jolt Physics double-precision 3D vector (Real-Vector).
     * Used primarily for world-space positions.
     *
     * @return A new {@link RVec3} instance.
     */
    public RVec3 readRVec3() {
        double x = this.readDouble();
        double y = this.readDouble();
        double z = this.readDouble();
        return new RVec3(x, y, z);
    }

    /**
     * Writes a Jolt Physics double-precision 3D vector.
     *
     * @param rvec The RVec3 to write.
     */
    public void writeRVec3(RVec3 rvec) {
        this.writeDouble(rvec.xx());
        this.writeDouble(rvec.yy());
        this.writeDouble(rvec.zz());
    }

    /**
     * Reads a Jolt Physics Color (RGBA).
     *
     * @return A new {@link Color} instance.
     */
    public Color readColor() {
        byte r = this.readByte();
        byte g = this.readByte();
        byte b = this.readByte();
        byte a = this.readByte();
        // Convert signed byte to unsigned int (0-255)
        return new Color(r & 0xFF, g & 0xFF, b & 0xFF, a & 0xFF);
    }

    /**
     * Writes a Jolt Physics Color.
     *
     * @param color The color to write.
     */
    public void writeColor(Color color) {
        this.writeByte(color.getR());
        this.writeByte(color.getG());
        this.writeByte(color.getB());
        this.writeByte(color.getA());
    }

    /**
     * Reads a Jolt Physics Float2 (2-component float).
     *
     * @return A new {@link Float2} instance.
     */
    public Float2 readFloat2() {
        float x = this.readFloat();
        float y = this.readFloat();
        return new Float2(x, y);
    }

    /**
     * Writes a Jolt Physics Float2.
     *
     * @param f2 The Float2 to write.
     */
    public void writeFloat2(Float2 f2) {
        this.writeFloat(f2.get(0));
        this.writeFloat(f2.get(1));
    }

    /**
     * Reads a Jolt Physics Plane equation (Normal + Constant).
     *
     * @return A new {@link Plane} instance.
     */
    public Plane readPlane() {
        float nx = this.readFloat();
        float ny = this.readFloat();
        float nz = this.readFloat();
        float c = this.readFloat();
        return new Plane(nx, ny, nz, c);
    }

    /**
     * Writes a Jolt Physics Plane.
     *
     * @param plane The plane to write.
     */
    public void writePlane(Plane plane) {
        this.writeFloat(plane.getNormalX());
        this.writeFloat(plane.getNormalY());
        this.writeFloat(plane.getNormalZ());
        this.writeFloat(plane.getConstant());
    }

    /**
     * Reads a Jolt Physics UVec4 (4-component unsigned integer vector).
     *
     * @return A new {@link UVec4} instance.
     */
    public UVec4 readUVec4() {
        int x = this.readInt();
        int y = this.readInt();
        int z = this.readInt();
        int w = this.readInt();
        return new UVec4(x, y, z, w);
    }

    /**
     * Writes a Jolt Physics UVec4.
     *
     * @param uvec The UVec4 to write.
     */
    public void writeUVec4(UVec4 uvec) {
        this.writeInt(uvec.getX());
        this.writeInt(uvec.getY());
        this.writeInt(uvec.getZ());
        this.writeInt(uvec.getW());
    }

    /**
     * Reads a Jolt Physics Vec4 (4-component float vector).
     *
     * @return A new {@link Vec4} instance.
     */
    public Vec4 readVec4() {
        float x = this.readFloat();
        float y = this.readFloat();
        float z = this.readFloat();
        float w = this.readFloat();
        return new Vec4(x, y, z, w);
    }

    /**
     * Writes a Jolt Physics Vec4.
     *
     * @param vec The Vec4 to write.
     */
    public void writeVec4(Vec4 vec) {
        this.writeFloat(vec.getX());
        this.writeFloat(vec.getY());
        this.writeFloat(vec.getZ());
        this.writeFloat(vec.getW());
    }

    /**
     * Reads a list of vertices.
     * <p>
     * The format is: [VarInt size] -> [Float3] * size.
     *
     * @return A new {@link VertexList} populated with the read data.
     */
    public VertexList readVertexList() {
        int size = this.readVarInt();
        VertexList list = new VertexList();
        list.resize(size);
        for (int i = 0; i < size; i++) {
            list.set(i, readFloat3());
        }
        return list;
    }

    /**
     * Writes a list of vertices.
     *
     * @param list The VertexList to write.
     */
    public void writeVertexList(VertexList list) {
        this.writeVarInt(list.size());
        for (int i = 0; i < list.size(); i++) {
            writeFloat3(list.get(i));
        }
    }

    // =================================================================
    // Variable Length Primitives (VarInt, VarLong)
    // =================================================================

    /**
     * Calculates the number of bytes required to encode a VarInt.
     *
     * @param value The integer value.
     * @return The number of bytes (1-5).
     */
    public static int varIntSize(int value) {
        int size = 0;
        do {
            value >>>= 7;
            size++;
        } while (value != 0);
        return size;
    }

    /**
     * Calculates the number of bytes required to encode a VarLong.
     *
     * @param value The long value.
     * @return The number of bytes (1-10).
     */
    public static int varLongSize(long value) {
        int size = 0;
        do {
            value >>>= 7;
            size++;
        } while (value != 0);
        return size;
    }

    /**
     * Reads a variable-length long from the buffer.
     * Note: FriendlyByteBuf already has readVarLong, but this is provided for completeness
     * or custom implementations if needed.
     *
     * @return The decoded long value.
     */
    @Override
    public long readVarLong() {
        long value = 0;
        int position = 0;
        byte currentByte;

        while (true) {
            currentByte = this.readByte();
            value |= (long) (currentByte & 127) << position;

            if ((currentByte & 128) == 0) break;

            position += 7;
            if (position >= 64) throw new RuntimeException("VarLong is too big");
        }

        return value;
    }

    /**
     * Writes a variable-length long to the buffer.
     *
     * @param value The long value to write.
     * @return The ByteBuf instance.
     */
    @Override
    public FriendlyByteBuf writeVarLong(long value) {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                this.writeByte((int) value);
                return this;
            } else {
                this.writeByte((int) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }
}