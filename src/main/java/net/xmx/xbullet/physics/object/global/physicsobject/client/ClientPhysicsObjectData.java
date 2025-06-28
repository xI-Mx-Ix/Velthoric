package net.xmx.xbullet.physics.object.global.physicsobject.client;

import net.minecraft.nbt.CompoundTag;
import net.xmx.xbullet.physics.object.global.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.rigidphysicsobject.client.ClientRigidPhysicsObjectData;
import net.xmx.xbullet.physics.object.softphysicsobject.client.ClientSoftPhysicsObjectData;

import javax.annotation.Nullable;
import java.util.UUID;

public class ClientPhysicsObjectData {
    private final UUID id;
    private final String typeIdentifier;
    private final EObjectType objectType;
    private CompoundTag syncedNbtData = new CompoundTag();

    @Nullable private ClientRigidPhysicsObjectData rigidData;
    @Nullable private ClientSoftPhysicsObjectData softData;

    public ClientPhysicsObjectData(UUID id, String typeIdentifier, EObjectType objectType) {
        this.id = id;
        this.typeIdentifier = typeIdentifier;
        this.objectType = objectType;
    }

    public void updateNbt(CompoundTag nbt) {
        this.syncedNbtData = nbt.copy();
        if (rigidData != null) rigidData.updateNbt(nbt);
        if (softData != null) softData.updateNbt(nbt);
    }

    public void updateInterpolation() {
        if (rigidData != null) rigidData.cleanupBuffer();
        if (softData != null) softData.cleanupBuffer();
    }

    public void cleanupAndRemove() {
        if (rigidData != null) rigidData.cleanupBuffer();
        if (softData != null) softData.cleanupBuffer();
    }

    public UUID getId() { return id; }
    public String getTypeIdentifier() { return typeIdentifier; }
    public EObjectType getObjectType() { return objectType; }
    public CompoundTag getSyncedNbtData() { return syncedNbtData.copy(); }
    @Nullable public ClientRigidPhysicsObjectData getRigidData() { return rigidData; }
    @Nullable public ClientSoftPhysicsObjectData getSoftData() { return softData; }

    public void setRigidData(ClientRigidPhysicsObjectData rigidData) { this.rigidData = rigidData; }
    public void setSoftData(ClientSoftPhysicsObjectData softData) { this.softData = softData; }
}