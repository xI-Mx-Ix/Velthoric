package net.xmx.xbullet.physics.constraint.manager;

import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.constraint.IConstraint;
import net.xmx.xbullet.physics.constraint.ManagedConstraint;
import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.serializer.registry.ConstraintSerializerRegistry;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ConstraintLoader {

    private final ConstraintManager constraintManager;
    private final ObjectManager objectManager;
    private final Map<UUID, CompletableFuture<IConstraint>> pendingJointLoads = new ConcurrentHashMap<>();

    public ConstraintLoader(ConstraintManager constraintManager, ObjectManager objectManager) {
        this.constraintManager = constraintManager;
        this.objectManager = objectManager;
    }

    public void loadConstraintsForChunk(ChunkPos chunkPos) {
        if (!constraintManager.isInitialized() || !objectManager.isInitialized()) {
            return;
        }

        for (Map.Entry<UUID, CompoundTag> entry : constraintManager.getSavedData().getAllJointEntries()) {
            UUID jointId = entry.getKey();
            if (constraintManager.isJointLoaded(jointId) || pendingJointLoads.containsKey(jointId)) {
                continue;
            }

            CompoundTag jointTag = entry.getValue();
            if (isJointInChunk(jointTag, chunkPos)) {
                scheduleJointLoad(jointId);
            }
        }
    }

    public CompletableFuture<IConstraint> scheduleJointLoad(UUID jointId) {
        return pendingJointLoads.computeIfAbsent(jointId, id -> {
            Optional<CompoundTag> jointTagOpt = constraintManager.getSavedData().getJointData(id);
            if (jointTagOpt.isEmpty()) {
                pendingJointLoads.remove(id);
                return CompletableFuture.failedFuture(new IllegalStateException("No saved data for joint " + id));
            }

            CompoundTag jointTag = jointTagOpt.get();
            String typeId = jointTag.getString("constraintType");
            Optional<IConstraintSerializer<?>> serializerOpt = ConstraintSerializerRegistry.getSerializer(typeId);

            if (serializerOpt.isEmpty()) {
                pendingJointLoads.remove(id);
                return CompletableFuture.failedFuture(new IllegalArgumentException("No serializer for type " + typeId));
            }

            IConstraintSerializer<?> serializer = serializerOpt.get();
            CompletableFuture<TwoBodyConstraint> constraintFuture = serializer.createAndLink(jointTag, constraintManager, objectManager);

            return constraintFuture.thenApplyAsync(joltConstraint -> {
                        if (joltConstraint == null) {
                            XBullet.LOGGER.warn("Serializer for {} returned null constraint for joint {}", typeId, id);
                            return null;
                        }

                        TwoBodyConstraintRef constraintRef = joltConstraint.toRef();
                        try {
                            UUID[] bodyIds = serializer.loadBodyIds(jointTag);
                            if (bodyIds == null) {
                                throw new IllegalStateException("Could not load body IDs for a successfully created joint " + id);
                            }

                            ManagedConstraint managedConstraint = new ManagedConstraint(id, bodyIds[0], bodyIds[1], constraintRef, typeId);
                            constraintManager.addManagedConstraint(managedConstraint);
                            return (IConstraint) managedConstraint;
                        } catch (Exception e) {
                            constraintRef.close();
                            throw new RuntimeException(e);
                        }
                    }, PhysicsWorld.get(constraintManager.getManagedLevel().dimension()))
                    .whenComplete((res, ex) -> {
                        if (ex != null) {
                            XBullet.LOGGER.error("Failed to load joint {}", id, ex.getCause() != null ? ex.getCause() : ex);
                        }
                        if (res == null && ex == null) {
                            XBullet.LOGGER.warn("Joint {} loading resulted in null but no exception.", id);
                        }
                        pendingJointLoads.remove(id);
                    });
        });
    }

    private boolean isJointInChunk(CompoundTag jointTag, ChunkPos chunkPos) {
        UUID[] bodyIds = ConstraintSerializerRegistry.getSerializer(jointTag.getString("constraintType"))
                .map(s -> s.loadBodyIds(jointTag))
                .orElse(null);

        if (bodyIds == null) return false;

        return objectManager.getDataSystem().getSavedData().getObjectData(bodyIds[0]).map(tag -> isObjectInChunk(tag, chunkPos)).orElse(false) ||
                objectManager.getDataSystem().getSavedData().getObjectData(bodyIds[1]).map(tag -> isObjectInChunk(tag, chunkPos)).orElse(false);
    }

    private boolean isObjectInChunk(CompoundTag objTag, ChunkPos chunkPos) {
        if (objTag.contains("transform", 10)) {
            CompoundTag transformTag = objTag.getCompound("transform");
            if (transformTag.contains("pos", 9)) {
                ListTag posList = transformTag.getList("pos", Tag.TAG_DOUBLE);
                if (posList.size() == 3) {
                    double x = posList.getDouble(0);
                    double z = posList.getDouble(2);
                    return ((int) Math.floor(x / 16.0)) == chunkPos.x &&
                            ((int) Math.floor(z / 16.0)) == chunkPos.z;
                }
            }
        }
        return false;
    }

    @Nullable
    public CompletableFuture<IConstraint> getPendingLoad(UUID jointId) {
        return pendingJointLoads.get(jointId);
    }

    public void shutdown() {
        pendingJointLoads.values().forEach(f -> f.cancel(true));
        pendingJointLoads.clear();
    }
}