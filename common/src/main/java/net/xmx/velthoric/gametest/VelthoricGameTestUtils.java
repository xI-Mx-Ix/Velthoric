/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.gametest;

import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Central utility and infrastructure provider for Velthoric physics GameTests.
 * <p>
 * This class provides shared constants and safe access methods to the server-side
 * physics world and body managers. It also serves as the primary test generator.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public final class VelthoricGameTestUtils {

    /**
     * The resource namespace used for physics test structures.
     */
    public static final String NAMESPACE = "minecraft:";

    /**
     * The batch name for all Velthoric physics tests.
     */
    private static final String BATCH = "velthoric";

    public VelthoricGameTestUtils() {}

    /**
     * Programmatically generates all physics tests for the Velthoric mod.
     * <p>
     * This approach allows us to map test logic to specific structure files 
     * without adhering to strict file naming conventions (Class.Method).
     * </p>
     *
     * @return A collection of generated TestFunctions.
     */
    @SuppressWarnings("unused")
    @GameTestGenerator
    public static Collection<TestFunction> generateTests() {
        List<TestFunction> tests = new ArrayList<>();

        tests.add(create("water_buoyancy", "physics_box_water", new WaterBuoyancyTest()::testWaterEntryPersistence));
        tests.add(create("funnel_flow", "physics_box_funnel", new FunnelFlowTest()::testFunnelFlowPersistence));
        tests.add(create("pegs_density", "physics_box_pegs", new PegsDensityTest()::testPegsDensityPersistence));
        tests.add(create("soft_body_interaction", "physics_box_empty", new EmptyStructureTest()::testInteractionPersistence));

        return tests;
    }

    /**
     * Helper to create a TestFunction with standard Velthoric parameters.
     */
    private static TestFunction create(String name, String template, Consumer<GameTestHelper> function) {
        return new TestFunction(
                BATCH,
                name,
                NAMESPACE + template,
                Rotation.NONE,
                200,
                0L,
                true,
                function
        );
    }

    /**
     * Safely retrieves the server-side body manager for the current test dimension.
     *
     * @param helper The GameTest helper provided by the Minecraft framework.
     * @return The active server-side body manager.
     */
    public static VxServerBodyManager getManager(GameTestHelper helper) {
        VxPhysicsWorld world = VxPhysicsWorld.get(helper.getLevel().dimension());
        if (world == null) {
            throw new IllegalStateException("Physics system not initialized for GameTest dimension: " 
                    + helper.getLevel().dimension().location());
        }
        return world.getBodyManager();
    }
}