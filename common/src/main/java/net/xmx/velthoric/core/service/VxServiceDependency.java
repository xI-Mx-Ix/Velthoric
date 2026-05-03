/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation allow for Services to be dependent of other services
 * @author LOLAtom
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface VxServiceDependency {
    /**
     * value represent what Other service the current annotated service depends on
     * Example :
     * /@ServiceDependency(RagdollManager.class, ExplosiveManager.class)
     * /public class DestroyedBodyManager implements IVxPhysicsService
     */
    Class<? extends IVxPhysicsService>[] value();
}