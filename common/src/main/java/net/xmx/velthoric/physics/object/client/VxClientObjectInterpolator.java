package net.xmx.velthoric.physics.object.client;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.util.Mth;
import net.xmx.velthoric.math.VxOperations;
import org.jetbrains.annotations.Nullable;

public class VxClientObjectInterpolator {

    private static final float MAX_EXTRAPOLATION_SECONDS = 0.2f;

    private final Quat tempFromRot = new Quat();
    private final Quat tempToRot = new Quat();
    private final Quat tempRenderRot = new Quat();

    public void updateInterpolationTargets(VxClientObjectStore store, long renderTimestamp) {
        final int objectCount = store.getObjectCount();

        for (int i = 0; i < objectCount; i++) {
            if (store.state1_timestamp[i] == 0) continue;

            store.prev_posX[i] = store.render_posX[i];
            store.prev_posY[i] = store.render_posY[i];
            store.prev_posZ[i] = store.render_posZ[i];
            store.prev_rotX[i] = store.render_rotX[i];
            store.prev_rotY[i] = store.render_rotY[i];
            store.prev_rotZ[i] = store.render_rotZ[i];
            store.prev_rotW[i] = store.render_rotW[i];

            if (store.render_vertexData[i] != null) {
                if (store.prev_vertexData[i] == null || store.prev_vertexData[i].length != store.render_vertexData[i].length) {
                    store.prev_vertexData[i] = new float[store.render_vertexData[i].length];
                }
                System.arraycopy(store.render_vertexData[i], 0, store.prev_vertexData[i], 0, store.render_vertexData[i].length);
            } else {
                store.prev_vertexData[i] = null;
            }

            calculateInterpolatedState(store, i, renderTimestamp);

            if (!store.render_isInitialized[i]) {
                store.prev_posX[i] = store.render_posX[i];
                store.prev_posY[i] = store.render_posY[i];
                store.prev_posZ[i] = store.render_posZ[i];
                store.prev_rotX[i] = store.render_rotX[i];
                store.prev_rotY[i] = store.render_rotY[i];
                store.prev_rotZ[i] = store.render_rotZ[i];
                store.prev_rotW[i] = store.render_rotW[i];

                if (store.render_vertexData[i] != null) {
                    if (store.prev_vertexData[i] == null || store.prev_vertexData[i].length != store.render_vertexData[i].length) {
                        store.prev_vertexData[i] = new float[store.render_vertexData[i].length];
                    }
                    System.arraycopy(store.render_vertexData[i], 0, store.prev_vertexData[i], 0, store.render_vertexData[i].length);
                }

                store.render_isInitialized[i] = true;
            }
        }
    }

    private void calculateInterpolatedState(VxClientObjectStore store, int i, long renderTimestamp) {
        long fromTime = store.state0_timestamp[i];
        long toTime = store.state1_timestamp[i];

        if (fromTime == 0) {
            setRenderStateToLatest(store, i);
            return;
        }

        if (toTime <= fromTime) {
            extrapolate(store, i, renderTimestamp, toTime, store.state1_posX[i], store.state1_posY[i], store.state1_posZ[i], store.state1_velX[i], store.state1_velY[i], store.state1_velZ[i]);
            store.render_rotX[i] = store.state1_rotX[i];
            store.render_rotY[i] = store.state1_rotY[i];
            store.render_rotZ[i] = store.state1_rotZ[i];
            store.render_rotW[i] = store.state1_rotW[i];
            store.render_vertexData[i] = store.state1_vertexData[i] != null ? store.state1_vertexData[i] : store.state0_vertexData[i];
            return;
        }

        long timeDiff = toTime - fromTime;
        float alpha = (float) (renderTimestamp - fromTime) / timeDiff;

        if (alpha > 1.0f) {
            extrapolate(store, i, renderTimestamp, toTime, store.state1_posX[i], store.state1_posY[i], store.state1_posZ[i], store.state1_velX[i], store.state1_velY[i], store.state1_velZ[i]);
            store.render_rotX[i] = store.state1_rotX[i];
            store.render_rotY[i] = store.state1_rotY[i];
            store.render_rotZ[i] = store.state1_rotZ[i];
            store.render_rotW[i] = store.state1_rotW[i];
            store.render_vertexData[i] = store.state1_vertexData[i];
            return;
        }

        alpha = Mth.clamp(alpha, 0.0f, 1.0f);

        store.render_posX[i] = Mth.lerp(alpha, store.state0_posX[i], store.state1_posX[i]);
        store.render_posY[i] = Mth.lerp(alpha, store.state0_posY[i], store.state1_posY[i]);
        store.render_posZ[i] = Mth.lerp(alpha, store.state0_posZ[i], store.state1_posZ[i]);

        tempFromRot.set(store.state0_rotX[i], store.state0_rotY[i], store.state0_rotZ[i], store.state0_rotW[i]);
        tempToRot.set(store.state1_rotX[i], store.state1_rotY[i], store.state1_rotZ[i], store.state1_rotW[i]);
        VxOperations.slerp(tempFromRot, tempToRot, alpha, tempRenderRot);
        store.render_rotX[i] = tempRenderRot.getX();
        store.render_rotY[i] = tempRenderRot.getY();
        store.render_rotZ[i] = tempRenderRot.getZ();
        store.render_rotW[i] = tempRenderRot.getW();

        float[] fromVerts = store.state0_vertexData[i];
        float[] toVerts = store.state1_vertexData[i];

        if (fromVerts != null && toVerts != null && fromVerts.length == toVerts.length) {
            if (store.render_vertexData[i] == null || store.render_vertexData[i].length != toVerts.length) {
                store.render_vertexData[i] = new float[toVerts.length];
            }
            for (int j = 0; j < toVerts.length; j++) {
                store.render_vertexData[i][j] = Mth.lerp(alpha, fromVerts[j], toVerts[j]);
            }
        } else if (toVerts != null) {
            store.render_vertexData[i] = toVerts;
        } else if (fromVerts != null) {
            store.render_vertexData[i] = fromVerts;
        }
    }

    private void extrapolate(VxClientObjectStore store, int i, long renderTimestamp, long fromTimestamp, float fromX, float fromY, float fromZ, float velX, float velY, float velZ) {
        float dt = (float) (renderTimestamp - fromTimestamp) / 1_000_000_000.0f;

        if (dt > 0 && dt < MAX_EXTRAPOLATION_SECONDS) {
            store.render_posX[i] = fromX + velX * dt;
            store.render_posY[i] = fromY + velY * dt;
            store.render_posZ[i] = fromZ + velZ * dt;
        } else {
            store.render_posX[i] = fromX;
            store.render_posY[i] = fromY;
            store.render_posZ[i] = fromZ;
        }
    }

    public void interpolateFrame(VxClientObjectStore store, int i, float partialTicks, RVec3 outPos, Quat outRot) {
        outPos.set(
                Mth.lerp(partialTicks, store.prev_posX[i], store.render_posX[i]),
                Mth.lerp(partialTicks, store.prev_posY[i], store.render_posY[i]),
                Mth.lerp(partialTicks, store.prev_posZ[i], store.render_posZ[i])
        );

        tempFromRot.set(store.prev_rotX[i], store.prev_rotY[i], store.prev_rotZ[i], store.prev_rotW[i]);
        tempToRot.set(store.render_rotX[i], store.render_rotY[i], store.render_rotZ[i], store.render_rotW[i]);
        VxOperations.slerp(tempFromRot, tempToRot, partialTicks, outRot);
    }

    public float @Nullable [] getInterpolatedVertexData(VxClientObjectStore store, int i, float partialTicks) {
        float[] prevVerts = store.prev_vertexData[i];
        float[] currVerts = store.render_vertexData[i];

        if (currVerts == null) {
            return null;
        }

        if (prevVerts == null || prevVerts.length != currVerts.length) {
            return currVerts;
        }

        float[] outVerts = new float[currVerts.length];
        for (int j = 0; j < currVerts.length; j++) {
            outVerts[j] = Mth.lerp(partialTicks, prevVerts[j], currVerts[j]);
        }
        return outVerts;
    }

    private void setRenderStateToLatest(VxClientObjectStore store, int i) {
        store.render_posX[i] = store.state1_posX[i];
        store.render_posY[i] = store.state1_posY[i];
        store.render_posZ[i] = store.state1_posZ[i];
        store.render_rotX[i] = store.state1_rotX[i];
        store.render_rotY[i] = store.state1_rotY[i];
        store.render_rotZ[i] = store.state1_rotZ[i];
        store.render_rotW[i] = store.state1_rotW[i];

        if (store.state1_vertexData[i] != null) {
            store.render_vertexData[i] = store.state1_vertexData[i];
        } else if (store.state0_vertexData[i] != null) {
            store.render_vertexData[i] = store.state0_vertexData[i];
        }
    }
}