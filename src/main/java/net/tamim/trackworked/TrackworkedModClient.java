package net.tamim.trackworked;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import net.tamim.trackworked.client.TrackworkPartialModels;
import net.tamim.trackworked.client.TrackworkSpriteShifts;

/**
 * Client-only entry point. Not loaded on dedicated servers, so client-only
 * Create/Flywheel/Ponder code is safe to touch from here.
 */
@Mod(value = TrackworkedMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = TrackworkedMod.MODID, value = Dist.CLIENT)
public class TrackworkedModClient {
    public TrackworkedModClient(ModContainer container) {
        // NeoForge in-game config screen (Mods > Trackworked > Config).
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        TrackworkPartialModels.init();
        TrackworkSpriteShifts.init();
        TrackPonderPlugin.registerPlugin();
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Client-side setup hook (kept for future renderer/keybind registration).
    }
}
