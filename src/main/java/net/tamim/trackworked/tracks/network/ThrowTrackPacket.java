package net.tamim.trackworked.tracks.network;

import com.simibubi.create.foundation.networking.BlockEntityDataPacket;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.tamim.trackworked.TrackPackets;
import net.tamim.trackworked.tracks.blocks.TrackBaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server to client: marks a run of track blocks as thrown (detracked) or fixed.
 */
public class ThrowTrackPacket extends BlockEntityDataPacket<TrackBaseBlockEntity> {
    public static final StreamCodec<ByteBuf, ThrowTrackPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, packet -> packet.pos,
            ByteBufCodecs.BOOL, packet -> packet.detracked,
            ThrowTrackPacket::new
    );

    public final boolean detracked;

    public ThrowTrackPacket(BlockPos pos, boolean detracked) {
        super(pos);
        this.detracked = detracked;
    }

    @Override
    protected void handlePacket(TrackBaseBlockEntity blockEntity) {
        blockEntity.handlePacket(this);
    }

    @Override
    public BasePacketPayload.PacketTypeProvider getTypeProvider() {
        return TrackPackets.THROW_TRACK;
    }
}
