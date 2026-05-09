/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#pragma once
#include <Jolt/Jolt.h>
#include <Jolt/Physics/Collision/Shape/Shape.h>
#include <Jolt/Physics/Collision/Shape/StaticCompoundShape.h>
#include "Velthoric/Terrain/TerrainGenerator.h"
#include <vector>

namespace Velthoric {

/**
 * @brief Settings object to configure and create a TerrainVoxelShape.
 *
 * This class inherits from JPH::ShapeSettings and holds the definition
 * of the voxel geometry prior to creating the actual physical shape.
 * It encapsulates a list of BoxShapeData objects, each representing an
 * independent voxel or clustered block AABB along with its physical material.
 */
class TerrainVoxelShapeSettings final : public JPH::ShapeSettings {
    JPH_DECLARE_SERIALIZABLE_VIRTUAL(, TerrainVoxelShapeSettings)
public:
    /// Collection of all voxel boxes that make up this terrain chunk shape.
    std::vector<BoxShapeData> mBoxes;

    /**
     * @brief Default constructor for serialization purposes.
     */
    TerrainVoxelShapeSettings() = default;

    /**
     * @brief Constructs settings from a raw array of box definitions.
     * 
     * @param inBoxes Pointer to the array of BoxShapeData.
     * @param inBoxCount The number of elements in the array.
     */
    TerrainVoxelShapeSettings(const BoxShapeData* inBoxes, int inBoxCount) {
        mBoxes.assign(inBoxes, inBoxes + inBoxCount);
    }

    /**
     * @brief Creates the actual TerrainVoxelShape instance.
     * 
     * This overrides the Jolt ShapeSettings::Create method. During creation,
     * it builds an internal StaticCompoundShape using the provided boxes.
     * Crucially, it performs an adjacency sweep to determine which faces of 
     * each voxel box are completely covered by neighboring solid voxels. It
     * stores this information so that internal collisions can be filtered out
     * efficiently at runtime.
     * 
     * @return ShapeResult containing the newly instantiated TerrainVoxelShape, or an error.
     */
    virtual ShapeResult Create() const override;
};

/**
 * @brief A custom Jolt Physics Shape optimized for voxel terrain.
 * 
 * The TerrainVoxelShape is designed to represent large chunks of Minecraft-like
 * voxel geometry. Unlike standard compound shapes or triangle meshes, this shape
 * encapsulates a highly optimized `JPH::StaticCompoundShape` constructed from BoxShapes,
 * but features a specialized internal collision filtering system.
 * 
 * Ghost collisions often occur in physics engines when an object (like a character
 * or vehicle wheel) slides across the perfectly flat seam between two adjacent grid blocks.
 * This class eliminates that issue by disabling completely covered "internal faces". 
 * It wraps the collision collectors for RayCasts, ShapeCasts, and CollidePoint queries 
 * to ignore collision normals pointing to these internal faces.
 */
class TerrainVoxelShape final : public JPH::Shape {
public:
    JPH_OVERRIDE_NEW_DELETE

    /**
     * @brief Structure to hold extended data per internal voxel box.
     */
    struct BoxExt {
        /// Bitmask defining which of the 6 faces are active (exposed to air).
        /// bit 0: -X, bit 1: +X, bit 2: -Y, bit 3: +Y, bit 4: -Z, bit 5: +Z.
        /// An active face means it can generate a collision hit.
        uint8_t activeFaces = 63; 
    };

    /**
     * @brief Constructor for the TerrainVoxelShape.
     * 
     * Should only be called by TerrainVoxelShapeSettings::Create().
     * 
     * @param inSettings The settings object that spawned this shape.
     * @param inCompoundShape The internally generated static compound shape.
     * @param inBoxExts The pre-calculated array of active face masks per box.
     */
    TerrainVoxelShape(const TerrainVoxelShapeSettings& inSettings, JPH::ShapeRefC inCompoundShape, std::vector<BoxExt>&& inBoxExts);
    
    // Jolt Shape Base Class Delegated Methods
    virtual JPH::Vec3 GetCenterOfMass() const override { return mCompoundShape->GetCenterOfMass(); }
    virtual JPH::AABox GetLocalBounds() const override { return mCompoundShape->GetLocalBounds(); }
    virtual JPH::uint GetSubShapeIDBitsRecursive() const override { return mCompoundShape->GetSubShapeIDBitsRecursive(); }
    virtual float GetInnerRadius() const override { return mCompoundShape->GetInnerRadius(); }
    virtual JPH::MassProperties GetMassProperties() const override { return mCompoundShape->GetMassProperties(); }
    virtual const JPH::PhysicsMaterial* GetMaterial(const JPH::SubShapeID& inSubShapeID) const override { return mCompoundShape->GetMaterial(inSubShapeID); }
    virtual JPH::uint64 GetSubShapeUserData(const JPH::SubShapeID& inSubShapeID) const override { return mCompoundShape->GetSubShapeUserData(inSubShapeID); }
    virtual JPH::Vec3 GetSurfaceNormal(const JPH::SubShapeID& inSubShapeID, JPH::Vec3Arg inLocalSurfacePosition) const override { return mCompoundShape->GetSurfaceNormal(inSubShapeID, inLocalSurfacePosition); }
    virtual void GetSubmergedVolume(JPH::Mat44Arg inCenterOfMassTransform, JPH::Vec3Arg inScale, const JPH::Plane& inSurface, float& outTotalVolume, float& outSubmergedVolume, JPH::Vec3& outCenterOfBuoyancy) const override {
        mCompoundShape->GetSubmergedVolume(inCenterOfMassTransform, inScale, inSurface, outTotalVolume, outSubmergedVolume, outCenterOfBuoyancy);
    }
    
    // Custom Collision Query Method Overrides

    /**
     * @brief Handles raycast hits against the voxel terrain, filtering internal faces.
     */
    virtual bool CastRay(const JPH::RayCast& inRay, const JPH::SubShapeIDCreator& inSubShapeIDCreator, JPH::RayCastResult& ioHit) const override;
    
    /**
     * @brief Handles multi-hit raycasts against the voxel terrain, filtering internal faces.
     */
    virtual void CastRay(const JPH::RayCast& inRay, const JPH::RayCastSettings& inRayCastSettings, const JPH::SubShapeIDCreator& inSubShapeIDCreator, JPH::CastRayCollector& ioCollector, const JPH::ShapeFilter& inShapeFilter = { }) const override;
    
    /**
     * @brief Checks point collision against the terrain. 
     */
    virtual void CollidePoint(JPH::Vec3Arg inPoint, const JPH::SubShapeIDCreator& inSubShapeIDCreator, JPH::CollidePointCollector& ioCollector, const JPH::ShapeFilter& inShapeFilter = { }) const override;
    
    virtual void CollideSoftBodyVertices(JPH::Mat44Arg inCenterOfMassTransform, JPH::Vec3Arg inScale, const JPH::CollideSoftBodyVertexIterator& inVertices, JPH::uint inNumVertices, int inCollidingShapeIndex) const override {
        mCompoundShape->CollideSoftBodyVertices(inCenterOfMassTransform, inScale, inVertices, inNumVertices, inCollidingShapeIndex);
    }
    virtual void TransformShape(JPH::Mat44Arg inCenterOfMassTransform, JPH::TransformedShapeCollector& ioCollector) const override {
        mCompoundShape->TransformShape(inCenterOfMassTransform, ioCollector);
    }

    virtual void GetTrianglesStart(GetTrianglesContext& ioContext, const JPH::AABox& inBox, JPH::Vec3Arg inPositionCOM, JPH::QuatArg inRotation, JPH::Vec3Arg inScale) const override {
        mCompoundShape->GetTrianglesStart(ioContext, inBox, inPositionCOM, inRotation, inScale);
    }
    virtual int GetTrianglesNext(GetTrianglesContext& ioContext, int inMaxTrianglesRequested, JPH::Float3* outTriangleVertices, const JPH::PhysicsMaterial** outMaterials = nullptr) const override {
        return mCompoundShape->GetTrianglesNext(ioContext, inMaxTrianglesRequested, outTriangleVertices, outMaterials);
    }

    virtual Stats GetStats() const override { return mCompoundShape->GetStats(); }
    virtual float GetVolume() const override { return mCompoundShape->GetVolume(); }

    // Terrain Specific Methods

    /**
     * @brief Checks whether the given face normal points to an active (exposed) face.
     * 
     * @param inSubShapeID The ID of the specific box primitive within the compound.
     * @param inNormal The collision normal vector.
     * @return true if the face is exposed, false if it is covered by an adjacent solid voxel.
     */
    bool IsFaceActive(const JPH::SubShapeID& inSubShapeID, JPH::Vec3Arg inNormal) const;

    /// The highly-optimized Jolt static compound shape holding the underlying boxes.
    JPH::RefConst<JPH::StaticCompoundShape> mCompoundShape;
    
    /// Parallel array of BoxExt storing the active face bitmasks for each subshape.
    std::vector<BoxExt> mBoxExts;

    /**
     * @brief Registers the TerrainVoxelShape double dispatch collision functions.
     * 
     * Must be called exactly once during the physics engine bootstrap phase to enable
     * collisions between standard shapes (Box, Sphere, Convex, etc.) and this custom shape.
     */
    static void sRegister();
};

} // namespace Velthoric