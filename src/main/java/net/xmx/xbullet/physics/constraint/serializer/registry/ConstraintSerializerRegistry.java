package net.xmx.xbullet.physics.constraint.serializer.registry;

import net.xmx.xbullet.physics.constraint.serializer.*;
import net.xmx.xbullet.physics.constraint.serializer.base.ConstraintSerializer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ConstraintSerializerRegistry {

    private static final Map<String, ConstraintSerializer<?, ?, ?>> SERIALIZERS = new ConcurrentHashMap<>();
    private static boolean defaultsRegistered = false;

    public static void registerDefaults() {
        if (defaultsRegistered) return;
        register(new ConeConstraintSerializer());
        register(new DistanceConstraintSerializer());
        register(new FixedConstraintSerializer());
        register(new GearConstraintSerializer());
        register(new HingeConstraintSerializer());
        register(new PathConstraintSerializer());
        register(new PointConstraintSerializer());
        register(new PulleyConstraintSerializer());
        register(new RackAndPinionConstraintSerializer());
        register(new SixDofConstraintSerializer());
        register(new SliderConstraintSerializer());
        register(new SwingTwistConstraintSerializer());
        defaultsRegistered = true;
    }

    public static void register(ConstraintSerializer<?, ?, ?> serializer) {
        if (SERIALIZERS.containsKey(serializer.getTypeId())) {
        }
        SERIALIZERS.put(serializer.getTypeId(), serializer);
    }

    public static Optional<ConstraintSerializer<?, ?, ?>> getSerializer(String typeId) {
        return Optional.ofNullable(SERIALIZERS.get(typeId));
    }
}