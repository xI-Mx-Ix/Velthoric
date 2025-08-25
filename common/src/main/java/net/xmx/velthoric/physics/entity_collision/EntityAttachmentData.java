package net.xmx.velthoric.physics.entity_collision;

import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.Vec3;

import java.util.UUID;

public final class EntityAttachmentData {
    public UUID attachedBodyUuid = null;
    public RMat44 lastBodyTransform = null;
    public int ticksSinceGrounded = 0;
    public Vec3 lastGroundNormal = new Vec3(0f, 1f, 0f);

    public Vec3 addedMovementLastTick = new Vec3(0f, 0f, 0f);
    public float addedYawRotLastTick = 0.0f;

    public boolean isAttached() {
        return attachedBodyUuid != null && ticksSinceGrounded < 5;
    }

    public void detach() {
        this.attachedBodyUuid = null;
        if (this.lastBodyTransform != null) {
            this.lastBodyTransform.close();
            this.lastBodyTransform = null;
        }
    }
}