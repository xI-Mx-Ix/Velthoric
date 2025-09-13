/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as unsafe. Use with caution: it can break things if used incorrectly.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface VxUnsafe {
    String value() default "This method is unsafe. Use at your own risk.";
}

