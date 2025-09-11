/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.physics.constraint.serializer.type;

import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.PathConstraintPath;
import com.github.stephengold.joltjni.PathConstraintSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.readonly.ConstPathConstraintPath;
import com.github.stephengold.joltjni.enumerate.EPathRotationConstraintType;
import io.netty.buffer.ByteBuf;
import net.xmx.velthoric.physics.constraint.serializer.ConstraintSerializerUtils;
import net.xmx.velthoric.physics.constraint.serializer.IVxConstraintSerializer;
import net.xmx.velthoric.physics.constraint.serializer.VxConstraintSerializer;

public class PathConstraintSerializer extends VxConstraintSerializer implements IVxConstraintSerializer<PathConstraintSettings> {

    @Override
    public void save(PathConstraintSettings settings, ByteBuf buf) {
        saveBase(settings, buf);

        ConstPathConstraintPath path = settings.getPath();
        ConstraintSerializerUtils.savePath(path, buf);

        ConstraintSerializerUtils.saveVec3(settings.getPathPosition(), buf);
        buf.writeFloat(settings.getPathRotation().getX());
        buf.writeFloat(settings.getPathRotation().getY());
        buf.writeFloat(settings.getPathRotation().getZ());
        buf.writeFloat(settings.getPathRotation().getW());
        buf.writeFloat(settings.getPathFraction());
        buf.writeInt(settings.getRotationConstraintType().ordinal());
        buf.writeFloat(settings.getMaxFrictionForce());
        try (MotorSettings ms = settings.getPositionMotorSettings()) {
            ConstraintSerializerUtils.saveMotor(ms, buf);
        }
    }

    @Override
    public PathConstraintSettings load(ByteBuf buf) {
        PathConstraintSettings settings = new PathConstraintSettings();
        loadBase(settings, buf);

        try (PathConstraintPath loadedPath = ConstraintSerializerUtils.loadPath(buf)) {
            settings.setPath(loadedPath);
        }

        settings.setPathPosition(ConstraintSerializerUtils.loadVec3(buf));
        float qx = buf.readFloat();
        float qy = buf.readFloat();
        float qz = buf.readFloat();
        float qw = buf.readFloat();
        settings.setPathRotation(new Quat(qx, qy, qz, qw));
        settings.setPathFraction(buf.readFloat());
        settings.setRotationConstraintType(EPathRotationConstraintType.values()[buf.readInt()]);
        settings.setMaxFrictionForce(buf.readFloat());
        try (MotorSettings ms = settings.getPositionMotorSettings()) {
            ConstraintSerializerUtils.loadMotor(ms, buf);
        }
        return settings;
    }
}