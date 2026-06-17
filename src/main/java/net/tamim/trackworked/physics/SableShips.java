package net.tamim.trackworked.physics;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Sable replacement for Valkyrien Skies' {@code VSGameUtilsKt} ship lookups.
 *
 * <p>VS2 modelled a vehicle as a "ship" that owned a region of shipyard space;
 * Sable instead uses a plotgrid of {@link SubLevel sub-levels} living ~30M blocks
 * out. A block that used to be "managed by a ship" is now inside the plot of a
 * {@link ServerSubLevel}, reachable via the level's {@link SubLevelContainer}.</p>
 */
public final class SableShips {
    private SableShips() {}

    /**
     * @return the server sub-level whose plot contains {@code pos}, or {@code null}
     *         if the position is in the ordinary world (not assembled onto a sub-level).
     */
    public static @Nullable ServerSubLevel getSubLevelManagingPos(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null || !container.inBounds(pos)) return null;
        LevelPlot plot = container.getPlot(new ChunkPos(pos));
        if (plot == null) return null;
        SubLevel sub = plot.getSubLevel();
        return (sub instanceof ServerSubLevel ssl) ? ssl : null;
    }

    /**
     * Transform a sub-level-local position into world coordinates using the sub-level's pose, so
     * positional audio / particles on a moving vehicle track the vehicle. Works on both the client
     * ({@link net.minecraft.client.multiplayer.ClientLevel}) and server via the generic
     * {@link SubLevelContainer#getContainer(Level)} lookup. Returns {@code localPos} unchanged when
     * the position is not inside any sub-level.
     */
    public static Vec3 toWorldCoordinates(Level level, Vec3 localPos) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return localPos;
        BlockPos bp = BlockPos.containing(localPos);
        if (!container.inBounds(bp)) return localPos;
        LevelPlot plot = container.getPlot(new ChunkPos(bp));
        if (plot == null) return localPos;
        SubLevel sub = plot.getSubLevel();
        if (sub == null) return localPos;
        return sub.logicalPose().transformPosition(localPos);
    }
}
