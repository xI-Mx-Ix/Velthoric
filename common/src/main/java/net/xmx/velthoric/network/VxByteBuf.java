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
 * @author xI-Mx-Ix
 */
public class VxByteBuf extends FriendlyByteBuf {

    public VxByteBuf(ByteBuf source) {
        super(source);
    }

    public Vec3 readVec3() {
        float x = this.readFloat();
        float y = this.readFloat();
        float z = this.readFloat();
        return new Vec3(x, y, z);
    }

    public void writeVec3(Vec3 vec) {
        this.writeFloat(vec.getX());
        this.writeFloat(vec.getY());
        this.writeFloat(vec.getZ());
    }

    public Quat readQuat() {
        float x = this.readFloat();
        float y = this.readFloat();
        float z = this.readFloat();
        float w = this.readFloat();
        return new Quat(x, y, z, w);
    }

    public void writeQuat(Quat quat) {
        this.writeFloat(quat.getX());
        this.writeFloat(quat.getY());
        this.writeFloat(quat.getZ());
        this.writeFloat(quat.getW());
    }

    public Float3 readFloat3() {
        float x = this.readFloat();
        float y = this.readFloat();
        float z = this.readFloat();
        return new Float3(x, y, z);
    }

    public void writeFloat3(Float3 f3) {
        this.writeFloat(f3.get(0));
        this.writeFloat(f3.get(1));
        this.writeFloat(f3.get(2));
    }

    public RVec3 readRVec3() {
        double x = this.readDouble();
        double y = this.readDouble();
        double z = this.readDouble();
        return new RVec3(x, y, z);
    }

    public void writeRVec3(RVec3 rvec) {
        this.writeDouble(rvec.xx());
        this.writeDouble(rvec.yy());
        this.writeDouble(rvec.zz());
    }

    public Color readColor() {
        byte r = this.readByte();
        byte g = this.readByte();
        byte b = this.readByte();
        byte a = this.readByte();
        return new Color(r & 0xFF, g & 0xFF, b & 0xFF, a & 0xFF);
    }

    public void writeColor(Color color) {
        this.writeByte(color.getR());
        this.writeByte(color.getG());
        this.writeByte(color.getB());
        this.writeByte(color.getA());
    }

    public Float2 readFloat2() {
        float x = this.readFloat();
        float y = this.readFloat();
        return new Float2(x, y);
    }

    public void writeFloat2(Float2 f2) {
        this.writeFloat(f2.get(0));
        this.writeFloat(f2.get(1));
    }

    public Plane readPlane() {
        float nx = this.readFloat();
        float ny = this.readFloat();
        float nz = this.readFloat();
        float c = this.readFloat();
        return new Plane(nx, ny, nz, c);
    }

    public void writePlane(Plane plane) {
        this.writeFloat(plane.getNormalX());
        this.writeFloat(plane.getNormalY());
        this.writeFloat(plane.getNormalZ());
        this.writeFloat(plane.getConstant());
    }

    public UVec4 readUVec4() {
        int x = this.readInt();
        int y = this.readInt();
        int z = this.readInt();
        int w = this.readInt();
        return new UVec4(x, y, z, w);
    }

    public void writeUVec4(UVec4 uvec) {
        this.writeInt(uvec.getX());
        this.writeInt(uvec.getY());
        this.writeInt(uvec.getZ());
        this.writeInt(uvec.getW());
    }

    public Vec4 readVec4() {
        float x = this.readFloat();
        float y = this.readFloat();
        float z = this.readFloat();
        float w = this.readFloat();
        return new Vec4(x, y, z, w);
    }

    public void writeVec4(Vec4 vec) {
        this.writeFloat(vec.getX());
        this.writeFloat(vec.getY());
        this.writeFloat(vec.getZ());
        this.writeFloat(vec.getW());
    }

    public VertexList readVertexList() {
        int size = this.readVarInt();
        VertexList list = new VertexList();
        list.resize(size);
        for (int i = 0; i < size; i++) {
            list.set(i, readFloat3());
        }
        return list;
    }

    public void writeVertexList(VertexList list) {
        this.writeVarInt(list.size());
        for (int i = 0; i < list.size(); i++) {
            writeFloat3(list.get(i));
        }
    }
}
