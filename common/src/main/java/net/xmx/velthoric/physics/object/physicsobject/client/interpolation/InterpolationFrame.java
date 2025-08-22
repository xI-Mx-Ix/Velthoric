package net.xmx.velthoric.physics.object.physicsobject.client.interpolation;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.util.Mth;
import net.xmx.velthoric.math.VxOperations;

public class InterpolationFrame {
    public final RenderState previous = new RenderState();
    public final RenderState current = new RenderState();
    public boolean isInitialized = false;

    public void interpolate(RenderState out, float partialTicks) {
        RVec3 prevPos = previous.transform.getTranslation();
        RVec3 currPos = current.transform.getTranslation();
        out.transform.getTranslation().set(
                (float) Mth.lerp(partialTicks, prevPos.xx(), currPos.xx()),
                (float) Mth.lerp(partialTicks, prevPos.yy(), currPos.yy()),
                (float) Mth.lerp(partialTicks, prevPos.zz(), currPos.zz())
        );

        Quat prevRot = previous.transform.getRotation();
        Quat currRot = current.transform.getRotation();
        VxOperations.slerp(prevRot, currRot, partialTicks, out.transform.getRotation());

        float[] prevVerts = previous.vertexData;
        float[] currVerts = current.vertexData;
        if (prevVerts != null && currVerts != null && prevVerts.length == currVerts.length) {
            if (out.vertexData == null || out.vertexData.length != currVerts.length) {
                out.vertexData = new float[currVerts.length];
            }
            for (int i = 0; i < currVerts.length; i++) {
                out.vertexData[i] = Mth.lerp(partialTicks, prevVerts[i], currVerts[i]);
            }
        } else {
            out.vertexData = currVerts;
        }
    }

    public RenderState getInterpolatedState(float partialTicks) {
        RenderState newState = new RenderState();
        interpolate(newState, partialTicks);
        return newState;
    }
}