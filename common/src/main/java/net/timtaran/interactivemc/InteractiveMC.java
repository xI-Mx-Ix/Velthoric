package net.timtaran.interactivemc;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class InteractiveMC {
    public static final String MOD_ID = "interactivemc";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Called during the common initialization phase (Server and Client).
     * Initializes registries, packets, and loads native libraries.
     */
    public static void onInit() {
        LOGGER.info("Initializing InteractiveMC");
    }
}
