package net.xmx.velthoric.physics.object.type;

import com.github.stephengold.joltjni.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.physics.object.PhysicsObjectType;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.client.interpolation.RenderState;
import net.xmx.velthoric.physics.raycasting.click.Clickable;
import net.xmx.velthoric.physics.riding.Rideable;
import net.xmx.velthoric.physics.riding.seat.Seat;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.nio.ByteBuffer;
import java.util.UUID;

public abstract class VxRigidBody extends VxAbstractBody implements Rideable, Clickable {

    protected VxRigidBody(PhysicsObjectType<? extends VxRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    public abstract ShapeSettings createShapeSettings();

    public abstract BodyCreationSettings createBodyCreationSettings(ShapeRefC shapeRef);

    public interface Renderer {
        void render(UUID id, RenderState renderState, ByteBuffer customData, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick, int packedLight);
    }

    // ---- Rideable ---- //

    @Override
    public void onStartRiding(ServerPlayer player, Seat seat) {}

    @Override
    public void onStopRiding(ServerPlayer player) {}

    // ---- Clickable ---- //

    @Override
    public void onLeftClick(ServerPlayer player, Vec3 hitPoint, Vec3 hitNormal) {}

    @Override
    public void onRightClick(ServerPlayer player, Vec3 hitPoint, Vec3 hitNormal) {}
}