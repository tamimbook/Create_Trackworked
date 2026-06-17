package net.tamim.trackworked.client;

import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import dev.engine_room.flywheel.api.backend.BackendManager;
import net.tamim.trackworked.TrackworkItems;
import net.tamim.trackworked.TrackworkedMod;
import net.tamim.trackworked.items.TrackToolkitRenderer;
import net.tamim.trackworked.sounds.TrackSoundScapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = TrackworkedMod.MODID, value = Dist.CLIENT)
public class ClientEvents {
    public static final ClientResourceReloadListener RESOURCE_RELOAD_LISTENER = new ClientResourceReloadListener();

    // NeoForge split the old TickEvent into Pre/Post; the ambient soundscape used to tick at END phase.
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!BackendManager.isBackendOn())
            return;

        TrackSoundScapes.tick();
    }

    @EventBusSubscriber(modid = TrackworkedMod.MODID, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(RESOURCE_RELOAD_LISTENER);
        }

        // 1.21.1: Item.initializeClient was removed; client item renderers register here instead.
        @SubscribeEvent
        public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
            event.registerItem(
                    SimpleCustomRenderer.create(TrackworkItems.TRACK_TOOL_KIT.get(), new TrackToolkitRenderer()),
                    TrackworkItems.TRACK_TOOL_KIT.get());
        }
    }
}
