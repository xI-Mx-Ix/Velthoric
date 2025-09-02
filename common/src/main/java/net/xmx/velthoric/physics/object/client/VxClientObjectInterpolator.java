package net.xmx.velthoric.physics.object.client;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.util.Mth;
import net.xmx.velthoric.math.VxOperations;
import org.jetbrains.annotations.Nullable;

public class VxClientObjectInterpolator {

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
            store.prev_vertexData[i] = store.render_vertexData[i];

            calculateInterpolatedState(store, i, renderTimestamp);

            if (!store.render_isInitialized[i]) {
                store.prev_posX[i] = store.render_posX[i];
                store.prev_posY[i] = store.render_posY[i];
                store.prev_posZ[i] = store.render_posZ[i];
                store.prev_rotX[i] = store.render_rotX[i];
                store.prev_rotY[i] = store.render_rotY[i];
                store.prev_rotZ[i] = store.render_rotZ[i];
                store.prev_rotW[i] = store.render_rotW[i];
                store.prev_vertexData[i] = store.render_vertexData[i];
                store.render_isInitialized[i] = true;
            }
        }
    }

    private void calculateInterpolatedState(VxClientObjectStore store, int i, long renderTimestamp) {
        long fromTime = store.state0_timestamp[i];
        long toTime = store.state1_timestamp[i];

        if (fromTime == 0 || toTime <= fromTime) {
            setRenderStateToLatest(store, i);
            return;
        }

        long timeDiff = toTime - fromTime;
        float alpha = Mth.clamp((float) (renderTimestamp - fromTime) / timeDiff, 0.0f, 1.0f);

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
        } else {
            store.render_vertexData[i] = toVerts;
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

        if (prevVerts != null && currVerts != null && prevVerts.length == currVerts.length) {
            float[] outVerts = new float[currVerts.length];
            for (int j = 0; j < currVerts.length; j++) {
                outVerts[j] = Mth.lerp(partialTicks, prevVerts[j], currVerts[j]);
            }
            return outVerts;
        }
        return currVerts;
    }

    private void setRenderStateToLatest(VxClientObjectStore store, int i) {
        store.render_posX[i] = store.state1_posX[i];
        store.render_posY[i] = store.state1_posY[i];
        store.render_posZ[i] = store.state1_posZ[i];
        store.render_rotX[i] = store.state1_rotX[i];
        store.render_rotY[i] = store.state1_rotY[i];
        store.render_rotZ[i] = store.state1_rotZ[i];
        store.render_rotW[i] = store.state1_rotW[i];
        store.render_vertexData[i] = store.state1_vertexData[i];
    }
}