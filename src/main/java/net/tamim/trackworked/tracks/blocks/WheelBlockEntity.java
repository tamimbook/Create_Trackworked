package net.tamim.trackworked.tracks.blocks;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.platform.CatnipServices;
import net.tamim.trackworked.*;
import net.tamim.trackworked.physics.SableShips;
import net.tamim.trackworked.tracks.data.SimpleWheelData;
import net.tamim.trackworked.tracks.forces.SimpleWheelController;
import net.tamim.trackworked.tracks.network.SimpleWheelPacket;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Math;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;
import static net.tamim.trackworked.TrackSounds.SUSPENSION_CREAK;

public class WheelBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor {
    private float wheelRadius;
    private float suspensionTravel = 1.5f;
    private double suspensionScale = 1.0f;
    private float steeringValue = 0.0f;
    private float linkedSteeringValue = 0.0f;
    protected final Random random = new Random();
    private float wheelTravel;
    private float prevWheelTravel;
    private float serverTargetWheelTravel;
    // Server-side only
    private float lastSyncedWheelTravel;

    private static final float COMPRESS_ALPHA = 0.667f;
    private static final float REBOUND_ALPHA = 0.394f;

    private float prevFreeWheelAngle;
    private float horizontalOffset;
    private float axialOffset;

    /** Server sub-level whose plot contains this block, or {@code null} when in the ordinary world. */
    @NotNull
    protected final Supplier<ServerSubLevel> subLevel;

    public boolean isFreespin = true;
    public boolean assembled;
    public boolean assembleNextTick = true;

    public WheelBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        this.wheelRadius = 1.0f;
        this.suspensionTravel = 1.5f;
        this.subLevel = () -> SableShips.getSubLevelManagingPos(this.level, pos);
        this.setLazyTickRate(10);
    }

    public static WheelBlockEntity med(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        WheelBlockEntity be = new WheelBlockEntity(type, pos, state);
        be.wheelRadius = 0.75f;
        be.suspensionTravel = 1.5f;
        return be;
    }

    public static WheelBlockEntity large(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        WheelBlockEntity be = new WheelBlockEntity(type, pos, state);
        be.wheelRadius = 1.5f;
        be.suspensionTravel = 2f;
        return be;
    }

    public static WheelBlockEntity small(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        WheelBlockEntity be = new WheelBlockEntity(type, pos, state);
        be.wheelRadius = 0.5f;
        be.suspensionTravel = 1f;
        return be;
    }

    @Override
    public void remove() {
        super.remove();

        if (this.level != null && !this.level.isClientSide && this.assembled) {
            ServerSubLevel sub = this.subLevel.get();
            if (sub != null) {
                SimpleWheelController controller = SimpleWheelController.getOrCreate(sub);
                controller.removeTrackBlock(this.getBlockPos());
            }
        }
    }

    private void assemble() {
        if (!WheelBlock.isValid(this.getBlockState().getValue(HORIZONTAL_FACING))) return;
        if (this.level != null && !this.level.isClientSide) {
            ServerSubLevel sub = this.subLevel.get();
            if (sub != null) {
                this.assembled = true;
                SimpleWheelController controller = SimpleWheelController.getOrCreate(sub);
                SimpleWheelData.SimpleWheelCreateData data =
                        new SimpleWheelData.SimpleWheelCreateData(JOMLConversion.atCenterOf(this.getBlockPos()));
                controller.addTrackBlock(this.getBlockPos(), data);
                this.sendData();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.subLevel.get() != null && this.assembleNextTick && !this.assembled && this.level != null) {
            this.assemble();
            this.assembleNextTick = false;
            return;
        }

        // TODO(Phase D): client-side ground contact particles + wheel slip sounds. The Forge build
        // sampled the VS2 ship pose (getWorldCoordinates) and ship velocity to spawn block-break
        // particles and play slip audio. Rebuild against the Sable sub-level render pose.

        // Freespin check
        Direction dir = this.getBlockState().getValue(HORIZONTAL_FACING);
        BlockPos innerBlock = this.getBlockPos().relative(dir);
        BlockState innerState = this.level.getBlockState(innerBlock);
        if (innerState.getBlock() instanceof KineticBlock ke && ke.hasShaftTowards(level, innerBlock, innerState, dir.getOpposite())) {
            isFreespin = false;
        } else {
            isFreespin = true;
            if (this.level.isClientSide) {
                this.prevFreeWheelAngle += this.getWheelSpeed() * 3f / 10;
            }
        }

        if (this.level.isClientSide) {
            this.prevWheelTravel = this.wheelTravel;
            float gap = this.serverTargetWheelTravel - this.wheelTravel;
            if (gap > 1.2f || gap < -1.2f) {
                this.wheelTravel = this.serverTargetWheelTravel;
            } else {
                this.wheelTravel += gap * (gap >= 0 ? COMPRESS_ALPHA : REBOUND_ALPHA);
            }
            return;
        }
        if (this.assembled) {
            Direction.Axis axis = dir.getAxis();
            double restOffset = this.wheelRadius - 0.5f;
            float trackRPM = this.getDrivenSpeed();
            double susScaled = this.suspensionTravel * this.suspensionScale;
            ServerSubLevel sub = this.subLevel.get();
            if (sub != null) {
                // Steering Control
                int bestSignal = this.level.getBestNeighborSignal(this.getBlockPos());
                float targetSteeringValue = bestSignal / 15f * ((dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1));
                float oldSteeringValue = this.steeringValue;

                // Smooth steering interpolation
                float steeringSpeed = 0.5f; // Adjust this value to control steering speed (0.1 = slower, 0.3 = faster)
                this.steeringValue = Mth.lerp(steeringSpeed, this.steeringValue, targetSteeringValue);

                float deltaSteeringValue = oldSteeringValue - this.steeringValue;

                // Flush linked val & propagate when pair target empty
                if (bestSignal > 0) this.linkedSteeringValue = 0f;
                this.onLinkedWheel(wbe -> {
                    int linkedSignal = this.level.getBestNeighborSignal(wbe.getBlockPos());
                    if (linkedSignal == 0) {
                        wbe.setLinkedSteeringValue(this.steeringValue);
                    }
                });

                SimpleWheelController controller = SimpleWheelController.getOrCreate(sub);
                SimpleWheelData.SimpleWheelUpdateData data = new SimpleWheelData.SimpleWheelUpdateData(
                        this.getSteeringValue(),
                        trackRPM,
                        axis,
                        this.getPointAxialOffset(),
                        this.getPointHorizontalOffset(),
                        susScaled,
                        this.wheelRadius,
                        isFreespin
                );

                TrackworkUtil.ClipResult clipResult = controller.getSuspensionData(this.getBlockPos());

                double suspensionTravel = clipResult.equals(TrackworkUtil.ClipResult.MISS) ? susScaled : clipResult.suspensionLength().length() - 0.5;
                boolean isOnGround = !clipResult.equals(TrackworkUtil.ClipResult.MISS);

                this.suspensionScale = controller.updateTrackBlock(this.getBlockPos(), data);
                float newWheelTravel = (float) (suspensionTravel + restOffset);
                float delta = newWheelTravel - wheelTravel;

                this.prevWheelTravel = this.wheelTravel;
                this.wheelTravel = newWheelTravel;
                if (Math.abs(this.wheelTravel - this.lastSyncedWheelTravel) > 0.04f || Math.abs(deltaSteeringValue) > 0.12f) {
                    this.syncToClient();
                    this.lastSyncedWheelTravel = this.wheelTravel;
                }

                // Entity Damage
                // TODO(Phase D): precise oriented-box collision against the sub-level pose. For the
                // stubbed (stationary) wheel this uses a world-space AABB around the block.
                AABB wheelAabb = new AABB(this.getBlockPos())
                        .deflate(0.25)
                        .expandTowards(0, -1.5, 0);
                List<LivingEntity> hits = this.level.getEntitiesOfClass(LivingEntity.class, wheelAabb);
                Vec3 worldPos = Vec3.atCenterOf(this.getBlockPos());

                for (LivingEntity e : hits) {
                    SuspensionTrackBlockEntity.push(e, worldPos);
                    float speed = Math.abs(trackRPM);
                    if (speed > 1) e.hurt(TrackDamageSources.runOver(this.level), (speed / 16f) * AllConfigs.server().kinetics.crushingDamage.get());
                    if (e instanceof ServerPlayer p) p.connection.send(new ClientboundSetEntityMotionPacket(p));
                }

                if (delta < -0.3) {
                    this.level.playSound(null, this.getBlockPos(), SUSPENSION_CREAK.get(), SoundSource.BLOCKS,
                            Math.clamp(0.0f, 2.0f, Math.abs(delta * 3 * (this.getSpeed() / 256))*0.5f),
                            Math.lerp(1.2f, 0.8f, -delta) + 0.4F * this.random.nextFloat()
                    );
                }
                if (isOnGround && this.random.nextFloat() < Math.abs(this.getSpeed() / 256)*0.1) {
                    this.level.playSound(null, this.getBlockPos(),
                            TrackSounds.WHEEL_ROCKTOSS.get(), SoundSource.BLOCKS,
                            Math.max(0.2f, Math.abs(this.getSpeed() / 256)*0.5f),
                            0.8F + 0.4F * this.random.nextFloat());
                }
            }
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (this.assembled && !this.level.isClientSide && this.subLevel.get() != null) this.syncToClient();
    }

    protected void onLinkedWheel(Consumer<WheelBlockEntity> action) {
        Direction dir = this.getBlockState().getValue(HORIZONTAL_FACING);
        for (int i = 1; i <= TrackworkConfigs.server().wheelPairDist.get() + 1; i++) {
            BlockPos bpos = this.getBlockPos().relative(dir, i);
            BlockEntity be = this.level.getBlockEntity(bpos);
            if (be instanceof WheelBlockEntity wbe) {
                action.accept(wbe);
                break;
            }
        }
    }

    public void setLinkedSteeringValue(float v) {
        float old = this.getSteeringValue();
        this.linkedSteeringValue = v;
        float delta = this.getSteeringValue() - old;
        if (Math.abs(delta) > 0.05f) this.syncToClient();
    }

    protected void syncToClient() {
        if (this.level instanceof ServerLevel serverLevel)
            CatnipServices.NETWORK.sendToClientsTrackingChunk(serverLevel, new ChunkPos(this.getBlockPos()),
                    new SimpleWheelPacket(this.getBlockPos(), this.wheelTravel, this.getSteeringValue(), this.horizontalOffset));
    }

    /*
        This includes steering!
     */
    public Vector3d getActionVec3d(Direction.Axis axis, float length) {
        return TrackworkUtil.getForwardVec3d(axis, length)
                .rotateAxis(this.getSteeringValue() * Math.toRadians(30), 0, 1, 0);
    }

    public float getFreeWheelAngle(float partialTick) {
        return (this.prevFreeWheelAngle + this.getWheelSpeed()*partialTick* 3f/10) % 360;
    }

    public float getWheelSpeed() {
        // TODO(Phase D): when free-spinning, derive wheel RPM from the sub-level's body velocity at
        // the contact point (the VS2 build projected ship linear+angular velocity onto the wheel
        // tangent). Until the Sable physics layer lands, fall back to the kinetic driven speed.
        return this.getDrivenSpeed();
    }

    public float getDrivenSpeed() {
        return this.getSpeed() * 1/this.wheelRadius;
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putBoolean("Assembled", this.assembled);
        compound.putFloat("WheelTravel", this.wheelTravel);
        compound.putFloat("HorizontalOffset", this.horizontalOffset);
        compound.putFloat("AxialOffset", this.axialOffset);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        this.assembled = compound.getBoolean("Assembled");
        if (clientPacket) {
            this.serverTargetWheelTravel = compound.getFloat("WheelTravel");
        } else {
            this.wheelTravel = compound.getFloat("WheelTravel");
            this.prevWheelTravel = this.wheelTravel;
            this.serverTargetWheelTravel = this.wheelTravel;
            this.lastSyncedWheelTravel = this.wheelTravel;
        }
        this.horizontalOffset = compound.getFloat("HorizontalOffset");
        this.axialOffset = compound.getFloat("AxialOffset");
        super.read(compound, registries, clientPacket);
    }

    public float getWheelRadius() {
        return this.wheelRadius;
    }

    public float getWheelTravel(float partialTicks) {
        return Mth.lerp(partialTicks, prevWheelTravel, wheelTravel);
    }

    /**
    For ponder usage only!
     **/
    public void setSteeringValue(float value) {
        this.steeringValue = value;
    }

    public float getSteeringValue() {
        return Math.abs(linkedSteeringValue) > Math.abs(steeringValue) ? linkedSteeringValue : steeringValue;
    }

    public void setOffset(Vector3dc offset, Direction face) {
        Direction.Axis axis = this.getBlockState().getValue(HORIZONTAL_FACING).getAxis();
        if (face.getAxis() == axis) {
            setHorizontalOffset(offset, axis);
        } else {
            setAxialOffset(offset, axis);
        }
    }

    public void setAxialOffset(Vector3dc offset, Direction.Axis axis) {
        double factor = offset.dot(TrackworkUtil.getAxisAsVec(axis));
        this.axialOffset = Math.clamp(-0.4f, 0.4f, Math.round(factor * 8.0f) / 8.0f);
        this.onLinkedWheel(wbe -> {
            wbe.axialOffset = -this.axialOffset;
            wbe.syncToClient();
        });
        this.syncToClient();
    }

    public float getPointAxialOffset() {
        return this.axialOffset;
    }

    public void setHorizontalOffset(Vector3dc offset, Direction.Axis axis) {
        double factor = offset.dot(getActionVec3d(axis, 1));
        this.horizontalOffset = Math.clamp(-0.4f, 0.4f, Math.round(factor * 8.0f) / 8.0f);
        this.onLinkedWheel(wbe -> {
            wbe.horizontalOffset = this.horizontalOffset;
            wbe.syncToClient();
        });
        this.syncToClient();
    }

    public float getPointHorizontalOffset() {
        return this.horizontalOffset;
    }

    @Override
    public float calculateStressApplied() {
        if (this.level.isClientSide || !TrackworkConfigs.server().enableStress.get() || !this.assembled)
            return super.calculateStressApplied();

        // Scale stress by the sub-level's mass (VS2 read ServerShip inertia; Sable exposes it via MassData).
        ServerSubLevel sub = this.subLevel.get();
        MassData mass = sub == null ? null : sub.getMassTracker();
        if (mass == null || mass.isInvalid())
            return super.calculateStressApplied();
        float impact = calculateStressApplied((float) mass.getMass());
        this.lastStressApplied = impact;
        return impact;
    }

    public float calculateStressApplied(float mass) {
        double impact = (mass / 1000) * TrackworkConfigs.server().stressMult.get() * (2.0f * this.wheelRadius);
        if (impact < 0) {
            impact = 0;
        }
        return (float) impact;
    }

    protected boolean isNoisy() {
        return false;
    }

    public void handlePacket(SimpleWheelPacket p) {
        this.serverTargetWheelTravel = p.wheelTravel;
        this.steeringValue = p.steeringValue;
        this.horizontalOffset = p.horizontalOffset;
    }

    /**
     * Sable physics hook: auto-discovered by {@code instanceof} ({@code LevelPlot.onBlockChange}) and
     * invoked once per physics substep with this block's sub-level rigid body. Delegates the suspension
     * + drive force math to the per-sub-level controller, keyed by this block's position.
     */
    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (!this.assembled || !handle.isValid()) return;
        SimpleWheelController.getOrCreate(subLevel).physicsTick(subLevel, handle, this.getBlockPos(), timeStep);
    }
}
