/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer.mesh.arena;

import com.mojang.blaze3d.systems.RenderSystem;
import net.xmx.velthoric.renderer.VxRConstants;
import net.xmx.velthoric.renderer.gl.VxVertexBuffer;
import net.xmx.velthoric.renderer.gl.VxVertexLayout;
import net.xmx.velthoric.renderer.mesh.VxMeshDefinition;
import net.xmx.velthoric.renderer.mesh.impl.VxArenaMesh;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Objects;

/**
 * Manages a single, large, dynamically-growing Vertex Buffer Object (VBO) and a corresponding
 * Vertex Array Object (VAO) as a singleton to store many smaller meshes efficiently.
 * <p>
 * This implementation uses a free-list allocator to manage memory, allowing
 * sub-meshes to be deallocated and their space to be reused. If the buffer runs out of space,
 * it automatically resizes itself, preserving existing data.
 * <p>
 * Instead of managing raw OpenGL handles directly, this class now delegates the low-level
 * buffer management to {@link VxVertexBuffer}, allowing for cleaner resource handling.
 *
 * @author xI-Mx-Ix
 */
public class VxArenaBuffer {

    private static final int INITIAL_CAPACITY_VERTICES = 262144;
    private static VxArenaBuffer instance;

    /**
     * Represents a contiguous block of free memory within the VBO.
     * Used by the internal allocator to track reusable gaps.
     */
    private static class FreeBlock {
        long offsetBytes;
        long sizeBytes;

        FreeBlock(long offsetBytes, long sizeBytes) {
            this.offsetBytes = offsetBytes;
            this.sizeBytes = sizeBytes;
        }

        @Override
        public String toString() {
            return "FreeBlock{" + "offset=" + offsetBytes + ", size=" + sizeBytes + '}';
        }
    }

    /**
     * The underlying vertex buffer wrapper that handles the actual GL calls.
     */
    private VxVertexBuffer buffer;

    /**
     * The high-water mark for the buffer, indicating the next available byte at the end of the used section.
     */
    private long usedBytes = 0;

    /**
     * A list of available memory regions, sorted by offset.
     * This list is central to the free-list allocation strategy.
     */
    private final LinkedList<FreeBlock> freeBlocks = new LinkedList<>();

    /**
     * Private constructor to enforce the singleton pattern.
     * Initializes the GPU resources via {@link VxVertexBuffer} with the initial capacity.
     *
     * @param capacityInVertices The initial maximum number of vertices this buffer can hold.
     */
    private VxArenaBuffer(int capacityInVertices) {
        long capacityBytes = (long) capacityInVertices * VxVertexLayout.STRIDE;
        // Initialize the dedicated buffer wrapper with dynamic usage hint.
        this.buffer = new VxVertexBuffer(capacityBytes, true);
    }

    /**
     * Gets the singleton instance of the arena buffer, creating it if necessary.
     *
     * @return The global VxArenaBuffer instance.
     */
    public static synchronized VxArenaBuffer getInstance() {
        if (instance == null || instance.buffer == null) {
            instance = new VxArenaBuffer(INITIAL_CAPACITY_VERTICES);
        }
        return instance;
    }

    /**
     * Resizes the VBO to a new, larger capacity, copying all existing data.
     * This method creates a new VBO wrapper, copies data from the old one, and then swaps them.
     *
     * @param requiredBytes The minimum number of additional bytes needed.
     */
    private void resize(long requiredBytes) {
        RenderSystem.assertOnRenderThread();

        long currentCapacity = this.buffer.getCapacityBytes();
        long newCapacity = Math.max((long) (currentCapacity * 1.5), currentCapacity + requiredBytes);

        VxRConstants.LOGGER.warn("Resizing VxArenaBuffer from {} to {} bytes. This may cause a brief stutter.", currentCapacity, newCapacity);

        // 1. Create a new, larger buffer.
        VxVertexBuffer newBuffer = new VxVertexBuffer(newCapacity, true);

        // 2. Copy the vertex data from the old buffer to the new one using GPU-side copy.
        this.buffer.copyTo(newBuffer, this.usedBytes);

        // 3. Delete the old buffer resources.
        this.buffer.delete();

        // 4. Swap the reference to point to the new buffer.
        this.buffer = newBuffer;
    }

    /**
     * Allocates space in the buffer for a new mesh and uploads its data.
     * It first attempts to find a suitable block from the free-list. If none is
     * found, it allocates new space from the end of the buffer. If the buffer is full,
     * it will be automatically resized.
     *
     * @param definition The mesh definition to add to the arena.
     * @return A {@link VxArenaMesh} handle representing the mesh's data in the shared buffer.
     */
    public VxArenaMesh allocate(VxMeshDefinition definition) {
        Objects.requireNonNull(definition, "Mesh definition cannot be null");
        RenderSystem.assertOnRenderThread();

        ByteBuffer modelBuffer = definition.getVertexData();
        int modelSizeBytes = modelBuffer.remaining();
        long allocationOffset = -1;

        // First-fit strategy: find the first free block that is large enough.
        ListIterator<FreeBlock> iterator = this.freeBlocks.listIterator();
        while (iterator.hasNext()) {
            FreeBlock block = iterator.next();
            if (block.sizeBytes >= modelSizeBytes) {
                allocationOffset = block.offsetBytes;

                // If the block is larger than needed, shrink it. Otherwise, remove it completely.
                if (block.sizeBytes > modelSizeBytes) {
                    block.offsetBytes += modelSizeBytes;
                    block.sizeBytes -= modelSizeBytes;
                } else {
                    iterator.remove();
                }
                break;
            }
        }

        // If no suitable free block was found, allocate from the end (bump allocation).
        if (allocationOffset == -1) {
            if (this.usedBytes + modelSizeBytes > this.buffer.getCapacityBytes()) {
                resize(modelSizeBytes);
            }
            allocationOffset = this.usedBytes;
            this.usedBytes += modelSizeBytes;
        }

        // Upload the new mesh data into the determined slot in the VBO via the wrapper.
        this.buffer.uploadSubData(allocationOffset, modelBuffer);

        // Create the mesh handle passing the necessary group maps for hierarchical rendering.
        return new VxArenaMesh(this, allocationOffset, modelSizeBytes, definition.allDrawCommands, definition.getGroupDrawCommands());
    }

    /**
     * Frees the memory associated with a sub-mesh, making it available for
     * future allocations. This method adds the freed block to the free-list and
     * attempts to merge it with adjacent free blocks to reduce fragmentation.
     *
     * @param subMesh The sub-mesh to deallocate.
     */
    public void free(VxArenaMesh subMesh) {
        RenderSystem.assertOnRenderThread();
        if (subMesh == null) return;

        long offset = subMesh.getOffsetBytes();
        long size = subMesh.getSizeBytes();

        // Optimization: if we are freeing the last element, we can just rewind the high-water mark.
        if (offset + size == this.usedBytes) {
            this.usedBytes = offset;
            // Also check if this newly freed space can merge with the last free block.
            if (!this.freeBlocks.isEmpty() && this.freeBlocks.getLast().offsetBytes + this.freeBlocks.getLast().sizeBytes == this.usedBytes) {
                this.usedBytes = this.freeBlocks.removeLast().offsetBytes;
            }
            return;
        }

        // Insert the new free block into the sorted list.
        ListIterator<FreeBlock> iterator = this.freeBlocks.listIterator();
        while (iterator.hasNext()) {
            if (iterator.next().offsetBytes > offset) {
                iterator.previous();
                break;
            }
        }
        iterator.add(new FreeBlock(offset, size));

        // Attempt to merge with previous and next blocks to reduce fragmentation.
        // 1. Check previous
        if (iterator.hasPrevious()) {
            FreeBlock previous = iterator.previous(); // Go to the newly added block
            if (iterator.hasPrevious()) {
                FreeBlock before = iterator.previous(); // Go to the block before it
                // Merge with the block before
                if (before.offsetBytes + before.sizeBytes == previous.offsetBytes) {
                    before.sizeBytes += previous.sizeBytes;
                    iterator.next(); // Go back to `before`
                    iterator.next(); // Go back to `previous`
                    iterator.remove(); // Remove `previous`
                    iterator.previous(); // Move cursor back to the merged block
                } else {
                    iterator.next(); // Go back to the newly added block
                }
            } else {
                iterator.next(); // Go back to the newly added block
            }
        }

        FreeBlock current = iterator.previous(); // The block we are potentially merging into
        iterator.next(); // Advance cursor past it

        // 2. Check next
        if (iterator.hasNext()) {
            FreeBlock next = iterator.next();
            // Merge with the block after
            if (current.offsetBytes + current.sizeBytes == next.offsetBytes) {
                current.sizeBytes += next.sizeBytes;
                iterator.remove();
            }
        }
    }

    /**
     * Binds the shared VAO to prepare for rendering a batch of ArenaSubMeshes.
     * This delegates to the underlying {@link VxVertexBuffer#bind()}.
     */
    public void preRender() {
        if (this.buffer != null) {
            this.buffer.bind();
        }
    }

    /**
     * Deletes the shared VBO and VAO, freeing all GPU memory.
     * This invalidates the singleton instance, which will be recreated on the next `getInstance()` call.
     */
    public void delete() {
        RenderSystem.assertOnRenderThread();
        if (this.buffer != null) {
            this.buffer.delete();
            this.buffer = null;
        }
        this.freeBlocks.clear();
        this.usedBytes = 0;
    }
}