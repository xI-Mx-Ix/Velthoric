package net.xmx.velthoric.ship;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.PhysicsObjectType;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.ship.plot.VxPlot;

import java.util.UUID;

public class VxStructureBody extends VxRigidBody {

    private VxPlot plot;
    private volatile boolean shapeDirty = true;

    public VxStructureBody(PhysicsObjectType<VxStructureBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    public void setPlot(VxPlot plot) {
        this.plot = plot;
        this.markDataDirty();
    }

    public VxPlot getPlot() {
        return this.plot;
    }

    public void markShapeDirty() {
        this.shapeDirty = true;
    }

    @Override
    public void physicsTick(VxPhysicsWorld world) {
        if (shapeDirty) {
            shapeDirty = false;
        }
    }

    @Override
    public ShapeSettings createShapeSettings() {
        return new BoxShapeSettings(new Vec3(2f, 2f, 2f));
    }

    @Override
    public BodyCreationSettings createBodyCreationSettings(ShapeRefC shapeRef) {
        var settings = new BodyCreationSettings(
                shapeRef,
                this.getGameTransform().getTranslation(),
                this.getGameTransform().getRotation(),
                EMotionType.Dynamic,
                VxLayers.DYNAMIC);

        settings.setOverrideMassProperties(EOverrideMassProperties.CalculateMassAndInertia);
        settings.setRestitution(0.1f);
        settings.setFriction(0.7f);
        return settings;
    }

    @Override
    public void writeCreationData(VxByteBuf buf) {
        if (plot != null) {
            buf.writeBoolean(true);
            BoundingBox bounds = plot.getBounds();
            buf.writeInt(bounds.minX());
            buf.writeInt(bounds.minY());
            buf.writeInt(bounds.minZ());
            buf.writeInt(bounds.maxX());
            buf.writeInt(bounds.maxY());
            buf.writeInt(bounds.maxZ());
        } else {
            buf.writeBoolean(false);
        }
    }

    @Override
    public void readCreationData(VxByteBuf buf) {
    }
}