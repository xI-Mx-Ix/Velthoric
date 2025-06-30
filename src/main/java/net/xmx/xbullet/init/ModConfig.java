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
    public static ForgeConfigSpec.IntValue MAX_SUBSTEPS;

    public static ForgeConfigSpec.IntValue MAX_BODIES;
    public static ForgeConfigSpec.IntValue MAX_BODY_MUTEXES;
    public static ForgeConfigSpec.IntValue MAX_BODY_PAIRS;
    public static ForgeConfigSpec.IntValue MAX_CONTACT_CONSTRAINTS;

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

        MAX_SUBSTEPS = builder
                .comment("Maximum number of physics sub-steps per game tick.",
                        "Prevents the 'spiral of death' on severe lag. 5-10 is a reasonable range.")
                .defineInRange("maxSubsteps", 5, 1, 50);


        ERP = builder
                .comment("Error Reduction Parameter for the physics simulation",
                        "Controls how quickly errors are corrected (0.1–0.9 recommended)")
                .defineInRange("erp", 0.235, 0.01, 1.0);



        MAX_BODIES = builder
                .comment("Maximum number of physics bodies")
                .defineInRange("maxBodies", 65536, 1024, Integer.MAX_VALUE);

        MAX_BODY_MUTEXES = builder
                .comment("Number of body mutexes (0 = default)")
                .defineInRange("numBodyMutexes", 0, 0, 4096);

        MAX_BODY_PAIRS = builder
                .comment("Maximum number of physics body pairs")
                .defineInRange("maxBodyPairs", 65536, 1024, Integer.MAX_VALUE);

        MAX_CONTACT_CONSTRAINTS = builder
                .comment("Maximum number of contact constraints")
                .defineInRange("maxContactConstraints", 10240, 512, Integer.MAX_VALUE);


        builder.pop();

        COMMON_SPEC = builder.build();
    }
}
