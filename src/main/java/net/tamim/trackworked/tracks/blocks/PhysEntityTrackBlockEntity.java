package net.tamim.trackworked.tracks.blocks;

import net.tamim.trackworked.*;
import net.tamim.trackworked.physics.SableShips;
import net.tamim.trackworked.sounds.TrackSoundScapes;
import net.tamim.trackworked.tracks.ITrackPointProvider;
import net.tamim.trackworked.tracks.data.PhysEntityTrackData;
import net.tamim.trackworked.tracks.forces.PhysEntityTrackController;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;
import org.joml.Vector3dc;

import java.util.List;
import java.util.function.Supplier;

import static com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS;
import static net.tamim.trackworked.tracks.blocks.TrackBaseBlock.*;
import static net.minecraft.ChatFormatting.GRAY;

public class PhysEntityTrackBlockEntity extends TrackBaseBlockEntity implements ITrackPointProvider, BlockEntitySubLevelActor {
    private float wheelRadius;
    /** Server sub-level whose plot contains this block, or {@code null} when in the ordinary world. */
    protected final Supplier<ServerSubLevel> subLevel;
    @Deprecated(forRemoval = true)
    private Integer trackID;
    // TODO(Phase D): id of the free wheel body once Sable physics entities exist. Persisted for compat.
    @Nullable
    private Long wheelId;
    private boolean assembled;
    public boolean assembleNextTick = true;
    MutableComponent chatMessage = Component.empty();

    public PhysEntityTrackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.assembled = false;
        this.wheelRadius = 0.5f;
        this.subLevel = () -> SableShips.getSubLevelManagingPos(this.level, pos);
        this.wheelId = null;
    }

    public static PhysEntityTrackBlockEntity large(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        PhysEntityTrackBlockEntity be = new PhysEntityTrackBlockEntity(type, pos, state);
        be.wheelRadius = 1.0f;
        return be;
    }

    public static PhysEntityTrackBlockEntity med(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        PhysEntityTrackBlockEntity be = new PhysEntityTrackBlockEntity(type, pos, state);
        be.wheelRadius = 0.75f;
        return be;
    }

    @Override
    public void destroy() {
        super.destroy();

        if (this.level != null && !this.level.isClientSide && this.assembled) {
            ServerSubLevel sub = this.subLevel.get();
            if (sub != null) {
                PhysEntityTrackController controller = PhysEntityTrackController.getOrCreate(sub);
                controller.removeTrackBlock((ServerLevel) this.level, this.getBlockPos());
                this.cleanupWheel();
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (!this.level.isClientSide && this.assembled) {
            // TODO(Phase D): reattach the free wheel body + revolute joint. For now re-register the
            // track point with a fresh controller on next tick.
            this.assembled = false;
            this.assembleNextTick = true;
        }
    }

    private void assemble() {
        if (this.level != null && !this.level.isClientSide) {
            if (!isValidAxis(this.getBlockState().getValue(AXIS))) return;
            ServerSubLevel sub = this.subLevel.get();
            if (sub != null) {
                PhysEntityTrackController controller = PhysEntityTrackController.getOrCreate(sub);
                if (this.getAssembled()) {
                    this.disassemble();
                    controller.removeTrackBlock((ServerLevel) this.level, this.getBlockPos());
                }
                this.assembled = true;
                Vector3dc trackLocalPos = JOMLConversion.atCenterOf(this.getBlockPos());
                Vector3dc axis = TrackworkUtil.getAxisAsVec(this.getBlockState().getValue(AXIS));

                // Design A (shipped): the sprocket is a powered drive point — PhysEntityTrackController
                // applies its drive force to the vehicle body each physics substep (see sable$physicsTick).
                // Design B (free wheel = own ServerSubLevel + revolute joint) is API-feasible on Sable
                // (SubLevelContainer.allocateNewSubLevel + pipeline.addConstraint(vehicle, wheel,
                // new RotaryConstraintConfiguration(hubVehicle, hubWheel, axis, axis)) + handle.setMotor(
                // ConstraintJointAxis.ANGULAR_X, -rpm, 0, damping, true, maxTorque)). It is deferred only
                // because the sub-level lifecycle (allocate, place the wheel block, remove, reattach) needs
                // validation in a live client, not because the API is missing.
                PhysEntityTrackData.CreateData trackData = new PhysEntityTrackData.CreateData(
                        trackLocalPos,
                        axis,
                        0L,
                        0,
                        0,
                        null,
                        this.getSpeed()
                );
                controller.addTrackBlock(this.getBlockPos(), trackData, -1);
                this.sendData();
            }
        }
    }

    public void disassemble() {
        this.assembled = false;
        cleanupWheel();
    }

    @Override
    public void tick() {
        super.tick();

        // For backwards compatibility
        if (this.trackID != null) {
            this.assembled = false;
            this.trackID = null;
        }

        if (this.subLevel.get() != null && !this.assembled && this.level != null) {
            if (this.assembleNextTick) {
                this.assemble();
                this.assembleNextTick = false;
            } else {
                this.assembleNextTick = true;
            }
            return;
        }

        if (this.level == null) {
            return;
        }
        if (this.assembled && !this.level.isClientSide) {
            ServerSubLevel sub = this.subLevel.get();
            if (sub != null) {
                PhysEntityTrackController controller = PhysEntityTrackController.getOrCreate(sub);
                PhysEntityTrackData.UpdateData data = new PhysEntityTrackData.UpdateData(
                        0,
                        0,
                        this.getSpeed(),
                        0L,
                        this.wheelRadius
                );
                controller.updateTrackBlock(this.getBlockPos(), data);

                // Entity pushing, no damage here
                // TODO(Phase D): push relative to the sub-level pose. Stubbed in world space.
                List<LivingEntity> hits = this.level.getEntitiesOfClass(LivingEntity.class, new AABB(this.getBlockPos())
                        .deflate(0.25)
                );
                Vec3 worldPos = Vec3.atCenterOf(this.getBlockPos());
                for (LivingEntity e : hits) {
                    SuspensionTrackBlockEntity.push(e, worldPos);
                    if (e instanceof ServerPlayer p) p.connection.send(new ClientboundSetEntityMotionPacket(p));
                }
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void tickAudio() {
        float spd = Math.abs(getSpeed());
        float pitch = Mth.clamp((spd / 256f) + .45f, .85f, 1f);
        if (spd < 8)
            return;
        TrackSoundScapes.play(TrackAmbientGroups.TRACK_SPROCKET_AMBIENT, worldPosition, pitch);
    }

    public boolean getAssembled() {
        return assembled;
    }

    public void cleanupWheel() {
        // TODO(Phase D): despawn the associated free wheel body when Sable physics entities exist.
        this.wheelId = null;
    }

    @Override
    public float getPointDownwardOffset(float partialTicks) {
        return (float) (this.wheelRadius - 0.5);
    }

    @Override
    public float getPointHorizontalOffset() {
        return 0.0f;
    }

    public boolean isBeltLarge() {
        return this.wheelRadius > 0.75;
    }

    @Override
    public Vec3 getTrackPointSlope(float partialTicks) {
        return new Vec3(0,
                Mth.lerp(partialTicks, this.nextPointVerticalOffset.getFirst(), this.nextPointVerticalOffset.getSecond()) - this.getPointDownwardOffset(partialTicks),
                this.nextPointHorizontalOffset
        );
    }

    @Override
    public @NotNull PointType getTrackPointType() {
        return PointType.WRAP;
    }

    @Override
    public float getWheelRadius() {
        return this.wheelRadius;
    }

    @Override
    public float getSpeed() {
        if (!assembled) return 0;
        float maxRpm = TrackworkConfigs.server().maxRPM.get();
        return Math.clamp(-maxRpm, maxRpm, super.getSpeed());
    }

    public void addMassStats(List<Component> tooltip, float mass) {
        Component.literal("Total Mass")
                .withStyle(GRAY);

        Component.literal(String.valueOf(mass))
                .append(" kg")
                .withStyle(ChatFormatting.WHITE);
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putBoolean("Assembled", this.assembled);
        if (this.trackID != null) compound.putInt("trackBlockID", this.trackID);
        if (this.wheelId != null) compound.putLong("wheelId", this.wheelId);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        this.assembled = compound.getBoolean("Assembled");
        if (this.trackID == null && compound.contains("trackBlockID")) this.trackID = compound.getInt("trackBlockID");
        if (this.wheelId == null && compound.contains("wheelId")) this.wheelId = compound.getLong("wheelId");
        super.read(compound, registries, clientPacket);
    }

    @Override
    public float calculateStressApplied() {
        if (this.level.isClientSide || !TrackworkConfigs.server().enableStress.get() ||
                !this.assembled || this.getBlockState().getValue(PART) != TrackPart.start) return super.calculateStressApplied();

        // Scale stress by the sub-level mass (VS2 read ServerShip inertia; Sable exposes it via MassData).
        ServerSubLevel sub = this.subLevel.get();
        MassData mass = sub == null ? null : sub.getMassTracker();
        if (mass == null || mass.isInvalid())
            return super.calculateStressApplied();
        float impact = calculateStressApplied((float) mass.getMass());
        this.lastStressApplied = impact;
        return impact;
    }

    public float calculateStressApplied(float mass) {
        double impact = (mass / 1000) * TrackworkConfigs.server().stressMult.get() * (2.0f * this.wheelRadius) * 8;
        if (impact < 0) {
            impact = 0;
        }
        return (float) impact;
    }

    /** Sable physics hook: applies the sprocket's drive force (Design A force-only) each physics substep. */
    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (!this.assembled || !handle.isValid()) return;
        PhysEntityTrackController.getOrCreate(subLevel).physicsTick(subLevel, handle, this.getBlockPos(), timeStep);
    }
}
