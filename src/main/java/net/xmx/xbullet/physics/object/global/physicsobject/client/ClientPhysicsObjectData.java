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

    /**
     * Speichert die NBT-Daten und leitet sie sofort an das bereits vorhandene
     * Kind-Objekt (rigid oder soft) weiter.
     */
    public void updateNbt(CompoundTag nbt) {
        this.syncedNbtData = nbt.copy();
        if (rigidData != null) {
            rigidData.updateNbt(this.syncedNbtData);
        }
        if (softData != null) {
            softData.updateNbt(this.syncedNbtData);
        }
    }

    /**
     * Setzt die RigidBody-Daten.
     * WICHTIG: Wenn für diesen Container bereits NBT-Daten gespeichert wurden,
     * werden sie sofort an das neue rigidData-Objekt übergeben.
     * Das macht die Initialisierungsreihenfolge flexibler.
     */
    public void setRigidData(ClientRigidPhysicsObjectData rigidData) {
        this.rigidData = rigidData;
        if (this.rigidData != null && !this.syncedNbtData.isEmpty()) {
            this.rigidData.updateNbt(this.syncedNbtData);
        }
    }

    /**
     * Setzt die SoftBody-Daten.
     * WICHTIG: Übergibt ebenfalls bereits vorhandene NBT-Daten sofort weiter.
     */
    public void setSoftData(ClientSoftPhysicsObjectData softData) {
        this.softData = softData;
        if (this.softData != null && !this.syncedNbtData.isEmpty()) {
            this.softData.updateNbt(this.syncedNbtData);
        }
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
}