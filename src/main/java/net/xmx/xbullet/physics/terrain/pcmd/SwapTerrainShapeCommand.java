package net.xmx.xbullet.physics.terrain.pcmd;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.enumerate.EActivation;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.world.pcmd.ICommand;
import net.xmx.xbullet.physics.terrain.chunk.TerrainSection;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystem;

public record SwapTerrainShapeCommand(TerrainSection section, ShapeRefC newShapeRef, TerrainSystem terrainSystem) implements ICommand {

    @Override
    public void execute(PhysicsWorld world) {
        if (terrainSystem.isShutdown() || section.getBodyId() == 0 || newShapeRef == null) {

            if (newShapeRef != null) {
                newShapeRef.close();
            }
            return;
        }

        BodyInterface bodyInterface = world.getBodyInterface();
        if (bodyInterface == null) {
            newShapeRef.close();
            return;
        }

        ShapeRefC oldShapeRef = section.getCurrentShapeRef();

        bodyInterface.setShape(section.getBodyId(), newShapeRef, true, EActivation.DontActivate);

        if (oldShapeRef != null) {
            oldShapeRef.close();
        }

        section.setCurrentShapeRef(newShapeRef);

        section.setState(TerrainSection.State.READY_INACTIVE);

        world.getObjectManager().onTerrainSectionReady(section.getPos());

    }
}