/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 *
 * Author: xI-Mx-Ix
 */
#include "Velthoric/Terrain/Shape/TerrainVoxelShape.h"
#include <Jolt/Physics/Collision/Shape/BoxShape.h>
#include <Jolt/Physics/Collision/RayCast.h>
#include <Jolt/Physics/Collision/CollisionCollectorImpl.h>
#include <Jolt/Physics/Collision/CollisionDispatch.h>
#include <Jolt/Physics/Collision/CastResult.h>
#include <cmath>
#include <jni.h>

namespace Velthoric {

JPH_IMPLEMENT_SERIALIZABLE_VIRTUAL(TerrainVoxelShapeSettings)
{
    JPH_ADD_BASE_CLASS(TerrainVoxelShapeSettings, JPH::ShapeSettings)
}

/**
 * @brief Creates the CompoundShape and resolves internal adjacency for culling.
 * 
 * Generates an optimized internal StaticCompoundShape and performs a spatial
 * sweep to test adjacency between voxel boxes. If a box's face is completely
 * obscured by another box, that face is disabled in the activeFaces bitmask
 * to prevent phantom collision normals.
 * 
 * @return ShapeResult A result encapsulating the instantiated TerrainVoxelShape or an error.
 */
JPH::ShapeSettings::ShapeResult TerrainVoxelShapeSettings::Create() const {
    if (mBoxes.empty()) {
        ShapeResult result;
        result.SetError("No boxes");
        return result;
    }

    JPH::StaticCompoundShapeSettings compoundSettings;
    std::vector<TerrainVoxelShape::BoxExt> exts(mBoxes.size());

    // Basic default material fallback
    JPH::RefConst<TerrainMaterial> defaultMat;
    if (TerrainGenerator::s_Materials[1]) defaultMat = TerrainGenerator::s_Materials[1];
    else defaultMat = new TerrainMaterial(1, 0.75f, 0.0f);

    struct BoxSettingsKey {
        float hx, hy, hz;
        uint32_t matId;
        bool operator==(const BoxSettingsKey& o) const {
            return hx == o.hx && hy == o.hy && hz == o.hz && matId == o.matId;
        }
    };

    struct BoxSettingsHash {
        std::size_t operator()(const BoxSettingsKey& k) const {
            return std::hash<float>()(k.hx) ^ (std::hash<float>()(k.hy) << 1) ^ (std::hash<float>()(k.hz) << 2) ^ std::hash<uint32_t>()(k.matId);
        }
    };

    std::unordered_map<BoxSettingsKey, JPH::Ref<JPH::BoxShapeSettings>, BoxSettingsHash> boxSettingsCache;

    for (size_t i = 0; i < mBoxes.size(); ++i) {
        const auto& b = mBoxes[i];
        
        BoxSettingsKey key = { b.hx, b.hy, b.hz, b.matId };
        JPH::Ref<JPH::BoxShapeSettings> boxSettings;
        
        auto it = boxSettingsCache.find(key);
        if (it != boxSettingsCache.end()) {
            boxSettings = it->second;
        } else {
            JPH::RefConst<TerrainMaterial> mat = TerrainGenerator::s_Materials[b.matId];
            if (!mat) mat = defaultMat;
            boxSettings = new JPH::BoxShapeSettings(JPH::Vec3(b.hx, b.hy, b.hz), 0.0f, mat);
            boxSettingsCache[key] = boxSettings;
        }
        
        compoundSettings.AddShape(JPH::Vec3(b.cx, b.cy, b.cz), JPH::Quat::sIdentity(), boxSettings);
        
        uint8_t activeFaces = 63;
        
        // Simple overlap test to disable internal faces
        for (size_t j = 0; j < mBoxes.size(); ++j) {
            if (i == j) continue;
            const auto& o = mBoxes[j];
            
            bool coversY = (o.cy - o.hy <= b.cy - b.hy + 1e-3f) && (o.cy + o.hy >= b.cy + b.hy - 1e-3f);
            bool coversZ = (o.cz - o.hz <= b.cz - b.hz + 1e-3f) && (o.cz + o.hz >= b.cz + b.hz - 1e-3f);
            bool coversX = (o.cx - o.hx <= b.cx - b.hx + 1e-3f) && (o.cx + o.hx >= b.cx + b.hx - 1e-3f);
            
            if (coversY && coversZ) {
                if (std::abs((b.cx - b.hx) - (o.cx + o.hx)) < 1e-3f) activeFaces &= ~1; // -X
                if (std::abs((b.cx + b.hx) - (o.cx - o.hx)) < 1e-3f) activeFaces &= ~2; // +X
            }
            if (coversX && coversZ) {
                if (std::abs((b.cy - b.hy) - (o.cy + o.hy)) < 1e-3f) activeFaces &= ~4; // -Y
                if (std::abs((b.cy + b.hy) - (o.cy - o.hy)) < 1e-3f) activeFaces &= ~8; // +Y
            }
            if (coversX && coversY) {
                if (std::abs((b.cz - b.hz) - (o.cz + o.hz)) < 1e-3f) activeFaces &= ~16; // -Z
                if (std::abs((b.cz + b.hz) - (o.cz - o.hz)) < 1e-3f) activeFaces &= ~32; // +Z
            }
        }
        exts[i].activeFaces = activeFaces;
    }

    JPH::ShapeSettings::ShapeResult compoundRes = compoundSettings.Create();
    if (compoundRes.HasError()) {
        ShapeResult result;
        result.SetError(compoundRes.GetError());
        return result;
    }

    TerrainVoxelShape* shape = new TerrainVoxelShape(*this, compoundRes.Get(), std::move(exts));
    ShapeResult result;
    result.Set(shape);
    return result;
}

/**
 * @brief Constructs a TerrainVoxelShape.
 * 
 * Binds the generated compound shape and face activity bitmasks to the shape object.
 * 
 * @param inSettings Settings containing the source voxel definitions.
 * @param inCompoundShape The finalized StaticCompoundShape created natively.
 * @param inBoxExts Active face data for each corresponding box shape.
 */
TerrainVoxelShape::TerrainVoxelShape(const TerrainVoxelShapeSettings& inSettings, JPH::ShapeRefC inCompoundShape, std::vector<BoxExt>&& inBoxExts)
    : Shape(JPH::EShapeType::User1, JPH::EShapeSubType::User1), mCompoundShape(static_cast<const JPH::StaticCompoundShape*>(inCompoundShape.GetPtr())), mBoxExts(std::move(inBoxExts)) {
}

/**
 * @brief Determines if a collision on a specific subshape and normal should be processed.
 * 
 * When a collision query contacts a voxel box, this method is used to verify
 * if the hit occurred on an exposed face. If the normal points toward an internal
 * (obscured) face, the hit is discarded.
 * 
 * @param inSubShapeID Identifies the specific child BoxShape that was hit.
 * @param inNormal The normal vector of the collision.
 * @return true if the face is exposed and the collision is valid, false otherwise.
 */
bool TerrainVoxelShape::IsFaceActive(const JPH::SubShapeID& inSubShapeID, JPH::Vec3Arg inNormal) const {
    JPH::SubShapeID remainder;
    uint32_t idx = mCompoundShape->GetSubShapeIndexFromID(inSubShapeID, remainder);
    if (idx >= mBoxExts.size()) return true; // safety fallback

    uint8_t flags = mBoxExts[idx].activeFaces;
    if (flags == 63) return true; // all faces active
    
    // Find dominant axis of normal to determine which face was hit
    float ax = std::abs(inNormal.GetX());
    float ay = std::abs(inNormal.GetY());
    float az = std::abs(inNormal.GetZ());
    
    if (ax > ay && ax > az) {
        if (inNormal.GetX() < 0) return (flags & 1) != 0;
        else return (flags & 2) != 0;
    } else if (ay > az) {
        if (inNormal.GetY() < 0) return (flags & 4) != 0;
        else return (flags & 8) != 0;
    } else {
        if (inNormal.GetZ() < 0) return (flags & 16) != 0;
        else return (flags & 32) != 0;
    }
}

/**
 * @brief A collector wrapper that filters RayCast results.
 * 
 * Delegates hits to the inner CastRayCollector only if the struck face
 * is exposed to the outside.
 */
class FilteredCastRayCollector : public JPH::CastRayCollector {
public:
    JPH::CastRayCollector& mInner;           ///< The underlying Jolt collector
    const TerrainVoxelShape* mShape;         ///< The shape being cast against
    JPH::RayCast mRay;                       ///< The original ray

    FilteredCastRayCollector(JPH::CastRayCollector& inInner, const TerrainVoxelShape* inShape, const JPH::RayCast& inRay)
        : mInner(inInner), mShape(inShape), mRay(inRay) {
        SetContext(inInner.GetContext());
    }

    /**
     * @brief Processes a potential ray hit.
     * 
     * Computes the surface normal at the hit fraction. If the normal points
     * to a disabled face, the hit is ignored.
     */
    virtual void AddHit(const JPH::RayCastResult& inResult) override {
        JPH::Vec3 normal = mShape->mCompoundShape->GetSurfaceNormal(inResult.mSubShapeID2, mRay.GetPointOnRay(inResult.mFraction));
        if (mShape->IsFaceActive(inResult.mSubShapeID2, normal)) {
            mInner.AddHit(inResult);
            UpdateEarlyOutFraction(mInner.GetEarlyOutFraction());
        }
    }
};

/**
 * @brief Casts a ray against the terrain utilizing the internal BVH.
 * 
 * Wraps the user's collector in a FilteredCastRayCollector to prevent
 * triggering callbacks on internal faces.
 */
void TerrainVoxelShape::CastRay(const JPH::RayCast& inRay, const JPH::RayCastSettings& inRayCastSettings, const JPH::SubShapeIDCreator& inSubShapeIDCreator, JPH::CastRayCollector& ioCollector, const JPH::ShapeFilter& inShapeFilter) const {
    FilteredCastRayCollector filtered(ioCollector, this, inRay);
    mCompoundShape->CastRay(inRay, inRayCastSettings, inSubShapeIDCreator, filtered, inShapeFilter);
}

/**
 * @brief Finds the closest raycast hit, strictly respecting face activity.
 */
bool TerrainVoxelShape::CastRay(const JPH::RayCast& inRay, const JPH::SubShapeIDCreator& inSubShapeIDCreator, JPH::RayCastResult& ioHit) const {
    JPH::ClosestHitCollisionCollector<JPH::CastRayCollector> collector;
    FilteredCastRayCollector filtered(collector, this, inRay);
    
    JPH::RayCastSettings settings; // defaults are fine
    mCompoundShape->CastRay(inRay, settings, inSubShapeIDCreator, filtered, JPH::ShapeFilter());
    if (collector.HadHit()) {
        ioHit = collector.mHit;
        return true;
    }
    return false;
}

/**
 * @brief Processes a point collision query.
 * 
 * Directly delegates to the compound shape since points inside internal
 * geometry don't produce directional normal anomalies in the same way.
 */
void TerrainVoxelShape::CollidePoint(JPH::Vec3Arg inPoint, const JPH::SubShapeIDCreator& inSubShapeIDCreator, JPH::CollidePointCollector& ioCollector, const JPH::ShapeFilter& inShapeFilter) const {
    mCompoundShape->CollidePoint(inPoint, inSubShapeIDCreator, ioCollector, inShapeFilter);
}

/**
 * @brief Wraps a CollideShapeCollector to discard internal face penetrations.
 * 
 * During narrow-phase, if two shapes penetrate, Jolt tests standard primitives.
 * This collector intercepts these hits and analyzes the penetration axis.
 */
class FilteredCollideShapeCollector : public JPH::CollideShapeCollector {
public:
    JPH::CollideShapeCollector& mInner;
    const TerrainVoxelShape* mShape;
    bool mIsShape2;

    FilteredCollideShapeCollector(JPH::CollideShapeCollector& inInner, const TerrainVoxelShape* inShape, bool inIsShape2)
        : mInner(inInner), mShape(inShape), mIsShape2(inIsShape2) {
        SetContext(inInner.GetContext());
    }

    virtual void AddHit(const JPH::CollideShapeResult& inResult) override {
        // mPenetrationAxis = ContactPointOn1 - ContactPointOn2
        // Points into shape 2, so negate when we are shape 2 to get outward normal
        JPH::Vec3 normal = inResult.mPenetrationAxis.Normalized();
        JPH::SubShapeID subShapeID = mIsShape2 ? inResult.mSubShapeID2 : inResult.mSubShapeID1;
        if (mIsShape2) normal = -normal;

        if (mShape->IsFaceActive(subShapeID, normal)) {
            mInner.AddHit(inResult);
            UpdateEarlyOutFraction(mInner.GetEarlyOutFraction());
        }
    }
};

/**
 * @brief Double dispatch function: Collides a generic shape (e.g. Sphere) against the TerrainVoxelShape.
 */
void sCollideShapeVsVoxelShape(const JPH::Shape* inShape1, const JPH::Shape* inShape2, JPH::Vec3Arg inScale1, JPH::Vec3Arg inScale2, JPH::Mat44Arg inCenterOfMassTransform1, JPH::Mat44Arg inCenterOfMassTransform2, const JPH::SubShapeIDCreator& inSubShapeIDCreator1, const JPH::SubShapeIDCreator& inSubShapeIDCreator2, const JPH::CollideShapeSettings& inCollideShapeSettings, JPH::CollideShapeCollector& ioCollector, const JPH::ShapeFilter& inShapeFilter) {
    auto voxelShape = static_cast<const TerrainVoxelShape*>(inShape2);
    FilteredCollideShapeCollector filtered(ioCollector, voxelShape, true);
    JPH::CollisionDispatch::sCollideShapeVsShape(inShape1, voxelShape->mCompoundShape, inScale1, inScale2, inCenterOfMassTransform1, inCenterOfMassTransform2, inSubShapeIDCreator1, inSubShapeIDCreator2, inCollideShapeSettings, filtered, inShapeFilter);
}

/**
 * @brief Double dispatch function: Collides the TerrainVoxelShape against a generic shape.
 */
void sCollideVoxelShapeVsShape(const JPH::Shape* inShape1, const JPH::Shape* inShape2, JPH::Vec3Arg inScale1, JPH::Vec3Arg inScale2, JPH::Mat44Arg inCenterOfMassTransform1, JPH::Mat44Arg inCenterOfMassTransform2, const JPH::SubShapeIDCreator& inSubShapeIDCreator1, const JPH::SubShapeIDCreator& inSubShapeIDCreator2, const JPH::CollideShapeSettings& inCollideShapeSettings, JPH::CollideShapeCollector& ioCollector, const JPH::ShapeFilter& inShapeFilter) {
    auto voxelShape = static_cast<const TerrainVoxelShape*>(inShape1);
    FilteredCollideShapeCollector filtered(ioCollector, voxelShape, false);
    JPH::CollisionDispatch::sCollideShapeVsShape(voxelShape->mCompoundShape, inShape2, inScale1, inScale2, inCenterOfMassTransform1, inCenterOfMassTransform2, inSubShapeIDCreator1, inSubShapeIDCreator2, inCollideShapeSettings, filtered, inShapeFilter);
}

/**
 * @brief Wrapper for shape casting, eliminating shape intersection reports on internal faces.
 */
class FilteredCastShapeCollector : public JPH::CastShapeCollector {
public:
    JPH::CastShapeCollector& mInner;
    const TerrainVoxelShape* mShape;
    bool mIsShape2;

    FilteredCastShapeCollector(JPH::CastShapeCollector& inInner, const TerrainVoxelShape* inShape, bool inIsShape2)
        : mInner(inInner), mShape(inShape), mIsShape2(inIsShape2) {
        SetContext(inInner.GetContext());
    }

    virtual void AddHit(const JPH::ShapeCastResult& inResult) override {
        JPH::Vec3 normal = inResult.mPenetrationAxis.Normalized();
        JPH::SubShapeID subShapeID = mIsShape2 ? inResult.mSubShapeID2 : inResult.mSubShapeID1;
        if (mIsShape2) normal = -normal;

        if (mShape->IsFaceActive(subShapeID, normal)) {
            mInner.AddHit(inResult);
            UpdateEarlyOutFraction(mInner.GetEarlyOutFraction());
        }
    }
};

/**
 * @brief Double dispatch function: Casts a generic shape against the TerrainVoxelShape.
 */
void sCastShapeVsVoxelShape(const JPH::ShapeCast& inShapeCast, const JPH::ShapeCastSettings& inShapeCastSettings, const JPH::Shape* inShape, JPH::Vec3Arg inScale, const JPH::ShapeFilter& inShapeFilter, JPH::Mat44Arg inCenterOfMassTransform2, const JPH::SubShapeIDCreator& inSubShapeIDCreator1, const JPH::SubShapeIDCreator& inSubShapeIDCreator2, JPH::CastShapeCollector& ioCollector) {
    auto voxelShape = static_cast<const TerrainVoxelShape*>(inShape);
    FilteredCastShapeCollector filtered(ioCollector, voxelShape, true);
    JPH::CollisionDispatch::sCastShapeVsShapeLocalSpace(inShapeCast, inShapeCastSettings, voxelShape->mCompoundShape, inScale, inShapeFilter, inCenterOfMassTransform2, inSubShapeIDCreator1, inSubShapeIDCreator2, filtered);
}

/**
 * @brief Double dispatch function: Casts the TerrainVoxelShape against a generic shape.
 */
void sCastVoxelShapeVsShape(const JPH::ShapeCast& inShapeCast, const JPH::ShapeCastSettings& inShapeCastSettings, const JPH::Shape* inShape, JPH::Vec3Arg inScale, const JPH::ShapeFilter& inShapeFilter, JPH::Mat44Arg inCenterOfMassTransform2, const JPH::SubShapeIDCreator& inSubShapeIDCreator1, const JPH::SubShapeIDCreator& inSubShapeIDCreator2, JPH::CastShapeCollector& ioCollector) {
    auto voxelShape = static_cast<const TerrainVoxelShape*>(inShapeCast.mShape);
    
    JPH::ShapeCast compoundCast = inShapeCast;
    compoundCast.mShape = voxelShape->mCompoundShape;
    
    FilteredCastShapeCollector filtered(ioCollector, voxelShape, false);
    JPH::CollisionDispatch::sCastShapeVsShapeLocalSpace(compoundCast, inShapeCastSettings, inShape, inScale, inShapeFilter, inCenterOfMassTransform2, inSubShapeIDCreator1, inSubShapeIDCreator2, filtered);
}

/**
 * @brief Registers the TerrainVoxelShape double dispatch collision functions globally.
 * 
 * Must be executed once during engine bootstrap to map EShapeSubType::User1 
 * interactions correctly within the JPH::CollisionDispatch table.
 */
void TerrainVoxelShape::sRegister() {
    for (JPH::EShapeSubType s : JPH::sAllSubShapeTypes) {
        if (s == JPH::EShapeSubType::User1) continue;
        JPH::CollisionDispatch::sRegisterCollideShape(s, JPH::EShapeSubType::User1, sCollideShapeVsVoxelShape);
        JPH::CollisionDispatch::sRegisterCollideShape(JPH::EShapeSubType::User1, s, sCollideVoxelShapeVsShape);
        JPH::CollisionDispatch::sRegisterCastShape(s, JPH::EShapeSubType::User1, sCastShapeVsVoxelShape);
        JPH::CollisionDispatch::sRegisterCastShape(JPH::EShapeSubType::User1, s, sCastVoxelShapeVsShape);
    }
}

} // namespace Velthoric

/**
 * @brief JNI bridge to register the TerrainVoxelShape at engine startup.
 * 
 * Called by VxPhysicsBootstrap.java during physics engine initialization.
 */
extern "C" JNIEXPORT void JNICALL
Java_net_xmx_velthoric_jni_TerrainVoxelShape_nRegisterVoxelShape(JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    Velthoric::TerrainVoxelShape::sRegister();
}