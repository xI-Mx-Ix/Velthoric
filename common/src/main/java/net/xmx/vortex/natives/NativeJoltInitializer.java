package net.xmx.vortex.natives;

import com.github.stephengold.joltjni.*;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.natives.loader.*;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

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
            VxMainClass.LOGGER.debug("Performing one-time global Jolt Physics initialization...");

            NativeJoltLibraryLoader.load();

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
            VxMainClass.LOGGER.debug("Global Jolt initialization complete.");
        }
    }

    private static void initializeCollisionFilters() {

        final int numObjectLayers = VxPhysicsWorld.Layers.NUM_LAYERS;

        final int numBroadPhaseLayers = VxPhysicsWorld.BroadPhaseLayers.NUM_LAYERS;

        ObjectLayerPairFilterTable olpfTable = new ObjectLayerPairFilterTable(numObjectLayers);

        olpfTable.disableCollision(VxPhysicsWorld.Layers.STATIC, VxPhysicsWorld.Layers.STATIC);

        olpfTable.enableCollision(VxPhysicsWorld.Layers.DYNAMIC, VxPhysicsWorld.Layers.DYNAMIC);

        olpfTable.enableCollision(VxPhysicsWorld.Layers.STATIC, VxPhysicsWorld.Layers.DYNAMIC);

        objectLayerPairFilter = olpfTable;

        BroadPhaseLayerInterfaceTable bpliTable = new BroadPhaseLayerInterfaceTable(numObjectLayers, numBroadPhaseLayers);

        bpliTable.mapObjectToBroadPhaseLayer(VxPhysicsWorld.Layers.STATIC, VxPhysicsWorld.BroadPhaseLayers.STATIC);
        bpliTable.mapObjectToBroadPhaseLayer(VxPhysicsWorld.Layers.DYNAMIC, VxPhysicsWorld.BroadPhaseLayers.DYNAMIC);

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
            VxMainClass.LOGGER.debug("Performing one-time global Jolt shutdown...");

            if (objectVsBroadPhaseLayerFilter != null) objectVsBroadPhaseLayerFilter.close();
            if (objectLayerPairFilter != null) objectLayerPairFilter.close();
            if (broadPhaseLayerInterface != null) broadPhaseLayerInterface.close();

            Jolt.destroyFactory();
            isInitialized = false;
            VxMainClass.LOGGER.debug("Global Jolt shutdown complete.");
        }
    }

    public static boolean isInitialized() {
        return isInitialized;
    }

    public static BroadPhaseLayerInterface getBroadPhaseLayerInterface() { return broadPhaseLayerInterface; }
    public static ObjectVsBroadPhaseLayerFilter getObjectVsBroadPhaseLayerFilter() { return objectVsBroadPhaseLayerFilter; }
    public static ObjectLayerPairFilter getObjectLayerPairFilter() { return objectLayerPairFilter; }
}