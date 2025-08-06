package net.xmx.vortex.physics.object.physicsobject.client.interpolation;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.util.Mth;
import net.xmx.vortex.math.VxOperations;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.client.ClientObjectDataManager;
import org.jetbrains.annotations.Nullable;

public class RenderData {
    public final VxTransform transform = new VxTransform();
    public float @Nullable [] vertexData = null;

    public static RenderData interpolate(ClientObjectDataManager.InterpolatedRenderState state, float partialTicks, RenderData out) {

        RVec3 prevPos = state.previous.transform.getTranslation();
        RVec3 currPos = state.current.transform.getTranslation();
        out.transform.getTranslation().set(
                (float) Mth.lerp(partialTicks, prevPos.xx(), currPos.xx()),
                (float) Mth.lerp(partialTicks, prevPos.yy(), currPos.yy()),
                (float) Mth.lerp(partialTicks, prevPos.zz(), currPos.zz())
        );

        Quat prevRot = state.previous.transform.getRotation();
        Quat currRot = state.current.transform.getRotation();
        VxOperations.slerp(prevRot, currRot, partialTicks, out.transform.getRotation());

        float[] prevVerts = state.previous.vertexData;
        float[] currVerts = state.current.vertexData;
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

        return out;
    }
}