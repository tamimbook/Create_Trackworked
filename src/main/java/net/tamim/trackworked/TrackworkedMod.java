package net.tamim.trackworked;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import static net.createmod.catnip.lang.FontHelper.Palette.STANDARD_CREATE;

/**
 * Main entry point for Create: Trackworked.
 *
 * <p>NeoForge 1.21.1 port of Trackwork, rebuilt on the Sable physics engine
 * (formerly Valkyrien Skies 2). This class is the merged successor of the old
 * Forge {@code TrackworkMod} entry point.</p>
 */
@Mod(TrackworkedMod.MODID)
public class TrackworkedMod {
    public static final String MODID = "trackworked";
    /** Legacy alias kept so existing source referencing {@code MOD_ID} continues to resolve. */
    public static final String MOD_ID = MODID;

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID);

    static {
        REGISTRATE.setTooltipModifierFactory(item ->
                new ItemDescription.Modifier(item, STANDARD_CREATE));
    }

    // FML passes IEventBus and ModContainer to the constructor automatically.
    public TrackworkedMod(IEventBus modEventBus, ModContainer modContainer) {
        REGISTRATE.registerEventListeners(modEventBus);

        // TODO(Phase D - Sable physics): re-implement controller persistence.
        // The old Forge build registered VS2 ship attachments and subscribed to the
        // ship-load event here. Sable has no attachment/serialization system, so this
        // state moves to BlockEntity NBT / SavedData during the physics phase.

        TrackworkConfigs.register(modContainer);
        TrackSounds.register(modEventBus);
        TrackCreativeTabs.register(modEventBus);
        TrackworkItems.register();
        TrackBlocks.register();
        TrackBlockEntityTypes.register();
        TrackEntityTypes.register();
        TrackPackets.register();

        modEventBus.addListener(EventPriority.LOWEST, TrackDatagen::gatherData);
    }

    public static void warn(String format, Object arg) {
        LOGGER.warn(format, arg);
    }

    public static void warn(String format, Object... args) {
        LOGGER.warn(format, args);
    }

    public static ResourceLocation getResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
