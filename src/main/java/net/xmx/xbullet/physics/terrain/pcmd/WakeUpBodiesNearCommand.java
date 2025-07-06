package net.xmx.xbullet.physics.terrain.pcmd;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.core.BlockPos;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.pcmd.ActivateBodyCommand;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.world.pcmd.ICommand;

public record WakeUpBodiesNearCommand(BlockPos pos) implements ICommand {

    private static final double WAKE_UP_RADIUS = 10.0;
    private static final double WAKE_UP_RADIUS_SQ = WAKE_UP_RADIUS * WAKE_UP_RADIUS;

    @Override
    public void execute(PhysicsWorld world) {
        if (world.getObjectManager() == null) {
            return;
        }

        final double blockCenterX = pos.getX() + 0.5;
        final double blockCenterY = pos.getY() + 0.5;
        final double blockCenterZ = pos.getZ() + 0.5;

        for (IPhysicsObject obj : world.getObjectManager().getManagedObjects().values()) {
            if (obj.getBodyId() == 0 || !obj.isPhysicsInitialized()) continue;

            RVec3 objPos = obj.getCurrentTransform().getTranslation();
            double dx = objPos.xx() - blockCenterX;
            double dy = objPos.yy() - blockCenterY;
            double dz = objPos.zz() - blockCenterZ;
            double distanceSq = dx * dx + dy * dy + dz * dz;

            if (distanceSq <= WAKE_UP_RADIUS_SQ) {

                world.queueCommand(new ActivateBodyCommand(obj.getBodyId()));
            }
        }
    }
}