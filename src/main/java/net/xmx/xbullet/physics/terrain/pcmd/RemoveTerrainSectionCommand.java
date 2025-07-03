package net.xmx.xbullet.physics.terrain.pcmd;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.ShapeRefC;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.world.pcmd.ICommand;
import net.xmx.xbullet.physics.terrain.chunk.TerrainSection;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystem;

public record RemoveTerrainSectionCommand(TerrainSection section, TerrainSystem terrainSystem) implements ICommand {

    @Override
    public void execute(PhysicsWorld world) {
        int bodyId = section.getBodyId();
        if (bodyId == 0) {
            return;
        }

        var objectManager = world.getObjectManager();
        if (objectManager == null) return;

        BodyInterface bodyInterface = world.getBodyInterface();
        if (bodyInterface == null) return;

        if (bodyInterface.isAdded(bodyId)) {
            bodyInterface.removeBody(bodyId);
        }

        terrainSystem.unregisterTerrainBody(bodyId);
        objectManager.unlinkBodyId(bodyId);

        bodyInterface.destroyBody(bodyId);

        ShapeRefC oldShapeRef = section.getCurrentShapeRef();
        if (oldShapeRef != null) {
            oldShapeRef.close();
        }

        section.setCurrentShapeRef(null);
        section.setBodyId(0);
        section.setState(TerrainSection.State.UNLOADED);
    }
}