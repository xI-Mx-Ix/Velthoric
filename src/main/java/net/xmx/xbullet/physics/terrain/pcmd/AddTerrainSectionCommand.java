package net.xmx.xbullet.physics.terrain.pcmd;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation; // Import hinzugefügt
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.world.pcmd.ICommand;
import net.xmx.xbullet.physics.terrain.chunk.TerrainSection;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystem;

public record AddTerrainSectionCommand(TerrainSection section, TerrainSystem terrainSystem) implements ICommand {

    @Override
    public void execute(PhysicsWorld world) {
        var objectManager = world.getObjectManager();
        if (terrainSystem.isShutdown() || section.getBodyId() != 0 || objectManager == null) {
            return;
        }

        try (Shape placeholderShape = new BoxShape(8f, 8f, 8f)) {

            RVec3 position = new RVec3(section.getPos().center().getX(), section.getPos().center().getY(), section.getPos().center().getZ());
            Quat rotation = new Quat();

            try (BodyCreationSettings settings = new BodyCreationSettings(
                    placeholderShape,
                    position,
                    rotation,
                    EMotionType.Static,
                    PhysicsWorld.Layers.STATIC
            )) {

                settings.setFriction(0.7f);
                settings.setRestitution(0.3f);

                BodyInterface bodyInterface = world.getBodyInterface();
                if (bodyInterface == null) return;

                try (Body body = bodyInterface.createBody(settings)) {
                    int newBodyId = body.getId();
                    long terrainIdentifier = section.getId().getMostSignificantBits() ^ section.getId().getLeastSignificantBits();
                    body.setUserData(terrainIdentifier | TerrainSystem.TERRAIN_USER_DATA_FLAG);

                    terrainSystem.registerTerrainBody(newBodyId);
                    bodyInterface.addBody(newBodyId, EActivation.DontActivate);

                    section.setBodyId(newBodyId);
                    section.setCurrentShapeRef(placeholderShape.toRefC());
                    section.setState(TerrainSection.State.PLACEHOLDER);

                    objectManager.linkBodyId(newBodyId, section.getId());
                }
            }
        }
    }
}