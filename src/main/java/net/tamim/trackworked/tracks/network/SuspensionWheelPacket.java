package net.tamim.trackworked.tracks.network;

import com.simibubi.create.foundation.networking.BlockEntityDataPacket;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.tamim.trackworked.TrackPackets;
import net.tamim.trackworked.tracks.blocks.SuspensionTrackBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server to client: syncs a suspension track's wheel travel.
 */
public final class SuspensionWheelPacket extends BlockEntityDataPacket<SuspensionTrackBlockEntity> {
    public static final StreamCodec<ByteBuf, SuspensionWheelPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, packet -> packet.pos,
            ByteBufCodecs.FLOAT, packet -> packet.wheelTravel,
            SuspensionWheelPacket::new
    );

    public final float wheelTravel;

    public SuspensionWheelPacket(BlockPos pos, float wheelTravel) {
        super(pos);
        this.wheelTravel = wheelTravel;
    }

    @Override
    protected void handlePacket(SuspensionTrackBlockEntity blockEntity) {
        blockEntity.handlePacket(this);
    }

    @Override
    public BasePacketPayload.PacketTypeProvider getTypeProvider() {
        return TrackPackets.SUSPENSION_WHEEL;
    }
}
