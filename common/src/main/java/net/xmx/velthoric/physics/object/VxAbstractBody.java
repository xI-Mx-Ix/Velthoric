package net.xmx.velthoric.physics.object;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyLockRead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public abstract class VxAbstractBody {

    protected final UUID physicsId;
    protected final PhysicsObjectType<? extends VxAbstractBody> type;
    protected final VxPhysicsWorld world;
    protected int bodyId = 0;
    protected final VxTransform gameTransform = new VxTransform();
    protected boolean isDataDirty = false;

    protected VxAbstractBody(PhysicsObjectType<? extends VxAbstractBody> type, VxPhysicsWorld world, UUID id) {
        this.type = type;
        this.world = world;
        this.physicsId = id;
    }

    public void physicsTick(VxPhysicsWorld world) {}

    public void gameTick(ServerLevel level) {}

    public void writeCreationData(FriendlyByteBuf buf) {}

    public void readCreationData(FriendlyByteBuf buf) {}

    public Optional<VxTransform> getTransform(VxPhysicsWorld world) {
        if (bodyId == 0) return Optional.empty();

        try (BodyLockRead lock = new BodyLockRead(world.getBodyLockInterface(), this.bodyId)) {
            if (lock.succeededAndIsInBroadPhase()) {
                VxTransform transform = new VxTransform();
                lock.getBody().getPositionAndRotation(transform.getTranslation(), transform.getRotation());
                return Optional.of(transform);
            }
        }
        return Optional.empty();
    }

    @Nullable
    public Body getBody() {
        if (bodyId == 0) {
            return null;
        }
        Optional<? extends VxAbstractBody> found = world.getObjectManager().getObjectContainer().getByBodyId(bodyId);
        if (found.isPresent() && found.get() == this) {
            try (BodyLockRead lock = new BodyLockRead(world.getBodyLockInterface(), bodyId)) {
                if (lock.succeededAndIsInBroadPhase()) {
                    return lock.getBody();
                }
            }
        }
        VxMainClass.LOGGER.warn("Returned null Body for bodyId {}", bodyId);
        return null;
    }

    public void setBodyId(int bodyId) {
        this.bodyId = bodyId;
    }

    public UUID getPhysicsId() {
        return this.physicsId;
    }

    public int getBodyId() {
        return this.bodyId;
    }

    public PhysicsObjectType<? extends VxAbstractBody> getType() {
        return type;
    }

    public VxTransform getGameTransform() {
        return this.gameTransform;
    }

    public VxPhysicsWorld getWorld() {
        return this.world;
    }

    public void markDataDirty() {
        this.isDataDirty = true;
    }

    public boolean isDataDirty() {
        return this.isDataDirty;
    }

    public void clearDataDirty() {
        this.isDataDirty = false;
    }
}