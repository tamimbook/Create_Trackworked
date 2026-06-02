package net.tamim.trackworked.tracks.blocks.variants;

import net.tamim.trackworked.TrackBlockEntityTypes;
import net.tamim.trackworked.tracks.blocks.PhysEntityTrackBlock;
import net.tamim.trackworked.tracks.blocks.PhysEntityTrackBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class LargePhysEntityTrackBlock extends PhysEntityTrackBlock {
    public LargePhysEntityTrackBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<PhysEntityTrackBlockEntity> getBlockEntityType() {
        return TrackBlockEntityTypes.LARGE_PHYS_TRACK.get();
    }
}
