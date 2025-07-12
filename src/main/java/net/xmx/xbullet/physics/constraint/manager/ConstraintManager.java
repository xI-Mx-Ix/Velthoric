package net.xmx.xbullet.physics.constraint.manager;

import com.github.stephengold.joltjni.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.constraint.IConstraint;
import net.xmx.xbullet.physics.constraint.ManagedConstraint;
import net.xmx.xbullet.physics.constraint.builder.*;
import net.xmx.xbullet.physics.constraint.builder.base.ConstraintBuilder;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.xbullet.physics.constraint.serializer.registry.ConstraintSerializerRegistry;
import net.xmx.xbullet.physics.object.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ConstraintManager {

    private final Map<UUID, IConstraint> managedConstraints = new ConcurrentHashMap<>();
    @Nullable private PhysicsWorld physicsWorld;
    private final ObjectManager objectManager;
    private final ConstraintDataSystem dataSystem;
    private final ConstraintLifecycleManager lifecycleManager;

    private final ThreadLocal<Map<Class<?>, Queue<ConstraintBuilder<?, ?>>>> builderPool = ThreadLocal.withInitial(HashMap::new);
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    public ConstraintManager(ObjectManager objectManager) {
        this.objectManager = objectManager;
        this.dataSystem = new ConstraintDataSystem(this);
        this.lifecycleManager = new ConstraintLifecycleManager(this);
    }

    public void initialize(PhysicsWorld world) {
        if (isInitialized.getAndSet(true)) return;
        this.physicsWorld = world;
        this.dataSystem.initialize(world.getLevel());
        ConstraintSerializerRegistry.registerDefaults();
    }

    public void shutdown() {
        if (!isInitialized.getAndSet(false)) return;
        if (dataSystem != null) dataSystem.shutdown();

        managedConstraints.values().forEach(IConstraint::release);
        managedConstraints.clear();
        this.physicsWorld = null;
    }

    public ConeConstraintBuilder createCone() { return getBuilder(ConeConstraintBuilder.class, ConeConstraintBuilder::new); }
    public DistanceConstraintBuilder createDistance() { return getBuilder(DistanceConstraintBuilder.class, DistanceConstraintBuilder::new); }
    public FixedConstraintBuilder createFixed() { return getBuilder(FixedConstraintBuilder.class, FixedConstraintBuilder::new); }
    public GearConstraintBuilder createGear() { return getBuilder(GearConstraintBuilder.class, GearConstraintBuilder::new); }
    public HingeConstraintBuilder createHinge() { return getBuilder(HingeConstraintBuilder.class, HingeConstraintBuilder::new); }
    public PathConstraintBuilder createPath() { return getBuilder(PathConstraintBuilder.class, PathConstraintBuilder::new); }
    public PointConstraintBuilder createPoint() { return getBuilder(PointConstraintBuilder.class, PointConstraintBuilder::new); }
    public PulleyConstraintBuilder createPulley() { return getBuilder(PulleyConstraintBuilder.class, PulleyConstraintBuilder::new); }
    public RackAndPinionConstraintBuilder createRackAndPinion() { return getBuilder(RackAndPinionConstraintBuilder.class, RackAndPinionConstraintBuilder::new); }
    public SixDofConstraintBuilder createSixDof() { return getBuilder(SixDofConstraintBuilder.class, SixDofConstraintBuilder::new); }
    public SliderConstraintBuilder createSlider() { return getBuilder(SliderConstraintBuilder.class, SliderConstraintBuilder::new); }
    public SwingTwistConstraintBuilder createSwingTwist() { return getBuilder(SwingTwistConstraintBuilder.class, SwingTwistConstraintBuilder::new); }

    @SuppressWarnings("unchecked")
    private <B extends ConstraintBuilder<B, C>, C extends TwoBodyConstraint> B getBuilder(Class<B> builderClass, Supplier<B> factory) {
        Queue<ConstraintBuilder<?, ?>> queue = builderPool.get().computeIfAbsent(builderClass, k -> new ArrayDeque<>());
        B builder = (B) queue.poll();
        if (builder == null) {
            builder = factory.get();
        }
        builder.setManager(this);
        return builder;
    }

    public void releaseBuilder(ConstraintBuilder<?, ?> builder) {
        builder.reset();
        builderPool.get().computeIfAbsent(builder.getClass(), k -> new ArrayDeque<>()).offer(builder);
    }

    public <B extends ConstraintBuilder<B, C>, C extends TwoBodyConstraint> void queueCreation(B builder) {
        if (physicsWorld == null) {
            XBullet.LOGGER.error("Cannot queue constraint creation, PhysicsWorld is not initialized.");
            releaseBuilder(builder);
            return;
        }

        physicsWorld.execute(() -> {
            try {
                createAndFinalizeConstraintFromBuilder(UUID.randomUUID(), builder);
            } finally {
                releaseBuilder(builder);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <B extends ConstraintBuilder<B, C>, C extends TwoBodyConstraint> void createAndFinalizeConstraintFromBuilder(UUID id, B builder) {
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }

        UUID b1Id = builder.getBody1Id();
        UUID b2Id = builder.getBody2Id();
        UUID d1Id = builder.getDependencyConstraintId1();
        UUID d2Id = builder.getDependencyConstraintId2();

        IPhysicsObject b1 = (b1Id != null) ? objectManager.getObject(b1Id).orElse(null) : null;
        IPhysicsObject b2 = (b2Id != null) ? objectManager.getObject(b2Id).orElse(null) : null;
        IConstraint d1 = (d1Id != null) ? getConstraint(d1Id) : null;
        IConstraint d2 = (d2Id != null) ? getConstraint(d2Id) : null;

        String typeId = builder.getTypeId();

        if (("xbullet:rack_and_pinion".equals(typeId) || "xbullet:gear".equals(typeId))) {
            if (d1 == null || d2 == null || d1.getJoltConstraint() == null || d2.getJoltConstraint() == null) {
                return;
            }
        } else {
            if ((b1Id != null && (b1 == null || b1.getBodyId() == 0)) || (b2Id != null && (b2 == null || b2.getBodyId() == 0))) {
                return;
            }
        }

        int bodyId1, bodyId2;
        UUID finalB1Id, finalB2Id;

        if ("xbullet:rack_and_pinion".equals(typeId) || "xbullet:gear".equals(typeId)) {
            bodyId1 = d1.getJoltConstraint().getBody1().getId();
            bodyId2 = d2.getJoltConstraint().getBody1().getId();
            finalB1Id = d1.getBody1Id();
            finalB2Id = d2.getBody1Id();
        } else {
            bodyId1 = (b1 != null) ? b1.getBodyId() : Body.sFixedToWorld().getId();
            bodyId2 = (b2 != null) ? b2.getBodyId() : Body.sFixedToWorld().getId();
            finalB1Id = b1Id;
            finalB2Id = b2Id;
        }

        if (bodyId1 == bodyId2) {
            return;
        }

        byte[] serializedData = serializeBuilder(builder);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(serializedData));

        try {
            ConstraintSerializer<B, C, ?> serializer = (ConstraintSerializer<B, C, ?>) ConstraintSerializerRegistry.getSerializer(typeId)
                    .orElseThrow(() -> new IllegalStateException("No serializer found for type " + typeId));

            buf.readUtf();
            if ("xbullet:rack_and_pinion".equals(typeId) || "xbullet:gear".equals(typeId)) {
                buf.readUUID(); buf.readUUID();
            } else {
                serializer.deserializeBodies(buf);
            }

            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            try (TwoBodyConstraintSettings settings = serializer.createSettings(buf)) {
                C joltConstraint = (C) bodyInterface.createConstraint(settings, bodyId1, bodyId2);
                if (joltConstraint == null) {
                    return;
                }

                if (joltConstraint instanceof RackAndPinionConstraint rp) rp.setConstraints(d1.getJoltConstraint(), d2.getJoltConstraint());
                else if (joltConstraint instanceof GearConstraint gc) gc.setConstraints(d1.getJoltConstraint(), d2.getJoltConstraint());

                physicsWorld.getPhysicsSystem().addConstraint(joltConstraint);
                ManagedConstraint managed = new ManagedConstraint(id, finalB1Id, finalB2Id, d1Id, d2Id, joltConstraint.toRef(), typeId, serializedData);

                managedConstraints.put(id, managed);
                dataSystem.storeConstraint(managed);
            }
        } catch (Exception e) {
            XBullet.LOGGER.error("Failed to create and finalize constraint {} from builder", id, e);
        } finally {
            buf.release();
        }
    }

    public void createAndFinalizeConstraint(UUID id, byte[] data, @Nullable UUID b1Id, @Nullable UUID b2Id, @Nullable UUID d1Id, @Nullable UUID d2Id) {
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }

        IPhysicsObject b1 = (b1Id != null) ? objectManager.getObject(b1Id).orElse(null) : null;
        IPhysicsObject b2 = (b2Id != null) ? objectManager.getObject(b2Id).orElse(null) : null;

        IConstraint d1 = (d1Id != null) ? this.getConstraint(d1Id) : null;
        IConstraint d2 = (d2Id != null) ? this.getConstraint(d2Id) : null;

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            String typeId = buf.readUtf();
            buf.readerIndex(0);

            XBullet.LOGGER.info("[CONSTRAINT FINALIZE] Finalizing constraint '{}' of type '{}'.", id, typeId);

            if (("xbullet:rack_and_pinion".equals(typeId) || "xbullet:gear".equals(typeId))) {
                if (d1 == null || d2 == null || d1.getJoltConstraint() == null || d2.getJoltConstraint() == null) {
                    XBullet.LOGGER.error("  > ABORT: A required dependency constraint for {} is not loaded or not initialized.", id);
                    return;
                }
            } else {
                if ((b1Id != null && (b1 == null || b1.getBodyId() == 0)) || (b2Id != null && (b2 == null || b2.getBodyId() == 0))) {
                    XBullet.LOGGER.error("  > ABORT: A required body for constraint {} does not have a valid bodyId. Body1 present/valid: {}/{}, Body2 present/valid: {}/{}.",
                            id, b1 != null, b1 != null && b1.getBodyId() != 0, b2 != null, b2 != null && b2.getBodyId() != 0);
                    return;
                }
            }

            int bodyId1, bodyId2;
            UUID finalB1Id, finalB2Id;

            if ("xbullet:rack_and_pinion".equals(typeId) || "xbullet:gear".equals(typeId)) {
                bodyId1 = d1.getJoltConstraint().getBody1().getId();
                bodyId2 = d2.getJoltConstraint().getBody1().getId();
                finalB1Id = d1.getBody1Id();
                finalB2Id = d2.getBody1Id();
            } else {
                bodyId1 = (b1 != null) ? b1.getBodyId() : Body.sFixedToWorld().getId();
                bodyId2 = (b2 != null) ? b2.getBodyId() : Body.sFixedToWorld().getId();
                finalB1Id = b1Id;
                finalB2Id = b2Id;
            }

            XBullet.LOGGER.info("  > Using native Body ID 1: {} (from UUID: {})", bodyId1, b1Id);
            XBullet.LOGGER.info("  > Using native Body ID 2: {} (from UUID: {})", bodyId2, b2Id);

            if (bodyId1 == bodyId2) {
                if (bodyId1 != Body.sFixedToWorld().getId()) {
                    XBullet.LOGGER.error("  > FATAL ABORT: Attempted to create a constraint between a body and itself (dynamic body ID: {}).", bodyId1);
                } else {
                    XBullet.LOGGER.warn("  > ABORT: Attempted to create a constraint between world and world. This is likely due to a race condition where bodies unloaded.");
                }
                return;
            }

            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            ConstraintSerializer<?, ?, ?> serializer = ConstraintSerializerRegistry.getSerializer(typeId)
                    .orElseThrow(() -> new IllegalStateException("No serializer found for type " + typeId));

            buf.readUtf();
            if ("xbullet:rack_and_pinion".equals(typeId) || "xbullet:gear".equals(typeId)) {
                buf.readUUID(); buf.readUUID();
            } else {
                serializer.deserializeBodies(buf);
            }

            try (TwoBodyConstraintSettings settings = serializer.createSettings(buf)) {
                @SuppressWarnings("unchecked")
                TwoBodyConstraint joltConstraint = (TwoBodyConstraint) bodyInterface.createConstraint(settings, bodyId1, bodyId2);
                if (joltConstraint == null) {
                    XBullet.LOGGER.error("  > FAILED: Jolt's bodyInterface.createConstraint returned null for {}.", id);
                    return;
                }

                if (joltConstraint instanceof RackAndPinionConstraint rp) rp.setConstraints(d1.getJoltConstraint(), d2.getJoltConstraint());
                else if (joltConstraint instanceof GearConstraint gc) gc.setConstraints(d1.getJoltConstraint(), d2.getJoltConstraint());

                physicsWorld.getPhysicsSystem().addConstraint(joltConstraint);
                ManagedConstraint managed = new ManagedConstraint(id, finalB1Id, finalB2Id, d1Id, d2Id, joltConstraint.toRef(), typeId, data);

                managedConstraints.put(id, managed);
                dataSystem.storeConstraint(managed);

                XBullet.LOGGER.info("[CONSTRAINT SUCCESS] Successfully created and added native Jolt constraint '{}'.", id);
            }
        } catch (Exception e) {
            XBullet.LOGGER.error("[CONSTRAINT FAILED] An unexpected exception occurred while creating and finalizing constraint {}", id, e);
        } finally {
            buf.release();
        }
    }

    private <B extends ConstraintBuilder<B, C>, C extends TwoBodyConstraint> byte[] serializeBuilder(B builder) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buffer);
            friendlyBuf.writeUtf(builder.getTypeId());
            @SuppressWarnings("unchecked")
            ConstraintSerializer<B, C, ?> serializer = (ConstraintSerializer<B, C, ?>) ConstraintSerializerRegistry.getSerializer(builder.getTypeId()).orElseThrow();
            serializer.serialize(builder, friendlyBuf);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } finally {
            ReferenceCountUtil.release(buffer);
        }
    }

    public void removeConstraint(UUID jointId, boolean permanent) {
        IConstraint constraint = managedConstraints.remove(jointId);
        if (constraint != null) {
            if (physicsWorld != null) {
                physicsWorld.execute(() -> {
                    TwoBodyConstraint joltConstraint = constraint.getJoltConstraint();
                    if (joltConstraint != null && joltConstraint.hasAssignedNativeObject()) {
                        physicsWorld.getPhysicsSystem().removeConstraint(joltConstraint);
                    }
                    constraint.release();
                });
            }
            if (permanent) {
                dataSystem.removePermanent(jointId);
            } else {
                dataSystem.storeConstraint(constraint);
            }
        }
    }

    public void removeConstraintsForObject(UUID objectId, boolean permanent) {
        if (!isInitialized.get()) return;
        List<UUID> idsToRemove = new ArrayList<>();
        for (IConstraint c : managedConstraints.values()) {
            if (objectId.equals(c.getBody1Id()) || objectId.equals(c.getBody2Id())) {
                idsToRemove.add(c.getId());
            }
        }
        idsToRemove.forEach(id -> removeConstraint(id, permanent));
    }

    public void unloadConstraint(UUID id) {
        this.removeConstraint(id, false);
    }

    @Nullable
    public IConstraint getConstraint(@Nullable UUID id) {
        if (id == null) {
            return null;
        }
        return managedConstraints.get(id);
    }

    public boolean isJointLoaded(UUID id) {
        return managedConstraints.containsKey(id);
    }

    public boolean isConstraintInChunk(UUID id, ChunkPos pos) {
        if (id == null) return false;
        return Optional.ofNullable(getConstraint(id)).map(c -> {
            boolean b1In = objectManager.isObjectInChunk(c.getBody1Id(), pos);
            boolean b2In = objectManager.isObjectInChunk(c.getBody2Id(), pos);
            return b1In || b2In;
        }).orElse(false);
    }

    public ConstraintDataSystem getDataSystem() {
        return dataSystem;
    }

    public ObjectManager getObjectManager() {
        return objectManager;
    }

    public ConstraintLifecycleManager getLifecycleManager() {
        return lifecycleManager;
    }

    public Collection<IConstraint> getManagedConstraints() {
        return Collections.unmodifiableCollection(managedConstraints.values());
    }

    public boolean isInitialized() {
        return isInitialized.get();
    }
}