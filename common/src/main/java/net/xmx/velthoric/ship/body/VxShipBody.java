/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.ship.body;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.network.NetworkHandler;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.ship.packet.UpdateShipPlotDataPacket;
import net.xmx.velthoric.ship.plot.VxPlotManager;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class VxShipBody extends VxRigidBody {

    @Nullable
    private UUID plotId;

    public VxShipBody(VxObjectType<? extends VxRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    public void setPlotId(UUID plotId) {
        this.plotId = plotId;
        if (!world.getLevel().isClientSide) {
            VxPlotManager plotManager = world.getPlotManager();
            if (plotManager != null) {
                plotManager.associateShipWithPlot(this);
            }
            this.sendPlotDataUpdate();
        }
    }

    private void sendPlotDataUpdate() {
        if (this.plotId == null) return;
        UpdateShipPlotDataPacket packet = new UpdateShipPlotDataPacket(this.getPhysicsId(), this.plotId, getPlotCenter(), getPlotRadius());
        world.getLevel().getPlayers(player -> true).forEach(player -> NetworkHandler.sendToPlayer(packet, player));

    }

    @Nullable
    public UUID getPlotId() {
        return plotId;
    }

    public ChunkPos getPlotCenter() {
        if (this.plotId == null || world.getPlotManager() == null) return new ChunkPos(0, 0);
        return world.getPlotManager().getPlotCenter(this.plotId);
    }

    public int getPlotRadius() {
        if (this.plotId == null || world.getPlotManager() == null) return 0;
        return world.getPlotManager().getPlotRadius(this.plotId);
    }

    @Override
    public void onBodyRemoved(VxPhysicsWorld world, VxRemovalReason reason) {
        super.onBodyRemoved(world, reason);
        if (!world.getLevel().isClientSide) {
            VxPlotManager plotManager = world.getPlotManager();
            if (plotManager != null) {
                plotManager.disassociateShipFromPlot(this);
            }
        }
    }

    @Override
    public void writeCreationData(VxByteBuf buf) {
        buf.writeBoolean(plotId != null);
        if (plotId != null) {
            buf.writeUUID(plotId);
            buf.writeChunkPos(getPlotCenter());
            buf.writeVarInt(getPlotRadius());
        }
    }

    @Override
    public ShapeSettings createShapeSettings() {
        var settings = new BoxShapeSettings(1);
        return settings;
    }


    @Override
    public BodyCreationSettings createBodyCreationSettings(ShapeRefC shapeRef) {
        return new BodyCreationSettings(
                shapeRef,
                this.getGameTransform().getTranslation(),
                this.getGameTransform().getRotation(),
                EMotionType.Dynamic,
                VxLayers.DYNAMIC);
    }
}