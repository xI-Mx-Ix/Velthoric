package net.xmx.xbullet.debug.drawer;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.github.stephengold.joltjni.readonly.ConstSoftBodySharedSettings;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class DebugShapeProvider {

    private static final Color RIGID_BODY_COLOR = new Color(0, 255, 0, 255);
    private static final Color SOFT_BODY_COLOR = new Color(255, 255, 0, 255);

    public static List<DebugLine> getDebugLinesForBody(Body body) {
        if (body == null || !body.isInBroadPhase()) {
            return new ArrayList<>();
        }

        if (body.isRigidBody()) {
            return getLinesForRigidBody(body);
        } else if (body.isSoftBody()) {
            return getLinesForSoftBody(body);
        }
        return new ArrayList<>();
    }

    private static List<DebugLine> getLinesForRigidBody(Body body) {
        List<DebugLine> lines = new ArrayList<>();
        ConstShape shape = body.getShape();
        RMat44 transform = body.getWorldTransform();

        int numTriangles = shape.countDebugTriangles();
        if (numTriangles > 0) {
            FloatBuffer triangleVertices = FloatBuffer.allocate(numTriangles * 3 * 3);
            shape.copyDebugTriangles(triangleVertices);
            triangleVertices.flip();

            for (int i = 0; i < numTriangles; i++) {
                Vec3 v1Local = new Vec3(triangleVertices.get(), triangleVertices.get(), triangleVertices.get());
                Vec3 v2Local = new Vec3(triangleVertices.get(), triangleVertices.get(), triangleVertices.get());
                Vec3 v3Local = new Vec3(triangleVertices.get(), triangleVertices.get(), triangleVertices.get());

                RVec3 v1World = transform.multiply3x4(v1Local);
                RVec3 v2World = transform.multiply3x4(v2Local);
                RVec3 v3World = transform.multiply3x4(v3Local);

                lines.add(new DebugLine(v1World, v2World, RIGID_BODY_COLOR.getUInt32()));
                lines.add(new DebugLine(v2World, v3World, RIGID_BODY_COLOR.getUInt32()));
                lines.add(new DebugLine(v3World, v1World, RIGID_BODY_COLOR.getUInt32()));
            }
        }
        return lines;
    }

    private static List<DebugLine> getLinesForSoftBody(Body body) {
        List<DebugLine> lines = new ArrayList<>();
        SoftBodyMotionProperties motionProperties = (SoftBodyMotionProperties) body.getMotionProperties();
        ConstSoftBodySharedSettings settings = motionProperties.getSettings();
        com.github.stephengold.joltjni.readonly.ConstSoftBodyVertex[] vertices = motionProperties.getVertices();

        int numEdges = settings.countEdgeConstraints();
        if (numEdges > 0) {
            IntBuffer edgeIndices = IntBuffer.allocate(numEdges * 2);
            settings.putEdgeIndices(edgeIndices);
            edgeIndices.flip();

            for (int i = 0; i < numEdges; i++) {
                int idx1 = edgeIndices.get();
                int idx2 = edgeIndices.get();

                if (idx1 < vertices.length && idx2 < vertices.length) {
                    Vec3 p1 = vertices[idx1].getPosition();
                    Vec3 p2 = vertices[idx2].getPosition();
                    lines.add(new DebugLine(p1.toRVec3(), p2.toRVec3(), SOFT_BODY_COLOR.getUInt32()));
                }
            }
        }
        return lines;
    }
}