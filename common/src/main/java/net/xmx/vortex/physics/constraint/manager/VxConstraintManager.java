package net.xmx.vortex.physics.constraint.manager;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.GearConstraint;
import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.RackAndPinionConstraint;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.constraint.IConstraint;
import net.xmx.vortex.physics.constraint.ManagedConstraint;
import net.xmx.vortex.physics.constraint.builder.ConeConstraintBuilder;
import net.xmx.vortex.physics.constraint.builder.DistanceConstraintBuilder;
import net.xmx.vortex.physics.constraint.builder.FixedConstraintBuilder;
import net.xmx.vortex.physics.constraint.builder.GearConstraintBuilder;
import net.xmx.vortex.physics.constraint.builder.HingeConstraintBuilder;
import net.xmx.vortex.physics.constraint.builder.PathConstraintBuilder;
import net.xmx.vortex.physics.constraint.builder.PointConstraintBuilder;
import net.xmx.vortex.physics.constraint.builder.PulleyConstraintBuilder;
import net.xmx.vortex.physics.constraint.builder.RackAndPinionConstraintBuilder;
import net.xmx.vortex.physics.constraint.builder.SixDofConstraintBuilder;
import net.xmx.vortex.physics.constraint.builder.SliderConstraintBuilder;
import net.xmx.vortex.physics.constraint.builder.SwingTwistConstraintBuilder;
import net.xmx.vortex.physics.constraint.builder.base.ConstraintBuilder;
import net.xmx.vortex.physics.constraint.serializer.base.ConstraintSerializer;
import net.xmx.vortex.physics.constraint.serializer.registry.ConstraintSerializerRegistry;
import net.xmx.vortex.physics.constraint.util.VxBufferUtil;
import net.xmx.vortex.physics.object.physicsobject.VxAbstractBody;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class VxConstraintManager {

    private final Map<UUID, IConstraint> managedConstraints = new ConcurrentHashMap<>();
    @Nullable
    private VxPhysicsWorld physicsWorld;
    private final VxObjectManager objectManager;
    private final ConstraintDataSystem dataSystem;
    private final ConstraintLifecycleManager lifecycleManager;

    private final ThreadLocal<Map<Class<?>, Queue<ConstraintBuilder<?, ?>>>> builderPool = ThreadLocal.withInitial(HashMap::new);
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    public VxConstraintManager(VxObjectManager objectManager) {
        this.objectManager = objectManager;
        this.dataSystem = new ConstraintDataSystem(this);
        this.lifecycleManager = new ConstraintLifecycleManager(this);
    }

    public void initialize(VxPhysicsWorld world) {
        if (isInitialized.getAndSet(true)) return;
        this.physicsWorld = world;
        this.dataSystem.initialize(world);
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
            releaseBuilder(builder);
            return;
        }

        byte[] fullData = serializeNewConstraint(builder);

        physicsWorld.execute(() -> {
            try {
                createAndFinalizeConstraint(UUID.randomUUID(), fullData);
            } finally {
                releaseBuilder(builder);
            }
        });
    }

    private <B extends ConstraintBuilder<B, C>, C extends TwoBodyConstraint> byte[] serializeNewConstraint(B builder) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buffer);
            @SuppressWarnings("unchecked")
            ConstraintSerializer<B, C, ?> serializer = (ConstraintSerializer<B, C, ?>) ConstraintSerializerRegistry.getSerializer(builder.getTypeId()).orElseThrow();

            friendlyBuf.writeUtf(builder.getTypeId());
            if ("vortex:rack_and_pinion".equals(builder.getTypeId()) || "vortex:gear".equals(builder.getTypeId())) {
                friendlyBuf.writeUUID(builder.getDependencyConstraintId1());
                friendlyBuf.writeUUID(builder.getDependencyConstraintId2());
            } else {
                serializer.serializeBodies(builder, friendlyBuf);
            }

            serializer.serializeSettings(builder, friendlyBuf);

            if (builder instanceof HingeConstraintBuilder hingeBuilder) {
                friendlyBuf.writeFloat(0f);
                friendlyBuf.writeFloat(0f);
                friendlyBuf.writeEnum(EMotorState.Off);
                try (MotorSettings motor = hingeBuilder.getSettings().getMotorSettings()) {
                    VxBufferUtil.putMotorSettings(friendlyBuf, motor);
                }
                try (SpringSettings spring = hingeBuilder.getSettings().getLimitsSpringSettings()) {
                    VxBufferUtil.putSpringSettings(friendlyBuf, spring);
                }
            }

            byte[] data = new byte[friendlyBuf.readableBytes()];
            friendlyBuf.readBytes(data);
            return data;
        } finally {
            ReferenceCountUtil.release(buffer);
        }
    }

    public void createAndFinalizeConstraintFromData(UUID id, byte[] fullData) {
        if (physicsWorld == null || !physicsWorld.isRunning() || fullData == null) {
            return;
        }
        createAndFinalizeConstraint(id, fullData);
    }

    private void createAndFinalizeConstraint(UUID id, byte[] fullConstraintData) {
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(fullConstraintData));

        try {
            String typeId = buf.readUtf();
            ConstraintSerializer<?, ?, ?> serializer = ConstraintSerializerRegistry.getSerializer(typeId)
                    .orElseThrow(() -> new IllegalStateException("No serializer found for type " + typeId));

            UUID b1Id = null, b2Id = null, d1Id = null, d2Id = null;
            if ("vortex:rack_and_pinion".equals(typeId) || "vortex:gear".equals(typeId)) {
                d1Id = buf.readUUID();
                d2Id = buf.readUUID();
            } else {
                UUID[] ids = serializer.deserializeBodies(buf);
                b1Id = ids[0];
                b2Id = ids[1];
            }

            VxAbstractBody b1 = (b1Id != null) ? objectManager.getObject(b1Id).orElse(null) : null;
            VxAbstractBody b2 = (b2Id != null) ? objectManager.getObject(b2Id).orElse(null) : null;
            IConstraint d1 = (d1Id != null) ? getConstraint(d1Id) : null;
            IConstraint d2 = (d2Id != null) ? getConstraint(d2Id) : null;

            if (("vortex:rack_and_pinion".equals(typeId) || "vortex:gear".equals(typeId))) {
                if (d1 == null || d2 == null || d1.getJoltConstraint() == null || d2.getJoltConstraint() == null) return;
            } else {
                if ((b1Id != null && (b1 == null || b1.getBodyId() == 0)) || (b2Id != null && (b2 == null || b2.getBodyId() == 0))) return;
            }

            int bodyId1 = (b1 != null) ? b1.getBodyId() : Body.sFixedToWorld().getId();
            int bodyId2 = (b2 != null) ? b2.getBodyId() : Body.sFixedToWorld().getId();
            if (bodyId1 == bodyId2 && b1Id != null && b2Id != null) return;

            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            TwoBodyConstraintSettings settings = serializer.createSettings(buf);

            try (settings) {
                TwoBodyConstraint joltConstraint = (TwoBodyConstraint) bodyInterface.createConstraint(settings, bodyId1, bodyId2);
                if (joltConstraint == null) return;

                if (joltConstraint instanceof RackAndPinionConstraint rp) rp.setConstraints(d1.getJoltConstraint(), d2.getJoltConstraint());
                else if (joltConstraint instanceof GearConstraint gc) gc.setConstraints(d1.getJoltConstraint(), d2.getJoltConstraint());

                physicsWorld.getPhysicsSystem().addConstraint(joltConstraint);

                if (buf.isReadable()) {
                    serializer.applyLiveState(joltConstraint, buf);
                }

                ManagedConstraint managed = new ManagedConstraint(id, b1Id, b2Id, d1Id, d2Id, joltConstraint.toRef(), typeId, fullConstraintData);

                managedConstraints.put(id, managed);
                dataSystem.storeConstraint(managed);
                dataSystem.onDependencyLoaded(id);
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("[CONSTRAINT FAILED] An unexpected exception occurred while creating native constraint {} from data", id, e);
        } finally {
            buf.release();
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
                dataSystem.onConstraintUnloaded(jointId);
            }
        }
    }

    public void removeConstraintsForObject(UUID objectId, boolean permanent) {
        if (!isInitialized() || objectId == null) return;

        List<UUID> idsToRemove = new ArrayList<>();
        for (IConstraint c : managedConstraints.values()) {
            if (objectId.equals(c.getBody1Id()) || objectId.equals(c.getBody2Id())) {
                idsToRemove.add(c.getId());
            }
        }
        idsToRemove.forEach(id -> removeConstraint(id, permanent));
    }

    public void unloadConstraint(UUID id, boolean permanent) {
        this.removeConstraint(id, permanent);
    }

    @Nullable
    public IConstraint getConstraint(@Nullable UUID id) {
        return id == null ? null : managedConstraints.get(id);
    }

    public boolean isJointLoaded(UUID id) {
        return managedConstraints.containsKey(id);
    }

    public ConstraintDataSystem getDataSystem() {
        return dataSystem;
    }

    public VxObjectManager getObjectManager() {
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