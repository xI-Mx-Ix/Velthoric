package net.xmx.xbullet.physics.terrain.manager;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.terrain.chunk.TerrainSection;
import net.xmx.xbullet.physics.terrain.pcmd.AddTerrainSectionCommand;
import net.xmx.xbullet.physics.terrain.pcmd.RemoveTerrainSectionCommand;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TerrainChunkManager {

    private final Map<SectionPos, TerrainSection> managedSections = new ConcurrentHashMap<>();
    private final PhysicsWorld physicsWorld;
    private final Level level;
    private final TerrainSystem terrainSystem;

    public TerrainChunkManager(TerrainSystem terrainSystem, PhysicsWorld physicsWorld, Level level) {
        this.terrainSystem = terrainSystem;
        this.physicsWorld = physicsWorld;
        this.level = level;
    }

    public void onChunkLoad(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        for (int sectionIndex = 0; sectionIndex < chunk.getSectionsCount(); ++sectionIndex) {
            LevelChunkSection chunkSection = chunk.getSection(sectionIndex);
            if (chunkSection.hasOnlyAir()) {
                continue;
            }
            int y = chunk.getSectionYFromSectionIndex(sectionIndex);
            SectionPos sectionPos = SectionPos.of(pos.x, y, pos.z);
            managedSections.computeIfAbsent(sectionPos, this::createAndAddSection);
        }
    }

    public void onChunkUnload(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        for (int y = level.getMinSection(); y < level.getMaxSection(); ++y) {
            SectionPos sectionPos = SectionPos.of(pos.x, y, pos.z);
            TerrainSection section = managedSections.remove(sectionPos);
            if (section != null) {
                physicsWorld.queueCommand(new RemoveTerrainSectionCommand(section, this.terrainSystem));
            }
        }
    }

    public Collection<TerrainSection> getManagedSections() {
        return managedSections.values();
    }

    public TerrainSection getSection(SectionPos pos) {
        return managedSections.get(pos);
    }

    private TerrainSection createAndAddSection(SectionPos pos) {
        TerrainSection section = new TerrainSection(pos);
        physicsWorld.queueCommand(new AddTerrainSectionCommand(section, this.terrainSystem));
        return section;
    }

    public void shutdown() {
        managedSections.values().forEach(section ->
                physicsWorld.queueCommand(new RemoveTerrainSectionCommand(section, this.terrainSystem))
        );
        managedSections.clear();
    }
}