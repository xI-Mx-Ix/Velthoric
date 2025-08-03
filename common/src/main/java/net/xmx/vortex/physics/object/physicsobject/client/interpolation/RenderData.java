package net.xmx.vortex.physics.object.physicsobject.client.interpolation;

import net.xmx.vortex.math.VxTransform;
import org.jetbrains.annotations.Nullable;

public class RenderData {
    public final VxTransform transform = new VxTransform();
    @Nullable
    public float[] vertexData = null;
}