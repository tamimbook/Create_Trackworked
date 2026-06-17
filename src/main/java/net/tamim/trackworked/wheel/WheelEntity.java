package net.tamim.trackworked.wheel;

import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3dc;

/**
 * Free physics-body "wheel" wrapper — the optional <b>Design B</b> upgrade for {@code phys_track}.
 *
 * <p>In the Valkyrien Skies build this wrapped a standalone {@code PhysicsEntity} — a rigid sphere
 * joined to the host ship by a revolute joint and spun by the {@code phys_track} sprocket. The
 * shipped Phase D build uses <b>Design A</b> instead: the sprocket applies its drive force to the
 * vehicle body directly (see {@code PhysEntityTrackController}), so these methods stay inert.</p>
 *
 * <p>Design B <em>is</em> reachable on Sable (the recon's "no joint API" note was wrong): a wheel can
 * be its own {@code ServerSubLevel} via {@code SubLevelContainer.allocateNewSubLevel(Pose3d)}, joined
 * with {@code pipeline.addConstraint(vehicle, wheel, new RotaryConstraintConfiguration(...))} and
 * driven by {@code PhysicsConstraintHandle.setMotor(ConstraintJointAxis.ANGULAR_X, ...)}. It is left
 * unimplemented only because the sub-level lifecycle (allocate, place the wheel block, register with
 * physics, remove, reattach on reload) must be validated in a live client. See {@code [[sable-physics-api]]}.</p>
 */
public final class WheelEntity {
    private WheelEntity() {}

    /** @return whether a wheel body with this id currently exists. Always {@code false} under Design A. */
    public static boolean aliveInLevel(ServerLevel level, long id) {
        return false;
    }

    /** Design B: allocate the wheel sub-level + revolute joint. No-op under the shipped Design A. */
    public static void createInLevel(ServerLevel level, long id, Vector3dc pos, double radius, double mass) {
        // no-op under Design A (force-only drive)
    }

    /** Design B: despawn the wheel sub-level + remove its joint. No-op under Design A. */
    public static void removeInLevel(ServerLevel level, long id) {
        // no-op under Design A
    }

    /** Design B: teleport the wheel body to {@code pos}. @return {@code false} under Design A. */
    public static boolean moveTo(ServerLevel level, long id, Vector3dc pos) {
        return false;
    }
}
