package net.tamim.trackworked.tracks.forces;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.tamim.trackworked.TrackworkUtil;
import net.tamim.trackworked.tracks.data.SimpleWheelData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Math;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-sub-level controller for {@code simple_wheel} blocks.
 *
 * <p>Phase D: real suspension (spring + damper) and drive/grip forces applied to the Sable rigid
 * body each physics substep. The owning {@link net.tamim.trackworked.tracks.blocks.WheelBlockEntity}
 * implements {@code BlockEntitySubLevelActor} and forwards Sable's {@code sable$physicsTick} here,
 * keyed by the block position. Ground contact uses vanilla {@code Level.clip} (Sable overwrites it
 * to project into sub-levels); see {@link TrackPhysics}.</p>
 */
public final class SimpleWheelController {
    public static final double RPM_TO_RADS = 0.10471975512;
    public static final double MAXIMUM_SLIP = 10;
    public static final double MAXIMUM_SLIP_LATERAL = MAXIMUM_SLIP * 1.5;
    public static final double MAX_FREESPIN_SLIP = 0.07;
    public static final double MAXIMUM_G = 98.1 * 5;
    public static final Vector3dc UP = new Vector3d(0, 1, 0);

    /** Suspension spring gain (stiff, like a road wheel). */
    private static final double SPRING_GAIN = 4.0;
    /** Suspension damper gain. */
    private static final double DAMPER_GAIN = 1.0;
    /** Longitudinal/lateral grip gain. */
    private static final double GRIP_GAIN = 1.0;

    private static final Map<UUID, SimpleWheelController> INSTANCES = new ConcurrentHashMap<>();

    /** Live per-block create state (origin), keyed by packed BlockPos. */
    private final ConcurrentHashMap<Long, SimpleWheelData> trackData = new ConcurrentHashMap<>();
    /** Latest per-tick update from the block entity (RPM, steering, suspension scale, ...). */
    private final ConcurrentHashMap<Long, SimpleWheelData.SimpleWheelUpdateData> trackUpdateData = new ConcurrentHashMap<>();
    /** Ground-probe read-back for renderers (suspension compression). */
    private final ConcurrentHashMap<Long, TrackworkUtil.ClipResult> suspensionData = new ConcurrentHashMap<>();

    private volatile Vector3dc suspensionAdjust = new Vector3d(0, 1, 0);
    private volatile float suspensionStiffness = 1.0f;
    private volatile float suspensionDampening = 1.2f;

    public SimpleWheelController() {}

    public static SimpleWheelController getOrCreate(ServerSubLevel subLevel) {
        return INSTANCES.computeIfAbsent(subLevel.getUniqueId(), $ -> new SimpleWheelController());
    }

    /**
     * Per-substep force application for a single wheel block. Clips to the ground, then applies the
     * suspension spring/damper plus the drive/grip slip force as an impulse at the contact point.
     */
    public void physicsTick(ServerSubLevel sub, RigidBodyHandle handle, BlockPos pos, double dt) {
        SimpleWheelData.SimpleWheelUpdateData u = trackUpdateData.get(pos.asLong());
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
        double susScaled = u.susScaled();
        double count = Math.max(1.0, trackUpdateData.size());
        double coP = Math.min(2.0, 3.0 / count);

        // Mount point (local) = block centre shifted down by restOffset, projected to world.
        Vector3d mountLocal = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5 - restOffset, pos.getZ() + 0.5);
        Vector3d worldStart = pose.transformPosition(mountLocal, new Vector3d());
        Vector3d worldDown = pose.transformNormal(new Vector3d(0, -1, 0), new Vector3d());
        if (worldDown.lengthSquared() < 1.0e-9) return;
        worldDown.normalize();
        Vector3d worldUp = new Vector3d(worldDown).negate();

        // Steered drive-forward direction in world space.
        Vector3d forwardLocal = TrackworkUtil.getForwardVec3d(u.wheelAxis(), 1f)
                .rotateAxis(u.steeringValue() * Math.toRadians(30.0), 0, 1, 0);
        Vector3d driveForwardWorld = pose.transformNormal(forwardLocal, new Vector3d());

        double maxLen = susScaled + 0.5 + R;
        TrackworkUtil.ClipResult clip = TrackPhysics.clipGround(sub, worldStart, worldDown, maxLen, driveForwardWorld);
        suspensionData.put(pos.asLong(), clip);
        if (clip == TrackworkUtil.ClipResult.MISS || clip.suspensionLength() == null) return;

        double hitDist = clip.suspensionLength().length();
        // Wheel of radius R touches ground when its extension == hitDist - R; compression is how far
        // past rest extension (susScaled) that contact pushes the strut. Zero at first contact.
        double compression = susScaled - (hitDist - R);
        if (compression <= 0) return; // ground out of suspension reach
        compression = Math.min(compression, susScaled);

        Vector3d contactWorld = new Vector3d(worldStart).add(clip.suspensionLength());
        Vector3d comWorld = TrackPhysics.centerOfMassWorld(pose, comLocal, new Vector3d());
        Vector3d vAtP = TrackPhysics.velocityAtPoint(handle, comWorld, contactWorld, new Vector3d());
        if (clip.groundVelocity() != null) vAtP.sub(clip.groundVelocity());

        double tilt = 1.0
                + Math.signum(contactWorld.x() - comWorld.x()) * suspensionAdjust.z()
                + Math.signum(contactWorld.z() - comWorld.z()) * suspensionAdjust.x();
        double gravityFactor = Math.max(0.3, worldUp.dot(UP));

        Vector3d tForce = new Vector3d();

        // Spring (along the suspension axis / world up).
        double springMag = m * SPRING_GAIN * coP * suspensionStiffness * tilt * compression;
        tForce.fma(springMag, worldUp);

        // Damper opposes the compression velocity at the contact.
        double compressionRate = -vAtP.dot(worldUp);
        double damperMag = m * DAMPER_GAIN * coP * suspensionDampening * compressionRate;
        tForce.fma(damperMag, worldUp);

        // Drive + grip: target tangential surface speed vs. actual surface velocity -> slip force.
        double trackSurface = u.trackRPM() * RPM_TO_RADS * R;
        Vector3dc tangent = clip.trackTangent();
        Vector3d surfaceVel = new Vector3d(vAtP);
        surfaceVel.fma(-surfaceVel.dot(worldUp), worldUp); // strip normal component
        Vector3d slip = new Vector3d(tangent).mul(trackSurface).sub(surfaceVel);
        double longMag = slip.dot(tangent);
        Vector3d longSlip = new Vector3d(tangent).mul(longMag);
        Vector3d latSlip = new Vector3d(slip).sub(longSlip);
        TrackPhysics.clampLength(longSlip, u.isFreespin() ? MAX_FREESPIN_SLIP : MAXIMUM_SLIP);
        TrackPhysics.clampLength(latSlip, MAXIMUM_SLIP_LATERAL);
        Vector3d gripSlip = (u.trackRPM() == 0f && u.isFreespin()) ? latSlip : new Vector3d(longSlip).add(latSlip);
        double gripMag = m * GRIP_GAIN * coP * gravityFactor;
        tForce.fma(gripMag, gripSlip);

        // Net-force ceiling (~5g).
        TrackPhysics.clampLength(tForce, MAXIMUM_G * m);

        TrackPhysics.logCalibration("SimpleWheel", sub, m, compression, tForce, dt, handle);
        TrackPhysics.applyWorldForce(handle, pose, tForce, contactWorld, dt);
    }

    public void addTrackBlock(BlockPos pos, SimpleWheelData.SimpleWheelCreateData data) {
        this.trackData.put(pos.asLong(), SimpleWheelData.from(data));
    }

    public double updateTrackBlock(BlockPos pos, SimpleWheelData.SimpleWheelUpdateData data) {
        this.trackUpdateData.put(pos.asLong(), data);
        return Math.round(this.suspensionAdjust.y() * 16) / 16. * ((9 + 1 / (this.suspensionStiffness * 2 - 1)) / 10);
    }

    public void removeTrackBlock(BlockPos pos) {
        long key = pos.asLong();
        this.trackData.remove(key);
        this.trackUpdateData.remove(key);
        this.suspensionData.remove(key);
    }

    public float setDamperCoefficient(float delta) {
        this.suspensionStiffness = Math.clamp(1.0f, 4.0f, this.suspensionStiffness + delta);
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

    public Vector3d getActionVec3d(Direction.Axis axis, float length, float steeringValue) {
        return TrackworkUtil.getForwardVec3d(axis, length).rotateAxis(steeringValue * Math.toRadians(30), 0, 1, 0);
    }

    public @Nonnull TrackworkUtil.ClipResult getSuspensionData(BlockPos pos) {
        return suspensionData.getOrDefault(pos.asLong(), TrackworkUtil.ClipResult.MISS);
    }
}
