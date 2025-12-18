/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.math;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * A utility class providing static methods for converting between various mathematical types
 * from different libraries: Jolt physics (joltjni), JOML, and Minecraft's world simulation types.
 * This class centralizes type conversions to ensure consistency, handle precision differences,
 * and reduce boilerplate code.
 * The class cannot be instantiated.
 *
 * @author xI-Mx-Ix
 */
public final class VxConversions {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private VxConversions() {
    }

    // --- To JOML Conversions ---

    /**
     * Converts a Jolt {@link Vec3} (single-precision) to a JOML {@link Vector3f}.
     * This method creates a new {@link Vector3f} instance.
     *
     * @param in The source Jolt {@link Vec3}.
     * @return A new {@link Vector3f} instance with the converted values.
     */
    public static Vector3f toJoml(Vec3 in) {
        return toJoml(in, new Vector3f());
    }

    /**
     * Converts a Jolt {@link Vec3} (single-precision) to a JOML {@link Vector3f}.
     * This method populates a pre-existing {@link Vector3f} instance for performance.
     *
     * @param in  The source Jolt {@link Vec3}.
     * @param out The target JOML {@link Vector3f} to store the result in.
     * @return The populated {@link Vector3f} (the same instance as {@code out}).
     */
    public static Vector3f toJoml(Vec3 in, Vector3f out) {
        return out.set(in.getX(), in.getY(), in.getZ());
    }

    /**
     * Converts a Jolt {@link RVec3} (double-precision) to a JOML {@link Vector3d}.
     * This method creates a new {@link Vector3d} instance.
     *
     * @param in The source Jolt {@link RVec3}.
     * @return A new {@link Vector3d} instance with the converted values.
     */
    public static Vector3d toJoml(RVec3 in) {
        return toJoml(in, new Vector3d());
    }

    /**
     * Converts a Jolt {@link RVec3} (double-precision) to a JOML {@link Vector3d}.
     * This method populates a pre-existing {@link Vector3d} instance for performance.
     *
     * @param in  The source Jolt {@link RVec3}.
     * @param out The target JOML {@link Vector3d} to store the result in.
     * @return The populated {@link Vector3d} (the same instance as {@code out}).
     */
    public static Vector3d toJoml(RVec3 in, Vector3d out) {
        return out.set(in.xx(), in.yy(), in.zz());
    }

    /**
     * Converts a Jolt {@link Quat} (single-precision) to a JOML {@link Quaternionf}.
     * This method creates a new {@link Quaternionf} instance.
     *
     * @param in The source Jolt {@link Quat}.
     * @return A new {@link Quaternionf} instance with the converted values.
     */
    public static Quaternionf toJoml(Quat in) {
        return toJoml(in, new Quaternionf());
    }

    /**
     * Converts a Jolt {@link Quat} (single-precision) to a JOML {@link Quaternionf}.
     * This method populates a pre-existing {@link Quaternionf} instance for performance.
     *
     * @param in  The source Jolt {@link Quat}.
     * @param out The target JOML {@link Quaternionf} to store the result in.
     * @return The populated {@link Quaternionf} (the same instance as {@code out}).
     */
    public static Quaternionf toJoml(Quat in, Quaternionf out) {
        return out.set(in.getX(), in.getY(), in.getZ(), in.getW());
    }

    /**
     * Converts a Minecraft {@link net.minecraft.world.phys.Vec3} to a JOML {@link Vector3d}.
     * This method creates a new {@link Vector3d} instance.
     *
     * @param in The source Minecraft {@link net.minecraft.world.phys.Vec3}.
     * @return A new {@link Vector3d} instance with the converted values.
     */
    public static Vector3d toJoml(net.minecraft.world.phys.Vec3 in) {
        return toJoml(in, new Vector3d());
    }

    /**
     * Converts a Minecraft {@link net.minecraft.world.phys.Vec3} to a JOML {@link Vector3d}.
     * This method populates a pre-existing {@link Vector3d} instance for performance.
     *
     * @param in  The source Minecraft {@link net.minecraft.world.phys.Vec3}.
     * @param out The target JOML {@link Vector3d} to store the result in.
     * @return The populated {@link Vector3d} (the same instance as {@code out}).
     */
    public static Vector3d toJoml(net.minecraft.world.phys.Vec3 in, Vector3d out) {
        return out.set(in.x(), in.y(), in.z());
    }

    // --- To Jolt Conversions ---

    /**
     * Converts a JOML {@link Vector3f} to a Jolt {@link Vec3} (single-precision).
     * This method creates a new {@link Vec3} instance.
     *
     * @param in The source JOML {@link Vector3f}.
     * @return A new {@link Vec3} instance with the converted values.
     */
    public static Vec3 toJolt(Vector3f in) {
        return toJolt(in, new Vec3());
    }

    /**
     * Converts a JOML {@link Vector3f} to a Jolt {@link Vec3} (single-precision).
     * This method populates a pre-existing {@link Vec3} instance for performance.
     *
     * @param in  The source JOML {@link Vector3f}.
     * @param out The target Jolt {@link Vec3} to store the result in.
     * @return The populated {@link Vec3} (the same instance as {@code out}).
     */
    public static Vec3 toJolt(Vector3f in, Vec3 out) {
        out.set(in.x, in.y, in.z);
        return out;
    }

    /**
     * Converts a JOML {@link Vector3d} to a Jolt {@link RVec3} (double-precision).
     * This method creates a new {@link RVec3} instance.
     *
     * @param in The source JOML {@link Vector3d}.
     * @return A new {@link RVec3} instance with the converted values.
     */
    public static RVec3 toJolt(Vector3d in) {
        return toJolt(in, new RVec3());
    }

    /**
     * Converts a JOML {@link Vector3d} to a Jolt {@link RVec3} (double-precision).
     * This method populates a pre-existing {@link RVec3} instance for performance.
     *
     * @param in  The source JOML {@link Vector3d}.
     * @param out The target Jolt {@link RVec3} to store the result in.
     * @return The populated {@link RVec3} (the same instance as {@code out}).
     */
    public static RVec3 toJolt(Vector3d in, RVec3 out) {
        out.set(in.x, in.y, in.z);
        return out;
    }

    /**
     * Converts a JOML {@link Quaternionf} to a Jolt {@link Quat} (single-precision).
     * This method creates a new {@link Quat} instance.
     *
     * @param in The source JOML {@link Quaternionf}.
     * @return A new {@link Quat} instance with the converted values.
     */
    public static Quat toJolt(Quaternionf in) {
        return toJolt(in, new Quat());
    }

    /**
     * Converts a JOML {@link Quaternionf} to a Jolt {@link Quat} (single-precision).
     * This method populates a pre-existing {@link Quat} instance for performance.
     *
     * @param in  The source JOML {@link Quaternionf}.
     * @param out The target Jolt {@link Quat} to store the result in.
     * @return The populated {@link Quat} (the same instance as {@code out}).
     */
    public static Quat toJolt(Quaternionf in, Quat out) {
        out.set(in.x, in.y, in.z, in.w);
        return out;
    }

    /**
     * Converts a Minecraft {@link net.minecraft.world.phys.Vec3} to a Jolt {@link RVec3} (double-precision).
     * This method creates a new {@link RVec3} instance.
     *
     * @param in The source Minecraft {@link net.minecraft.world.phys.Vec3}.
     * @return A new {@link RVec3} instance with the converted values.
     */
    public static RVec3 toJolt(net.minecraft.world.phys.Vec3 in) {
        return toJolt(in, new RVec3());
    }

    /**
     * Converts a Minecraft {@link net.minecraft.world.phys.Vec3} to a Jolt {@link RVec3} (double-precision).
     * This method populates a pre-existing {@link RVec3} instance for performance.
     *
     * @param in  The source Minecraft {@link net.minecraft.world.phys.Vec3}.
     * @param out The target Jolt {@link RVec3} to store the result in.
     * @return The populated {@link RVec3} (the same instance as {@code out}).
     */
    public static RVec3 toJolt(net.minecraft.world.phys.Vec3 in, RVec3 out) {
        out.set(in.x(), in.y(), in.z());
        return out;
    }

    // --- To Minecraft Conversions ---

    /**
     * Converts a JOML {@link Vector3d} to a Minecraft {@link net.minecraft.world.phys.Vec3}.
     * Note: A new {@link net.minecraft.world.phys.Vec3} is always returned as it is an immutable type.
     *
     * @param in The source JOML {@link Vector3d}.
     * @return A new Minecraft {@link net.minecraft.world.phys.Vec3} instance.
     */
    public static net.minecraft.world.phys.Vec3 toMinecraft(Vector3d in) {
        return new net.minecraft.world.phys.Vec3(in.x, in.y, in.z);
    }

    /**
     * Converts a JOML {@link Vector3f} to a Minecraft {@link net.minecraft.world.phys.Vec3}.
     * Note: A new {@link net.minecraft.world.phys.Vec3} is always returned as it is an immutable type.
     *
     * @param in The source JOML {@link Vector3f}.
     * @return A new Minecraft {@link net.minecraft.world.phys.Vec3} instance.
     */
    public static net.minecraft.world.phys.Vec3 toMinecraft(Vector3f in) {
        return new net.minecraft.world.phys.Vec3(in.x, in.y, in.z);
    }

    /**
     * Converts a Jolt {@link RVec3} (double-precision) to a Minecraft {@link net.minecraft.world.phys.Vec3}.
     * Note: A new {@link net.minecraft.world.phys.Vec3} is always returned as it is an immutable type.
     *
     * @param in The source Jolt {@link RVec3}.
     * @return A new Minecraft {@link net.minecraft.world.phys.Vec3} instance.
     */
    public static net.minecraft.world.phys.Vec3 toMinecraft(RVec3 in) {
        return new net.minecraft.world.phys.Vec3(in.xx(), in.yy(), in.zz());
    }

    /**
     * Converts a Jolt {@link Vec3} (single-precision) to a Minecraft {@link net.minecraft.world.phys.Vec3}.
     * Note: A new {@link net.minecraft.world.phys.Vec3} is always returned as it is an immutable type.
     *
     * @param in The source Jolt {@link Vec3}.
     * @return A new Minecraft {@link net.minecraft.world.phys.Vec3} instance.
     */
    public static net.minecraft.world.phys.Vec3 toMinecraft(Vec3 in) {
        return new net.minecraft.world.phys.Vec3(in.getX(), in.getY(), in.getZ());
    }

    // --- Matrix Component Extraction ---

    /**
     * Extracts the translation component from a Jolt {@link RMat44} and converts it to a Minecraft {@link net.minecraft.world.phys.Vec3}.
     * Note: A new {@link net.minecraft.world.phys.Vec3} is always returned as it is an immutable type.
     *
     * @param in The source Jolt {@link RMat44} matrix.
     * @return A new Minecraft {@link net.minecraft.world.phys.Vec3} instance representing the translation.
     */
    public static net.minecraft.world.phys.Vec3 getTranslation(RMat44 in) {
        RVec3 translation = in.getTranslation();
        return new net.minecraft.world.phys.Vec3(translation.xx(), translation.yy(), translation.zz());
    }

    /**
     * Extracts the translation component from a Jolt {@link RMat44} and stores it in a JOML {@link Vector3d}.
     *
     * @param in  The source Jolt {@link RMat44} matrix.
     * @param out The target JOML {@link Vector3d} to store the translation in.
     * @return The populated {@link Vector3d} (the same instance as {@code out}).
     */
    public static Vector3d getTranslation(RMat44 in, Vector3d out) {
        RVec3 joltTranslation = in.getTranslation();
        return toJoml(joltTranslation, out);
    }
}