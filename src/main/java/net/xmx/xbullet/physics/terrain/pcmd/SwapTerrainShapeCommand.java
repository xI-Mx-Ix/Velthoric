package net.xmx.xbullet.physics.terrain.pcmd;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;
import net.xmx.xbullet.physics.physicsworld.pcmd.ICommand;
import net.xmx.xbullet.physics.terrain.chunk.TerrainSection;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystem;

public record SwapTerrainShapeCommand(TerrainSection section, ShapeSettings newShapeSettings, TerrainSystem terrainSystem) implements ICommand {

    @Override
    public void execute(PhysicsWorld world) {
        if (terrainSystem.isShutdown() || section.getBodyId() == 0) {
            newShapeSettings.close();
            return;
        }

        try (ShapeResult result = newShapeSettings.create()) {
            if (result.isValid()) {
                try (ShapeRefC newShapeRef = result.get()) {
                    BodyInterface bodyInterface = world.getBodyInterface();

                    if (bodyInterface.isAdded(section.getBodyId())) {
                        bodyInterface.removeBody(section.getBodyId());
                    }

                    bodyInterface.setShape(section.getBodyId(), newShapeRef.getPtr(), false, EActivation.DontActivate);

                    ShapeRefC oldShapeRef = section.getCurrentShapeRef();
                    if (oldShapeRef != null) {
                        oldShapeRef.close();
                    }

                    section.setCurrentShapeRef(newShapeRef.getPtr().toRefC());

                    section.setState(TerrainSection.State.READY_INACTIVE);

                }
            } else {

                section.setState(TerrainSection.State.PLACEHOLDER);
                XBullet.LOGGER.error("Failed to create shape for section {}: {}", section.getPos(), result.getError());
            }
        } finally {
            newShapeSettings.close();
        }
    }
}