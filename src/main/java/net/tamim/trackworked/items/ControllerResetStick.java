package net.tamim.trackworked.items;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.tamim.trackworked.physics.SableShips;
import net.tamim.trackworked.tracks.forces.PhysEntityTrackController;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class ControllerResetStick extends Item {
    public ControllerResetStick(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.mayBuild())
            return super.useOn(context);

        Level level = context.getLevel();
        ServerSubLevel ship = SableShips.getSubLevelManagingPos(level, context.getClickedPos());
        if (ship == null) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            PhysEntityTrackController controller = PhysEntityTrackController.getOrCreate(ship);
            controller.resetController();
            MutableComponent chatMessage = Component.empty();
            player.displayClientMessage(chatMessage.append("Fix! "), true);
        }

        return InteractionResult.SUCCESS;
    }
}
