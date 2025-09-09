package net.xmx.velthoric.natives;

import com.github.stephengold.joltjni.*;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.world.VxLayers;

public class VxNativeJolt {

    private static volatile boolean isInitialized = false;

    private static BroadPhaseLayerInterface broadPhaseLayerInterface;
    private static ObjectVsBroadPhaseLayerFilter objectVsBroadPhaseLayerFilter;
    private static ObjectLayerPairFilter objectLayerPairFilter;

    public static void initialize() {
        if (isInitialized) {
            return;
        }

        VxMainClass.LOGGER.debug("Performing Physics initialization...");

        VxNativeLibraryLoader.load();

        Jolt.registerDefaultAllocator();
        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();
        JoltPhysicsObject.startCleaner();

        if (!Jolt.newFactory()) {
            throw new IllegalStateException("Jolt Factory could not be created.");
        }
        Jolt.registerTypes();

        initializeCollisionFilters();

        isInitialized = true;
        VxMainClass.LOGGER.debug("Physics initialization complete.");
    }

    private static void initializeCollisionFilters() {
        final int numObjectLayers = VxLayers.NUM_LAYERS;
        final int numBroadPhaseLayers = VxLayers.NUM_LAYERS;

        ObjectLayerPairFilterTable olpfTable = new ObjectLayerPairFilterTable(numObjectLayers);
        olpfTable.disableCollision(VxLayers.STATIC, VxLayers.STATIC);
        olpfTable.enableCollision(VxLayers.DYNAMIC, VxLayers.DYNAMIC);
        olpfTable.enableCollision(VxLayers.STATIC, VxLayers.DYNAMIC);
        objectLayerPairFilter = olpfTable;

        BroadPhaseLayerInterfaceTable bpliTable = new BroadPhaseLayerInterfaceTable(numObjectLayers, numBroadPhaseLayers);
        bpliTable.mapObjectToBroadPhaseLayer(VxLayers.STATIC, VxLayers.STATIC);
        bpliTable.mapObjectToBroadPhaseLayer(VxLayers.DYNAMIC, VxLayers.DYNAMIC);
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

        VxMainClass.LOGGER.debug("Performing Physics shutdown...");

        if (objectVsBroadPhaseLayerFilter != null) objectVsBroadPhaseLayerFilter.close();
        if (objectLayerPairFilter != null) objectLayerPairFilter.close();
        if (broadPhaseLayerInterface != null) broadPhaseLayerInterface.close();

        Jolt.destroyFactory();
        isInitialized = false;
        VxMainClass.LOGGER.debug("Physics shutdown complete.");
    }

    public static boolean isInitialized() {
        return isInitialized;
    }

    public static BroadPhaseLayerInterface getBroadPhaseLayerInterface() {
        return broadPhaseLayerInterface;
    }

    public static ObjectVsBroadPhaseLayerFilter getObjectVsBroadPhaseLayerFilter() {
        return objectVsBroadPhaseLayerFilter;
    }

    public static ObjectLayerPairFilter getObjectLayerPairFilter() {
        return objectLayerPairFilter;
    }
}