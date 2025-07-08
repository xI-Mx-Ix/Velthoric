package net.xmx.xbullet.builtin.block;

import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.model.converter.VoxelShapeConverter;
import net.xmx.xbullet.physics.object.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.RigidPhysicsObject;

import javax.annotation.Nullable;
import java.util.UUID;

public class BlockRigidPhysicsObject extends RigidPhysicsObject {
    public static final String TYPE_IDENTIFIER = "xbullet:block_obj";

    private BlockState representedBlockState;

    public BlockRigidPhysicsObject(UUID physicsId, Level level, String objectTypeIdentifier, PhysicsTransform initialTransform, IPhysicsObjectProperties properties, @Nullable CompoundTag initialNbt) {
        super(physicsId, level, objectTypeIdentifier, initialTransform, properties, initialNbt);
        if (this.representedBlockState == null) {
            this.representedBlockState = Blocks.STONE.defaultBlockState();
        }
    }

    protected BlockPos getPositionAsBlockPos() {
        var pos = getCurrentTransform().getTranslation();
        return BlockPos.containing(pos.xx(), pos.yy(), pos.zz());
    }

    @Override
    public ShapeSettings buildShapeSettings() {
        BlockState stateForShape = getRepresentedBlockState();
        VoxelShape voxelShape = stateForShape.getCollisionShape(this.level, this.getPositionAsBlockPos());

        ShapeSettings convertedShapeSettings = VoxelShapeConverter.convert(voxelShape);

        if (convertedShapeSettings != null) {
            return convertedShapeSettings;
        } else {
            XBullet.LOGGER.warn("VoxelShape conversion failed for BlockState {}. Using BoxShape as fallback.", stateForShape);
            return new BoxShapeSettings(0.5f, 0.5f, 0.5f);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.representedBlockState != null) {
            tag.put("blockState", NbtUtils.writeBlockState(this.representedBlockState));
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("blockState", CompoundTag.TAG_COMPOUND)) {
            // Holder-Lookup ist auf dem Server immer verfügbar, wenn das Level da ist
            this.representedBlockState = NbtUtils.readBlockState(
                    this.level.holderLookup(Registries.BLOCK), tag.getCompound("blockState")
            );
            if (this.representedBlockState.isAir()) {
                this.representedBlockState = Blocks.STONE.defaultBlockState();
            }
        } else {
            this.representedBlockState = Blocks.STONE.defaultBlockState();
        }
    }

    public BlockState getRepresentedBlockState() {
        return (this.representedBlockState != null && !this.representedBlockState.isAir()) ? this.representedBlockState : Blocks.STONE.defaultBlockState();
    }
}