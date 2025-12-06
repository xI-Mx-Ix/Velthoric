/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer;

import java.lang.reflect.Method;

/**
 * Detects if a shaderpack (Iris) is currently active.
 * Uses reflection to avoid hard dependencies on the mod.
 *
 * @author xI-Mx-Ix
 */
public final class VxShaderDetector {

    /**
     * Whether Iris is present on the classpath
     */
    private static final boolean isIrisPresent = classExists("net.irisshaders.iris.api.v0.IrisApi");

    // --- Iris Reflection ---
    private static Object irisApiInstance;
    private static Method irisIsShaderPackInUse;

    static {
        // Setup Iris reflection
        if (isIrisPresent) {
            try {
                Class<?> irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Method getInstanceMethod = irisApi.getDeclaredMethod("getInstance");
                irisApiInstance = getInstanceMethod.invoke(null);
                irisIsShaderPackInUse = irisApi.getDeclaredMethod("isShaderPackInUse");
            } catch (Exception e) {
                VxRConstants.LOGGER.error("Velthoric failed to reflectively setup Iris API. Shader detection may fail.", e);
            }
        }
    }

    /**
     * Checks if a class exists on the classpath.
     * @param name Fully qualified class name
     * @return True if class exists, false otherwise
     */
    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if a shaderpack is currently active.
     * @return True if Iris is rendering with a shaderpack
     */
    public static boolean isShaderpackActive() {
        try {
            if (isIrisPresent && irisApiInstance != null && irisIsShaderPackInUse != null) {
                return (boolean) irisIsShaderPackInUse.invoke(irisApiInstance);
            }
        } catch (Exception e) {
            VxRConstants.LOGGER.error("Error checking Iris shaderpack state.", e);
        }

        return false;
    }
}