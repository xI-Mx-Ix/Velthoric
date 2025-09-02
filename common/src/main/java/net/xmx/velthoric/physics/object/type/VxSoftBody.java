package net.xmx.velthoric.physics.object.type;

import com.github.stephengold.joltjni.SoftBodyCreationSettings;
import com.github.stephengold.joltjni.SoftBodySharedSettings;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.xmx.velthoric.physics.object.PhysicsObjectType;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.client.RenderState;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.UUID;

public abstract class VxSoftBody extends VxAbstractBody {

    protected float @Nullable [] lastSyncedVertexData;

    protected VxSoftBody(PhysicsObjectType<? extends VxSoftBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    public abstract SoftBodySharedSettings createSoftBodySharedSettings();

    public abstract SoftBodyCreationSettings createSoftBodyCreationSettings(SoftBodySharedSettings sharedSettings);

    public float @Nullable [] getLastSyncedVertexData() {
        return this.lastSyncedVertexData;
    }

    public void setLastSyncedVertexData(float @Nullable [] data) {
        this.lastSyncedVertexData = data;
    }

    public interface Renderer {
        void render(UUID id, RenderState renderState, ByteBuffer customData, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick, int packedLight);
    }
}