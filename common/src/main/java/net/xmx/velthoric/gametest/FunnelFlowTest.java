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
 * Performance and collision verification for funnel-based flow dynamics.
 * <p>
 * This test utilizes a high-density grid of rigid bodies to stress-test the
 * engine's collision resolution and narrow-passage navigation within
 * a funnel structure.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class FunnelFlowTest {

    /**
     * Verifies funnel flow dynamics and body persistence.
     * <p>
     * Spawns a 19x19 grid (361 bodies) of 0.5m rigid boxes at Y=11.0. 
     * The spacing (1.0m step) and activation state are designed to maximize initial 
     * collision pressure as bodies are funneled through the structure.
     * </p>
     *
     * @param helper The GameTest helper instance.
     */
    @SuppressWarnings("unused")
    public void testFunnelFlowPersistence(GameTestHelper helper) {
        VxServerBodyManager manager = VelthoricGameTestUtils.getManager(helper);
        UUID firstBodyId = null;

        float step = 1.0f; // 0.5m body + 0.5m gap

        for (float x = 0.5f; x <= 18.5f; x += step) {
            for (float z = 0.5f; z <= 18.5f; z += step) {
                Vec3 absPos = helper.absoluteVec(new Vec3(x, 11.0, z));
                VxTransform transform = new VxTransform(new RVec3(absPos.x, absPos.y, absPos.z), Quat.sIdentity());

                VxBody body = manager.createBody(
                        VxRegisteredBodies.BOX, 
                        transform, 
                        EMotionType.Dynamic,
                        EActivation.Activate,
                        b -> {
                            if (b instanceof BoxRigidBody box) {
                                box.setHalfExtents(new com.github.stephengold.joltjni.Vec3(0.25f, 0.25f, 0.25f));
                                box.setColor(BoxColor.getRandom());
                            }
                        }
                );
                if (firstBodyId == null && body != null) firstBodyId = body.getPhysicsId();
            }
        }

        final UUID finalId = firstBodyId;
        // Wait 3 seconds (60 ticks) to verify that physics bodies have not been discarded
        helper.runAtTickTime(60, () -> {
            boolean exists = finalId != null && manager.getVxBody(finalId) != null;
            helper.assertTrue(exists, "Funnel grid bodies were removed prematurely.");
            helper.succeed();
        });
    }
}