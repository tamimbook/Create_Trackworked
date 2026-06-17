package net.tamim.trackworked;

import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.CatnipPacketRegistry;
import net.tamim.trackworked.tracks.network.OleoWheelPacket;
import net.tamim.trackworked.tracks.network.SimpleWheelPacket;
import net.tamim.trackworked.tracks.network.SuspensionWheelPacket;
import net.tamim.trackworked.tracks.network.ThrowTrackPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Locale;

/**
 * Packet registry for Trackworked, built on Create/catnip's payload system
 * ({@link BasePacketPayload} + {@link CatnipPacketRegistry}). Each constant is both the
 * {@link BasePacketPayload.PacketTypeProvider type provider} returned by its packet's
 * {@code getTypeProvider()} and the registry entry carrying its {@link StreamCodec}.
 *
 * <p>Registration happens once from the mod constructor via {@link #register()} — catnip's
 * platform {@code NetworkHelper} defers the actual payload-handler wiring to NeoForge's
 * {@code RegisterPayloadHandlersEvent} internally, exactly as Create's {@code AllPackets} does.</p>
 */
public enum TrackPackets implements BasePacketPayload.PacketTypeProvider {
    THROW_TRACK(ThrowTrackPacket.class, ThrowTrackPacket.STREAM_CODEC),
    SIMPLE_WHEEL(SimpleWheelPacket.class, SimpleWheelPacket.STREAM_CODEC),
    OLEO_WHEEL(OleoWheelPacket.class, OleoWheelPacket.STREAM_CODEC),
    SUSPENSION_WHEEL(SuspensionWheelPacket.class, SuspensionWheelPacket.STREAM_CODEC);

    public static final int NETWORK_VERSION = 3;

    private final CatnipPacketRegistry.PacketType<?> type;

    <T extends BasePacketPayload> TrackPackets(Class<T> clazz, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        String name = this.name().toLowerCase(Locale.ROOT);
        this.type = new CatnipPacketRegistry.PacketType<>(
                new CustomPacketPayload.Type<>(TrackworkedMod.getResource(name)),
                clazz, codec
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> getType() {
        return (CustomPacketPayload.Type<T>) this.type.type();
    }

    public static void register() {
        CatnipPacketRegistry registry = new CatnipPacketRegistry(TrackworkedMod.MODID, NETWORK_VERSION);
        for (TrackPackets packet : values()) {
            registry.registerPacket(packet.type);
        }
        registry.registerAllPackets();
    }
}
