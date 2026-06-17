package net.tamim.trackworked.client;

import net.tamim.trackworked.TrackSounds;
import net.tamim.trackworked.physics.SableShips;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;

public class HornSoundInstance extends AbstractTickableSoundInstance {
    private boolean playing;
    private int ticksLeft;
    private final int note;

    private final @Nonnull BlockPos anchorPos;

    public HornSoundInstance(int note, BlockPos pos) {
        super(TrackSounds.HORN.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.note = note;
        looping = true;
        playing = true;
        volume = 0.5f;
        delay = 0;
        this.keepAlive();
        this.anchorPos = pos;
        Vec3 center = pos.getCenter();
        x = center.x;
        y = center.y;
        z = center.z;
    }

    @Override
    public void tick() {
        // Track the host sub-level's pose so the horn follows a moving vehicle.
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Vec3 world = SableShips.toWorldCoordinates(mc.level, anchorPos.getCenter());
            x = world.x;
            y = world.y;
            z = world.z;
        }
        if (playing) {
            volume = Math.min(1, volume + .5f);
            this.ticksLeft--;
            if (ticksLeft == 0) {
                this.kill();
            }
            return;
        }
        volume = Math.max(0, volume - .5f);
        if (volume == 0) {
            stop();
        }
    }

    public void kill() {
        this.playing = false;
    }

    public void keepAlive() {
        this.ticksLeft = 2;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public int getNote() {
        return note;
    }
}
