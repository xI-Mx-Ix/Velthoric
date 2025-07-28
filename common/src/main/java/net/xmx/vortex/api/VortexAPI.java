package net.xmx.vortex.api;

import net.xmx.vortex.init.VxMainClass;

public class VortexAPI {

    private static VortexAPI instance;

    private final PhysicsObjectModule physicsObjectModule;

    private VortexAPI() {
        VxMainClass.LOGGER.debug("VortexAPI singleton constructor called.");
        this.physicsObjectModule = new PhysicsObjectModule();
    }

    public static VortexAPI getInstance() {
        if (instance == null) {
            synchronized (VortexAPI.class) {
                if (instance == null) {
                    instance = new VortexAPI();
                    VxMainClass.LOGGER.debug("VortexAPI singleton created.");
                }
            }
        }
        return instance;
    }

    /**
     * Access the main module for physics object interactions.
     * @return The singleton instance of the PhysicsObjectModule.
     */
    public PhysicsObjectModule objects() {
        return this.physicsObjectModule;
    }
}