/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.mesh;

import net.xmx.velthoric.renderer.VxDrawCommand;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A complete, CPU-side description of a renderable mesh.
 *
 * <p>This class holds the raw vertex data and a structural organization of that data.
 * It supports named grouping, allowing specific parts of the model (defined by 'g' or 'o' tags
 * in the source file) to be accessed and rendered independently.</p>
 *
 * @author xI-Mx-Ix
 */
public class VxMeshDefinition {
    private final ByteBuffer vertexData;

    /**
     * A flat list of all draw commands required to render the complete model.
     */
    public final List<VxDrawCommand> allDrawCommands;

    /**
     * A map linking raw group names (from the source model file) to their specific draw commands.
     */
    private final Map<String, List<VxDrawCommand>> groupDrawCommands;

    /**
     * Constructs a new mesh definition.
     *
     * @param vertexData        A direct ByteBuffer containing the interleaved vertex data.
     * @param allDrawCommands   A list of commands describing how to render the entire mesh.
     * @param groupDrawCommands A map associating group names with their specific subset of draw commands.
     */
    public VxMeshDefinition(ByteBuffer vertexData, List<VxDrawCommand> allDrawCommands, Map<String, List<VxDrawCommand>> groupDrawCommands) {
        this.vertexData = vertexData;
        this.allDrawCommands = allDrawCommands;
        this.groupDrawCommands = groupDrawCommands;
    }

    /**
     * Gets the vertex data buffer.
     *
     * @return A read-only view of the vertex buffer, ready for uploading.
     */
    public ByteBuffer getVertexData() {
        return vertexData.asReadOnlyBuffer();
    }

    /**
     * Retrieves the list of draw commands associated with a specific group name.
     *
     * @param groupName The name of the group as defined in the source model file (case-sensitive).
     * @return A list of draw commands for that group, or an empty list if the group does not exist.
     */
    public List<VxDrawCommand> getGroupParts(String groupName) {
        return groupDrawCommands.getOrDefault(groupName, Collections.emptyList());
    }

    /**
     * Retrieves the full map of group names to draw commands.
     * Used by mesh implementations to store grouping logic.
     *
     * @return The map of group draw commands.
     */
    public Map<String, List<VxDrawCommand>> getGroupDrawCommands() {
        return groupDrawCommands;
    }

    /**
     * Returns a set of all group names available in this mesh.
     *
     * @return A set of strings representing the group names.
     */
    public Set<String> getAvailableGroups() {
        return Collections.unmodifiableSet(groupDrawCommands.keySet());
    }
}