/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.util.intersect;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.*;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.init.VxMainClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detects physics intersects for a given shape.
 *
 * @author timtaran
 */
public class VxPhysicsIntersector {
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    private VxPhysicsIntersector() {
    }

    /**
     * Performs a broad-phase intersect with default filters, colliding with all possible objects.
     *
     * @param physicsWorld The physics world to perform the intersect in.
     * @param point        The point to test.
     * @return Body IDs of the intersecting bodies.
     */
    public static int[] broadIntersectPoint(VxPhysicsWorld physicsWorld, Vec3Arg point) {
        try (ObjectLayerFilter olFilter = new ObjectLayerFilter();
             BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter()) {
            return broadIntersectPoint(physicsWorld, point, bplFilter, olFilter);
        }
    }

    /**
     * Performs a broad-phase intersect with a point using a specific object layer filter, with default broad-phase filter.
     *
     * @param physicsWorld The physics world to perform the intersect in.
     * @param point        The point to test.
     * @param olFilter     A custom filter for object layers.
     * @return Body IDs of the intersecting bodies.
     */
    public static int[] broadIntersectPoint(VxPhysicsWorld physicsWorld, Vec3Arg point, ObjectLayerFilter olFilter) {
        try (BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter()) {
            return broadIntersectPoint(physicsWorld, point, bplFilter, olFilter);
        }
    }

    /**
     * Performs a broad-phase intersect with a point with fully customized broad-phase and object-layer filters.
     *
     * @param physicsWorld The physics world to perform the intersect in.
     * @param point        The point to test.
     * @param bplFilter    A custom filter for broad-phase layers.
     * @param olFilter     A custom filter for object layers.
     * @return Body IDs of the intersecting bodies.
     */
    public static int[] broadIntersectPoint(VxPhysicsWorld physicsWorld, Vec3Arg point, BroadPhaseLayerFilter bplFilter, ObjectLayerFilter olFilter) {
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getPhysicsSystem() == null) {
            return EMPTY_INT_ARRAY;
        }

        PhysicsSystem physicsSystem = physicsWorld.getPhysicsSystem();
        ConstBroadPhaseQuery broadPhaseQuery = physicsSystem.getBroadPhaseQuery();

        try (AllHitCollideShapeBodyCollector collector = new AllHitCollideShapeBodyCollector()) {
            // Perform colliding with the provided arguments.
            broadPhaseQuery.collidePoint(point, collector, bplFilter, olFilter);
            return collector.getHits();
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during physics point broad-phase intersect", e);
        }
        return EMPTY_INT_ARRAY;

    }

    /**
     * Performs a narrow-phase intersect with default filters, colliding with all possible objects.
     *
     * @param physicsWorld The physics world to perform the intersect in.
     * @param point        The point to test.
     * @return Body IDs of the intersecting bodies.
     */
    public static int[] narrowIntersectPoint(VxPhysicsWorld physicsWorld, RVec3Arg point) {
        try (ObjectLayerFilter olFilter = new ObjectLayerFilter();
             BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
             BodyFilter bodyFilter = new BodyFilter();
             ShapeFilter shapeFilter = new ShapeFilter()) {
            return narrowIntersectPoint(physicsWorld, point, bplFilter, olFilter, bodyFilter, shapeFilter);
        }
    }

    /**
     * Performs a narrow-phase intersect with a point using a specific object layer filter, with default broad-phase, body, and shape filters.
     *
     * @param physicsWorld The physics world to perform the intersect in.
     * @param point        The point to test.
     * @param olFilter     A custom filter for object layers.
     * @return Body IDs of the intersecting bodies.
     */
    public static int[] narrowIntersectPoint(VxPhysicsWorld physicsWorld, RVec3Arg point, ObjectLayerFilter olFilter) {
        try (BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
             BodyFilter bodyFilter = new BodyFilter();
             ShapeFilter shapeFilter = new ShapeFilter()) {
            return narrowIntersectPoint(physicsWorld, point, bplFilter, olFilter, bodyFilter, shapeFilter);
        }
    }

    /**
     * Performs a narrow-phase intersect with a point using specific broad-phase and object-layer filters, with default body and shape filters.
     *
     * @param physicsWorld The physics world to perform the intersect in.
     * @param point        The point to test.
     * @param bplFilter    A custom filter for broad-phase layers.
     * @param olFilter     A custom filter for object layers.
     * @return Body IDs of the intersecting bodies.
     */
    public static int[] narrowIntersectPoint(VxPhysicsWorld physicsWorld, RVec3Arg point, BroadPhaseLayerFilter bplFilter,
                                             ObjectLayerFilter olFilter) {
        try (BodyFilter bodyFilter = new BodyFilter();
             ShapeFilter shapeFilter = new ShapeFilter()) {
            return narrowIntersectPoint(physicsWorld, point, bplFilter, olFilter, bodyFilter, shapeFilter);
        }
    }

    /**
     * Performs a narrow-phase intersect with a point with fully customized broad-phase, object-layer, body, and shape filters.
     *
     * @param physicsWorld The physics world to perform the intersect in.
     * @param point        The point to test.
     * @param bplFilter    A custom filter for broad-phase layers.
     * @param olFilter     A custom filter for object layers.
     * @param bodyFilter   A custom filter for individual physics bodies.
     * @param shapeFilter  A custom filter for shapes.
     * @return Body IDs of the intersecting bodies.
     */
    public static int[] narrowIntersectPoint(VxPhysicsWorld physicsWorld, RVec3Arg point, BroadPhaseLayerFilter bplFilter,
                                             ObjectLayerFilter olFilter, BodyFilter bodyFilter,
                                             ShapeFilter shapeFilter) {
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getPhysicsSystem() == null) {
            return EMPTY_INT_ARRAY;
        }

        PhysicsSystem physicsSystem = physicsWorld.getPhysicsSystem();
        ConstNarrowPhaseQuery narrowPhaseQuery = physicsSystem.getNarrowPhaseQuery();

        try (AllHitCollidePointCollector collector = new AllHitCollidePointCollector()) {

            // Perform colliding with the provided arguments.
            narrowPhaseQuery.collidePoint(point, collector, bplFilter, olFilter, bodyFilter, shapeFilter);

            List<CollidePointResult> hits = collector.getHits();

            if (!hits.isEmpty()) {
                int size = hits.size();

                int[] results = new int[size];
                for (int i = 0; i < size; i++) {
                    results[i] = hits.get(i).getBodyId();
                }

                return results;
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during physics point narrow-phase intersect", e);
        }
        return EMPTY_INT_ARRAY;
    }

    /**
     * Performs a broad-phase intersect with default filters, colliding with all possible objects.
     *
     * @param physicsWorld The physics world to perform the intersect in.
     * @param center       The center of the sphere.
     * @param radius       The radius of the sphere.
     * @return Body IDs of the intersecting bodies.
     */
    public static int[] broadIntersectSphere(VxPhysicsWorld physicsWorld, Vec3Arg center, float radius) {
        try (ObjectLayerFilter olFilter = new ObjectLayerFilter();
             BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter()) {
            return broadIntersectSphere(physicsWorld, center, radius, bplFilter, olFilter);
        }
    }

    /**
     * Performs a broad-phase intersect with a sphere using a specific object layer filter, with default broad-phase filter.
     *
     * @param physicsWorld The physics world to perform the intersect in.
     * @param center       The center of the sphere.
     * @param radius       The radius of the sphere.
     * @param olFilter     A custom filter for object layers.
     * @return Body IDs of the intersecting bodies.
     */
    public static int[] broadIntersectSphere(VxPhysicsWorld physicsWorld, Vec3Arg center, float radius, ObjectLayerFilter olFilter) {
        try (BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter()) {
            return broadIntersectSphere(physicsWorld, center, radius, bplFilter, olFilter);
        }
    }

    /**
     * Performs a broad-phase intersect with a sphere with fully customized broad-phase and object-layer filters.
     *
     * @param physicsWorld The physics world to perform the intersect in.
     * @param center       The center of the sphere.
     * @param radius       The radius of the sphere.
     * @param bplFilter    A custom filter for broad-phase layers.
     * @param olFilter     A custom filter for object layers.
     * @return Body IDs of the intersecting bodies.
     */
    public static int[] broadIntersectSphere(VxPhysicsWorld physicsWorld, Vec3Arg center, float radius,
                                             BroadPhaseLayerFilter bplFilter, ObjectLayerFilter olFilter) {
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getPhysicsSystem() == null) {
            return EMPTY_INT_ARRAY;
        }

        PhysicsSystem physicsSystem = physicsWorld.getPhysicsSystem();
        ConstBroadPhaseQuery broadPhaseQuery = physicsSystem.getBroadPhaseQuery();

        try (AllHitCollideShapeBodyCollector collector = new AllHitCollideShapeBodyCollector()) {
            // Perform colliding with the provided arguments.
            broadPhaseQuery.collideSphere(center, radius, collector, bplFilter, olFilter);
            return collector.getHits();
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during physics sphere intersect", e);
        }
        return EMPTY_INT_ARRAY;
    }

    /**
     * Performs a narrow-phase intersect with a shape using default filters, colliding with all possible objects.
     *
     * @param physicsWorld The physics world to perform the intersect in.
     * @param shape        The shape to test.
     * @param shapeScale   The scaling vector for the shape.
     * @param comTransform The coordinate transform to apply to the shape.
     * @param base         The base location for reporting hits ((0,0,0)→world coordinates).
     * @return List containing generic physics intersect results.
     */
    public static List<IntersectShapeResult> narrowIntersectShape(VxPhysicsWorld physicsWorld, ConstShape shape, Vec3Arg shapeScale,
                                                                  RMat44Arg comTransform, RVec3Arg base) {
        try (ObjectLayerFilter olFilter = new ObjectLayerFilter();
             BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
             BodyFilter bodyFilter = new BodyFilter();
             ShapeFilter shapeFilter = new ShapeFilter()) {
            return narrowIntersectShape(physicsWorld, shape, shapeScale, comTransform, base, bplFilter, olFilter, bodyFilter, shapeFilter);
        }
    }

    /**
     * Performs a narrow-phase intersect with a shape using a specific object layer filter, with default broad-phase, body, and shape filters.
     *
     * @param physicsWorld The physics world to perform the intersect in.
     * @param shape        The shape to test.
     * @param shapeScale   The scaling vector for the shape.
     * @param comTransform The coordinate transform to apply to the shape.
     * @param base         The base location for reporting hits ((0,0,0)→world coordinates).
     * @param olFilter     A custom filter for object layers.
     * @return List containing generic physics intersect results.
     */
    public static List<IntersectShapeResult> narrowIntersectShape(VxPhysicsWorld physicsWorld, ConstShape shape, Vec3Arg shapeScale,
                                                                  RMat44Arg comTransform, RVec3Arg base, ObjectLayerFilter olFilter) {
        try (BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
             BodyFilter bodyFilter = new BodyFilter();
             ShapeFilter shapeFilter = new ShapeFilter()) {
            return narrowIntersectShape(physicsWorld, shape, shapeScale, comTransform, base, bplFilter, olFilter, bodyFilter, shapeFilter);
        }
    }

    /**
     * Performs a narrow-phase intersect with a shape using specific broad-phase and object-layer filters, with default shape filter.
     *
     * @param physicsWorld The physics world to perform the intersect in.
     * @param shape        The shape to test.
     * @param shapeScale   The scaling vector for the shape.
     * @param comTransform The coordinate transform to apply to the shape.
     * @param base         The base location for reporting hits ((0,0,0)→world coordinates).
     * @param bplFilter    A custom filter for broad-phase layers.
     * @param olFilter     A custom filter for object layers.
     * @param bodyFilter   A custom filter for individual physics bodies.
     * @return List containing generic physics intersect results.
     */
    public static List<IntersectShapeResult> narrowIntersectShape(VxPhysicsWorld physicsWorld, ConstShape shape, Vec3Arg shapeScale,
                                                                  RMat44Arg comTransform, RVec3Arg base, BroadPhaseLayerFilter bplFilter, ObjectLayerFilter olFilter,
                                                                  BodyFilter bodyFilter) {
        try (ShapeFilter shapeFilter = new ShapeFilter()) {
            return narrowIntersectShape(physicsWorld, shape, shapeScale, comTransform, base, bplFilter, olFilter, bodyFilter, shapeFilter);
        }
    }

    /**
     * Performs a narrow-phase intersect with a shape using fully customized broad-phase, object-layer, body, and shape filters.
     *
     * @param physicsWorld The physics world to perform the intersect in.
     * @param shape        The shape to test.
     * @param shapeScale   The scaling vector for the shape.
     * @param comTransform The coordinate transform to apply to the shape.
     * @param base         The base location for reporting hits ((0,0,0)→world coordinates).
     * @param bplFilter    A custom filter for broad-phase layers.
     * @param olFilter     A custom filter for object layers.
     * @param bodyFilter   A custom filter for individual physics bodies.
     * @param shapeFilter  A custom filter for shapes.
     * @return List containing generic physics intersect results.
     */
    public static List<IntersectShapeResult> narrowIntersectShape(VxPhysicsWorld physicsWorld, ConstShape shape, Vec3Arg shapeScale,
                                                                  RMat44Arg comTransform, RVec3Arg base,
                                                                  BroadPhaseLayerFilter bplFilter, ObjectLayerFilter olFilter,
                                                                  BodyFilter bodyFilter, ShapeFilter shapeFilter) {
        if (physicsWorld == null || !physicsWorld.isRunning() || physicsWorld.getPhysicsSystem() == null) {
            return Collections.emptyList();
        }

        PhysicsSystem physicsSystem = physicsWorld.getPhysicsSystem();
        ConstNarrowPhaseQuery narrowPhaseQuery = physicsSystem.getNarrowPhaseQuery();

        try (CollideShapeSettings settings = new CollideShapeSettings();
             AllHitCollideShapeCollector collector = new AllHitCollideShapeCollector()) {

            // Perform colliding with the provided arguments.
            narrowPhaseQuery.collideShape(shape, shapeScale, comTransform, settings, base, collector,
                    bplFilter, olFilter, bodyFilter, shapeFilter);

            List<CollideShapeResult> hits = collector.getHits();
            int size = hits.size();

            if (size != 0) {
                List<IntersectShapeResult> results = new ArrayList<>(size);

                for (CollideShapeResult hit : hits) {
                    results.add(new IntersectShapeResult(
                            hit.getBodyId2(),
                            hit.getContactPointOn1(),
                            hit.getContactPointOn2()
                    ));
                }

                return results;
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during physics shape intersect", e);
        }
        return Collections.emptyList();
    }

    /**
     * A record to hold the raw results of a physics intersect via collide shape, using only Jolt and primitive types.
     */
    public record IntersectShapeResult(int bodyId, Vec3 shapeContactPoint, Vec3 bodyContactPoint) {
    }
}
