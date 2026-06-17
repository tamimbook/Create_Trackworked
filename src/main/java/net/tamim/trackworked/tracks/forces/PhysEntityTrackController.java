package net.tamim.trackworked.tracks.forces;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.tamim.trackworked.TrackworkUtil;
import net.tamim.trackworked.tracks.data.PhysEntityTrackData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Math;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-sub-level controller for the {@code phys_track} sprocket variant.
 *
 * <p>Phase D ships <b>Design A (force-only)</b>: the VS2 build drove free wheel rigid bodies through
 * revolute joints, but creating a runtime free-body anchored sub-level is not reachable through
 * Sable's public API (see {@code sable-physics-api} notes). So the sprocket instead applies its drive
 * force directly to the vehicle body at the ground contact below it — RPM in, traction out — and the
 * wheel model spins cosmetically client-side. Design B (real revolute joints) remains gated on a
 * public runtime sub-level-creation path.</p>
 */
public final class PhysEntityTrackController {
    public static final double RPM_TO_RADS = 0.10471975512;
    public static final double MAXIMUM_G = 98.1 * 5;

    /** Drive force gain (matches the VS2 {@code -slip * m * 0.4 * coP} torque model). */
    private static final double DRIVE_GAIN = 0.4;
    /** Max drive slip (rad/s-equivalent), clamped both directions. */
    private static final double MAX_DRIVE_SLIP = 3.0;
    /** Light support spring so the sprocket holds the vehicle up without a full suspension. */
    private static final double SUPPORT_SPRING = 2.0;
    private static final double SUPPORT_DAMPER = 0.8;

    private static final Map<UUID, PhysEntityTrackController> INSTANCES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, List<Long>> ignoreIdsPerVehicle = new ConcurrentHashMap<>();

    public final ConcurrentHashMap<Long, PhysEntityTrackData> trackData2 = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PhysEntityTrackData.UpdateData> trackUpdateData = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> posToJointId = new ConcurrentHashMap<>();

    public PhysEntityTrackController() {}

    public static PhysEntityTrackController getOrCreate(ServerSubLevel subLevel) {
        return INSTANCES.computeIfAbsent(subLevel.getUniqueId(), $ -> new PhysEntityTrackController());
    }

    /** Per-substep drive force for a single sprocket (Design A force-only). */
    public void physicsTick(ServerSubLevel sub, RigidBodyHandle handle, BlockPos pos, double dt) {
        PhysEntityTrackData.UpdateData u = trackUpdateData.get(pos.asLong());
        PhysEntityTrackData cd = trackData2.get(pos.asLong());
        if (u == null || cd == null || !handle.isValid()) return;

        MassData massData = sub.getMassTracker();
        if (massData == null || massData.isInvalid()) return;
        double m = massData.getMass();
        if (m <= 0) return;
        Vector3dc comLocal = massData.getCenterOfMass();
        Pose3dc pose = sub.logicalPose();

        double R = u.wheelRadius() > 0 ? u.wheelRadius() : 0.5;
        double count = Math.max(1.0, trackUpdateData.size());
        double coP = Math.min(1.0, 4.0 / count);

        Vector3d centerLocal = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vector3d worldStart = pose.transformPosition(centerLocal, new Vector3d());
        Vector3d worldDown = pose.transformNormal(new Vector3d(0, -1, 0), new Vector3d());
        if (worldDown.lengthSquared() < 1.0e-9) return;
        worldDown.normalize();
        Vector3d worldUp = new Vector3d(worldDown).negate();

        // Drive-forward is perpendicular to the axle axis, horizontal.
        Vector3dc axis = cd.wheelAxis;
        Vector3d forwardLocal = (Math.abs(axis.x()) > 0.5) ? new Vector3d(0, 0, 1) : new Vector3d(1, 0, 0);
        Vector3d driveForwardWorld = pose.transformNormal(forwardLocal, new Vector3d());

        double maxLen = R + 1.0;
        TrackworkUtil.ClipResult clip = TrackPhysics.clipGround(sub, worldStart, worldDown, maxLen, driveForwardWorld);
        if (clip == TrackworkUtil.ClipResult.MISS || clip.suspensionLength() == null) return;

        double hitDist = clip.suspensionLength().length();
        Vector3d contactWorld = new Vector3d(worldStart).add(clip.suspensionLength());
        Vector3d comWorld = TrackPhysics.centerOfMassWorld(pose, comLocal, new Vector3d());
        Vector3d vAtP = TrackPhysics.velocityAtPoint(handle, comWorld, contactWorld, new Vector3d());

        Vector3d tForce = new Vector3d();

        // Light support so the sprocket can hold the contraption up (no full suspension model here).
        double support = (R + 0.1) - hitDist;
        if (support > 0) {
            tForce.fma(m * SUPPORT_SPRING * coP * support, worldUp);
            double compressionRate = -vAtP.dot(worldUp);
            tForce.fma(m * SUPPORT_DAMPER * coP * compressionRate, worldUp);
        }

        // Drive force from RPM slip, applied along the track tangent.
        double rpm = u.trackRPM();
        if (rpm != 0) {
            double trackSurface = rpm * RPM_TO_RADS * R;
            Vector3dc tangent = clip.trackTangent();
            Vector3d surfaceVel = new Vector3d(vAtP);
            surfaceVel.fma(-surfaceVel.dot(worldUp), worldUp);
            double slip = Math.clamp(-MAX_DRIVE_SLIP, MAX_DRIVE_SLIP, trackSurface - surfaceVel.dot(tangent));
            tForce.fma(slip * m * DRIVE_GAIN * coP, tangent);
        }

        TrackPhysics.clampLength(tForce, MAXIMUM_G * m);
        TrackPhysics.logCalibration("PhysEntityTrack", sub, m, 0.0, tForce, dt, handle);
        TrackPhysics.applyWorldForce(handle, pose, tForce, contactWorld, dt);
    }

    public void addTrackBlock(BlockPos pos, PhysEntityTrackData.CreateData data, int axleId) {
        this.trackData2.put(pos.asLong(), PhysEntityTrackData.from(data));
        this.posToJointId.put(pos.asLong(), axleId);
    }

    public void updateTrackBlock(BlockPos pos, PhysEntityTrackData.UpdateData data) {
        this.trackUpdateData.put(pos.asLong(), data);
    }

    public void removeTrackBlock(ServerLevel level, BlockPos pos) {
        long key = pos.asLong();
        this.trackData2.remove(key);
        this.trackUpdateData.remove(key);
        this.posToJointId.remove(key);
    }

    public static @Nonnull List<Long> getWheelIds(long vehicleId) {
        return ignoreIdsPerVehicle.getOrDefault(vehicleId, new ArrayList<>());
    }

    /**
     * Clears any stale per-vehicle joint bookkeeping. A no-op for drive forces (Design A); kept so the
     * reset stick has a stable hook once free wheel bodies + joints exist (Design B).
     */
    public void resetController() {
        this.posToJointId.clear();
    }
}
