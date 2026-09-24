package com.emerald.item;

import com.emerald.haven.door.HavenDoorFrame;
import com.emerald.haven.door.HavenDoorKind;
import com.emerald.haven.door.HavenDoors;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Une porte de Jak 3 a poser (cahier §95) : la porte du Hip Hog, la petite, le sas du port.
 *
 * Clic droit sur le sol : la porte se dresse au-dessus du bloc vise, centree sur lui, sa
 * largeur en travers du regard, sa face avant tournee vers le joueur -- on la voit de face (les
 * barres du sas sont sur sa face avant). Le sas a quatre blocs d'epaisseur : trois d'entre eux
 * s'etendent au-dela du bloc vise. Toute son ouverture doit etre libre. Elle s'ouvre ensuite
 * toute seule a l'approche, comme les portes d'office de la ville.
 */
public class HavenDoorItem extends Item {

    private final HavenDoorKind kind;

    public HavenDoorItem(HavenDoorKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide || player == null) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos base = context.getClickedPos().relative(context.getClickedFace());
        // la largeur en travers du regard, la face vers le joueur : son lacet retourne, au quart de tour pres
        float yaw = Mth.wrapDegrees(Math.round(player.getYRot() / 90.0F) * 90.0F + 180.0F);
        double cx = base.getX() + 0.5;
        double cz = base.getZ() + 0.5;
        if (this.kind.width % 2 == 0) {
            // une largeur paire : le centre tombe entre deux cellules
            cx += 0.5 * Math.cos(Math.toRadians(yaw));
            cz += 0.5 * Math.sin(Math.toRadians(yaw));
        }
        if (this.kind.depth % 2 == 0) {
            // une epaisseur paire : le centre recule d'une demi-cellule, loin du joueur
            cx += 0.5 * Math.sin(Math.toRadians(yaw));
            cz -= 0.5 * Math.cos(Math.toRadians(yaw));
        }
        HavenDoorFrame frame = new HavenDoorFrame(this.kind, cx, base.getY(), cz, yaw);
        if (!HavenDoors.place(level, frame, false)) {
            player.displayClientMessage(Component.translatable("item.emeraldweapons.haven_door.blocked",
                    this.kind.width, this.kind.height), true);
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.emeraldweapons.haven_door.hint", this.kind.width, this.kind.height));
    }
}
