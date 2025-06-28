package net.xmx.xbullet.init;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;

import java.util.Collections;
import java.util.List;

public class ModConfig {
    public static ForgeConfigSpec COMMON_SPEC;

    public static ConfigValue<List<? extends String>> DISABLED_PHYSICS_DIMENSIONS;
    public static ForgeConfigSpec.IntValue NUM_ITERATIONS;
    public static ForgeConfigSpec.DoubleValue ERP;

    public static void init() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("physics");

        DISABLED_PHYSICS_DIMENSIONS = builder
                .comment("List of dimensions where physics is disabled",
                        "Example: [\"minecraft:nether\", \"mymod:custom_dimension\"]")
                .defineList("disabledDimensions", Collections.emptyList(),
                        entry -> entry instanceof String);

        NUM_ITERATIONS = builder
                .comment("Number of iterations for the physics solver",
                        "Higher values improve accuracy but reduce performance")
                .defineInRange("numIterations", 11, 1, 100);

        ERP = builder
                .comment("Error Reduction Parameter for the physics simulation",
                        "Controls how quickly errors are corrected (0.1–0.9 recommended)")
                .defineInRange("erp", 0.235, 0.01, 1.0);

        builder.pop();

        COMMON_SPEC = builder.build();
    }
}
