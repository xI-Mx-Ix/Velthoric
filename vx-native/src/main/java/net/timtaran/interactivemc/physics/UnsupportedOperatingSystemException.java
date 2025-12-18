/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics;

/**
 * An exception thrown when an operation is attempted on an unsupported operating system or architecture.
 *
 * @author xI-Mx-Ix
 */
public class UnsupportedOperatingSystemException extends RuntimeException {

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message.
     */
    public UnsupportedOperatingSystemException(String message) {
        super(message);
    }
}