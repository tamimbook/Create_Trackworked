package net.tamim.trackworked.tracks.network;

import com.simibubi.create.foundation.networking.BlockEntityDataPacket;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.tamim.trackworked.TrackPackets;
import net.tamim.trackworked.tracks.blocks.WheelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server to client: syncs a wheel's suspension travel, steering and horizontal offset.
 */
public final class SimpleWheelPacket extends BlockEntityDataPacket<WheelBlockEntity> {
    public static final StreamCodec<ByteBuf, SimpleWheelPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, packet -> packet.pos,
            ByteBufCodecs.FLOAT, packet -> packet.wheelTravel,
            ByteBufCodecs.FLOAT, packet -> packet.steeringValue,
            ByteBufCodecs.FLOAT, packet -> packet.horizontalOffset,
            SimpleWheelPacket::new
    );

    public final float wheelTravel;
    public final float steeringValue;
    public final float horizontalOffset;

    public SimpleWheelPacket(BlockPos pos, float wheelTravel, float steeringValue, float horizontalOffset) {
        super(pos);
        this.wheelTravel = wheelTravel;
        this.steeringValue = steeringValue;
        this.horizontalOffset = horizontalOffset;
    }

    @Override
    protected void handlePacket(WheelBlockEntity blockEntity) {
        blockEntity.handlePacket(this);
    }

    @Override
    public BasePacketPayload.PacketTypeProvider getTypeProvider() {
        return TrackPackets.SIMPLE_WHEEL;
    }
}
