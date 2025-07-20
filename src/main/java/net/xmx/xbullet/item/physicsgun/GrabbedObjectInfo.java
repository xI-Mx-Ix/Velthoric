package net.xmx.xbullet.item.physicsgun;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.Vec3;

import java.util.UUID;

public record GrabbedObjectInfo(
            UUID objectId,
            int bodyId,
            Vec3 grabPointLocal,
            float currentDistance,
            float originalAngularDamping,
            Quat initialBodyRotation,
            Quat initialPlayerRotation
    ) {}