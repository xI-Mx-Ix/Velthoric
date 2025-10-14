/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.xmx.velthoric.physics.buoyancy.VxFluidType;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * An abstract base class providing utility methods for growing arrays used in
 * Structure of Arrays (SoA) data stores. This avoids boilerplate code in concrete
 * data store implementations.
 *
 * @author xI-Mx-Ix
 */
public abstract class AbstractDataStore {

    /**
     * Grows a long array, preserving its contents.
     *
     * @param src  The source array (can be null).
     * @param size The desired new size.
     * @return A new array of the specified size.
     */
    protected long[] grow(long[] src, int size) {
        return src == null ? new long[size] : Arrays.copyOf(src, size);
    }

    /**
     * Grows a float array, preserving its contents.
     *
     * @param src  The source array (can be null).
     * @param size The desired new size.
     * @return A new array of the specified size.
     */
    protected float[] grow(float[] src, int size) {
        return src == null ? new float[size] : Arrays.copyOf(src, size);
    }

    /**
     * Grows a boolean array, preserving its contents.
     *
     * @param src  The source array (can be null).
     * @param size The desired new size.
     * @return A new array of the specified size.
     */
    protected boolean[] grow(boolean[] src, int size) {
        return src == null ? new boolean[size] : Arrays.copyOf(src, size);
    }

    /**
     * Grows an array of float arrays, preserving its contents.
     *
     * @param src  The source array (can be null).
     * @param size The desired new size.
     * @return A new array of the specified size.
     */
    protected float[][] grow(float[][] src, int size) {
        return src == null ? new float[size][] : Arrays.copyOf(src, size);
    }

    /**
     * Grows an EBodyType enum array, preserving its contents.
     *
     * @param src  The source array (can be null).
     * @param size The desired new size.
     * @return A new array of the specified size.
     */
    protected EBodyType[] grow(EBodyType[] src, int size) {
        return src == null ? new EBodyType[size] : Arrays.copyOf(src, size);
    }

    /**
     * Grows a ByteBuffer array, preserving its contents.
     *
     * @param src  The source array (can be null).
     * @param size The desired new size.
     * @return A new array of the specified size.
     */
    protected ByteBuffer[] grow(ByteBuffer[] src, int size) {
        return src == null ? new ByteBuffer[size] : Arrays.copyOf(src, size);
    }

    /**
     * Grows a generic object array, preserving its contents.
     *
     * @param src  The source array (can be null).
     * @param size The desired new size.
     * @return A new array of the specified size.
     */
    @SuppressWarnings("unchecked")
    protected <T> T[] grow(T[] src, int size) {
        return src == null ? (T[]) new Object[size] : Arrays.copyOf(src, size);
    }

    /**
     * Grows an RVec3 array, preserving its contents and initializing new elements.
     *
     * @param src  The source array (can be null).
     * @param size The desired new size.
     * @return A new array of the specified size.
     */
    protected RVec3[] grow(RVec3[] src, int size) {
        int oldSize = (src == null) ? 0 : src.length;
        RVec3[] dest = Arrays.copyOf(src != null ? src : new RVec3[0], size);

        for (int i = oldSize; i < size; i++) {
            dest[i] = new RVec3();
        }
        return dest;
    }

    /**
     * Grows an int array, preserving its contents.
     *
     * @param src  The source array (can be null).
     * @param size The desired new size.
     * @return A new array of the specified size.
     */
    protected int[] grow(int[] src, int size) {
        return src == null ? new int[size] : Arrays.copyOf(src, size);
    }

    /**
     * Grows a ShapeRefC array, preserving its contents.
     *
     * @param src  The source array (can be null).
     * @param size The desired new size.
     * @return A new array of the specified size.
     */
    protected ShapeRefC[] grow(ShapeRefC[] src, int size) {
        return src == null ? new ShapeRefC[size] : Arrays.copyOf(src, size);
    }

    /**
     * Grows a VxFluidType enum array, preserving its contents.
     *
     * @param src  The source array (can be null).
     * @param size The desired new size.
     * @return A new array of the specified size.
     */
    protected VxFluidType[] grow(VxFluidType[] src, int size) {
        return src == null ? new VxFluidType[size] : Arrays.copyOf(src, size);
    }
}