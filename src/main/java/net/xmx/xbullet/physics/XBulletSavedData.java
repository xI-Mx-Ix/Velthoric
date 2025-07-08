package net.xmx.xbullet.physics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.physicsobject.IPhysicsObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class XBulletSavedData extends SavedData {

    private static final String DATA_NAME = "xbullet";

    private final ConcurrentMap<UUID, CompoundTag> physicsObjectDataMap = new ConcurrentHashMap<>();

    private final ConcurrentMap<UUID, CompoundTag> jointDataMap = new ConcurrentHashMap<>();

    @Override
    public CompoundTag save(CompoundTag nbt) {

        ListTag objectListTag = new ListTag();
        for (CompoundTag objTag : physicsObjectDataMap.values()) {
            objectListTag.add(objTag.copy());
        }
        nbt.put("physicsObjects", objectListTag);

        ListTag jointListTag = new ListTag();
        for (CompoundTag jointTag : jointDataMap.values()) {
            jointListTag.add(jointTag.copy());
        }
        nbt.put("physicsJoints", jointListTag);

        return nbt;
    }

    public static XBulletSavedData load(CompoundTag nbt) {
        XBulletSavedData savedData = new XBulletSavedData();

        if (nbt.contains("physicsObjects", ListTag.TAG_LIST)) {
            ListTag listTag = nbt.getList("physicsObjects", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag objTag = listTag.getCompound(i);
                if (objTag.hasUUID("physicsId")) {
                    UUID id = objTag.getUUID("physicsId");
                    savedData.physicsObjectDataMap.put(id, objTag);
                } else {
                    XBullet.LOGGER.warn("Found a saved physics object without a UUID! Skipping entry: {}", objTag);
                }
            }
        }

        if (nbt.contains("physicsJoints", ListTag.TAG_LIST)) {
            ListTag jointListTag = nbt.getList("physicsJoints", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < jointListTag.size(); i++) {
                CompoundTag jointTag = jointListTag.getCompound(i);
                if (jointTag.hasUUID("jointDataId")) {
                    UUID id = jointTag.getUUID("jointDataId");
                    savedData.jointDataMap.put(id, jointTag);
                } else {
                    XBullet.LOGGER.warn("Found a saved joint without a UUID! Skipping entry: {}", jointTag);
                }
            }
        }
        return savedData;
    }

    public static XBulletSavedData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(XBulletSavedData::load, XBulletSavedData::new, DATA_NAME);
    }

    public void updateObjectData(IPhysicsObject physicsObject) {
        CompoundTag tag = physicsObject.saveToNbt(new CompoundTag());
        physicsObjectDataMap.put(physicsObject.getPhysicsId(), tag);
        setDirty();
    }

    public void removeObjectData(UUID id) {
        if (physicsObjectDataMap.remove(id) != null) {
            setDirty();
        }
    }

    public Optional<CompoundTag> getObjectData(UUID id) {
        return Optional.ofNullable(physicsObjectDataMap.get(id)).map(CompoundTag::copy);
    }

    public Set<Map.Entry<UUID, CompoundTag>> getAllObjectEntries() {
        return physicsObjectDataMap.entrySet();
    }

    public void updateJointData(UUID jointId, CompoundTag jointTag) {
        jointDataMap.put(jointId, jointTag.copy());
        setDirty();
    }

    public void removeJointData(UUID jointId) {
        if (jointDataMap.remove(jointId) != null) {
            setDirty();
        }
    }

    public Optional<CompoundTag> getJointData(UUID id) {
        return Optional.ofNullable(jointDataMap.get(id)).map(CompoundTag::copy);
    }

    public Set<Map.Entry<UUID, CompoundTag>> getAllJointEntries() {
        return jointDataMap.entrySet();
    }

    public Collection<CompoundTag> getAllJointDataTags() {
        return jointDataMap.values().stream()
                .map(CompoundTag::copy)
                .collect(Collectors.toList());
    }
}