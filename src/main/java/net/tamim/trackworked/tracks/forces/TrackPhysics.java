package net.tamim.trackworked.tracks.forces;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.tamim.trackworked.TrackworkUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared Phase-D physics helpers for the per-sub-level wheel/track controllers.
 *
 * <p>Sable hands each {@code BlockEntitySubLevelActor} a {@link RigidBodyHandle} every physics
 * substep. Force math is done in WORLD space (the VS2 model was world-space), then converted to
 * the body-local frame for {@link RigidBodyHandle#applyImpulseAtPoint} — whose arguments are
 * impulses (force x timeStep) in sub-level-local coordinates.</p>
 *
 * <p>NOTE: body linear/angular velocities are treated as world-frame here. For a ground vehicle
 * that stays roughly upright the sub-level orientation is near-identity, so the world/local
 * distinction is negligible; the M1 calibration probe should confirm the convention before any
 * heavy-tilt cases are trusted.</p>
 */
public final class TrackPhysics {
    private TrackPhysics() {}

    public static final Vector3dc WORLD_UP = new Vector3d(0, 1, 0);

    /**
     * One-shot calibration logger (M1 probe). Enabled via {@code -Dtrackworked.debugPhysics=true}
     * (set in the {@code client} run config). Throttled to one line/second across all systems so a
     * playtest directly shows whether our applied impulses are the right magnitude vs. gravity:
     * a vehicle holds its ride height when the summed upward impulse ≈ {@code mass·g·dt} per substep,
     * i.e. the printed {@code ratio} (per-wheel impulse / gravity-impulse) is sane (~0.2–1 per wheel).
     */
    public static volatile boolean LOG_CALIBRATION = Boolean.getBoolean("trackworked.debugPhysics");
    private static final Logger CALIB_LOG = LoggerFactory.getLogger("trackworked/physics");
    private static volatile long lastLogTick = Long.MIN_VALUE;

    public static void logCalibration(String system, ServerSubLevel sub, double mass, double compression,
                                      Vector3dc worldForce, double dt, RigidBodyHandle handle) {
        if (!LOG_CALIBRATION) return;
        long t = sub.getLevel().getGameTime();
        if (t == lastLogTick || (t % 20L) != 0L) return;
        lastLogTick = t;
        Vector3d v = handle.getLinearVelocity(new Vector3d());
        double forceMag = worldForce.length();
        double impulse = forceMag * dt;
        double gravityImpulse = mass * 9.81 * dt;
        CALIB_LOG.info("[{}] mass={} comp={} |F|={} impulse={} grav/step={} ratio={} vLin=({},{},{}) dt={}",
                system, r(mass), r(compression), r(forceMag), r(impulse), r(gravityImpulse),
                r(gravityImpulse == 0 ? 0 : impulse / gravityImpulse), r(v.x), r(v.y), r(v.z), r(dt));
    }

    private static String r(double d) {
        return String.format("%.4f", d);
    }

    /**
     * Cast a ray from {@code worldStart} along {@code worldDownUnit} up to {@code maxLen}, ignoring
     * the wheel's own sub-level so a contraption never clips itself. Returns {@link TrackworkUtil.ClipResult#MISS}
     * when nothing is hit.
     */
    public static TrackworkUtil.ClipResult clipGround(ServerSubLevel sub, Vector3dc worldStart, Vector3dc worldDownUnit,
                                                      double maxLen, Vector3dc driveForwardWorld) {
        Level level = sub.getLevel();
        Vec3 from = new Vec3(worldStart.x(), worldStart.y(), worldStart.z());
        Vec3 to = from.add(worldDownUnit.x() * maxLen, worldDownUnit.y() * maxLen, worldDownUnit.z() * maxLen);
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
        // Sable @Overwrites BlockGetter.clip; this makes the ray skip the contraption it belongs to.
        ((ClipContextExtension) ctx).sable$setIgnoredSubLevel(sub);
        BlockHitResult hit = level.clip(ctx);
        if (hit.getType() == HitResult.Type.MISS) return TrackworkUtil.ClipResult.MISS;

        Vec3 loc = hit.getLocation();
        Vector3d suspensionLength = new Vector3d(loc.x - from.x, loc.y - from.y, loc.z - from.z);

        // Drive tangent: forward direction projected onto the horizontal ground plane.
        Vector3d tangent = new Vector3d(driveForwardWorld);
        double vdot = tangent.dot(WORLD_UP);
        tangent.sub(WORLD_UP.x() * vdot, WORLD_UP.y() * vdot, WORLD_UP.z() * vdot);
        if (tangent.lengthSquared() > 1.0e-6) tangent.normalize();
        else tangent.set(driveForwardWorld);

        // Static terrain has zero velocity. TODO(Phase D+): resolve hit sub-level velocity for
        // vehicle-on-vehicle contact (Sable.HELPER.getVelocity at the contact point).
        return new TrackworkUtil.ClipResult(tangent, suspensionLength, new Vector3d());
    }

    /** Velocity of the body at a world point: {@code linVel + angVel x (point - comWorld)}. */
    public static Vector3d velocityAtPoint(RigidBodyHandle handle, Vector3dc comWorld, Vector3dc worldPoint, Vector3d dest) {
        Vector3d lin = handle.getLinearVelocity(new Vector3d());
        Vector3d ang = handle.getAngularVelocity(new Vector3d());
        Vector3d r = new Vector3d(worldPoint).sub(comWorld);
        ang.cross(r, dest);
        return dest.add(lin);
    }

    /** World center-of-mass of the sub-level (falls back to the pose origin when COM is unavailable). */
    public static Vector3d centerOfMassWorld(Pose3dc pose, Vector3dc comLocal, Vector3d dest) {
        if (comLocal == null) return dest.set(pose.position());
        return pose.transformPosition(dest.set(comLocal), dest);
    }

    /** Apply a world-space force at a world point as an impulse (force x dt) in the body-local frame. */
    public static void applyWorldForce(RigidBodyHandle handle, Pose3dc pose, Vector3dc worldForce, Vector3dc worldPoint, double dt) {
        if (!finite(worldForce) || !finite(worldPoint)) return;
        Vector3d localImpulse = pose.transformNormalInverse(new Vector3d(worldForce), new Vector3d()).mul(dt);
        Vector3d localPoint = pose.transformPositionInverse(new Vector3d(worldPoint), new Vector3d());
        handle.applyImpulseAtPoint(localPoint, localImpulse);
    }

    /** Clamp a vector's length to {@code max} in place. */
    public static Vector3d clampLength(Vector3d v, double max) {
        double l = v.length();
        if (l > max && l > 1.0e-9) v.mul(max / l);
        return v;
    }

    public static boolean finite(Vector3dc v) {
        return Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z());
    }
}
