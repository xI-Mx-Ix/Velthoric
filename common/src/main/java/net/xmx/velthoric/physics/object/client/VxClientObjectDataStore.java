package net.xmx.velthoric.physics.object.client;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.xmx.velthoric.physics.object.AbstractDataStore;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.*;

public class VxClientObjectDataStore extends AbstractDataStore {
    private static final int INITIAL_CAPACITY = 256;

    private final Map<UUID, Integer> uuidToIndex = new HashMap<>();
    private final List<UUID> indexToUuid = new ArrayList<>();
    private final Deque<Integer> freeIndices = new ArrayDeque<>();
    private int count = 0;
    private int capacity = 0;

    public long[] state0_timestamp;
    public float[] state0_posX, state0_posY, state0_posZ;
    public float[] state0_rotX, state0_rotY, state0_rotZ, state0_rotW;
    public float[] state0_velX, state0_velY, state0_velZ;
    public boolean[] state0_isActive;
    public float[] @Nullable [] state0_vertexData;

    public long[] state1_timestamp;
    public float[] state1_posX, state1_posY, state1_posZ;
    public float[] state1_rotX, state1_rotY, state1_rotZ, state1_rotW;
    public float[] state1_velX, state1_velY, state1_velZ;
    public boolean[] state1_isActive;
    public float[] @Nullable [] state1_vertexData;

    public float[] prev_posX, prev_posY, prev_posZ;
    public float[] prev_rotX, prev_rotY, prev_rotZ, prev_rotW;
    public float[] @Nullable [] prev_vertexData;

    public float[] render_posX, render_posY, render_posZ;
    public float[] render_rotX, render_rotY, render_rotZ, render_rotW;
    public float[] @Nullable [] render_vertexData;
    public boolean[] render_isInitialized;

    public EBodyType[] objectType;
    public Object[] renderer;
    public ByteBuffer[] customData;
    public RVec3[] lastKnownPosition;

    public VxClientObjectDataStore() {
        allocate(INITIAL_CAPACITY);
    }

    private void allocate(int newCapacity) {
        state0_timestamp = grow(state0_timestamp, newCapacity);
        state0_posX = grow(state0_posX, newCapacity);
        state0_posY = grow(state0_posY, newCapacity);
        state0_posZ = grow(state0_posZ, newCapacity);
        state0_rotX = grow(state0_rotX, newCapacity);
        state0_rotY = grow(state0_rotY, newCapacity);
        state0_rotZ = grow(state0_rotZ, newCapacity);
        state0_rotW = grow(state0_rotW, newCapacity);
        state0_velX = grow(state0_velX, newCapacity);
        state0_velY = grow(state0_velY, newCapacity);
        state0_velZ = grow(state0_velZ, newCapacity);
        state0_isActive = grow(state0_isActive, newCapacity);
        state0_vertexData = grow(state0_vertexData, newCapacity);

        state1_timestamp = grow(state1_timestamp, newCapacity);
        state1_posX = grow(state1_posX, newCapacity);
        state1_posY = grow(state1_posY, newCapacity);
        state1_posZ = grow(state1_posZ, newCapacity);
        state1_rotX = grow(state1_rotX, newCapacity);
        state1_rotY = grow(state1_rotY, newCapacity);
        state1_rotZ = grow(state1_rotZ, newCapacity);
        state1_rotW = grow(state1_rotW, newCapacity);
        state1_velX = grow(state1_velX, newCapacity);
        state1_velY = grow(state1_velY, newCapacity);
        state1_velZ = grow(state1_velZ, newCapacity);
        state1_isActive = grow(state1_isActive, newCapacity);
        state1_vertexData = grow(state1_vertexData, newCapacity);

        prev_posX = grow(prev_posX, newCapacity);
        prev_posY = grow(prev_posY, newCapacity);
        prev_posZ = grow(prev_posZ, newCapacity);
        prev_rotX = grow(prev_rotX, newCapacity);
        prev_rotY = grow(prev_rotY, newCapacity);
        prev_rotZ = grow(prev_rotZ, newCapacity);
        prev_rotW = grow(prev_rotW, newCapacity);
        prev_vertexData = grow(prev_vertexData, newCapacity);

        render_posX = grow(render_posX, newCapacity);
        render_posY = grow(render_posY, newCapacity);
        render_posZ = grow(render_posZ, newCapacity);
        render_rotX = grow(render_rotX, newCapacity);
        render_rotY = grow(render_rotY, newCapacity);
        render_rotZ = grow(render_rotZ, newCapacity);
        render_rotW = grow(render_rotW, newCapacity);
        render_vertexData = grow(render_vertexData, newCapacity);
        render_isInitialized = grow(render_isInitialized, newCapacity);

        objectType = grow(objectType, newCapacity);
        renderer = grow(renderer, newCapacity);
        customData = grow(customData, newCapacity);
        lastKnownPosition = grow(lastKnownPosition, newCapacity);

        this.capacity = newCapacity;
    }

    public int addObject(UUID id) {
        if (count == capacity) {
            allocate(capacity * 2);
        }
        int index = freeIndices.isEmpty() ? count++ : freeIndices.pop();

        uuidToIndex.put(id, index);
        if (index >= indexToUuid.size()) {
            indexToUuid.add(id);
        } else {
            indexToUuid.set(index, id);
        }
        return index;
    }

    public void removeObject(UUID id) {
        Integer index = uuidToIndex.remove(id);
        if (index != null) {
            resetIndex(index);
            freeIndices.push(index);
            indexToUuid.set(index, null);
        }
    }

    public void clear() {
        uuidToIndex.clear();
        indexToUuid.clear();
        freeIndices.clear();
        count = 0;
        allocate(INITIAL_CAPACITY);
    }

    @Nullable
    public Integer getIndexForId(UUID id) {
        return uuidToIndex.get(id);
    }

    public int getObjectCount() {
        return this.count;
    }

    public Collection<UUID> getAllObjectIds() {
        return Collections.unmodifiableSet(uuidToIndex.keySet());
    }

    public boolean hasObject(UUID id) {
        return uuidToIndex.containsKey(id);
    }

    private void resetIndex(int index) {
        state0_timestamp[index] = 0;
        state1_timestamp[index] = 0;
        render_isInitialized[index] = false;
        state0_isActive[index] = false;
        state1_isActive[index] = false;
        state0_vertexData[index] = null;
        state1_vertexData[index] = null;
        prev_vertexData[index] = null;
        render_vertexData[index] = null;
        renderer[index] = null;
        customData[index] = null;
        objectType[index] = null;
        if (lastKnownPosition != null && lastKnownPosition[index] != null) {
            lastKnownPosition[index].loadZero();
        }
        state0_velX[index] = state0_velY[index] = state0_velZ[index] = 0;
        state1_velX[index] = state1_velY[index] = state1_velZ[index] = 0;
    }
}