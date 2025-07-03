package net.xmx.xbullet.physics.object.global.physicsobject.manager.loader;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.xbullet.physics.XBulletSavedData;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ObjectDataSystem {

    private final ObjectManager objectManager;
    private final ObjectLoader loader;
    private final ObjectSaver saver;
    private XBulletSavedData savedData;

    public ObjectDataSystem(ObjectManager objectManager) {
        this.objectManager = objectManager;
        this.loader = new ObjectLoader(objectManager, this);
        this.saver = new ObjectSaver();
    }

    public void initialize(ServerLevel level) {
        this.savedData = XBulletSavedData.get(level);
        this.loader.initialize();
    }

    public void loadObjectsInChunk(ChunkPos chunkPos) {
        if (savedData != null) {
            loader.loadObjectsInChunk(chunkPos, savedData);
        }
    }

    @Nullable
    public XBulletSavedData getSavedData() {
        return this.savedData;
    }

    public CompletableFuture<IPhysicsObject> getOrLoadObject(UUID objectId, boolean initiallyActive) {
        return loader.scheduleObjectLoad(objectId, initiallyActive, savedData);
    }
    
    public void unloadObjectsInChunk(ChunkPos chunkPos) {
        loader.unloadObjectsInChunk(chunkPos);
    }

    public void saveObject(IPhysicsObject object) {
        if (savedData != null) {
            saver.saveObject(object, savedData);
        }
    }
    
    public void saveAll(Collection<IPhysicsObject> objects) {
        if(savedData != null) {
            saver.saveAll(objects, savedData);
        }
    }

    public void removeObject(UUID id) {
        if (savedData != null) {
            saver.removeObject(id, savedData);
        }
    }

    public boolean hasObjectData(UUID id) {
        return savedData != null && savedData.getObjectData(id).isPresent();
    }
    
    public void cancelLoad(UUID id) {
        loader.cancelLoad(id);
    }

    public void shutdown() {
        loader.shutdown();
    }

    public int getPendingLoadCount() {
        return loader.getPendingLoadCount();
    }
}