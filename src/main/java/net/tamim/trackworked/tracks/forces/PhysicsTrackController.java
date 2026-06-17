package net.tamim.trackworked.tracks.forces;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.tamim.trackworked.TrackworkUtil;
import net.tamim.trackworked.tracks.data.PhysTrackData;
import net.minecraft.core.BlockPos;
import org.joml.Math;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-sub-level controller for suspension {@code phys_track} (continuous track) blocks.
 *
 * <p>Phase D: softer suspension than a road wheel, a single combined slip force (no lateral split),
 * and a low-thrust in-water paddle. Force is applied at the block centre to keep per-block pitch
 * moments small along a long track. The owning {@code SuspensionTrackBlockEntity} implements
 * {@code BlockEntitySubLevelActor} and forwards {@code sable$physicsTick} here.</p>
 */
public final class PhysicsTrackController {
    public static final double RPM_TO_RADS = 0.10471975512;
    public static final double MAXIMUM_SLIP = 10;
    public static final double MAXIMUM_G = 98.1 * 5;
    public static final Vector3dc UP = new Vector3d(0, 1, 0);

    /** Soft track suspension spring gain. */
    private static final double SPRING_GAIN = 1.0;
    /** Track suspension damper gain (basis = stiffness). */
    private static final double DAMPER_GAIN = 0.6;
    /** Track grip gain. */
    private static final double GRIP_GAIN = 1.0;
    /** Fraction of drive thrust delivered while paddling in water. */
    private static final double WATER_THRUST = 0.2;

    private static final Map<UUID, PhysicsTrackController> INSTANCES = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, PhysTrackData> trackData2 = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PhysTrackData.PhysTrackUpdateData> trackUpdateData = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, TrackworkUtil.ClipResult> suspensionData = new ConcurrentHashMap<>();

    private volatile Vector3dc suspensionAdjust = new Vector3d(0, 1, 0);
    private volatile float suspensionStiffness = 1.0f;

    public PhysicsTrackController() {}

    public static PhysicsTrackController getOrCreate(ServerSubLevel subLevel) {
        return INSTANCES.computeIfAbsent(subLevel.getUniqueId(), $ -> new PhysicsTrackController());
    }

    /** Per-substep force application for a single track block. */
    public void physicsTick(ServerSubLevel sub, RigidBodyHandle handle, BlockPos pos, double dt) {
        PhysTrackData.PhysTrackUpdateData u = trackUpdateData.get(pos.asLong());
        if (u == null || !handle.isValid()) return;

        MassData massData = sub.getMassTracker();
        if (massData == null || massData.isInvalid()) {
            suspensionData.put(pos.asLong(), TrackworkUtil.ClipResult.MISS);
            return;
        }
        double m = massData.getMass();
        if (m <= 0) return;
        Vector3dc comLocal = massData.getCenterOfMass();
        Pose3dc pose = sub.logicalPose();

        double R = u.wheelRadius();
        double restOffset = R - 0.5;
        double susScaled = u.effectiveSuspensionTravel();
        double count = Math.max(1.0, trackUpdateData.size());
        double coP = Math.min(2.0, 14.0 / count);

        Vector3d mountLocal = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5 - restOffset, pos.getZ() + 0.5);
        Vector3d worldStart = pose.transformPosition(mountLocal, new Vector3d());
        Vector3d worldDown = pose.transformNormal(new Vector3d(0, -1, 0), new Vector3d());
        if (worldDown.lengthSquared() < 1.0e-9) return;
        worldDown.normalize();
        Vector3d worldUp = new Vector3d(worldDown).negate();

        Vector3d forwardLocal = TrackworkUtil.getForwardVec3d(u.wheelAxis(), 1f);
        Vector3d driveForwardWorld = pose.transformNormal(forwardLocal, new Vector3d());
        Vector3d applyAt = pose.transformPosition(new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), new Vector3d());

        // Low-thrust water paddle, applied whether or not the track is grounded.
        if (u.inWater() && u.trackRPM() != 0f) {
            double surf = u.trackRPM() * RPM_TO_RADS * R * WATER_THRUST;
            Vector3d thrust = new Vector3d(driveForwardWorld).mul(m * coP * surf);
            TrackPhysics.applyWorldForce(handle, pose, thrust, applyAt, dt);
        }

        double maxLen = susScaled + 0.5 + R;
        TrackworkUtil.ClipResult clip = TrackPhysics.clipGround(sub, worldStart, worldDown, maxLen, driveForwardWorld);
        suspensionData.put(pos.asLong(), clip);
        if (clip == TrackworkUtil.ClipResult.MISS || clip.suspensionLength() == null) return;

        double hitDist = clip.suspensionLength().length();
        double compression = susScaled - (hitDist - R);
        if (compression <= 0) return;
        compression = Math.min(compression, susScaled);

        Vector3d contactWorld = new Vector3d(worldStart).add(clip.suspensionLength());
        Vector3d comWorld = TrackPhysics.centerOfMassWorld(pose, comLocal, new Vector3d());
        Vector3d vAtP = TrackPhysics.velocityAtPoint(handle, comWorld, contactWorld, new Vector3d());
        if (clip.groundVelocity() != null) vAtP.sub(clip.groundVelocity());

        Vector3d tForce = new Vector3d();

        // Spring (soft).
        double springMag = m * SPRING_GAIN * coP * suspensionStiffness * compression;
        tForce.fma(springMag, worldUp);

        // Damper (basis = stiffness).
        double compressionRate = -vAtP.dot(worldUp);
        double damperMag = m * DAMPER_GAIN * coP * suspensionStiffness * compressionRate;
        tForce.fma(damperMag, worldUp);

        // Drive: combined slip, no lateral split, no gravity factor. Zero horizontal when unpowered
        // (a stopped track is free to slide sideways, matching the VS2 model).
        if (u.trackRPM() != 0f && !u.inWater()) {
            double trackSurface = u.trackRPM() * RPM_TO_RADS * R;
            Vector3dc tangent = clip.trackTangent();
            Vector3d surfaceVel = new Vector3d(vAtP);
            surfaceVel.fma(-surfaceVel.dot(worldUp), worldUp);
            Vector3d slip = new Vector3d(tangent).mul(trackSurface).sub(surfaceVel);
            TrackPhysics.clampLength(slip, MAXIMUM_SLIP);
            tForce.fma(m * GRIP_GAIN * coP, slip);
        }

        TrackPhysics.clampLength(tForce, MAXIMUM_G * m);
        TrackPhysics.logCalibration("PhysTrack", sub, m, compression, tForce, dt, handle);
        TrackPhysics.applyWorldForce(handle, pose, tForce, applyAt, dt);
    }

    public void addTrackBlock(PhysTrackData.PhysTrackCreateData data) {
        this.trackData2.put(data.blockPos().asLong(), PhysTrackData.from(data));
    }

    public double updateTrackBlock(BlockPos pos, PhysTrackData.PhysTrackUpdateData data) {
        this.trackUpdateData.put(pos.asLong(), data);
        return Math.round(this.suspensionAdjust.y() * 16) / 16. * ((9 + 1 / (this.suspensionStiffness * 2 - 1)) / 10);
    }

    public void removeTrackBlock(long id) {
        this.trackData2.remove(id);
        this.trackUpdateData.remove(id);
        this.suspensionData.remove(id);
    }

    public float setSuspensionDampening(float delta) {
        this.suspensionStiffness = Math.clamp(1f, 4.0f, this.suspensionStiffness + delta);
        return this.suspensionStiffness;
    }

    public void adjustSuspension(Vector3f delta) {
        Vector3dc old = this.suspensionAdjust;
        this.suspensionAdjust = new Vector3d(
                Math.clamp(-0.5, 0.5, old.x() + delta.x() * 5),
                Math.clamp(0.1, 1, old.y() + delta.y()),
                Math.clamp(-0.5, 0.5, old.z() + delta.z() * 5)
        );
    }

    public void resetSuspension() {
        double y = this.suspensionAdjust.y();
        this.suspensionAdjust = new Vector3d(0, y, 0);
    }

    public @Nonnull TrackworkUtil.ClipResult getSuspensionData(BlockPos pos) {
        return suspensionData.getOrDefault(pos.asLong(), TrackworkUtil.ClipResult.MISS);
    }
}
