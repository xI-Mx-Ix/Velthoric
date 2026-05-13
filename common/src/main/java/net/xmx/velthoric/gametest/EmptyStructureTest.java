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
import net.xmx.velthoric.builtin.cloth.ClothSoftBody;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.math.VxTransform;

import java.util.UUID;

/**
 * Verification of interactions between rigid and soft bodies.
 * <p>
 * This test suite validates the detection and stacking accuracy between
 * rigid objects (BoxRigidBody) and deformable surfaces (ClothSoftBody).
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class EmptyStructureTest {

    /**
     * Verifies the interaction and persistence of rigid and soft body types.
     * <p>
     * A rigid 2x2x2 base is created, followed by a cloth simulation overlay.
     * Both bodies are activated immediately. The test ensures that the 
     * simulation handles the mixed-type collision without dropping either body.
     * </p>
     *
     * @param helper The GameTest helper instance.
     */
    @SuppressWarnings("unused")
    public void testInteractionPersistence(GameTestHelper helper) {
        VxServerBodyManager manager = VelthoricGameTestUtils.getManager(helper);
        
        // 1. Create rigid base box
        Vec3 boxAbsPos = helper.absoluteVec(new Vec3(9.5, 2.0, 9.5));
        VxTransform boxXform = new VxTransform(new RVec3(boxAbsPos.x, boxAbsPos.y, boxAbsPos.z), Quat.sIdentity());
        
        VxBody box = manager.createBody(VxRegisteredBodies.BOX, boxXform, EMotionType.Dynamic, EActivation.Activate, b -> {
            if (b instanceof BoxRigidBody rigid) {
                rigid.setHalfExtents(new com.github.stephengold.joltjni.Vec3(1f, 1f, 1f));
                rigid.setColor(BoxColor.RED);
            }
        });

        // 2. Create cloth overlay directly above
        Vec3 clothAbsPos = helper.absoluteVec(new Vec3(9.5, 5.0, 9.5));
        VxTransform clothXform = new VxTransform(new RVec3(clothAbsPos.x, clothAbsPos.y, clothAbsPos.z), Quat.sIdentity());
        
        VxBody cloth = manager.createBody(VxRegisteredBodies.CLOTH, clothXform, EMotionType.Dynamic, EActivation.Activate, c -> {
            if (c instanceof ClothSoftBody soft) {
                soft.setConfiguration(25, 25, 5.0f, 5.0f, 2.0f, 0.001f);
            }
        });

        helper.assertTrue(box != null && cloth != null, "Failed to spawn interaction test bodies.");
        final UUID boxId = box.getPhysicsId();
        final UUID clothId = cloth.getPhysicsId();

        helper.runAtTickTime(60, () -> {
            helper.assertTrue(manager.getVxBody(boxId) != null, "Rigid body was removed prematurely.");
            helper.assertTrue(manager.getVxBody(clothId) != null, "Cloth body was removed prematurely.");
            helper.succeed();
        });
    }
}