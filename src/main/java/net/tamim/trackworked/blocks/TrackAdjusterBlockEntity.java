package net.tamim.trackworked.blocks;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.tamim.trackworked.physics.SableShips;
import net.tamim.trackworked.tracks.forces.OleoWheelController;
import net.tamim.trackworked.tracks.forces.PhysicsTrackController;
import net.tamim.trackworked.tracks.forces.SimpleWheelController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public class TrackAdjusterBlockEntity extends KineticBlockEntity {
    public TrackAdjusterBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public void destroy() {
        super.destroy();

        if (this.level.isClientSide) return;
        ServerSubLevel ship = SableShips.getSubLevelManagingPos(this.level, this.getBlockPos());
        if (ship != null) {
            PhysicsTrackController controller = PhysicsTrackController.getOrCreate(ship);
            controller.resetSuspension();
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level.isClientSide) return;
        ServerSubLevel ship = SableShips.getSubLevelManagingPos(this.level, this.getBlockPos());
        if (ship != null) {
            Direction.Axis axis = this.getBlockState().getValue(RotatedPillarKineticBlock.AXIS);
            Vector3f vec = Direction.get(Direction.AxisDirection.POSITIVE, axis).step();
            vec.mul(this.getSpeed() / 20000f);

            PhysicsTrackController controller = PhysicsTrackController.getOrCreate(ship);
            controller.adjustSuspension(vec);

            SimpleWheelController controller2 = SimpleWheelController.getOrCreate(ship);
            controller2.adjustSuspension(vec);

            OleoWheelController controller3 = OleoWheelController.getOrCreate(ship);
            controller3.adjustSuspension(vec);
        }
    }
}
