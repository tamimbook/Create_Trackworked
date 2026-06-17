package net.tamim.trackworked.tracks.network;

import com.simibubi.create.foundation.networking.BlockEntityDataPacket;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.tamim.trackworked.TrackPackets;
import net.tamim.trackworked.tracks.blocks.OleoWheelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server to client: syncs an oleo wheel's suspension travel, steering and horizontal offset.
 */
public final class OleoWheelPacket extends BlockEntityDataPacket<OleoWheelBlockEntity> {
    public static final StreamCodec<ByteBuf, OleoWheelPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, packet -> packet.pos,
            ByteBufCodecs.FLOAT, packet -> packet.wheelTravel,
            ByteBufCodecs.FLOAT, packet -> packet.steeringValue,
            ByteBufCodecs.FLOAT, packet -> packet.horizontalOffset,
            OleoWheelPacket::new
    );

    public final float wheelTravel;
    public final float steeringValue;
    public final float horizontalOffset;

    public OleoWheelPacket(BlockPos pos, float wheelTravel, float steeringValue, float horizontalOffset) {
        super(pos);
        this.wheelTravel = wheelTravel;
        this.steeringValue = steeringValue;
        this.horizontalOffset = horizontalOffset;
    }

    @Override
    protected void handlePacket(OleoWheelBlockEntity blockEntity) {
        blockEntity.handlePacket(this);
    }

    @Override
    public BasePacketPayload.PacketTypeProvider getTypeProvider() {
        return TrackPackets.OLEO_WHEEL;
    }
}
