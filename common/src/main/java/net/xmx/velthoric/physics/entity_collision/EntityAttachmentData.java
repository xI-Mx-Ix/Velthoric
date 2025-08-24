package net.xmx.velthoric.physics.entity_collision;

import com.github.stephengold.joltjni.RMat44;

import java.util.UUID;

public class EntityAttachmentData {
    public UUID attachedBodyUuid = null;
    public RMat44 lastBodyTransform = null;
    public int ticksSinceGrounded = 0;

    public boolean isAttached() {
        return attachedBodyUuid != null && ticksSinceGrounded < 5;
    }

    public void detach() {
        this.attachedBodyUuid = null;
        this.lastBodyTransform = null;
    }
}