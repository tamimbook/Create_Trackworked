package net.tamim.trackworked;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class TrackSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, TrackworkedMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SUSPENSION_CREAK = registerSoundEvents("suspension_creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_TOOL = registerSoundEvents("power_wrench");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPRING_TOOL = registerSoundEvents("spring_tool");

    public static final DeferredHolder<SoundEvent, SoundEvent> TRACK_AMBIENT_SPROCKET = registerSoundEvents("track_ambient_sprocket");

    public static final DeferredHolder<SoundEvent, SoundEvent> TRACK_AMBIENT_GROUND_1 = registerSoundEvents("track_ambient_ground_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRACK_AMBIENT_GROUND_2 = registerSoundEvents("track_ambient_ground_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRACK_GROUND_SLIP = registerSoundEvents("track_ground_slip");

    public static final DeferredHolder<SoundEvent, SoundEvent> WHEEL_ROCKTOSS = registerSoundEvents("wheel_rocktoss");

    public static final DeferredHolder<SoundEvent, SoundEvent> WHEEL_AMBIENT_GROUND_1 = registerSoundEvents("wheel_ambient_ground_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHEEL_AMBIENT_GROUND_2 = registerSoundEvents("wheel_ambient_ground_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHEEL_GROUND_SLIP = registerSoundEvents("wheel_ground_slip");

    public static final DeferredHolder<SoundEvent, SoundEvent> HORN = registerSoundEvents("honk");

    private static DeferredHolder<SoundEvent, SoundEvent> registerSoundEvents(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(TrackworkedMod.MODID, name)));
    }

    public static void register(IEventBus bus) { SOUND_EVENTS.register(bus); }
}
