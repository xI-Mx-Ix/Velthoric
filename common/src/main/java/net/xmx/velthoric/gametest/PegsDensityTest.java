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
 * Stress-test suite for high-density collision resolution within a constrained environment.
 * <p>
 * This test initializes a dense grid of small rigid bodies at a designated altitude 
 * within the pegs structure. It verifies the native Jolt Physics solver's ability 
 * to handle large-scale simultaneous activations and complex collision filtering 
 * between dynamic bodies and static world geometry.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class PegsDensityTest {

    /**
     * Executes a high-density grid spawn and verifies simulation persistence.
     * <p>
     * Logic flow:
     * 1. Calculates a 19x19 grid (361 bodies) centered within the structure bounds.
     * 2. Spawns 0.5m rigid boxes with a 0.5m gap (1.0m total step) at Y=11.0.
     * 3. Activates all bodies immediately upon creation.
     * 4. Schedules a validation task after 60 ticks (3 seconds) to ensure 
     *    the bodies remain correctly registered in the server-side manager.
     * </p>
     *
     * @param helper The GameTest helper instance provided by the framework.
     */
    @SuppressWarnings("unused")
    public void testPegsDensityPersistence(GameTestHelper helper) {
        VxServerBodyManager manager = VelthoricGameTestUtils.getManager(helper);
        UUID firstBodyId = null;

        float bodySize = 0.5f;
        float halfExtent = bodySize / 2.0f;
        float gap = 0.5f;
        float step = bodySize + gap; // Results in a 1.0 block interval

        // Iterating from 0.5 to 18.5 to center the 0.5m bodies within the 1.0m grid slots
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
                                box.setHalfExtents(new com.github.stephengold.joltjni.Vec3(halfExtent, halfExtent, halfExtent));
                                box.setColor(BoxColor.ORANGE);
                            }
                        }
                );

                if (firstBodyId == null && body != null) {
                    firstBodyId = body.getPhysicsId();
                }
            }
        }

        final UUID finalId = firstBodyId;
        
        // Wait 3 seconds to verify that physics bodies have not been discarded or crashed the simulation
        helper.runAtTickTime(60, () -> {
            boolean exists = finalId != null && manager.getVxBody(finalId) != null;
            helper.assertTrue(exists, "Physics bodies were removed from the manager prematurely after 3 seconds.");
            helper.succeed();
        });
    }
}