package net.xmx.vortex.physics.object.physicsobject.client.interpolation;

public class InterpolatedRenderState {
    public final RenderData previous = new RenderData();
    public final RenderData current = new RenderData();
    public boolean isInitialized = false;
}
