package net.xmx.vortex.debug.drawer;

import com.github.stephengold.joltjni.*;
import net.minecraft.server.level.ServerLevel;
import net.xmx.vortex.debug.drawer.packet.DebugShapesUpdatePacket;
import net.xmx.vortex.network.NetworkHandler;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

public class ServerShapeDrawerManager {

    private final VxPhysicsWorld physicsWorld;

    public ServerShapeDrawerManager(VxPhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
    }

    public void tick() {
        PhysicsSystem system = physicsWorld.getPhysicsSystem();
        if (system == null) return;

        Map<Integer, DebugShapesUpdatePacket.BodyDrawData> allBodyData = new HashMap<>();
        BodyLockInterface lockInterface = system.getBodyLockInterfaceNoLock();

        try (BodyIdVector bodyIds = new BodyIdVector()) {
            system.getBodies(bodyIds);
            for (int i = 0; i < bodyIds.size(); i++) {
                int bodyId = bodyIds.get(i);
                if (bodyId == 0) continue;

                try (BodyLockRead lock = new BodyLockRead(lockInterface, bodyId)) {
                    if (lock.succeededAndIsInBroadPhase()) {
                        Body body = lock.getBody();
                        if (body != null) {
                            extractAndAddBodyData(body, allBodyData);
                        }
                    }
                }
            }
        }

        sendPacket(allBodyData);
    }

    private void extractAndAddBodyData(Body body, Map<Integer, DebugShapesUpdatePacket.BodyDrawData> allBodyData) {
        try (TransformedShape transformedShape = body.getTransformedShape()) {
            if (transformedShape == null) {
                return;
            }

            if (!body.isInBroadPhase()) {
                return;
            }

            int numTriangles = transformedShape.countDebugTriangles();
            if (numTriangles == 0) {
                return;
            }

            float[] allVertices = new float[numTriangles * 9];
            FloatBuffer vertexBuffer = null;

            try {
                vertexBuffer = MemoryUtil.memAllocFloat(allVertices.length);
                transformedShape.copyDebugTriangles(vertexBuffer);
                vertexBuffer.get(0, allVertices);
            } finally {
                if (vertexBuffer != null) {
                    MemoryUtil.memFree(vertexBuffer);
                }
            }

            if (allVertices.length > 0) {
                int color = switch (body.getMotionType()) {
                    case Static -> 0xFF_FFFFFF;
                    case Kinematic -> 0xFF_FFFF00;
                    case Dynamic -> 0xFF_00FF00;
                };

                allBodyData.put(body.getId(), new DebugShapesUpdatePacket.BodyDrawData(color, allVertices));
            }
        }
    }

    private void sendPacket(Map<Integer, DebugShapesUpdatePacket.BodyDrawData> allBodyData) {
        ServerLevel level = physicsWorld.getLevel();
        if (level == null || allBodyData.isEmpty()) {
            return;
        }

        DebugShapesUpdatePacket packet = new DebugShapesUpdatePacket(allBodyData);

        NetworkHandler.sendToDimension(packet, level.dimension());
    }
}