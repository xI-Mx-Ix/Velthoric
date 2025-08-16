package net.xmx.vortex.physics.object.physicsobject.client.interpolation;

import net.xmx.vortex.math.VxTransform;
import org.jetbrains.annotations.Nullable;

public class RenderState {
    public final VxTransform transform = new VxTransform();
    public float @Nullable [] vertexData = null;

    public void set(RenderState other) {
        this.transform.set(other.transform);
        if (other.vertexData != null) {
            if (this.vertexData == null || this.vertexData.length != other.vertexData.length) {
                this.vertexData = new float[other.vertexData.length];
            }
            System.arraycopy(other.vertexData, 0, this.vertexData, 0, other.vertexData.length);
        } else {
            this.vertexData = null;
        }
    }
}