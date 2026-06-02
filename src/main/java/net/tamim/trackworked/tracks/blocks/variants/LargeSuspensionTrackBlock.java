package net.tamim.trackworked.tracks.blocks.variants;

import net.tamim.trackworked.TrackBlockEntityTypes;
import net.tamim.trackworked.tracks.blocks.SuspensionTrackBlock;
import net.tamim.trackworked.tracks.blocks.SuspensionTrackBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class LargeSuspensionTrackBlock extends SuspensionTrackBlock {

    public LargeSuspensionTrackBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends SuspensionTrackBlockEntity> getBlockEntityType() {
        return TrackBlockEntityTypes.LARGE_SUSPENSION_TRACK.get();
    }
}
