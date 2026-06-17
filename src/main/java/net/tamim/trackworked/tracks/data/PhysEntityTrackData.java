package net.tamim.trackworked.tracks.data;

import org.joml.Vector3dc;

import javax.annotation.Nonnull;

public class PhysEntityTrackData {
    public final Vector3dc trackPos;
    public final Vector3dc wheelAxis;
    public final long shiptraptionID;
    public final double springConstant;
    public final double damperConstant;
    // TODO(Phase D): was a VS2 VSRevoluteJoint; replace with the Sable joint/constraint handle.
    public final Object constraint;
    public volatile Integer axleId;
    public final double trackRPM;
    public float trackSU;
    public double previousSpringDist;

    private PhysEntityTrackData(Vector3dc trackPos, Vector3dc wheelAxis, long shiptraptionID, double springConstant, double damperConstant, Object constraint, int axleId, double trackRPM, double springDist) {
        this.trackPos = trackPos;
        this.wheelAxis = wheelAxis;
        this.springConstant = springConstant;
        this.damperConstant = damperConstant;
        this.constraint = constraint;
        this.shiptraptionID = shiptraptionID;
        this.axleId = axleId;
        this.trackRPM = trackRPM;
        this.previousSpringDist = springDist;
    }

    public final PhysEntityTrackData updateWith(@Nonnull UpdateData update) {
        return new PhysEntityTrackData(this.trackPos, this.wheelAxis, update.shiptraptionID, update.springConstant, update.damperConstant, this.constraint, axleId, update.trackRPM, this.previousSpringDist);
    }

    public static PhysEntityTrackData from(@Nonnull CreateData data) {
        return new PhysEntityTrackData(data.trackPos, data.wheelAxis, data.shiptraptionID, data.springConstant, data.damperConstant, data.constraint, -1, data.trackRPM, 0);
    }

    public record UpdateData(double springConstant, double damperConstant, double trackRPM, long shiptraptionID, double wheelRadius) {
    }

    public record CreateData(Vector3dc trackPos, Vector3dc wheelAxis, long shiptraptionID, double springConstant, double damperConstant, Object constraint, double trackRPM) {
    }
}
