package net.xmx.xbullet.math;

import com.github.stephengold.joltjni.RMat44;
import org.joml.Matrix4f;

public final class MatrixUtil {

    private MatrixUtil() {}

    public static Matrix4f convert(RMat44 joltMatrix) {
        Matrix4f jomlMatrix = new Matrix4f();

        jomlMatrix.m00((float) joltMatrix.getElement(0, 0));
        jomlMatrix.m01((float) joltMatrix.getElement(1, 0));
        jomlMatrix.m02((float) joltMatrix.getElement(2, 0));
        jomlMatrix.m03((float) joltMatrix.getElement(3, 0));

        jomlMatrix.m10((float) joltMatrix.getElement(0, 1));
        jomlMatrix.m11((float) joltMatrix.getElement(1, 1));
        jomlMatrix.m12((float) joltMatrix.getElement(2, 1));
        jomlMatrix.m13((float) joltMatrix.getElement(3, 1));

        jomlMatrix.m20((float) joltMatrix.getElement(0, 2));
        jomlMatrix.m21((float) joltMatrix.getElement(1, 2));
        jomlMatrix.m22((float) joltMatrix.getElement(2, 2));
        jomlMatrix.m23((float) joltMatrix.getElement(3, 2));

        jomlMatrix.m30((float) joltMatrix.getElement(0, 3));
        jomlMatrix.m31((float) joltMatrix.getElement(1, 3));
        jomlMatrix.m32((float) joltMatrix.getElement(2, 3));
        jomlMatrix.m33((float) joltMatrix.getElement(3, 3));

        return jomlMatrix;
    }
}