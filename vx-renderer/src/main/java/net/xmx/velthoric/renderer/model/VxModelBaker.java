/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.model;

import net.xmx.velthoric.renderer.gl.VxDrawCommand;
import net.xmx.velthoric.renderer.gl.VxMaterial;
import net.xmx.velthoric.renderer.mesh.VxMeshDefinition;
import net.xmx.velthoric.renderer.model.generator.VxNormalGenerator;
import net.xmx.velthoric.renderer.model.generator.VxTangentGenerator;
import net.xmx.velthoric.renderer.model.raw.VxRawGroup;
import net.xmx.velthoric.renderer.model.raw.VxRawMesh;
import net.xmx.velthoric.renderer.model.raw.VxRawModel;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Responsible for converting a {@link VxRawModel} (CPU-side editable data) into a
 * {@link VxMeshDefinition} (GPU-ready interleaved buffer).
 * <p>
 * This class reads the optimized flat index arrays from {@link VxRawMesh} and processes
 * them into a stream of vertices for OpenGL.
 *
 * @author xI-Mx-Ix
 */
public class VxModelBaker {

    /**
     * Bakes the raw model into a renderable mesh definition.
     *
     * @param rawModel The raw model data.
     * @return The GPU-ready mesh definition.
     */
    public static VxMeshDefinition bake(VxRawModel rawModel) {
        int totalVertices = rawModel.calculateTotalVertexCount();
        if (totalVertices == 0) {
            return new VxMeshDefinition(ByteBuffer.allocate(0), new ArrayList<>(), new HashMap<>());
        }

        // Allocate the final buffer
        ByteBuffer buffer = ByteBuffer.allocateDirect(totalVertices * VxVertexBufferBuilder.STRIDE)
                .order(ByteOrder.nativeOrder());

        VxVertexBufferBuilder builder = new VxVertexBufferBuilder();

        List<VxDrawCommand> allCommands = new ArrayList<>();
        Map<String, List<VxDrawCommand>> groupCommands = new HashMap<>();

        int currentVertexOffset = 0;

        // Iterate over groups
        for (VxRawGroup group : rawModel.groups.values()) {
            List<VxDrawCommand> thisGroupCommands = new ArrayList<>();

            // Iterate over materials (meshes) within the group
            for (Map.Entry<String, VxRawMesh> entry : group.meshesByMaterial.entrySet()) {
                String matName = entry.getKey();
                VxRawMesh mesh = entry.getValue();
                int faceCount = mesh.getFaceCount();

                if (faceCount == 0) continue;

                int startVertex = currentVertexOffset;
                int vertexCount = 0;

                VxMaterial material = rawModel.materials.getOrDefault(matName, new VxMaterial("default"));

                // Iterate over faces using the flat index array
                for (int i = 0; i < faceCount; i++) {
                    // Extract indices using the helper methods of VxRawMesh
                    int v0 = mesh.getPositionIndex(i, 0);
                    int v1 = mesh.getPositionIndex(i, 1);
                    int v2 = mesh.getPositionIndex(i, 2);

                    int uv0 = mesh.getUvIndex(i, 0);
                    int uv1 = mesh.getUvIndex(i, 1);
                    int uv2 = mesh.getUvIndex(i, 2);

                    int n0 = mesh.getNormalIndex(i, 0);
                    int n1 = mesh.getNormalIndex(i, 1);
                    int n2 = mesh.getNormalIndex(i, 2);

                    // Calculate flat normal if vertex normals are missing for this face
                    Vector3f flatNormal = null;
                    if (!mesh.hasNormals(i)) {
                        flatNormal = VxNormalGenerator.calculateFaceNormal(rawModel.positions, v0, v1, v2);
                    }

                    // Write the 3 vertices of the triangle to the builder
                    writeVertexToBuilder(builder, rawModel, v0, uv0, n0, material, flatNormal);
                    writeVertexToBuilder(builder, rawModel, v1, uv1, n1, material, flatNormal);
                    writeVertexToBuilder(builder, rawModel, v2, uv2, n2, material, flatNormal);

                    vertexCount += 3;
                }

                // Append to main buffer
                ByteBuffer segment = builder.buildAndReset();
                buffer.put(segment);

                // Create Draw Command
                VxDrawCommand cmd = new VxDrawCommand(material, startVertex, vertexCount);
                allCommands.add(cmd);
                thisGroupCommands.add(cmd);

                currentVertexOffset += vertexCount;
            }

            if (!thisGroupCommands.isEmpty()) {
                groupCommands.put(group.name, thisGroupCommands);
            }
        }

        // Finalize
        buffer.flip();
        VxTangentGenerator.calculate(buffer, totalVertices);
        buffer.rewind();

        return new VxMeshDefinition(buffer, allCommands, groupCommands);
    }

    private static void writeVertexToBuilder(VxVertexBufferBuilder builder, VxRawModel raw, int pIdx, int uIdx, int nIdx, VxMaterial mat, Vector3f flatNormal) {
        builder.writeVertex(
                raw.positions,
                raw.texCoords,
                raw.normals,
                raw.colors,
                pIdx, uIdx, nIdx,
                mat.baseColorFactor,
                flatNormal
        );
    }
}