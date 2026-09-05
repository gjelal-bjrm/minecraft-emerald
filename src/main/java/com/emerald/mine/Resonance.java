package com.emerald.mine;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.weather.Weather;
import com.emerald.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * LA RESONANCE : la pioche comme sonar.
 *
 * Quand on casse un minerai, LES MINERAIS DU MEME TYPE dans la roche autour
 * repondent -- un carillon, et une silhouette breve a travers la pierre. On en
 * casse un, on entend le suivant, on va le chercher, il en montre un autre. Le
 * minage devient une CHAINE qu'on remonte, pas une direction qu'on suit.
 *
 * La portee suit l'outil : fer 4, diamant 8, netherite et au-dela 12. C'est la
 * premiere raison de jeu qu'une pioche ait d'etre meilleure que « elle casse
 * plus vite ». Pendant l'Aurore, la portee double : la fenetre de mine et la
 * pioche se repondent.
 *
 * Toujours actif, pas lie a une meteo : c'est le minage ORDINAIRE qui devient
 * vivant. Jamais en creatif -- on n'y mine pas, on y efface.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class Resonance {

    /** Autant d'echos au plus : au-dela, la paroi devient une guirlande. */
    private static final int MAX_ECHOES = 6;
    /** Le jalon reste deux secondes et demie : le temps de tourner la tete. */
    private static final int ECHO_TICKS = 50;

    private Resonance() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator()
                || !Underground.ore(event.getState())) {
            return;
        }
        echo(level, player, event.getPos(), event.getState().getBlock());
    }

    /**
     * L'echo d'un filon casse : ce que la commande d'essai appelle aussi.
     *
     * @return le nombre de filons qui ont repondu
     */
    public static int echo(ServerLevel level, ServerPlayer player, BlockPos centre, Block ore) {
        int range = range(player.getMainHandItem());
        if (range <= 0) {
            return 0;
        }
        if (WeatherManager.current() == Weather.AURORE) {
            range *= 2;
        }
        List<BlockPos> echoes = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (dx * dx + dy * dy + dz * dz > range * range) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(ore) && !exposed(level, cursor)) {
                        echoes.add(cursor.immutable());
                    }
                }
            }
        }
        if (echoes.isEmpty()) {
            return 0;
        }
        echoes.sort((a, b) -> Double.compare(a.distSqr(centre), b.distSqr(centre)));
        if (echoes.size() > MAX_ECHOES) {
            echoes = echoes.subList(0, MAX_ECHOES);
        }
        for (BlockPos echo : echoes) {
            Jalons.place(level, echo, ECHO_TICKS);
            // le carillon, a l'endroit meme du filon : on l'entend d'ou il est,
            // et plus haut quand il est proche
            float near = (float) Math.sqrt(echo.distSqr(centre)) / range;
            level.playSound(null, echo, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                    0.8F, 1.6F - 0.6F * near);
        }
        return echoes.size();
    }

    /**
     * Un filon deja a l'air libre n'a pas besoin d'echo : on le voit. La
     * resonance ne sert qu'a ce qui est CACHE.
     */
    private static boolean exposed(ServerLevel level, BlockPos pos) {
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            if (level.getBlockState(pos.relative(side)).isAir()) {
                return true;
            }
        }
        return false;
    }

    /** La portee de l'outil en main : la pioche fait la difference, pas la main nue. */
    private static int range(ItemStack stack) {
        if (!(stack.getItem() instanceof TieredItem tiered)) {
            return 0;
        }
        Tier tier = tiered.getTier();
        if (tier == Tiers.WOOD || tier == Tiers.GOLD) {
            return 2;
        }
        if (tier == Tiers.STONE) {
            return 3;
        }
        if (tier == Tiers.IRON) {
            return 4;
        }
        if (tier == Tiers.DIAMOND) {
            return 8;
        }
        // netherite, et tout ce qu'un mod met au-dessus
        return 12;
    }
}
