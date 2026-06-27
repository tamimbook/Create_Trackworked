package net.tamim.trackworked;

import com.simibubi.create.AllCreativeModeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static net.minecraft.network.chat.Component.translatable;

public class TrackCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TrackworkedMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BASE_CREATIVE_TAB = REGISTER.register("base",
            () -> CreativeModeTab.builder()
                    .title(translatable("itemGroup.trackwork"))
                    .icon(TrackBlocks.SIMPLE_WHEEL_PART::asStack)
                    .displayItems((displayParams, output) -> {
                        // Add all items and blocks manually
                        output.accept(TrackworkItems.TRACK_TOOL_KIT.get());
                        
                        // Tracks
                        output.accept(TrackBlocks.LARGE_SUSPENSION_TRACK.get());
                        output.accept(TrackBlocks.MED_SUSPENSION_TRACK.get());
                        output.accept(TrackBlocks.SUSPENSION_TRACK.get());
                        output.accept(TrackBlocks.LARGE_PHYS_TRACK.get());
                        output.accept(TrackBlocks.MED_PHYS_TRACK.get());
                        output.accept(TrackBlocks.PHYS_TRACK.get());
                        
                        // Wheels
                        output.accept(TrackBlocks.LARGE_SIMPLE_WHEEL.get());
                        output.accept(TrackBlocks.MED_SIMPLE_WHEEL.get());
                        output.accept(TrackBlocks.SIMPLE_WHEEL.get());
                        output.accept(TrackBlocks.SMALL_SIMPLE_WHEEL.get());
                        output.accept(TrackBlocks.OLEO_WHEEL.get());
                        
                        // Wheel parts
                        output.accept(TrackBlocks.LARGE_SIMPLE_WHEEL_PART.get());
                        output.accept(TrackBlocks.MED_SIMPLE_WHEEL_PART.get());
                        output.accept(TrackBlocks.SIMPLE_WHEEL_PART.get());
                        output.accept(TrackBlocks.SMALL_SIMPLE_WHEEL_PART.get());
                        
                        // Other
                        output.accept(TrackBlocks.TRACK_LEVEL_CONTROLLER.get());
                        output.accept(TrackBlocks.HORN.get());
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        REGISTER.register(modEventBus);
    }
}
