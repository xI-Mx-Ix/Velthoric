package net.xmx.vortex.builtin.block;

import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.model.converter.VoxelShapeConverter;
import net.xmx.vortex.physics.object.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.RigidPhysicsObject;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public class BlockRigidPhysicsObject extends RigidPhysicsObject {
    public static final String TYPE_IDENTIFIER = "vortex:block_obj";

    private BlockState representedBlockState;

    public BlockRigidPhysicsObject(UUID physicsId, Level level, VxTransform initialTransform, IPhysicsObjectProperties properties, BlockState blockState) {
        super(physicsId, level, TYPE_IDENTIFIER, initialTransform, properties);
        this.representedBlockState = (blockState != null && !blockState.isAir()) ? blockState : Blocks.STONE.defaultBlockState();
    }

    public BlockRigidPhysicsObject(UUID physicsId, Level level, String typeId, VxTransform initialTransform, IPhysicsObjectProperties properties, @Nullable FriendlyByteBuf initialData) {
        super(physicsId, level, typeId, initialTransform, properties);
        this.representedBlockState = Blocks.STONE.defaultBlockState();
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
            VxMainClass.LOGGER.warn("VoxelShape conversion failed for BlockState {}. Using BoxShape as fallback.", stateForShape);
            return new BoxShapeSettings(0.5f, 0.5f, 0.5f);
        }
    }

    @Override
    protected void addAdditionalData(FriendlyByteBuf buf) {
        super.addAdditionalData(buf);

        buf.writeVarInt(Block.getId(this.representedBlockState));
    }

    @Override
    protected void readAdditionalData(FriendlyByteBuf buf) {
        super.readAdditionalData(buf);
        int blockStateId = buf.readVarInt();
        this.representedBlockState = Block.stateById(blockStateId);
        if (this.representedBlockState.isAir()) {
            this.representedBlockState = Blocks.STONE.defaultBlockState();
        }
    }

    public BlockState getRepresentedBlockState() {
        return (this.representedBlockState != null && !this.representedBlockState.isAir()) ? this.representedBlockState : Blocks.STONE.defaultBlockState();
    }
}