package net.xmx.xbullet.physics.terrain.pcmd;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;
import net.xmx.xbullet.physics.physicsworld.pcmd.ICommand;
import net.xmx.xbullet.physics.terrain.chunk.TerrainSection;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystem;

public record AddTerrainSectionCommand(TerrainSection section, TerrainSystem terrainSystem) implements ICommand {

    @Override
    public void execute(PhysicsWorld world) {
        if (terrainSystem.isShutdown() || section.getBodyId() != 0) {
            return;
        }

        try (Shape placeholderShape = new BoxShape(8f, 8f, 8f)) {
            try (BodyCreationSettings settings = new BodyCreationSettings(
                    placeholderShape,
                    new RVec3(section.getPos().center().getX(), section.getPos().center().getY(), section.getPos().center().getZ()),
                    new Quat(),
                    EMotionType.Static,
                    PhysicsWorld.Layers.STATIC
            )) {
                BodyInterface bodyInterface = world.getBodyInterface();
                if (bodyInterface == null) return;

                Body body = bodyInterface.createBody(settings);
                int newBodyId = body.getId();
                body.setUserData(section.getId().getMostSignificantBits() ^ section.getId().getLeastSignificantBits());

                section.setBodyId(newBodyId);

                section.setCurrentShapeRef(placeholderShape.toRefC());
                section.setState(TerrainSection.State.PLACEHOLDER);
                world.getBodyIdToUuidMap().put(newBodyId, section.getId());

            }
        }
    }
}