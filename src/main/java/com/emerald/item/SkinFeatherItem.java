package com.emerald.item;

import com.emerald.specialization.Specialization;
import com.emerald.specialization.WingSkin;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * La Plume d'apparence : une plume teintee aux couleurs d'une apparence
 * d'ailes. A +15 et au-dela, un clic droit debloque cette apparence (la
 * plume est consommee) ou y revient si elle l'est deja (la plume reste).
 *
 * UNE RECOMPENSE, ET RIEN D'AUTRE (cahier §96, le joueur, 24 sept.) : une pour
 * chaque joueur a la victoire du Defi, et trois chances sur cent pour chacun a la
 * prise d'un sanctuaire. Plus aucun monstre n'en lache -- elle tombait a 35 % des
 * monstres de trois cents points de vie, boss final compris --, et aucun coffre
 * n'en a jamais contenu. Au hasard parmi les apparences que le joueur n'a pas
 * encore ; le Rubis et le Souverain Astral y entrent comme les autres.
 */
public class SkinFeatherItem extends Item {

    private static final String TAG_SKIN = "wing_skin";
    /** Les chances d'une plume pour chaque joueur, a la prise d'un sanctuaire. */
    public static final float SANCTUARY_CHANCE = 0.03F;

    public SkinFeatherItem(Properties properties) {
        super(properties);
    }

    public static ItemStack stack(WingSkin skin, Item item) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_SKIN, skin.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static WingSkin skinOf(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        WingSkin skin = WingSkin.byId(data.copyTag().getString(TAG_SKIN));
        return skin == null ? WingSkin.PRISMATIQUES : skin;
    }

    /** La couleur de la plume, par apparence. */
    public static int color(WingSkin skin) {
        return switch (skin) {
            case PRISMATIQUES -> 0xD8DCFF;
            case RUBIS -> 0xE0304C;
            case AURORE -> 0x7ADFC8;
            case PIERRES_PRECIEUSES -> 0xE0B060;
            case BRAISE -> 0xFF8A2E;
            case TEMPETE -> 0x8898C8;
            case EMERAUDE -> 0x3EC884;
            case OBSCURES -> 0x5A2C78;
            case GIVRE -> 0xBFE6FF;
            case PAPILLON -> 0xF2EAD0;
            case SOUVERAIN_ASTRAL -> 0x8C6BFF;    // la nebuleuse, pas la plume noire
        };
    }

    public static int color(ItemStack stack) {
        return color(skinOf(stack));
    }

    /**
     * L'apparence d'une plume de recompense : au hasard parmi celles que le joueur n'a pas
     * encore, parmi toutes s'il les a toutes (il peut la donner). Jamais le Prismatique,
     * qu'on a d'office.
     */
    public static WingSkin pickReward(Player player, RandomSource random) {
        List<WingSkin> all = new ArrayList<>();
        List<WingSkin> missing = new ArrayList<>();
        for (WingSkin skin : WingSkin.values()) {
            if (skin == WingSkin.PRISMATIQUES) {
                continue;
            }
            all.add(skin);
            if (!Specialization.unlocked(player, skin)) {
                missing.add(skin);
            }
        }
        List<WingSkin> pool = missing.isEmpty() ? all : missing;
        return pool.get(random.nextInt(pool.size()));
    }

    /** La plume dans l'inventaire du joueur (a ses pieds s'il est plein), et son message. */
    public static void reward(ServerPlayer player, WingSkin skin, String message) {
        player.getInventory().placeItemBackInInventory(stack(skin, ModItems.SKIN_FEATHER.get()));
        player.sendSystemMessage(Component.translatable(message,
                Component.translatable("wings.emeraldweapons." + skin.id())).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.emeraldweapons.skin_feather.named",
                Component.translatable("wings.emeraldweapons." + skinOf(stack).id()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            Specialization.applySkin(server, skinOf(stack), stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        WingSkin skin = skinOf(stack);
        tooltip.add(Component.translatable("item.emeraldweapons.skin_feather.desc",
                Specialization.WINGS_FULL).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("wings.emeraldweapons." + skin.id() + ".bonus")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
