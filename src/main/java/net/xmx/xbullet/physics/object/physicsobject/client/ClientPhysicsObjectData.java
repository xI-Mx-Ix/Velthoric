package net.xmx.xbullet.physics.object.physicsobject.client;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.physics.object.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.client.ClientRigidPhysicsObjectData;
import net.xmx.xbullet.physics.object.physicsobject.type.soft.client.ClientSoftPhysicsObjectData;

import javax.annotation.Nullable;
import java.util.UUID;

public class ClientPhysicsObjectData {

    private final UUID id;
    private final String typeIdentifier;
    private final EObjectType objectType;

    @Nullable
    private ClientRigidPhysicsObjectData rigidData;
    @Nullable
    private ClientSoftPhysicsObjectData softData;

    public ClientPhysicsObjectData(UUID id, String typeIdentifier, EObjectType objectType) {
        this.id = id;
        this.typeIdentifier = typeIdentifier;
        this.objectType = objectType;
    }

    public void setRigidData(ClientRigidPhysicsObjectData rigidData) {
        this.rigidData = rigidData;
    }

    public void setSoftData(ClientSoftPhysicsObjectData softData) {
        this.softData = softData;
    }

    public void updateData(ByteBuf data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(data);
        if (rigidData != null) {
            rigidData.readData(buf);
        }
        if (softData != null) {
            softData.readData(buf);
        }
    }

    public void cleanupAndRemove() {
        if (rigidData != null) {
            rigidData.releaseAll();
        }
        if (softData != null) {
            softData.releaseAll();
        }
    }

    public void cleanupBuffers() {
        if (rigidData != null) {
            rigidData.cleanupBuffer();
        }
        if (softData != null) {
            softData.cleanupBuffer();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getTypeIdentifier() {
        return typeIdentifier;
    }

    public EObjectType getObjectType() {
        return objectType;
    }

    @Nullable
    public ClientRigidPhysicsObjectData getRigidData() {
        return rigidData;
    }

    @Nullable
    public ClientSoftPhysicsObjectData getSoftData() {
        return softData;
    }
}