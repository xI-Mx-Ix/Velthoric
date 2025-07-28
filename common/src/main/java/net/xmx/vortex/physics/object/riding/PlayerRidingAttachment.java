package net.xmx.vortex.physics.object.riding;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.xmx.vortex.math.VxTransform;
import org.jetbrains.annotations.Nullable;

public class PlayerRidingAttachment {

    private boolean isRiding = false;
    @Nullable
    private RidingProxyEntity currentProxy = null;

    public final VxTransform renderTransform = new VxTransform();
    public final RVec3 localPositionOnObject = new RVec3();
    public final Quat localLookRotation = new Quat();

    public boolean isRiding() {
        return isRiding;
    }

    public void setRiding(boolean riding) {
        isRiding = riding;
    }

    @Nullable
    public RidingProxyEntity getCurrentProxy() {
        return currentProxy;
    }

    public void setCurrentProxy(@Nullable RidingProxyEntity currentProxy) {
        this.currentProxy = currentProxy;
    }
}