package net.xmx.velthoric.physics.object;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;

import java.nio.ByteBuffer;
import java.util.Arrays;

public abstract class AbstractDataStore {

    protected long[] grow(long[] src, int size) {
        return src == null ? new long[size] : Arrays.copyOf(src, size);
    }

    protected float[] grow(float[] src, int size) {
        return src == null ? new float[size] : Arrays.copyOf(src, size);
    }

    protected boolean[] grow(boolean[] src, int size) {
        return src == null ? new boolean[size] : Arrays.copyOf(src, size);
    }

    protected float[][] grow(float[][] src, int size) {
        return src == null ? new float[size][] : Arrays.copyOf(src, size);
    }

    protected EBodyType[] grow(EBodyType[] src, int size) {
        return src == null ? new EBodyType[size] : Arrays.copyOf(src, size);
    }

    protected ByteBuffer[] grow(ByteBuffer[] src, int size) {
        return src == null ? new ByteBuffer[size] : Arrays.copyOf(src, size);
    }

    @SuppressWarnings("unchecked")
    protected <T> T[] grow(T[] src, int size) {
        return src == null ? (T[]) new Object[size] : Arrays.copyOf(src, size);
    }

    protected RVec3[] grow(RVec3[] src, int size) {
        int oldSize = (src == null) ? 0 : src.length;
        RVec3[] dest = Arrays.copyOf(src != null ? src : new RVec3[0], size);

        for (int i = oldSize; i < size; i++) {
            dest[i] = new RVec3();
        }
        return dest;
    }
}