package net.tamim.trackworked.tracks.blocks;

import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.platform.CatnipServices;
import net.tamim.trackworked.*;
import net.tamim.trackworked.physics.SableShips;
import net.tamim.trackworked.tracks.ITrackPointProvider;
import net.tamim.trackworked.tracks.data.PhysTrackData;
import net.tamim.trackworked.tracks.forces.PhysicsTrackController;
import net.tamim.trackworked.tracks.network.SuspensionWheelPacket;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Math;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS;
import static net.tamim.trackworked.TrackSounds.SUSPENSION_CREAK;

public class SuspensionTrackBlockEntity extends TrackBaseBlockEntity implements ITrackPointProvider, BlockEntitySubLevelActor {
    private float wheelRadius;
    private float maxSuspensionTravel = 1.5f;
    protected final Random random = new Random();
    /** Server sub-level whose plot contains this block, or {@code null} when in the ordinary world. */
    @NotNull
    protected final Supplier<ServerSubLevel> subLevel;
    @Deprecated
    private Integer trackID;
    public boolean assembled;
    public boolean assembleNextTick = true;
    private float wheelTravel;
    private float prevWheelTravel;
    private float serverTargetWheelTravel;
    // Server-side only
    private float lastSyncedWheelTravel;

    private static final float COMPRESS_ALPHA = 0.667f;
    private static final float REBOUND_ALPHA = 0.394f;

    private double suspensionScale = 1.0;
    private float horizontalOffset;

    public SuspensionTrackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.assembled = false;
        this.wheelRadius = 0.5f;
        this.maxSuspensionTravel = 1.5f;
        this.subLevel = () -> SableShips.getSubLevelManagingPos(this.level, pos);
    }

    public static SuspensionTrackBlockEntity large(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        SuspensionTrackBlockEntity be = new SuspensionTrackBlockEntity(type, pos, state);
        be.wheelRadius = 1.0f;
        be.maxSuspensionTravel = 2.0f;
        return be;
    }

    public static SuspensionTrackBlockEntity med(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        SuspensionTrackBlockEntity be = new SuspensionTrackBlockEntity(type, pos, state);
        be.wheelRadius = 0.75f;
        be.maxSuspensionTravel = 1.5f;
        return be;
    }

    @Override
    public void onLoad() {
        super.onLoad();
    }

    @Override
    public void remove() {
        super.remove();

        if (this.level != null && !this.level.isClientSide && this.assembled) {
            ServerSubLevel sub = this.subLevel.get();
            if (sub != null) {
                PhysicsTrackController controller = PhysicsTrackController.getOrCreate(sub);
                controller.removeTrackBlock(this.getBlockPos().asLong());
            }
        }
    }

    private void assemble() {
        if (!TrackBaseBlock.isValidAxis(this.getBlockState().getValue(AXIS))) return;
        if (this.level != null && !this.level.isClientSide) {
            ServerSubLevel sub = this.subLevel.get();
            if (sub != null) {
                this.assembled = true;
                PhysicsTrackController controller = PhysicsTrackController.getOrCreate(sub);
                PhysTrackData.PhysTrackCreateData data = new PhysTrackData.PhysTrackCreateData(this.getBlockPos());
                controller.addTrackBlock(data);
                this.sendData();
            }
        }
    }

    public void disassemble() {

    }

    @Override
    public void tick() {
        super.tick();

        // Backwards compatibility
        if (this.trackID != null) {
            this.trackID = null;
            this.assembled = false;
            this.assembleNextTick = true;
        }

        if (this.subLevel.get() != null && this.assembleNextTick && !this.assembled && this.level != null) {
            this.assemble();
            this.assembleNextTick = false;
            return;
        }

        // TODO(Phase D): client-side ground contact particles + track slip sounds. The Forge build
        // sampled the VS2 ship pose (getWorldCoordinates) and ship velocity here. Rebuild against
        // the Sable sub-level render pose.

        // TODO: degrass + de-snowlayer

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
            Vec3 start = Vec3.atCenterOf(this.getBlockPos());
            Direction.Axis axis = this.getBlockState().getValue(AXIS);
            double restOffset = this.wheelRadius - 0.5f;
            float trackRPM = this.getSpeed();
            double effectiveSuspensionTravel = this.maxSuspensionTravel * this.suspensionScale;
            ServerSubLevel sub = this.subLevel.get();
            if (sub != null) {
                // TODO(Phase D): transform to world space via the sub-level pose. Stubbed in world space.
                Vec3 worldSpaceStart = start.add(0, -restOffset, 0);

                boolean inWater = false;
                BlockState b = this.level.getBlockState(BlockPos.containing(worldSpaceStart));
                if (b.getFluidState().is(FluidTags.WATER)) {
                    inWater = true;
                }

                PhysicsTrackController controller = PhysicsTrackController.getOrCreate(sub);
                PhysTrackData.PhysTrackUpdateData data = new PhysTrackData.PhysTrackUpdateData(
                        axis,
                        horizontalOffset,
                        effectiveSuspensionTravel,
                        wheelRadius,
                        inWater,
                        trackRPM
                );

                TrackworkUtil.ClipResult clipResult = controller.getSuspensionData(this.getBlockPos());
                double suspensionTravel = clipResult.equals(TrackworkUtil.ClipResult.MISS) ? effectiveSuspensionTravel : clipResult.suspensionLength().length() - 0.5;

                this.suspensionScale = controller.updateTrackBlock(this.getBlockPos(), data);
                this.prevWheelTravel = this.wheelTravel;
                float newWheelTravel = (float) (suspensionTravel + restOffset);
                float wheelTravelDelta = newWheelTravel - this.wheelTravel;
                this.wheelTravel = newWheelTravel;
                if (Math.abs(this.wheelTravel - this.lastSyncedWheelTravel) > 0.04f) {
                    if (this.level instanceof ServerLevel serverLevel)
                        CatnipServices.NETWORK.sendToClientsTrackingChunk(serverLevel, new ChunkPos(this.getBlockPos()),
                                new SuspensionWheelPacket(this.getBlockPos(), this.wheelTravel));
                    this.lastSyncedWheelTravel = this.wheelTravel;
                }

                // Entity Damage
                // TODO(Phase D): precise oriented-box collision against the sub-level pose. For the
                // stubbed (stationary) track this uses a world-space AABB around the block.
                AABB trackAabb = new AABB(this.getBlockPos())
                        .deflate(0.25)
                        .expandTowards(0, -1.5, 0);
                List<LivingEntity> hits = this.level.getEntitiesOfClass(LivingEntity.class, trackAabb);
                Vec3 worldPos = Vec3.atCenterOf(this.getBlockPos());

                for (LivingEntity e : hits) {
                    push(e, worldPos);
                    float speed = Math.abs(this.getSpeed());
                    if (speed > 1) e.hurt(TrackDamageSources.runOver(this.level), (speed / 8f) * AllConfigs.server().kinetics.crushingDamage.get());
                    if (e instanceof ServerPlayer p) p.connection.send(new ClientboundSetEntityMotionPacket(p));
                }

                BlockState state = this.getBlockState();
                if (wheelTravelDelta < -0.3 && state.hasProperty(SuspensionTrackBlock.WHEEL_VARIANT)
                        && state.getValue(SuspensionTrackBlock.WHEEL_VARIANT) != SuspensionTrackBlock.TrackVariant.blank) {
                    this.level.playSound(null, this.getBlockPos(), SUSPENSION_CREAK.get(), SoundSource.BLOCKS,
                            Math.clamp(0.0f, 2.0f, Math.abs(wheelTravelDelta * 3 * (this.getSpeed() / 256))*0.5f),
                            Math.lerp(1, 0.3f, -wheelTravelDelta) + 0.4F * this.random.nextFloat()
                    );
                }
            }
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (this.assembled && !this.level.isClientSide && this.subLevel.get() != null
                && this.level instanceof ServerLevel serverLevel)
            CatnipServices.NETWORK.sendToClientsTrackingChunk(serverLevel, new ChunkPos(this.getBlockPos()),
                    new SuspensionWheelPacket(this.getBlockPos(), this.wheelTravel));
    }

    public void setHorizontalOffset(Vector3dc offset) {
        Direction.Axis axis = this.getBlockState().getValue(AXIS);
        double factor = offset.dot(TrackworkUtil.getForwardVec3d(axis, 1));
        this.horizontalOffset = Math.clamp(-0.5f, 0.5f, Math.round(factor * 8.0f) / 8.0f);
        this.setChanged();
    }

    @Override
    public float getPointDownwardOffset(float partialTicks) {
        return this.getWheelTravel(partialTicks);
    }

    @Override
    public float getPointHorizontalOffset() {
        return this.horizontalOffset;
    }

    public boolean isBeltLarge() {
        return this.wheelRadius > 0.75;
    }

    @Override
    public Vec3 getTrackPointSlope(float partialTicks) {
        return new Vec3(0,
                Mth.lerp(partialTicks, this.nextPointVerticalOffset.getFirst(), this.nextPointVerticalOffset.getSecond()) - this.getWheelTravel(partialTicks),
                this.nextPointHorizontalOffset - this.horizontalOffset
        );
    }

    @Override
    public @NotNull PointType getTrackPointType() {
        return PointType.GROUND;
    }

    @Override
    public float getWheelRadius() {
        return this.wheelRadius;
    }

    @Override
    public float getSpeed() {
        if (!assembled) return 0;
        return Math.clamp(-TrackworkConfigs.server().maxRPM.get(), TrackworkConfigs.server().maxRPM.get(), super.getSpeed());
    }

    public static void push(Entity entity, Vec3 worldPos) {
        if (!entity.noPhysics) {
            double d0 = entity.getX() - worldPos.x;
            double d1 = entity.getZ() - worldPos.z;
            double d2 = Mth.absMax(d0, d1);
            if (d2 >= (double)0.01F) {
                d2 = java.lang.Math.sqrt(d2);
                d0 /= d2;
                d1 /= d2;
                double d3 = 1.0D / d2;
                if (d3 > 1.0D) {
                    d3 = 1.0D;
                }

                d0 *= d3;
                d1 *= d3;
                d0 *= 0.1F;
                d1 *= 0.1F;

                if (!entity.isVehicle()) {
                    entity.push(d0, 0.0D, d1);
                }
            }
        }
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putBoolean("Assembled", this.assembled);
        if (this.trackID != null) compound.putInt("trackBlockID", this.trackID);
        compound.putFloat("WheelTravel", this.wheelTravel);
        compound.putFloat("horizontalOffset", this.horizontalOffset);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        this.assembled = compound.getBoolean("Assembled");
        if (this.trackID == null && compound.contains("trackBlockID")) this.trackID = compound.getInt("trackBlockID");
        if (clientPacket) {
            this.serverTargetWheelTravel = compound.getFloat("WheelTravel");
        } else {
            this.wheelTravel = compound.getFloat("WheelTravel");
            this.prevWheelTravel = this.wheelTravel;
            this.serverTargetWheelTravel = this.wheelTravel;
            this.lastSyncedWheelTravel = this.wheelTravel;
        }
        if (compound.contains("horizontalOffset")) this.horizontalOffset = compound.getFloat("horizontalOffset");
        super.read(compound, registries, clientPacket);
    }

    public float getWheelTravel() {
        return this.wheelTravel;
    }

    public float getWheelTravel(float partialTicks) {
        return Mth.lerp(partialTicks, prevWheelTravel, wheelTravel);
    }

    @Override
    protected boolean isNoisy() {
        return false;
    }

    public void handlePacket(SuspensionWheelPacket p) {
        this.serverTargetWheelTravel = p.wheelTravel;
    }

    /** Sable physics hook: applies the continuous-track suspension + drive force each physics substep. */
    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (!this.assembled || !handle.isValid()) return;
        PhysicsTrackController.getOrCreate(subLevel).physicsTick(subLevel, handle, this.getBlockPos(), timeStep);
    }
}
