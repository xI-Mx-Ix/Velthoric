package net.xmx.velthoric.physics.entity_collision;


import com.github.stephengold.joltjni.RMat44;

public class EntityAttachmentData {
    public Integer attachedBodyId = null;
    public RMat44 lastBodyTransform = null;
    public int ticksSinceGrounded = 0;

    public boolean isAttached() {
        return attachedBodyId != null && ticksSinceGrounded < 5;
    }
}