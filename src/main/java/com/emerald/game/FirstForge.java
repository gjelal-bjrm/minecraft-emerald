package com.emerald.game;

import com.emerald.block.ModBlocks;
import com.emerald.item.GearRarity;
import com.emerald.item.ModItems;
import com.emerald.item.Upgrade;
import com.emerald.mine.Jalons;
import com.emerald.mine.Underground;
import com.emerald.specialization.Specialization;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * LA PREMIERE FORGE : ce que le village donne quand on l'a defendu.
 *
 * « Ce serait une introduction a l'utilisation des objets de notre mode pour
 * ameliorer leurs armes, leurs armures et leurs personnages. »
 *
 * Le prologue enseignait a se battre et rien d'autre. On y gagnait trois
 * sanctuaires a prendre, mais aucune raison de toucher aux trois etablis de
 * l'atelier -- et le joueur, faute d'avoir essaye une fois, ne les cherchait
 * meme plus. Chaque defenseur repart donc avec DE QUOI FAIRE LES TROIS GESTES,
 * une fois chacun, et avec l'endroit ou les faire :
 *
 *   - le metal de deux ameliorations au premier cran (une piece defensive,
 *     une arme) -- exactement ce que la Forge demande, ni plus ni moins ;
 *   - les plumes du premier palier de specialisation ;
 *   - trois lignes qui disent a quoi sert chaque etabli, et l'atelier signale
 *     par des contours lumineux qu'on voit A TRAVERS la roche et les maisons.
 *
 * On donne le materiau, jamais le resultat : le geste reste a faire, et c'est
 * lui qu'on veut enseigner. Une piece deja amelioree n'aurait rien appris.
 */
public final class FirstForge {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Ou chercher les trois etablis autour de la Lame. */
    private static final int SEARCH = 26;
    private static final int SEARCH_UP = 8;
    /** Combien de temps les etablis restent detoures : le temps de revenir et de s'en servir. */
    private static final int GLOW_TICKS = 10 * 60 * 20;
    /** L'or de l'atelier : la meme couleur que la ligne qui l'annonce. */
    private static final int GLOW_COLOUR = 0xFFFFD24A;

    private FirstForge() {
    }

    /**
     * Le village tient : chacun recoit sa premiere forge.
     *
     * Appele une seule fois, a la victoire du prologue.
     */
    public static void award(ServerLevel level, BlockPos village) {
        // LE METAL DE DEUX CRANS, lu dans le bareme et non recopie : si le cout
        // du premier cran change un jour, la recompense suit toute seule.
        Upgrade.Cost first = Upgrade.cost(1);
        int metal = first.amount() * 2;                 // une defense, une arme
        int feathers = Specialization.COST[1];
        // SIX ECLATS DU DESTIN, et le compte n'est pas rond par hasard : c'est
        // PITY_PER_DRAW (GearRarity), donc six essais d'un coup, et 45 % de
        // chances d'atteindre Splendide sur la piece qu'on y met. Le joueur
        // ressortait du village sans rien pour la RARETE -- « je peux passer
        // l'arme +1 et monter la specialisation, mais pas la rarete » -- et
        // les Eclats ne tombent qu'une fois sur douze au combat.
        int shards = GearRarity.pityPerDraw();

        for (ServerPlayer player : level.players()) {
            give(player, new ItemStack(first.material(), metal));
            give(player, new ItemStack(ModItems.ARCENCIUM_FEATHER.get(), feathers));
            give(player, new ItemStack(ModItems.FATE_SHARD.get(), shards));
            // ET LE BOIS DE PRISME. Toutes nos recettes en demandent -- une
            // branche ou une fibre par piece -- et il ne pousse que dans les
            // bosquets de prisme et les coffres des sanctuaires. Un joueur qui
            // n'avait croise ni l'un ni l'autre ne pouvait fabriquer AUCUNE
            // arme, quel que soit l'Arcencium ramasse. De quoi monter une arme
            // et une piece d'armure, pour que la porte ne reste jamais fermee.
            give(player, new ItemStack(ModItems.PRISM_BRANCH.get(), 4));
            give(player, new ItemStack(ModItems.PRISM_FIBER.get(), 2));
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.first_forge")
                    .withStyle(style -> style.withColor(0xFFD24A).withBold(true)));
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.first_forge.forge",
                            metal, first.material().getDescription())
                    .withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.first_forge.altar", feathers)
                    .withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.first_forge.bench",
                            shards, com.emerald.item.GearRarity.oddsPercent(3, shards))
                    .withStyle(ChatFormatting.GRAY));
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
        highlight(level, village);
    }

    /** Dans le sac, ou aux pieds si le sac est plein : rien ne se perd. */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * L'ATELIER, QU'ON VOIT ENFIN.
     *
     * « J'ai du mal a les trouver dans le village. » Trois blocs poses au ras du
     * sol, a douze blocs de la Lame, au milieu d'un village entier : rien ne
     * les distinguait. On les detoure donc -- un marqueur brillant sur chacun,
     * visible A TRAVERS les murs, dix minutes durant -- et l'on dit ou ils
     * sont, en clair, avec la direction et la distance.
     */
    public static void highlight(ServerLevel level, BlockPos village) {
        // L'ATELIER SE SOUVIENT DE SON CENTRE (GameState) : on cherche autour de
        // LUI, et non autour de la Lame. La recherche autour du village reste
        // la porte de secours des parties commencees avant qu'on le retienne.
        BlockPos centre = GameState.get(level).workshop();
        BlockPos from = centre.equals(BlockPos.ZERO) ? village : centre;
        BlockPos forge = find(level, from, ModBlocks.ARCENCIUM_FORGE.get());
        BlockPos bench = find(level, from, ModBlocks.SOCKET_BENCH.get());
        BlockPos altar = find(level, from, ModBlocks.SPECIALIZATION_ALTAR.get());
        LOGGER.info("Atelier signale : forge {} ; etabli {} ; autel {}", forge, bench, altar);
        BlockPos any = forge != null ? forge : bench != null ? bench : altar;
        if (any == null) {
            return;                                    // pas d'atelier : rien a montrer
        }
        for (BlockPos station : new BlockPos[]{forge, bench, altar}) {
            if (station != null) {
                Jalons.glow(level, station, level.getBlockState(station), GLOW_TICKS, GLOW_COLOUR);
            }
        }
        int distance = (int) Math.round(Underground.flat(village, any));
        Component line = Component.translatable("game.emeraldweapons.first_forge.where",
                        distance, Finale.cardinal(any.getX() - village.getX(), any.getZ() - village.getZ()),
                        any.getX(), any.getY(), any.getZ())
                .withStyle(style -> style.withColor(0x9CE8FF));
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(line);
        }
    }

    /** Le poste le plus proche de la Lame, ou rien s'il n'y en a pas dans le perimetre. */
    @Nullable
    private static BlockPos find(ServerLevel level, BlockPos village, Block wanted) {
        BlockPos best = null;
        double nearest = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SEARCH; dx <= SEARCH; dx++) {
            for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                for (int dy = -SEARCH_UP; dy <= SEARCH_UP; dy++) {
                    cursor.set(village.getX() + dx, village.getY() + dy, village.getZ() + dz);
                    if (!level.hasChunk(cursor.getX() >> 4, cursor.getZ() >> 4)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(wanted)) {
                        continue;
                    }
                    double d = cursor.distSqr(village);
                    if (d < nearest) {
                        nearest = d;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best;
    }

    /** Les trois postes de l'atelier, pour qui veut les montrer ailleurs. */
    public static List<BlockPos> stations(ServerLevel level, BlockPos village) {
        BlockPos centre = GameState.get(level).workshop();
        BlockPos from = centre.equals(BlockPos.ZERO) ? village : centre;
        List<BlockPos> found = new ArrayList<>();
        for (Block block : new Block[]{ModBlocks.ARCENCIUM_FORGE.get(),
                ModBlocks.SOCKET_BENCH.get(), ModBlocks.SPECIALIZATION_ALTAR.get()}) {
            BlockPos at = find(level, from, block);
            if (at != null) {
                found.add(at);
            }
        }
        return found;
    }
}
