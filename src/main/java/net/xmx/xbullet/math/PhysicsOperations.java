package net.xmx.xbullet.math;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.QuatArg;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import net.minecraft.util.Mth;

public class PhysicsOperations {

    public static Quat slerp(QuatArg q1, QuatArg q2, float alpha, Quat store) {
        if (store == null) {
            store = new Quat();
        }

        double dot = (q1.getX() * q2.getX()) + (q1.getY() * q2.getY()) + (q1.getZ() * q2.getZ()) + (q1.getW() * q2.getW());

        Quat q2b = new Quat(q2);

        if (dot < 0.0f) {
            dot = -dot;
            q2b.set(q2.conjugated());
        }

        if (dot > 0.9995f) {
            float w = 1.0f - alpha;
            store.set(
                    (w * q1.getX()) + (alpha * q2b.getX()),
                    (w * q1.getY()) + (alpha * q2b.getY()),
                    (w * q1.getZ()) + (alpha * q2b.getZ()),
                    (w * q1.getW()) + (alpha * q2b.getW())
            );
        } else {
            double theta = Math.acos(dot);
            double sinTheta = Math.sin(theta);
            double w = Math.sin((1.0 - alpha) * theta) / sinTheta;
            double b = Math.sin(alpha * theta) / sinTheta;
            store.set(
                    (float) (w * q1.getX() + b * q2b.getX()),
                    (float) (w * q1.getY() + b * q2b.getY()),
                    (float) (w * q1.getZ() + b * q2b.getZ()),
                    (float) (w * q1.getW() + b * q2b.getW())
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

    public static RVec3 cubicHermite(RVec3Arg p0, Vec3 v0, RVec3Arg p1, Vec3 v1, float t, float dt, RVec3 store) {
        if (store == null) {
            store = new RVec3();
        }

        float t2 = t * t;
        float t3 = t2 * t;
        float h1 = 2f * t3 - 3f * t2 + 1f;
        float h2 = -2f * t3 + 3f * t2;
        float h3 = t3 - 2f * t2 + t;
        float h4 = t3 - t2;

        double x = h1 * p0.xx() + h2 * p1.xx() + h3 * (v0.getX() * dt) + h4 * (v1.getX() * dt);
        double y = h1 * p0.yy() + h2 * p1.yy() + h3 * (v0.getY() * dt) + h4 * (v1.getY() * dt);
        double z = h1 * p0.zz() + h2 * p1.zz() + h3 * (v0.getZ() * dt) + h4 * (v1.getZ() * dt);

        store.set(x, y, z);
        return store;
    }

    public static Vec3 quatToEulerAngles(QuatArg q) {
        Vec3 angles = new Vec3();

        double sinr_cosp = 2.0 * (q.getW() * q.getX() + q.getY() * q.getZ());
        double cosr_cosp = 1.0 - 2.0 * (q.getX() * q.getX() + q.getY() * q.getY());
        angles.setX((float) Math.atan2(sinr_cosp, cosr_cosp));

        double sinp = 2.0 * (q.getW() * q.getY() - q.getZ() * q.getX());
        if (Math.abs(sinp) >= 1) {
            angles.setY((float) Math.copySign(Math.PI / 2, sinp));
        } else {
            angles.setY((float) Math.asin(sinp));
        }

        double siny_cosp = 2.0 * (q.getW() * q.getZ() + q.getX() * q.getY());
        double cosy_cosp = 1.0 - 2.0 * (q.getY() * q.getY() + q.getZ() * q.getZ());
        angles.setZ((float) Math.atan2(siny_cosp, cosy_cosp));

        return angles;
    }

    public static void extrapolateRotation(Quat startRot, Vec3 angVel, float dt, Quat out) {
        float angle = angVel.length() * dt;
        if (angle > 1e-6) {
            Vec3 axis = angVel.normalized();
            Quat deltaRot = new Quat(axis, angle);
            Quat finalRot = Op.star(deltaRot, startRot);

            Quat normalizedFinal = finalRot.normalized();
            out.set(normalizedFinal);

        } else {
            out.set(startRot.getX(), startRot.getY(), startRot.getZ(), startRot.getW());
        }
    }
}