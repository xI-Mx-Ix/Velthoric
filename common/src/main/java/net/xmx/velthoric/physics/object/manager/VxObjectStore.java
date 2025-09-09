package net.xmx.velthoric.physics.object.manager;

import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.xmx.velthoric.physics.object.AbstractDataStore;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VxObjectStore extends AbstractDataStore {
    private static final int INITIAL_CAPACITY = 256;

    private final Map<UUID, Integer> uuidToIndex = new HashMap<>();
    private final List<UUID> indexToUuid = new ArrayList<>();
    private final Deque<Integer> freeIndices = new ArrayDeque<>();
    private int count = 0;
    private int capacity = 0;

    // Physics State Data
    public float[] posX, posY, posZ;
    public float[] rotX, rotY, rotZ, rotW;
    public float[] velX, velY, velZ;
    public float[] angVelX, angVelY, angVelZ;
    public float[] @Nullable [] vertexData;
    public boolean[] isActive;
    public EBodyType[] bodyType;

    // Sync & Management Data
    public boolean[] isDirty;
    public long[] lastUpdateTimestamp;

    public VxObjectStore() {
        allocate(INITIAL_CAPACITY);
    }

    private void allocate(int newCapacity) {
        posX = grow(posX, newCapacity);
        posY = grow(posY, newCapacity);
        posZ = grow(posZ, newCapacity);
        rotX = grow(rotX, newCapacity);
        rotY = grow(rotY, newCapacity);
        rotZ = grow(rotZ, newCapacity);
        rotW = grow(rotW, newCapacity);
        velX = grow(velX, newCapacity);
        velY = grow(velY, newCapacity);
        velZ = grow(velZ, newCapacity);
        angVelX = grow(angVelX, newCapacity);
        angVelY = grow(angVelY, newCapacity);
        angVelZ = grow(angVelZ, newCapacity);
        vertexData = grow(vertexData, newCapacity);
        isActive = grow(isActive, newCapacity);
        bodyType = grow(bodyType, newCapacity);

        isDirty = grow(isDirty, newCapacity);
        lastUpdateTimestamp = grow(lastUpdateTimestamp, newCapacity);

        this.capacity = newCapacity;
    }

    public int addObject(UUID id, EBodyType type) {
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

        bodyType[index] = type;
        return index;
    }

    public Optional<Integer> removeObject(UUID id) {
        Integer index = uuidToIndex.remove(id);
        if (index != null) {
            resetIndex(index);
            freeIndices.push(index);
            indexToUuid.set(index, null);
            return Optional.of(index);
        }
        return Optional.empty();
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

    @Nullable
    public UUID getIdForIndex(int index) {
        if (index < 0 || index >= indexToUuid.size()) {
            return null;
        }
        return indexToUuid.get(index);
    }

    public int getObjectCount() {
        return this.count - freeIndices.size();
    }

    public int getCapacity() {
        return this.capacity;
    }

    private void resetIndex(int index) {
        posX[index] = posY[index] = posZ[index] = 0f;
        rotX[index] = rotY[index] = rotZ[index] = 0f;
        rotW[index] = 1f;
        velX[index] = velY[index] = velZ[index] = 0f;
        angVelX[index] = angVelY[index] = angVelZ[index] = 0f;
        vertexData[index] = null;
        isActive[index] = false;
        bodyType[index] = null;
        isDirty[index] = false;
        lastUpdateTimestamp[index] = 0L;
    }
}