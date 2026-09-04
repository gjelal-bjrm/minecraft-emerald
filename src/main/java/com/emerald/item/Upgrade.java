package com.emerald.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * L'amelioration, de +1 a +10 : le troisieme systeme qui touche une piece.
 *
 * Trois systemes, trois questions differentes, et c'est ce qui les rend
 * compatibles plutot que redondants :
 *
 *   - la RARETE dit ce que la piece EST (Utile a Phenomenal), et commande le
 *     rang des runes qu'elle accepte ;
 *   - les RUNES et les ARTEFACTS disent ce qu'elle FAIT ;
 *   - l'AMELIORATION dit seulement de combien elle frappe ou protege PLUS.
 *
 * LA FORME VIENT DE NOSTALE, LES CHIFFRES SONT LES NOTRES. Le releve montait
 * jusqu'a +200 % au dixieme cran : le joueur l'a juge « beaucoup trop », et la
 * mesure lui donne raison -- une arme qui triple ses degats termine seule une
 * partie d'une heure et demie, et tout ce qu'on a bati par ailleurs (rarete,
 * runes, fiche du Heros) devient decoratif.
 *
 * Le plafond descend donc a +110 %, et la FORME est conservee, car c'est elle
 * qui compte : les sept premiers crans montent doucement -- cinq, neuf,
 * treize... -- puis le huitieme saute a cinquante-cinq et le dixieme a cent dix.
 * Les trois derniers valent a eux seuls plus que les sept premiers reunis
 * (240 contre 139), et c'est bien a partir du +8 qu'on gagne gros.
 *
 * C'est cette courbe qui fait tout l'interet du systeme : un +7 est une piece
 * correcte qu'on obtient sans y penser, un +10 est un evenement. Une
 * progression reguliere aurait donne dix ameliorations tiedes et aucune
 * histoire a raconter.
 */
public final class Upgrade {

    public static final int MAX = 10;

    /**
     * Ce que chaque cran ajoute, en pour cent des degats ou de l'armure.
     *
     * L'indice zero vaut zero : une piece non amelioree n'ajoute rien.
     */
    private static final int[] BONUS = {0, 5, 9, 13, 18, 24, 31, 39, 55, 75, 110};

    /**
     * Les chances de reussite, en pour cent, pour passer AU cran suivant.
     *
     * L'indice est le niveau ACTUEL : depuis zero on reussit neuf fois sur dix,
     * depuis neuf une fois sur vingt.
     *
     * La courbe est l'inverse de celle des gains, et c'est voulu : ce qui
     * rapporte le plus coute le plus cher a obtenir. Sans cela, les trois
     * derniers crans seraient simplement les trois meilleurs, et il n'y aurait
     * rien a decider -- seulement a attendre.
     */
    private static final int[] ODDS = {90, 82, 74, 62, 52, 44, 36, 26, 18, 10};

    /**
     * UN ECHEC NE FAIT PAS REDESCENDRE. Il coute la pierre et le metal, rien
     * de plus.
     *
     * J'avais commence par faire retomber d'un cran a partir du septieme, pour
     * la tension. La mesure a tranche : la marche aleatoire qui en resultait
     * demandait TROIS MILLE CENT PIERRES et onze mille diamants pour un +10 --
     * c'est-a-dire jamais, dans un mode qui dure une heure. Une tension qu'on
     * n'atteint pas n'est pas une tension.
     *
     * La difficulte des derniers crans passe donc entierement par la table
     * ci-dessus : une chance sur dix au dernier pas. Cela suffit, et cela reste
     * lisible -- le joueur sait ce qu'il risque, et ce qu'il risque est du
     * materiel, jamais son arme.
     *
     * Mesure du bareme actuel, en partant de zero :
     *
     *    +5  :  7 pierres
     *    +8  : 15 pierres,  38 diamants
     *    +9  : 21 pierres,  11 netherite
     *    +10 : 29 pierres,  60 lingots d'Arcencium
     *
     * Une partie rapporte une soixantaine de pierres : le +10 est donc
     * atteignable, et coute a peu pres tout ce qu'on ramasse.
     */
    private static final String TAG = "ArcenciumUpgrade";

    /**
     * Ce qu'il faut fondre pour atteindre chaque cran.
     *
     * UNE ECHELLE DE METAUX, du fer a l'Arcencium. Elle fait deux choses d'un
     * seul geste : elle donne aux metaux vanilla une raison d'exister dans un
     * mode d'une heure -- sans elle, on ne ramasse plus de fer passe la
     * cinquieme minute -- et elle borne naturellement la progression. On ne
     * monte pas un +9 avant d'avoir trouve de la netherite, quelle que soit la
     * chance qu'on ait.
     *
     * Les quantites montent a l'interieur de chaque palier, de sorte que le
     * troisieme cran d'un metal coute le double du premier. Le changement de
     * metal, lui, remet le compteur bas : on respire en changeant d'echelon.
     */
    public record Cost(net.minecraft.world.item.Item material, int amount) {
    }

    public static Cost cost(int target) {
        return switch (Math.max(1, Math.min(MAX, target))) {
            case 1 -> new Cost(net.minecraft.world.item.Items.IRON_INGOT, 4);
            case 2 -> new Cost(net.minecraft.world.item.Items.IRON_INGOT, 6);
            case 3 -> new Cost(net.minecraft.world.item.Items.IRON_INGOT, 9);
            case 4 -> new Cost(net.minecraft.world.item.Items.GOLD_INGOT, 4);
            case 5 -> new Cost(net.minecraft.world.item.Items.GOLD_INGOT, 6);
            case 6 -> new Cost(net.minecraft.world.item.Items.GOLD_INGOT, 9);
            case 7 -> new Cost(net.minecraft.world.item.Items.DIAMOND, 4);
            case 8 -> new Cost(net.minecraft.world.item.Items.DIAMOND, 7);
            case 9 -> new Cost(net.minecraft.world.item.Items.NETHERITE_INGOT, 2);
            default -> new Cost(ModItems.ARCENCIUM_INGOT.get(), 6);
        };
    }

    /** Combien de ce materiau le joueur possede. */
    public static int carried(net.minecraft.world.entity.player.Player player, Cost cost) {
        int total = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack held = inventory.getItem(slot);
            if (held.is(cost.material())) {
                total += held.getCount();
            }
        }
        return total;
    }

    /** Vrai si le joueur peut payer le passage au cran suivant. */
    public static boolean affordable(net.minecraft.world.entity.player.Player player,
                                     ItemStack gear) {
        int level = of(gear);
        if (level >= MAX) {
            return false;
        }
        Cost cost = cost(level + 1);
        return carried(player, cost) >= cost.amount();
    }

    /**
     * Preleve le materiau. Rend faux si la reserve etait insuffisante.
     *
     * On preleve AVANT de tirer, jamais apres : un tirage qui se solderait par
     * un echec de paiement laisserait la piece amelioree sans que rien n'ait
     * ete depense.
     */
    public static boolean charge(net.minecraft.world.entity.player.Player player,
                                 ItemStack gear) {
        int level = of(gear);
        if (level >= MAX) {
            return false;
        }
        Cost cost = cost(level + 1);
        int owed = cost.amount();
        if (carried(player, cost) < owed) {
            return false;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && owed > 0; slot++) {
            ItemStack held = inventory.getItem(slot);
            if (!held.is(cost.material())) {
                continue;
            }
            int taken = Math.min(owed, held.getCount());
            held.shrink(taken);
            owed -= taken;
        }
        return true;
    }

    private Upgrade() {
    }

    /** Le niveau d'amelioration d'une piece, de zero a dix. */
    public static int of(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag();
        return Math.max(0, Math.min(MAX, tag.getInt(TAG)));
    }

    /** Ecrit le niveau, et refait le nom. */
    public static void set(ItemStack stack, int level) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag();
        tag.putInt(TAG, Math.max(0, Math.min(MAX, level)));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        GearName.refresh(stack);
    }

    /** Ce que ce niveau ajoute, en fraction : 0,90 pour un +8. */
    public static double bonus(int level) {
        return BONUS[Math.max(0, Math.min(MAX, level))] / 100.0;
    }

    /** Ce que porte cette piece, en fraction. */
    public static double bonus(ItemStack stack) {
        return bonus(of(stack));
    }

    /** Les chances de reussir le passage au cran suivant, en pour cent. */
    public static int odds(int level) {
        return odds(level, com.emerald.weather.WeatherManager.current()
                == com.emerald.weather.Weather.HEURE_DOREE);
    }

    /**
     * L'HEURE DOREE AJOUTE QUINZE POINTS.
     *
     * Pas un multiplicateur : quinze points fixes. Sur un +9 a 10 %, cela fait
     * 25 % -- deux fois et demie plus de chances, et c'est enorme ; sur un +1 a
     * 95 %, cela ne change presque rien. C'est exactement le bon sens de
     * l'effet : la fenetre vaut pour les paris qu'on n'ose pas, pas pour ceux
     * qu'on gagne de toute facon.
     *
     * L'infobulle affiche donc le chiffre DU MOMENT : on voit la fenetre
     * s'ouvrir sans avoir a la connaitre.
     */
    public static int odds(int level, boolean golden) {
        if (level >= MAX) {
            return 0;
        }
        int base = ODDS[Math.max(0, level)];
        return golden ? Math.min(100, base + 15) : base;
    }

    /** Vrai si l'on approche du sommet : sert a prevenir dans l'infobulle. */
    public static boolean risky(int level) {
        return level >= 8;
    }

    /**
     * Ce que donne UNE tentative.
     *
     * @return le nouveau niveau, qui peut etre le meme ou un de moins
     */
    public static int attempt(int level, RandomSource random) {
        if (level >= MAX) {
            return level;
        }
        return random.nextInt(100) < odds(level) ? level + 1 : level;
    }
}
