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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * La Plume d'apparence : une plume teintee aux couleurs d'une apparence
 * d'ailes. A +15 et au-dela, un clic droit debloque cette apparence (la
 * plume est consommee) ou y revient si elle l'est deja (la plume reste).
 *
 * Elle tombe des puissants -- selon leur element et la meteo du moment --
 * et se trouve dans les coffres rares. Le Rubis ne tombe d'aucun monstre.
 */
public class SkinFeatherItem extends Item {

    private static final String TAG_SKIN = "wing_skin";

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
        };
    }

    public static int color(ItemStack stack) {
        return color(skinOf(stack));
    }

    /**
     * Quelle apparence un puissant lache : celle de son element quand elle
     * existe, celle de la meteo en cours sinon, et au hasard parmi les
     * autres autrement. Jamais le Rubis : lui ne vient que des coffres.
     */
    public static WingSkin pickDrop(LivingEntity victim, RandomSource random) {
        com.emerald.element.Element element = com.emerald.element.Attunement.of(victim);
        if (element == com.emerald.element.Element.OBSCUR) {
            return WingSkin.OBSCURES;
        }
        if (element == com.emerald.element.Element.EAU) {
            return WingSkin.GIVRE;
        }
        com.emerald.weather.Weather weather = com.emerald.weather.WeatherManager.current();
        if (weather == com.emerald.weather.Weather.ORAGE) {
            return WingSkin.TEMPETE;
        }
        if (weather == com.emerald.weather.Weather.METEORES) {
            return WingSkin.BRAISE;
        }
        WingSkin[] pool = {WingSkin.PIERRES_PRECIEUSES, WingSkin.EMERAUDE, WingSkin.PAPILLON, WingSkin.AURORE};
        return pool[random.nextInt(pool.length)];
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
