package net.xmx.velthoric.math;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.QuatArg;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import com.github.stephengold.joltjni.readonly.Vec3Arg;
import net.minecraft.util.Mth;

public class VxOperations {

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

    public static Quat slerp(QuatArg q1, QuatArg q2, float alpha, Quat store) {
        if (store == null) {
            store = new Quat();
        }

        double dot = q1.getX() * q2.getX() + q1.getY() * q2.getY() + q1.getZ() * q2.getZ() + q1.getW() * q2.getW();

        Quat q2b = new Quat(q2);

        if (dot < 0.0) {
            dot = -dot;
            q2b.set(-q2b.getX(), -q2b.getY(), -q2b.getZ(), -q2b.getW());
        }

        if (dot > 0.9995f) {
            float resX = Mth.lerp(alpha, q1.getX(), q2b.getX());
            float resY = Mth.lerp(alpha, q1.getY(), q2b.getY());
            float resZ = Mth.lerp(alpha, q1.getZ(), q2b.getZ());
            float resW = Mth.lerp(alpha, q1.getW(), q2b.getW());
            store.set(resX, resY, resZ, resW);
            store.set(store.normalized());
            return store;
        }

        double theta0 = Math.acos(dot);
        double sinTheta0 = Math.sin(theta0);

        double scale0 = Math.sin((1.0 - alpha) * theta0) / sinTheta0;
        double scale1 = Math.sin(alpha * theta0) / sinTheta0;

        float resX = (float) (scale0 * q1.getX() + scale1 * q2b.getX());
        float resY = (float) (scale0 * q1.getY() + scale1 * q2b.getY());
        float resZ = (float) (scale0 * q1.getZ() + scale1 * q2b.getZ());
        float resW = (float) (scale0 * q1.getW() + scale1 * q2b.getW());

        store.set(resX, resY, resZ, resW);
        return store;
    }

    public static void extrapolateRotation(QuatArg startRot, Vec3Arg angVel, float dt, Quat out) {
        float angle = angVel.length() * dt;
        if (angle > 1e-6f) {
            Vec3 axis = angVel.normalized();
            Quat deltaRot = Quat.sRotation(axis, angle);
            Quat finalRot = Op.star(deltaRot, startRot);
            out.set(finalRot.normalized());
        } else {
            out.set(startRot);
        }
    }

    public static RVec3 extrapolatePosition(RVec3Arg p0, Vec3Arg v0, float dt, RVec3 store) {
        if (store == null) {
            store = new RVec3();
        }
        store.set(
                p0.xx() + (double) v0.getX() * dt,
                p0.yy() + (double) v0.getY() * dt,
                p0.zz() + (double) v0.getZ() * dt
        );
        return store;
    }

    public static Quat interpolateCubic(QuatArg q0, Vec3Arg angVel0, QuatArg q1, Vec3Arg angVel1, float dt, float t, Quat store) {
        if (store == null) {
            store = new Quat();
        }

        Quat c0 = new Quat();
        extrapolateRotation(q0, angVel0, dt / 3f, c0);

        Quat c1 = new Quat();
        Vec3 invertedAngVel1 = Op.minus(angVel1);
        extrapolateRotation(q1, invertedAngVel1, dt / 3f, c1);

        Quat tempA = new Quat();
        Quat tempB = new Quat();

        slerp(q0, c0, t, tempA);
        slerp(c0, c1, t, tempB);
        slerp(c1, q1, t, store);

        slerp(tempA, tempB, t, tempA);
        slerp(tempB, store, t, tempB);

        slerp(tempA, tempB, t, store);

        store.set(store.normalized());
        return store;
    }

    public static RVec3 cubicHermite(RVec3Arg p0, Vec3 v0, RVec3Arg p1, Vec3 v1, float t, float dt, RVec3 store) {
        if (store == null) {
            store = new RVec3();
        }

        float t2 = t * t;
        float t3 = t2 * t;

        float h1 =  2f * t3 - 3f * t2 + 1f;
        float h2 = -2f * t3 + 3f * t2;
        float h3 =      t3 - 2f * t2 + t;
        float h4 =      t3 -      t2;

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
}