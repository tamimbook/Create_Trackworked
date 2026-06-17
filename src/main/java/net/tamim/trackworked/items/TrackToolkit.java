package net.tamim.trackworked.items;

import com.simibubi.create.AllSoundEvents;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.tamim.trackworked.TrackSounds;
import net.tamim.trackworked.physics.SableShips;
import net.tamim.trackworked.tracks.blocks.*;
import net.tamim.trackworked.tracks.forces.OleoWheelController;
import net.tamim.trackworked.tracks.forces.PhysicsTrackController;
import net.tamim.trackworked.tracks.forces.SimpleWheelController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class TrackToolkit extends Item {
    public enum TOOL implements StringRepresentable {
        STIFFNESS,
        OFFSET;

        private static final TOOL[] vals = values();

        public static TOOL from(int i) {
            return vals[i];
        }

        public static int next(int i) {
            return (i + 1) % vals.length;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name();
        }
    }

    public TrackToolkit(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, @NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.mayBuild())
            return InteractionResult.PASS;

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        MutableComponent chatMessage = Component.empty();

        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (nbt.contains("Tool")) {
            TOOL type = TOOL.from(nbt.getInt("Tool"));

            switch (type) {
                case OFFSET -> {
                    BlockEntity be = level.getBlockEntity(pos);

                    AllSoundEvents.WRENCH_ROTATE.playOnServer(player.level(), pos, 1, player.getRandom().nextFloat() + .5f);

                    Vec3 offset = context.getClickLocation().subtract(Vec3.atCenterOf(context.getClickedPos()));
                    if (be instanceof SuspensionTrackBlockEntity se) {
                        if (SableShips.getSubLevelManagingPos(level, pos) == null) return InteractionResult.FAIL;
                        se.setHorizontalOffset(JOMLConversion.toJOML(offset));
                        return InteractionResult.SUCCESS;
                    } else if (be instanceof WheelBlockEntity wbe) {
                        if (SableShips.getSubLevelManagingPos(level, pos) == null) return InteractionResult.FAIL;
                        wbe.setOffset(JOMLConversion.toJOML(offset), context.getClickedFace());
                        return InteractionResult.SUCCESS;
                    } else if (be instanceof OleoWheelBlockEntity owbe) {
                        if (SableShips.getSubLevelManagingPos(level, pos) == null) return InteractionResult.FAIL;
                        owbe.setOffset(JOMLConversion.toJOML(offset), context.getClickedFace());
                        return InteractionResult.SUCCESS;
                    }
                }
                case STIFFNESS -> {
                    Block hitBlock = level.getBlockState(pos).getBlock();

                    player.playSound(TrackSounds.SPRING_TOOL.get(), 1.0f, 0.8f + 0.4f * player.getRandom().nextFloat());

                    boolean isSneaking = player.isShiftKeyDown();
                    ServerSubLevel ship = SableShips.getSubLevelManagingPos(level, pos);
                    if (hitBlock instanceof TrackBaseBlock<?>) {
                        if (ship == null) return InteractionResult.FAIL;
                        if (!level.isClientSide) {
                            PhysicsTrackController controller = PhysicsTrackController.getOrCreate(ship);
                            float result = controller.setSuspensionDampening(isSneaking ? -1f : 1f);
                            chatMessage.append("Adjusted suspension stiffness to " + result);
                            player.displayClientMessage(chatMessage, true);
                        }
                        return InteractionResult.SUCCESS;
                    } else if (hitBlock instanceof WheelBlock) {
                        if (ship == null) return InteractionResult.FAIL;
                        if (!level.isClientSide) {
                            SimpleWheelController controller = SimpleWheelController.getOrCreate(ship);
                            float result = controller.setDamperCoefficient(isSneaking ? -1f : 1f);
                            chatMessage.append("Adjusted suspension stiffness to " + result);
                            player.displayClientMessage(chatMessage, true);
                        }
                        return InteractionResult.SUCCESS;
                    } else if (hitBlock instanceof OleoWheelBlock) {
                        if (ship == null) return InteractionResult.FAIL;
                        if (!level.isClientSide) {
                            OleoWheelController controller = OleoWheelController.getOrCreate(ship);
                            float result = controller.setDamperCoefficient(isSneaking ? -1f : 1f);
                            chatMessage.append("Adjusted suspension stiffness to " + result);
                            player.displayClientMessage(chatMessage, true);
                        }
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        }

        return this.use(level, player, context.getHand()).getResult();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!player.isShiftKeyDown()) {
            if (!level.isClientSide) nextMode(stack);
            player.getCooldowns()
                    .addCooldown(this, 2);
        }

        return InteractionResultHolder.pass(stack);
    }

    private void nextMode(ItemStack stack) {
        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        if (!nbt.contains("Tool")) {
            nbt.putInt("Tool", 0);
        } else {
            nbt.putInt("Tool", TOOL.next(nbt.getInt("Tool")));
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
    }
}
