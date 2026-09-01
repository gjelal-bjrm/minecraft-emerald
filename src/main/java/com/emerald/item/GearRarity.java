package com.emerald.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Les niveaux de rarete, a la maniere de NosTale.
 *
 * Une piece d'equipement porte un rang de zero a huit. Le rang ne change ni ce
 * que l'objet fait ni comment on s'en sert : il en ameliore les chiffres, d'un
 * cran a la fois, et il se voit au premier coup d'oeil -- un mot devant le nom
 * et une couleur.
 *
 * L'ECART EST FAIBLE A DESSEIN. Quarante centiemes de degat par rang, soit
 * trois et deux dixiemes du normal au Phenomenal : de quoi rendre une montee
 * desirable sans qu'une piece de rang huit rende inutile tout ce qu'on trouvera
 * ensuite. Le mode dure une heure ; une arme qui double ses degats la
 * termine toute seule.
 *
 * Les rangs sept et huit ne s'obtiennent pas par patience mais par quantite :
 * voir {@link #roll}. C'est ce qui donne son sel au systeme -- on peut toujours
 * tenter, jamais garantir.
 */
public enum GearRarity {

    NORMAL("", 0xFFFFFF, ChatFormatting.WHITE),
    UTILE("utile", 0xA0C8FF, ChatFormatting.WHITE),
    BON("bon", 0x6FD1FF, ChatFormatting.AQUA),
    BONNE_QUALITE("bonne_qualite", 0x5CE68A, ChatFormatting.GREEN),
    EXCELLENT("excellent", 0xC8F050, ChatFormatting.YELLOW),
    ANCESTRAL("ancestral", 0xFFD24A, ChatFormatting.GOLD),
    MYSTERIEUX("mysterieux", 0xC77DFF, ChatFormatting.LIGHT_PURPLE),
    LEGENDAIRE("legendaire", 0xFF9B3D, ChatFormatting.GOLD),
    PHENOMENAL("phenomenal", 0xFF4D6D, ChatFormatting.RED);

    /** Ce que chaque rang ajoute aux degats d'une arme. */
    public static final double DAMAGE_STEP = 0.40;
    /** Ce que chaque rang ajoute a l'armure d'une piece. */
    public static final double ARMOR_STEP = 0.35;

    private static final String TAG = "ArcenciumRarity";

    private final String key;
    private final int colour;
    private final ChatFormatting style;

    GearRarity(String key, int colour, ChatFormatting style) {
        this.key = key;
        this.colour = colour;
        this.style = style;
    }

    public int rank() {
        return ordinal();
    }

    public int colour() {
        return colour;
    }

    /** Le mot qui precede le nom, ou vide pour le rang normal. */
    public Component label() {
        return key.isEmpty() ? Component.empty()
                : Component.translatable("rarity.emeraldweapons." + key);
    }

    // ------------------------------------------------------------- la pile

    /** Le rang d'une piece. NORMAL par defaut, y compris pour ce qui n'en porte pas. */
    public static GearRarity of(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag();
        int rank = tag.getInt(TAG);
        GearRarity[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, rank))];
    }

    /**
     * Ecrit le rang, et refait le nom.
     *
     * Le nom passe par le composant CUSTOM_NAME plutot que par une surcharge de
     * getName : ainsi le mot et sa couleur suivent la piece partout -- coffres,
     * infobulles, tchat, cadres d'objet -- sans qu'aucun ecran n'ait a le
     * savoir. Une piece renommee par le joueur perd son nom ; c'est le prix, et
     * il est mince devant la lisibilite gagnee.
     */
    public static void set(ItemStack stack, GearRarity rarity) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag();
        tag.putInt(TAG, rarity.rank());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        if (rarity == NORMAL) {
            stack.remove(DataComponents.CUSTOM_NAME);
            return;
        }
        MutableComponent named = Component.empty()
                .append(rarity.label())
                .append(" ")
                .append(Component.translatable(stack.getItem().getDescriptionId()))
                .withStyle(style -> style.withColor(rarity.colour()).withItalic(false));
        stack.set(DataComponents.CUSTOM_NAME, named);
    }

    // ------------------------------------------------------------- le tirage

    /**
     * Le tirage d'une tentative.
     *
     * Le principe est celui de NosTale, et c'est ce qui le rend supportable :
     * on ne monte pas d'un cran, on TIRE un rang, et plus l'on met de matiere
     * plus la loi penche vers le haut. Une tentative maigre donne presque
     * toujours du bas de table ; une tentative riche ouvre le haut sans jamais
     * le promettre.
     *
     * Concretement, chaque eclat ajoute une chance de tirer a nouveau et de
     * garder le meilleur. Huit eclats ne garantissent donc pas le rang huit --
     * ils rendent seulement le mauvais tirage improbable, ce qui est
     * exactement la sensation recherchee.
     *
     * On ne DESCEND jamais : un rang acquis est acquis. Perdre une piece
     * amelioree sur un mauvais jet serait juste dans un jeu qui dure des mois,
     * pas dans une partie d'une heure.
     */
    public static GearRarity roll(GearRarity current, int shards, RandomSource random) {
        int best = current.rank();
        for (int i = 0; i < Math.max(1, shards); i++) {
            best = Math.max(best, draw(random));
        }
        return values()[Math.min(values().length - 1, best)];
    }

    /**
     * La loi d'un jet seul, rang par rang, en dix-millemes.
     *
     * UNE TABLE, et non une formule. J'avais pris une puissance quatrieme en
     * croyant qu'elle rendait le Phenomenal exceptionnel ; la mesure disait
     * un virgule six pour cent des le premier eclat, et vingt-deux pour cent du
     * Legendaire a huit. Une courbe se raisonne mal, une table se lit.
     *
     * Ces nombres-ci se lisent donc directement : plus de la moitie des jets ne
     * donnent rien, et le Phenomenal sort cinq fois sur dix mille. Comme on
     * garde le MEILLEUR de plusieurs jets, la quantite fait tout le travail --
     * a soixante-quatre eclats, le Legendaire sort environ une fois sur six et
     * le Phenomenal une fois sur trente.
     */
    private static final int[] WEIGHTS = {5500, 2000, 1200, 700, 350, 160, 60, 25, 5};

    private static int draw(RandomSource random) {
        int roll = random.nextInt(10000);
        int seen = 0;
        for (int rank = 0; rank < WEIGHTS.length; rank++) {
            seen += WEIGHTS[rank];
            if (roll < seen) {
                return rank;
            }
        }
        return 0;
    }

    /**
     * Les chances, en centiemes, d'atteindre au moins ce rang avec tant d'eclats.
     *
     * On garde le meilleur de N jets : la probabilite de RATER est celle d'un
     * jet, elevee a la puissance N. C'est tout le systeme -- on n'achete pas un
     * rang, on achete des essais.
     */
    public static int oddsPercent(int rank, int shards) {
        int atLeast = 0;
        for (int r = Math.max(0, rank); r < WEIGHTS.length; r++) {
            atLeast += WEIGHTS[r];
        }
        double single = atLeast / 10000.0;
        double none = Math.pow(1.0 - single, Math.max(1, shards));
        return (int) Math.round((1.0 - none) * 100.0);
    }
}
