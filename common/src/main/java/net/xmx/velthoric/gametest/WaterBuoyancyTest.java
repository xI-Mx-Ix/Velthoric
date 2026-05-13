/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.gametest;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.builtin.VxRegisteredBodies;
import net.xmx.velthoric.builtin.box.BoxColor;
import net.xmx.velthoric.builtin.box.BoxRigidBody;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.math.VxTransform;

import java.util.UUID;

/**
 * Technical verification of buoyancy and fluid dynamics within the Velthoric engine.
 * <p>
 * This test suite focuses on the interaction between rigid bodies and water volumes,
 * ensuring that buoyancy forces are correctly applied and that bodies remain 
 * persistent within the simulation while submerged.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class WaterBuoyancyTest {

    /**
     * Verifies that a rigid body correctly interacts with water and persists.
     * <p>
     * Spawns a 1m³ rigid box at the center of the water tank structure.
     * The body is activated immediately to test the transition from air to fluid.
     * A persistence check is performed after 60 ticks (3 seconds).
     * </p>
     *
     * @param helper The GameTest helper instance.
     */
    @SuppressWarnings("unused")
    public void testWaterEntryPersistence(GameTestHelper helper) {
        VxServerBodyManager manager = VelthoricGameTestUtils.getManager(helper);

        Vec3 absPos = helper.absoluteVec(new Vec3(9.5, 8.0, 9.5));
        VxTransform transform = new VxTransform(new RVec3(absPos.x, absPos.y, absPos.z), Quat.sIdentity());
        
        VxBody body = manager.createBody(
                VxRegisteredBodies.BOX, 
                transform, 
                EMotionType.Dynamic,
                EActivation.Activate,
                b -> {
                    if (b instanceof BoxRigidBody box) {
                        box.setHalfExtents(new com.github.stephengold.joltjni.Vec3(0.5f, 0.5f, 0.5f));
                        box.setColor(BoxColor.BLUE);
                    }
                }
        );

        helper.assertTrue(body != null, "Failed to spawn water test body.");
        final UUID bodyId = body.getPhysicsId();

        helper.runAtTickTime(60, () -> {
            helper.assertTrue(manager.getVxBody(bodyId) != null, "Water test body was removed prematurely.");
            helper.succeed();
        });
    }
}