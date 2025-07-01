package net.xmx.xbullet.physics.terrain.chunk;

import com.github.stephengold.joltjni.ShapeRefC;
import net.minecraft.core.SectionPos;

import java.util.UUID;

public class TerrainSection {

    public enum State {
        UNLOADED,
        PLACEHOLDER,
        MESHING,
        READY_INACTIVE,
        READY_ACTIVE
    }

    private final SectionPos pos;
    private final UUID id;
    private int bodyId = 0;
    private ShapeRefC currentShapeRef = null;
    private State state = State.UNLOADED;
    private double priority = 0.0;

    public TerrainSection(SectionPos pos) {
        this.pos = pos;
        this.id = new UUID(pos.asLong(), 0xB10C_B00B_DEAD_BEEFL);
    }

    public SectionPos getPos() { return pos; }
    public UUID getId() { return id; }
    public int getBodyId() { return bodyId; }
    public State getState() { return state; }
    public double getPriority() { return priority; }
    public ShapeRefC getCurrentShapeRef() { return currentShapeRef; }

    public void setCurrentShapeRef(ShapeRefC shapeRef) { this.currentShapeRef = shapeRef; }
    public void setBodyId(int bodyId) { this.bodyId = bodyId; }
    public void setState(State state) { this.state = state; }
    public void setPriority(double priority) { this.priority = priority; }
}