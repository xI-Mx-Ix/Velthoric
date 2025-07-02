package net.xmx.xbullet.physics.constraint.serializer.registry;

import net.xmx.xbullet.physics.constraint.serializer.IConstraintSerializer;
import net.xmx.xbullet.physics.constraint.serializer.type.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ConstraintSerializerRegistry {

    private static final Map<String, IConstraintSerializer<?>> SERIALIZERS = new ConcurrentHashMap<>();

    public static void registerDefaults() {
        register("xbullet:fixed", new FixedConstraintSerializer());
        register("xbullet:point", new PointConstraintSerializer());
        register("xbullet:hinge", new HingeConstraintSerializer());
        register("xbullet:slider", new SliderConstraintSerializer());
        register("xbullet:distance", new DistanceConstraintSerializer());
        register("xbullet:cone", new ConeConstraintSerializer());
        register("xbullet:six_dof", new SixDofConstraintSerializer());
        register("xbullet:swing_twist", new SwingTwistConstraintSerializer());
        register("xbullet:gear", new GearConstraintSerializer());
        register("xbullet:rack_and_pinion", new RackAndPinionConstraintSerializer());
        register("xbullet:pulley", new PulleyConstraintSerializer());
        register("xbullet:path", new PathConstraintSerializer());
    }

    public static void register(String typeId, IConstraintSerializer<?> serializer) {
        SERIALIZERS.put(typeId, serializer);
    }

    public static Optional<IConstraintSerializer<?>> getSerializer(String typeId) {
        return Optional.ofNullable(SERIALIZERS.get(typeId));
    }
}