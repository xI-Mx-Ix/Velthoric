package net.xmx.xbullet.physics.terrain.pcmd;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.enumerate.EActivation;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.world.pcmd.ICommand;
import net.xmx.xbullet.physics.terrain.chunk.TerrainSection;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystem;

public record SwapTerrainShapeCommand(TerrainSection section, ShapeRefC newShapeRef, TerrainSystem terrainSystem) implements ICommand {

    @Override
    public void execute(PhysicsWorld world) {
        try {
            if (terrainSystem.isShutdown() || section.getBodyId() == 0) {
                return;
            }

            BodyInterface bodyInterface = world.getBodyInterface();

            ShapeRefC oldShapeRef = section.getCurrentShapeRef();

            bodyInterface.setShape(section.getBodyId(), newShapeRef.getPtr(), true, EActivation.DontActivate);

            if (oldShapeRef != null) {
                oldShapeRef.close();
            }

            section.setCurrentShapeRef(newShapeRef.getPtr().toRefC());
            section.setState(TerrainSection.State.READY_INACTIVE);

        } finally {

            if (newShapeRef != null) {
                newShapeRef.close();
            }
        }
    }
}