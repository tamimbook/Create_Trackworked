package net.tamim.trackworked.tracks.forces;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.tamim.trackworked.TrackworkUtil;
import net.tamim.trackworked.tracks.data.OleoWheelData;
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
 * Per-sub-level controller for {@code oleo_wheel} (landing gear) blocks.
 *
 * <p>Phase D: a stiff oleo-pneumatic strut — firm spring, asymmetric damper (firm in compression,
 * soft on rebound), steering, and lateral grip. Undriven (free-rolling), so it absorbs landings and
 * resists sideways slide rather than propelling. Stowed gear (suspension scale 0) applies no force.
 * Each wheel applies its impulse immediately (no shared 5g net-clamp), matching the VS2 model.</p>
 */
public final class OleoWheelController {
    public static final double RPM_TO_RADS = 0.10471975512;
    public static final double MAXIMUM_SLIP = 10;
    public static final double MAXIMUM_SLIP_LATERAL = MAXIMUM_SLIP * 1.5;
    public static final double MAX_FREESPIN_SLIP = 0.07;
    public static final double MAXIMUM_G = 98.1 * 5;
    public static final Vector3dc UP = new Vector3d(0, 1, 0);

    /** Stiff oleo spring gain. */
    private static final double SPRING_GAIN = 4.0;
    /** Oleo damper gain. */
    private static final double DAMPER_GAIN = 1.0;
    /** Rebound damping is softer than compression. */
    private static final double REBOUND_FACTOR = 0.5;
    private static final double GRIP_GAIN = 1.0;

    private static final Map<UUID, OleoWheelController> INSTANCES = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, OleoWheelData> wheelUpdateData = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, TrackworkUtil.ClipResult> suspensionData = new ConcurrentHashMap<>();

    private volatile Vector3dc suspensionAdjust = new Vector3d(0, 1, 0);
    private volatile float suspensionStiffness = 1.0f;
    private volatile float suspensionDampening = 1.2f;

    public OleoWheelController() {}

    public static OleoWheelController getOrCreate(ServerSubLevel subLevel) {
        return INSTANCES.computeIfAbsent(subLevel.getUniqueId(), $ -> new OleoWheelController());
    }

    /** Per-substep oleo-strut force application for a single landing-gear wheel. */
    public void physicsTick(ServerSubLevel sub, RigidBodyHandle handle, BlockPos pos, double dt) {
        OleoWheelData u = wheelUpdateData.get(pos.asLong());
        if (u == null || !handle.isValid()) return;
        if (u.susScaled == 0) { // stowed
            suspensionData.put(pos.asLong(), TrackworkUtil.ClipResult.MISS);
            return;
        }

        MassData massData = sub.getMassTracker();
        if (massData == null || massData.isInvalid()) {
            suspensionData.put(pos.asLong(), TrackworkUtil.ClipResult.MISS);
            return;
        }
        double m = massData.getMass();
        if (m <= 0) return;
        Vector3dc comLocal = massData.getCenterOfMass();
        Pose3dc pose = sub.logicalPose();

        double R = u.wheelRadius;
        double restOffset = R - 0.5;
        double susScaled = u.susScaled;
        double count = Math.max(1.0, wheelUpdateData.size());
        double coP = Math.min(2.0, 3.0 / count);

        Vector3d mountLocal = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5 - restOffset, pos.getZ() + 0.5);
        Vector3d worldStart = pose.transformPosition(mountLocal, new Vector3d());
        Vector3d worldDown = pose.transformNormal(new Vector3d(0, -1, 0), new Vector3d());
        if (worldDown.lengthSquared() < 1.0e-9) return;
        worldDown.normalize();
        Vector3d worldUp = new Vector3d(worldDown).negate();

        Vector3d forwardLocal = TrackworkUtil.getForwardVec3d(u.wheelAxis, 1f)
                .rotateAxis(u.steeringValue * Math.toRadians(30.0), 0, 1, 0);
        Vector3d driveForwardWorld = pose.transformNormal(forwardLocal, new Vector3d());

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

        double gravityFactor = Math.max(0.3, worldUp.dot(UP));
        Vector3d tForce = new Vector3d();

        // Stiff spring.
        double springMag = m * SPRING_GAIN * coP * suspensionStiffness * compression;
        tForce.fma(springMag, worldUp);

        // Asymmetric oleo damper: firm in compression, soft on rebound.
        double compressionRate = -vAtP.dot(worldUp);
        double damperMag = m * DAMPER_GAIN * coP * suspensionDampening * compressionRate;
        if (compressionRate < 0) damperMag *= REBOUND_FACTOR;
        tForce.fma(damperMag, worldUp);

        // Drive + grip (undriven gear -> mostly lateral grip).
        double trackSurface = u.wheelRPM * RPM_TO_RADS * R;
        Vector3dc tangent = clip.trackTangent();
        Vector3d surfaceVel = new Vector3d(vAtP);
        surfaceVel.fma(-surfaceVel.dot(worldUp), worldUp);
        Vector3d slip = new Vector3d(tangent).mul(trackSurface).sub(surfaceVel);
        double longMag = slip.dot(tangent);
        Vector3d longSlip = new Vector3d(tangent).mul(longMag);
        Vector3d latSlip = new Vector3d(slip).sub(longSlip);
        TrackPhysics.clampLength(longSlip, u.isFreespin ? MAX_FREESPIN_SLIP : MAXIMUM_SLIP);
        TrackPhysics.clampLength(latSlip, MAXIMUM_SLIP_LATERAL);
        Vector3d gripSlip = (u.wheelRPM == 0f && u.isFreespin) ? latSlip : new Vector3d(longSlip).add(latSlip);
        tForce.fma(m * GRIP_GAIN * coP * gravityFactor, gripSlip);

        // No shared net-clamp: apply this wheel's force immediately.
        TrackPhysics.logCalibration("OleoWheel", sub, m, compression, tForce, dt, handle);
        TrackPhysics.applyWorldForce(handle, pose, tForce, contactWorld, dt);
    }

    public double updateTrackBlock(BlockPos pos, OleoWheelData data) {
        this.wheelUpdateData.put(pos.asLong(), data);
        return Math.round(this.suspensionAdjust.y() * 16) / 16. * ((9 + 1 / (this.suspensionStiffness * 2 - 1)) / 10);
    }

    public void removeTrackBlock(BlockPos pos) {
        this.wheelUpdateData.remove(pos.asLong());
        this.suspensionData.remove(pos.asLong());
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

    public @Nonnull TrackworkUtil.ClipResult getSuspensionData(BlockPos pos) {
        return suspensionData.getOrDefault(pos.asLong(), TrackworkUtil.ClipResult.MISS);
    }
}
