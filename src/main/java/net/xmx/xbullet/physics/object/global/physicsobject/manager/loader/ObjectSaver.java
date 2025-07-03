package net.xmx.xbullet.physics.object.global.physicsobject.manager.loader;

import net.xmx.xbullet.physics.XBulletSavedData;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import java.util.Collection;
import java.util.UUID;

class ObjectSaver {

    void saveObject(IPhysicsObject object, XBulletSavedData savedData) {
        if (object == null || savedData == null) return;
        savedData.updateObjectData(object);
        savedData.setDirty();
    }

    void saveAll(Collection<IPhysicsObject> objects, XBulletSavedData savedData) {
        if (objects == null || savedData == null) return;
        for (IPhysicsObject obj : objects) {
            savedData.updateObjectData(obj);
        }
        savedData.setDirty();
    }
    
    void removeObject(UUID id, XBulletSavedData savedData) {
        if (id == null || savedData == null) return;
        savedData.removeObjectData(id);
        savedData.setDirty();
    }
}