package net.xmx.vortex.physics.constraint.serializer;

import com.github.stephengold.joltjni.TwoBodyConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSubType;
import net.xmx.vortex.physics.constraint.serializer.type.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class ConstraintSerializerRegistry {

    private static final Map<EConstraintSubType, IVxConstraintSerializer<?>> serializers = new EnumMap<>(EConstraintSubType.class);

    public static void initialize() {
        register(EConstraintSubType.Hinge, new HingeConstraintSerializer());
        register(EConstraintSubType.SixDof, new SixDofConstraintSerializer());
        register(EConstraintSubType.Cone, new ConeConstraintSerializer());
        register(EConstraintSubType.Distance, new DistanceConstraintSerializer());
        register(EConstraintSubType.Fixed, new FixedConstraintSerializer());
        register(EConstraintSubType.Gear, new GearConstraintSerializer());
        register(EConstraintSubType.Path, new PathConstraintSerializer());
        register(EConstraintSubType.Point, new PointConstraintSerializer());
        register(EConstraintSubType.Pulley, new PulleyConstraintSerializer());
        register(EConstraintSubType.RackAndPinion, new RackAndPinionConstraintSerializer());
        register(EConstraintSubType.Slider, new SliderConstraintSerializer());
        register(EConstraintSubType.SwingTwist, new SwingTwistConstraintSerializer());
    }

    private static <T extends TwoBodyConstraintSettings> void register(EConstraintSubType type, IVxConstraintSerializer<T> serializer) {
        serializers.put(type, serializer);
    }

    public static Optional<IVxConstraintSerializer<?>> get(EConstraintSubType type) {
        return Optional.ofNullable(serializers.get(type));
    }
}