package net.tamim.trackworked.tracks.blocks.variants;

import net.tamim.trackworked.TrackBlockEntityTypes;
import net.tamim.trackworked.tracks.blocks.PhysEntityTrackBlock;
import net.tamim.trackworked.tracks.blocks.PhysEntityTrackBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class MedPhysEntityTrackBlock extends PhysEntityTrackBlock {
    public MedPhysEntityTrackBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<PhysEntityTrackBlockEntity> getBlockEntityType() {
        return TrackBlockEntityTypes.MED_PHYS_TRACK.get();
    }
}
