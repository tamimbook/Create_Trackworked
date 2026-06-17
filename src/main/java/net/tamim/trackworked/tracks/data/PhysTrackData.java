package net.tamim.trackworked.tracks.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nullable;

public class PhysTrackData {
    public final Long blockPos;

    public final Direction.Axis wheelAxis;
    public final float horizontalOffset;

    public final double wheelRadius;
    public final boolean inWater;

    @Nullable
    public Vector3dc lastSuspensionForce;
    public final double effectiveSuspensionTravel;

    public final float trackRPM;

    @Deprecated
    public final Vector3dc trackOriginPosition;

    public float trackSU;

    private PhysTrackData(BlockPos trackPos) {
        this.trackOriginPosition = new Vector3d();
        this.lastSuspensionForce = new Vector3d();
        this.blockPos = trackPos.asLong();
        this.wheelAxis = Direction.Axis.X;
        this.horizontalOffset = 0;
        this.effectiveSuspensionTravel = 0;
        this.wheelRadius = 0;
        this.inWater = false;
        this.trackRPM = 0;
    }

    public PhysTrackData(Vector3dc lastSuspensionForce, BlockPos pos, Direction.Axis wheelAxis, float horizontalOffset,
                         double effectiveSuspensionTravel, double wheelRadius, boolean inWater, float trackRPM) {
        this.lastSuspensionForce = lastSuspensionForce;
        this.trackOriginPosition = new Vector3d();
        this.blockPos = pos.asLong();
        this.wheelAxis = wheelAxis;
        this.horizontalOffset = horizontalOffset;
        this.effectiveSuspensionTravel = effectiveSuspensionTravel;
        this.wheelRadius = wheelRadius;
        this.inWater = inWater;
        this.trackRPM = trackRPM;
    }

    public final PhysTrackData updateWith(PhysTrackUpdateData update) {
        return new PhysTrackData(
                this.lastSuspensionForce,
                BlockPos.of(this.blockPos),
                update.wheelAxis,
                update.horizontalOffset,
                update.effectiveSuspensionTravel,
                update.wheelRadius,
                update.inWater,
                update.trackRPM
        );
    }

    public static PhysTrackData from(PhysTrackCreateData data) {
        return new PhysTrackData(data.blockPos);
    }

    public record PhysTrackUpdateData(Direction.Axis wheelAxis, float horizontalOffset,
                                      double effectiveSuspensionTravel, double wheelRadius, boolean inWater, float trackRPM) {
    }

    public record PhysTrackCreateData(BlockPos blockPos) {}
}
