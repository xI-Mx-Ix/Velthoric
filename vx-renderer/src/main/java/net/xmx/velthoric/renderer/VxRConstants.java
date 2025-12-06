/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.renderer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides a duplicated set of constants for the Velthoric project.
 * <p>
 * These values already exist in {@code VxMainClass}, but this module
 * cannot access that class, so the constants are re-declared here to
 * remain independently usable.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class VxRConstants {
    public static final String MODID = "velthoric";
    public static final Logger LOGGER = LogManager.getLogger("Velthoric Renderer");
}