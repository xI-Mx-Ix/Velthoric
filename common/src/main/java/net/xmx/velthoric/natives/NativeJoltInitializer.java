package net.xmx.velthoric.natives;

import com.github.stephengold.joltjni.*;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.natives.loader.*;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxBroadPhaseLayers;

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

        final int numObjectLayers = VxLayers.NUM_LAYERS;

        final int numBroadPhaseLayers = VxBroadPhaseLayers.NUM_LAYERS;

        ObjectLayerPairFilterTable olpfTable = new ObjectLayerPairFilterTable(numObjectLayers);

        olpfTable.disableCollision(VxLayers.STATIC, VxLayers.STATIC);

        olpfTable.enableCollision(VxLayers.DYNAMIC, VxLayers.DYNAMIC);

        olpfTable.enableCollision(VxLayers.STATIC, VxLayers.DYNAMIC);

        objectLayerPairFilter = olpfTable;

        BroadPhaseLayerInterfaceTable bpliTable = new BroadPhaseLayerInterfaceTable(numObjectLayers, numBroadPhaseLayers);

        bpliTable.mapObjectToBroadPhaseLayer(VxLayers.STATIC, VxBroadPhaseLayers.STATIC);
        bpliTable.mapObjectToBroadPhaseLayer(VxLayers.DYNAMIC, VxBroadPhaseLayers.DYNAMIC);

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