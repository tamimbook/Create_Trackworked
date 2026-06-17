package net.tamim.trackworked;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static net.tamim.trackworked.TrackworkedMod.REGISTRATE;
import static net.minecraft.network.chat.Component.translatable;

@EventBusSubscriber(modid = TrackworkedMod.MODID)
public class TrackCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TrackworkedMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BASE_CREATIVE_TAB = REGISTER.register("base",
            () -> CreativeModeTab.builder()
                    .title(translatable("itemGroup.trackwork"))
                    .icon(TrackBlocks.SIMPLE_WHEEL_PART::asStack)
                    .displayItems((displayParams, output) -> {
                        for (RegistryEntry<Block, Block> entry : REGISTRATE.getAll(Registries.BLOCK)) {
                            if (CreateRegistrate.isInCreativeTab(entry, AllCreativeModeTabs.BASE_CREATIVE_TAB))
                                output.accept(entry.get().asItem());
                        }

                        for (RegistryEntry<Item, Item> entry : REGISTRATE.getAll(Registries.ITEM)) {
                            if (CreateRegistrate.isInCreativeTab(entry, AllCreativeModeTabs.BASE_CREATIVE_TAB))
                                output.accept(entry.get());
                        }
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        REGISTER.register(modEventBus);
    }
}
