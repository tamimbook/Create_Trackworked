package net.tamim.trackworked.tracks.blocks.variants;

import net.tamim.trackworked.TrackBlockEntityTypes;
import net.tamim.trackworked.tracks.blocks.SuspensionTrackBlock;
import net.tamim.trackworked.tracks.blocks.SuspensionTrackBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class MedSuspensionTrackBlock extends SuspensionTrackBlock {
    public MedSuspensionTrackBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<SuspensionTrackBlockEntity> getBlockEntityType() {
        return TrackBlockEntityTypes.MED_SUSPENSION_TRACK.get();
    }
}
