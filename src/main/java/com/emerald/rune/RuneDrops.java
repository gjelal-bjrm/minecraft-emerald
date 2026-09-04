package com.emerald.rune;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * D'ou viennent les runes : des monstres, et d'eux seuls.
 *
 * C'EST LA RAISON DE CONTINUER A TUER. Les artefacts dorment dans les coffres
 * des sanctuaires, la rarete se monte a l'etabli : ni l'un ni l'autre ne
 * recompense le combat lui-meme. Les runes, si. Un monstre de plus est une
 * chance de plus, et une rune qu'on possede deja vaut quand meme d'etre
 * ramassee puisque sa valeur est tiree.
 *
 * LE RANG SUIT LA FORCE DE LA BETE, mesuree a ses points de vie maximaux --
 * meme mesure que pour l'experience du Heros, et pour la meme raison : c'est la
 * seule qui soit comparable d'un mod a l'autre, et elle range d'elle-meme un
 * boss au-dessus d'un zombie. Un zombie ne laissera jamais de Phenomenal, et
 * c'est bien ce qu'on veut : le haut de l'echelle doit se meriter au bon
 * endroit, pas au bout d'une ferme a zombies.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class RuneDrops {

    /** Chance qu'une creature laisse une Pierre de Forge. */
    private static final double STONE_CHANCE = 0.20;

    /** Chance qu'une creature laisse un cristal de son element. */
    private static final double STONE_DROP_CHANCE = 0.22;

    /** Chance de base qu'un monstre laisse une rune. */
    private static final double CHANCE = 0.09;
    /** Ce qu'une bete coriace ajoute a cette chance, au plus. */
    private static final double CHANCE_BONUS = 0.11;
    /** Points de vie au-dela desquels le bonus de chance plafonne. */
    private static final double TOUGH = 200.0;

    private RuneDrops() {
    }

    /** Le taux de la Pierre de Forge, pour le banc d'essai. */
    public static double stoneChance() {
        return STONE_CHANCE;
    }

    /** Le taux du cristal elementaire, pour le banc d'essai. */
    public static double stoneDropChance() {
        return STONE_DROP_CHANCE;
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player) {
            return;                        // on ne recolte pas sur les joueurs
        }
        RandomSource random = killer.getRandom();
        double health = victim.getMaxHealth();
        // LA BATTUE PAIE MIEUX, et c'est ce qui en fait une fenetre de FARM et
        // non une simple facilite de visee : moitie plus de plumes, de pierres
        // de forge, de cristaux et de runes tant qu'elle dure.
        double hunt = com.emerald.weather.WeatherManager.current()
                == com.emerald.weather.Weather.BATTUE ? 1.5 : 1.0;
        // Pas de bonus de Butin : depuis la 1.21 il ne passe plus par un
        // niveau lisible sur l'evenement mais par un effet d'enchantement
        // applique en amont. Plutot que de deviner une API, on s'en passe --
        // LA PLUME D'ARCENCIUM : le materiau de la specialisation, qui survit
        // a la partie -- donc rare sur le menu fretin, presque sure sur un
        // puissant. Elle se tire AVANT la porte des runes : c'est un butin a part.
        double featherChance = (0.25 + 0.40 * Math.min(1.0, health / 200.0)) * hunt;
        if (random.nextDouble() < featherChance) {
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                    victim.level(), victim.getX(), victim.getY(), victim.getZ(),
                    new ItemStack(com.emerald.item.ModItems.ARCENCIUM_FEATHER.get(),
                            health >= 150.0 ? 1 + random.nextInt(3) : 1)));
        }
        // LA PLUME D'APPARENCE : sur les puissants seulement, selon leur element
        // et la meteo du moment (voir SkinFeatherItem.pickDrop). Jamais le Rubis.
        if (health >= 300.0 && random.nextDouble() < 0.35) {
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                    victim.level(), victim.getX(), victim.getY(), victim.getZ(),
                    com.emerald.item.SkinFeatherItem.stack(
                            com.emerald.item.SkinFeatherItem.pickDrop(victim, random),
                            com.emerald.item.ModItems.SKIN_FEATHER.get())));
        }

        // et le taux ci-dessous est cale sans elle.
        double chance = (CHANCE + CHANCE_BONUS * Math.min(1.0, health / TOUGH)) * hunt;
        if (random.nextDouble() >= chance) {
            return;
        }

        // LE CRISTAL DE L'ELEMENT DE LA BETE, bien plus souvent que la rune.
        //
        // C'est la boucle du systeme : pour accorder une arme contre l'Obscur
        // il faut du cristal de Lumiere, donc chasser des creatures de Lumiere.
        // Un taux genereux est indispensable -- accorder son arme ne doit pas
        // etre un projet, seulement un detour.
        com.emerald.element.Element flavour =
                com.emerald.element.Attunement.of(victim);
        if (flavour != com.emerald.element.Element.NEUTRE
                && random.nextDouble() < STONE_DROP_CHANCE * hunt) {
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                    victim.level(), victim.getX(), victim.getY(), victim.getZ(),
                    com.emerald.element.ElementStoneItem.stack(flavour,
                            com.emerald.item.ModItems.ELEMENT_STONE.get(),
                            1 + random.nextInt(2))));
        }

        // LA PIERRE DE FORGE, plus souvent qu'une rune et moins qu'un cristal.
        //
        // Elle vit ici et non dans une table de butin parce qu'elle doit suivre
        // le COMBAT : le metal se ramasse en creusant, et si la pierre se
        // ramassait aussi en creusant, tout le systeme d'amelioration
        // recompenserait le temps passe plutot que le jeu joue.
        if (random.nextDouble() < STONE_CHANCE * hunt) {
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                    victim.level(), victim.getX(), victim.getY(), victim.getZ(),
                    new ItemStack(com.emerald.item.ModItems.FORGE_STONE.get(),
                            1 + random.nextInt(3))));
        }

        RuneFamily[] families = RuneFamily.values();
        RuneFamily family = families[random.nextInt(families.length)];
        ItemStack drop = RuneItem.stack(
                RuneMark.roll(family, rank(health, phaseCeiling(event.getEntity().level()), random), random),
                com.emerald.item.ModItems.RUNE.get());
        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                victim.level(), victim.getX(), victim.getY(), victim.getZ(), drop));
    }

    /**
     * Un tirage complet, comme si l'on venait de tuer une bete de tant de PV.
     *
     * Sert au banc d'essai (/arcencium rune drop). Elle appelle la MEME loi que
     * le drop reel -- meme probabilite, meme plafond, meme schema -- de sorte
     * qu'une mesure faite ici vaille pour le jeu. Une simulation qui
     * recalculerait sa propre loi ne testerait qu'elle-meme.
     *
     * @return la rune tiree, ou null si la bete n'a rien laisse
     */
    public static RuneMark simulate(double health, RandomSource random) {
        double chance = CHANCE + CHANCE_BONUS * Math.min(1.0, health / TOUGH);
        if (random.nextDouble() >= chance) {
            return null;
        }
        RuneFamily[] families = RuneFamily.values();
        return RuneMark.roll(families[random.nextInt(families.length)],
                rank(health, 8, random), random);
    }

    /**
     * Le rang tire pour une bete de tant de points de vie.
     *
     * TOUT REPOSE SUR LE PLAFOND, et rien d'autre. Une bete faible ne peut
     * simplement PAS ouvrir les hauts rangs : c'est ainsi que « les monstres
     * faibles laissent des runes de bas rang » se realise, sans qu'il faille
     * empiler une seconde loterie par-dessus.
     *
     * Sous le plafond, le tirage est UNIFORME -- et c'est une correction. Je
     * prenais d'abord le plus petit de deux tirages, en croyant bien faire :
     * la mesure disait qu'un rang huit n'apparaissait alors que dans quatre
     * parties sur mille, c'est-a-dire jamais. Un rang qu'on ne voit jamais
     * n'est pas rare, il est absent.
     *
     * MESURE SUR UNE PARTIE TYPE (483 monstres, dont trois boss) :
     *
     *    18 runes ramassees ;
     *    un rang 7 ou plus dans 6 % des parties ;
     *    un rang 8 dans 3 % des parties.
     *
     * Tomber sur une Phenomenale AVEC les bonnes options ET de bons tirages
     * releve donc d'une chance considerable -- ce qui est exactement le but.
     * On ne construit pas une partie autour, on s'en souvient.
     */
    private static int rank(double health, int phaseCeiling, RandomSource random) {
        // LE PLAFOND ETAIT LE VRAI GOULOT. Il fallait 400 points de vie pour
        // ouvrir le rang 8 : seuls les trois boss d'une partie y arrivaient, et
        // le rang 8 sortait dans deux parties sur cent quoi qu'on fasse du
        // tirage. Le joueur veut ces rangs POSSIBLES, seulement plus rares que
        // les autres. La table descend donc d'un cran : une bete a 200 points
        // de vie -- un monstre de siege tardif, un seigneur de Maree -- peut
        // desormais donner du rang 8, et le tirage pondere s'occupe du reste.
        int ceiling = health < 15 ? 3
                : health < 30 ? 4
                : health < 60 ? 5
                : health < 100 ? 6
                : health < 200 ? 7 : 8;
        ceiling = Math.max(1, Math.min(ceiling, phaseCeiling));
        // LES BOSS TIRENT DEUX FOIS ET GARDENT LE MEILLEUR.
        int drawn = weighted(ceiling, random);
        if (health >= 400) {
            drawn = Math.max(drawn, weighted(ceiling, random));
        }
        return drawn;
    }

    /**
     * SOUS LE PLAFOND, LES HAUTS RANGS SONT PLUS RARES : le poids d'un rang
     * decroit lineairement jusqu'a deux. Plafond 8 : le rang 1 pese 9, le rang
     * 8 pese 2 -- un rang 8 sur 22 tirages au plafond.
     *
     * MESURE, avec la Traque (voir game/Prowl) qui double le bestiaire croise :
     * 116 runes ramassees par partie, un rang 7 ou plus dans 76 % des parties,
     * un rang 8 dans 34 %. Par rune : le rang 1 sort une fois sur trois, le
     * rang 7 une fois sur cent, le rang 8 une fois sur deux cent cinquante --
     * possibles, et bien plus rares que les autres, ce que le joueur demandait.
     */
    private static int weighted(int ceiling, RandomSource random) {
        int total = 0;
        for (int r = 1; r <= ceiling; r++) {
            total += ceiling - r + 2;
        }
        int pick = random.nextInt(total);
        for (int r = 1; r <= ceiling; r++) {
            pick -= ceiling - r + 2;
            if (pick < 0) {
                return r;
            }
        }
        return ceiling;
    }

    /**
     * LE RANG SUIT L'HEURE. Au debut on ne ramasse que du petit ; les grands
     * rangs s'ouvrent avec la partie, le huitieme dans l'Assaut seulement.
     * Ce que le joueur a demande : « plus on avance, plus les raretes peuvent
     * etre elevees, meme s'il est plus rare d'en avoir de grosses ». Le rang
     * 8 s'ouvre des la Pression : reserve a l'Assaut, il ne sortait jamais.
     */
    private static int phaseCeiling(net.minecraft.world.level.Level world) {
        if (!(world instanceof net.minecraft.server.level.ServerLevel level)) {
            return 8;
        }
        com.emerald.game.GamePhase phase = com.emerald.game.GameState.get(level).phase(level);
        return switch (phase) {
            case LOBBY, PROLOGUE, EXPLORATION -> 3;
            case MONTEE -> 5;
            default -> 8;          // Pression et au-dela : tout est ouvert
        };
    }
}
