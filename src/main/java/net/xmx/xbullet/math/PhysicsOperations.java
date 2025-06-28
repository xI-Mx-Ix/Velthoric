package net.xmx.xbullet.math;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import net.minecraft.util.Mth;

public class PhysicsOperations {

    public static Quat slerp(Quat q1, Quat q2, float alpha, Quat store) {
        if (store == null) {
            store = new Quat();
        }

        float dot = (q1.getX() * q2.getX()) + (q1.getY() * q2.getY()) + (q1.getZ() * q2.getZ()) + (q1.getW() * q2.getW());
        float a = alpha;
        Quat q2b = q2;

        if (dot < 0.0f) {
            dot = -dot;
            q2b = q2.conjugated();
        }

        if (dot > 0.9995f) {
            float w = 1.0f - a;
            store.set(
                (w * q1.getX()) + (a * q2b.getX()),
                (w * q1.getY()) + (a * q2b.getY()),
                (w * q1.getZ()) + (a * q2b.getZ()),
                (w * q1.getW()) + (a * q2b.getW())
            );
        } else {
            float theta = (float) Math.acos(dot);
            float sinTheta = (float) Math.sin(theta);
            float w = (float) Math.sin((1.0f - a) * theta) / sinTheta;
            float b = (float) Math.sin(a * theta) / sinTheta;
            store.set(
                (w * q1.getX()) + (b * q2b.getX()),
                (w * q1.getY()) + (b * q2b.getY()),
                (w * q1.getZ()) + (b * q2b.getZ()),
                (w * q1.getW()) + (b * q2b.getW())
            );
        }
        store.set(store.normalized());
        return store;
    }

    public static RVec3 lerp(RVec3Arg v1, RVec3Arg v2, float alpha, RVec3 store) {
        if (store == null) {
            store = new RVec3();
        }
        double x = Mth.lerp(alpha, v1.xx(), v2.xx());
        double y = Mth.lerp(alpha, v1.yy(), v2.yy());
        double z = Mth.lerp(alpha, v1.zz(), v2.zz());
        store.set(x, y, z);
        return store;
    }
}