package net.xmx.xbullet.natives;

import com.github.stephengold.joltjni.*;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;

public class NativeJoltInitializer {

    private static volatile boolean isInitialized = false;
    private static final Object lock = new Object();

    private static BroadPhaseLayerInterface broadPhaseLayerInterface;
    private static ObjectVsBroadPhaseLayerFilter objectVsBroadPhaseLayerFilter;
    private static ObjectLayerPairFilter objectLayerPairFilter;

    public static void initialize() {
        if (isInitialized) {
            return;
        }
        synchronized (lock) {
            if (isInitialized) {
                return;
            }
            XBullet.LOGGER.debug("Performing one-time global Jolt Physics initialization...");

            NativeLibraryLoader.load();
            Jolt.registerDefaultAllocator();
            Jolt.installDefaultAssertCallback();
            Jolt.installDefaultTraceCallback();
            //Jolt.setTraceAllocations(true);

            if (!Jolt.newFactory()) {
                throw new IllegalStateException("Global Jolt Factory could not be created.");
            }
            Jolt.registerTypes();

            initializeCollisionFilters();

            isInitialized = true;
            XBullet.LOGGER.debug("Global Jolt initialization complete.");
        }
    }

    private static void initializeCollisionFilters() {

        final int numObjectLayers = PhysicsWorld.Layers.NUM_LAYERS;

        final int numBroadPhaseLayers = PhysicsWorld.BroadPhaseLayers.NUM_LAYERS;

        ObjectLayerPairFilterTable olpfTable = new ObjectLayerPairFilterTable(numObjectLayers);

        olpfTable.disableCollision(PhysicsWorld.Layers.STATIC, PhysicsWorld.Layers.STATIC);

        olpfTable.enableCollision(PhysicsWorld.Layers.DYNAMIC, PhysicsWorld.Layers.DYNAMIC);

        olpfTable.enableCollision(PhysicsWorld.Layers.STATIC, PhysicsWorld.Layers.DYNAMIC);

        objectLayerPairFilter = olpfTable;

        BroadPhaseLayerInterfaceTable bpliTable = new BroadPhaseLayerInterfaceTable(numObjectLayers, numBroadPhaseLayers);

        bpliTable.mapObjectToBroadPhaseLayer(PhysicsWorld.Layers.STATIC, PhysicsWorld.BroadPhaseLayers.STATIC);
        bpliTable.mapObjectToBroadPhaseLayer(PhysicsWorld.Layers.DYNAMIC, PhysicsWorld.BroadPhaseLayers.DYNAMIC);

        broadPhaseLayerInterface = bpliTable;

        objectVsBroadPhaseLayerFilter = new ObjectVsBroadPhaseLayerFilterTable(
                broadPhaseLayerInterface, numBroadPhaseLayers,
                objectLayerPairFilter, numObjectLayers
        );
    }

    public static void shutdown() {
        if (!isInitialized) {
            return;
        }
        synchronized (lock) {
            if (!isInitialized) {
                return;
            }
            XBullet.LOGGER.info("Performing one-time global Jolt shutdown...");

            if (objectVsBroadPhaseLayerFilter != null) objectVsBroadPhaseLayerFilter.close();
            if (objectLayerPairFilter != null) objectLayerPairFilter.close();
            if (broadPhaseLayerInterface != null) broadPhaseLayerInterface.close();

            Jolt.destroyFactory();
            isInitialized = false;
            XBullet.LOGGER.info("Global Jolt shutdown complete.");
        }
    }

    public static boolean isInitialized() {
        return isInitialized;
    }

    public static BroadPhaseLayerInterface getBroadPhaseLayerInterface() { return broadPhaseLayerInterface; }
    public static ObjectVsBroadPhaseLayerFilter getObjectVsBroadPhaseLayerFilter() { return objectVsBroadPhaseLayerFilter; }
    public static ObjectLayerPairFilter getObjectLayerPairFilter() { return objectLayerPairFilter; }
}