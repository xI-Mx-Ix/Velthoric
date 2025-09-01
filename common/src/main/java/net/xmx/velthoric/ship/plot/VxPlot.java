package net.xmx.velthoric.ship.plot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class VxPlot {

    private final int id;
    private final BoundingBox bounds;
    private UUID assignedShipId;

    public VxPlot(int id, BoundingBox bounds) {
        this.id = id;
        this.bounds = bounds;
        this.assignedShipId = null;
    }

    public int getId() {
        return id;
    }

    public BoundingBox getBounds() {
        return bounds;
    }

    public Optional<UUID> getAssignedShipId() {
        return Optional.ofNullable(assignedShipId);
    }

    public void setAssignedShipId(@Nullable UUID assignedShipId) {
        this.assignedShipId = assignedShipId;
    }

    public static VxPlot fromNbt(CompoundTag tag) {
        int id = tag.getInt("id");
        int[] boxData = tag.getIntArray("box");
        BoundingBox bounds = new BoundingBox(boxData[0], boxData[1], boxData[2], boxData[3], boxData[4], boxData[5]);
        VxPlot plot = new VxPlot(id, bounds);
        if (tag.hasUUID("shipId")) {
            plot.setAssignedShipId(tag.getUUID("shipId"));
        }
        return plot;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", this.id);
        tag.putIntArray("box", new int[]{bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()});
        if (this.assignedShipId != null) {
            tag.putUUID("shipId", this.assignedShipId);
        }
        return tag;
    }
}