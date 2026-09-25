package com.emerald.game;

import com.emerald.rune.RuneFamily;
import com.emerald.rune.RuneMark;
import com.emerald.rune.Runes;

import com.emerald.item.GearRarity;
import com.emerald.item.Upgrade;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * L'equipement des monstres, indexe sur le STADE de la partie.
 *
 * SANS CELA, TOUT LE RESTE CASSE LE JEU. Le joueur monte sa fiche de Heros, sa
 * rarete, ses runes et ses ameliorations pendant une heure ; si le bestiaire ne
 * bouge pas, la quarantieme minute devient une promenade et les trois systemes
 * qu'on vient de batir ne servent plus qu'a se regarder progresser.
 *
 * On equipe donc les assaillants avec les MEMES systemes que le joueur : la
 * meme rarete, la meme amelioration, les memes tables. C'est ce qui garantit
 * que le bestiaire suit exactement la courbe du joueur -- si l'on retouche le
 * bareme d'amelioration demain, les monstres en profitent le jour meme, sans
 * qu'on ait a s'en souvenir.
 *
 * L'ECHELLE EST CELLE DES METAUX VANILLA, du cuir a la netherite, et non de
 * l'Arcencium. Deux raisons : l'Arcencium est ce que le joueur convoite, et le
 * voir sur chaque zombie le banaliserait ; et une piece de fer amelioree reste
 * un butin utile en debut de partie, la ou une piece d'Arcencium serait soit
 * inutile soit trop forte.
 */
public final class MobGear {

    /** Ce qui tombe, rarement : un butin, pas une source. */
    private static final float DROP_CHANCE = 0.015F;

    /** Les cinq echelons, du plus tendre au plus dur. */
    private record Kit(Item helmet, Item chest, Item legs, Item boots, Item weapon) {
    }

    private static final Kit[] LADDER = {
            new Kit(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE,
                    Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS, Items.STONE_SWORD),
            new Kit(Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE,
                    Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS, Items.IRON_SWORD),
            new Kit(Items.IRON_HELMET, Items.IRON_CHESTPLATE,
                    Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.IRON_SWORD),
            new Kit(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
                    Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.DIAMOND_SWORD),
            new Kit(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
                    Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.NETHERITE_SWORD),
    };

    /**
     * L'ARMURE SATURE, PAS L'ARME. C'est la mesure qui l'a dit.
     *
     * Minecraft plafonne la reduction de degats a vingt points d'armure. Un
     * plastron de diamant complet y arrive DEJA ; l'ameliorer davantage ne
     * change rien du tout, et j'avais d'abord monte le bestiaire jusqu'a
     * trente-huit points d'armure -- dix-huit de purs chiffres perdus, pendant
     * que les monstres devenaient injouables a quatre-vingts pour cent de
     * reduction.
     *
     * L'echelle s'arrete donc au diamant, l'armure ne monte que de trois crans,
     * et c'est l'ARME qui porte la difficulte : les degats, eux, montent
     * lineairement et ne plafonnent jamais. Un monstre de fin de partie n'est
     * pas plus dur a tuer, il est plus dangereux -- ce qui se joue mieux.
     */
    private static final double ARMOUR_MAX_UPGRADE = 3.0;
    private static final double WEAPON_MAX_UPGRADE = 6.0;
    private static final double ARMOUR_MAX_RANK = 4.0;
    private static final double WEAPON_MAX_RANK = 6.0;

    private MobGear() {
    }

    /**
     * Le stade de la partie, de zero a un.
     *
     * TROIS SOURCES, parce qu'aucune ne suffit seule. Le TEMPS ecoule dit ou
     * l'on en est dans l'heure ; les ANCRES tenues disent ce que le joueur a
     * reellement accompli -- un joueur rapide merite une opposition plus dure
     * qu'un joueur qui a traine ; le PALIER du siege dit a quel sanctuaire on
     * s'attaque.
     *
     * On prend la plus grande des trois plutot que leur moyenne : c'est la
     * mesure la plus AVANCEE qui compte. Un joueur qui a pris trois ancres en
     * vingt minutes est un joueur en avance, et lui envoyer des monstres de
     * vingtieme minute serait le punir de sa vitesse en le privant d'adversite.
     */
    public static double stage(ServerLevel level, int tier) {
        GameState state = GameState.get(level);
        // En monde ouvert, le CYCLE remplace l'horloge : chaque tour de piste
        // durcit l'opposition d'un tiers, et le monde ne se durcit plus tout
        // seul pendant qu'on batit.
        double byClock;
        if (state.status() != GameState.Status.RUNNING) {
            byClock = 0.0;
        } else if (state.timed()) {
            byClock = Math.min(1.0, state.elapsed(level) / (double) GameState.GAME_TICKS);
        } else {
            byClock = Math.min(1.0, (state.cycle() - 1) / 3.0);
        }
        double byAnchors = state.anchorsActive() / 3.0;
        double byTier = Math.max(0, tier - 1) / 3.0;
        return Math.min(1.0, Math.max(byClock, Math.max(byAnchors, byTier)));
    }

    /**
     * Habille un assaillant pour ce stade-la.
     *
     * L'echelon, l'amelioration et la rarete montent ENSEMBLE, mais chacun avec
     * sa propre part de hasard : deux zombies de la meme vague ne sont donc pas
     * identiques, et le joueur ne peut pas lire la difficulte d'un coup d'oeil
     * sur le premier venu.
     */
    public static void equip(Mob mob, double stage, RandomSource random) {
        // DEPUIS LE 25 SEPTEMBRE, LE STADE NE COMPTE PLUS : les monstres s'alignent sur les
        // joueurs (MobScaling, cahier §104). Les systemes qui posent leurs monstres -- siege,
        // garnisons, Traque, Echos, Battue -- appellent toujours ici ; on les habille tout de
        // suite, selon la reference des joueurs.
        MobScaling.scaleNow(mob, random);
    }

    /**
     * L'ancien habillage, selon le stade de la partie. Garde pour memoire et pour le banc,
     * qui compare les coups pour tuer avant et apres (ArcenciumAutotest, epreuve 13).
     */
    public static void equipByStage(Mob mob, double stage, RandomSource random) {
        int rung = Math.min(LADDER.length - 1, (int) (stage * LADDER.length));
        Kit kit = LADDER[rung];

        // Toutes les pieces ne sont pas garanties : une opposition entierement
        // equipee des la premiere vague se lit comme un mur, alors qu'un
        // equipement partiel se lit comme une troupe.
        double odds = 0.35 + stage * 0.60;
        put(mob, EquipmentSlot.HEAD, kit.helmet(), stage, odds, random);
        put(mob, EquipmentSlot.CHEST, kit.chest(), stage, odds, random);
        put(mob, EquipmentSlot.LEGS, kit.legs(), stage, odds * 0.85, random);
        put(mob, EquipmentSlot.FEET, kit.boots(), stage, odds * 0.85, random);

        // L'arme ne se donne qu'a ce qui n'en a pas : un squelette garde son
        // arc, un vindicateur sa hache. Les remplacer effacerait ce qui fait
        // leur interet.
        if (mob.getMainHandItem().isEmpty()) {
            put(mob, EquipmentSlot.MAINHAND, kit.weapon(), stage, odds, random);
        }
    }

    // ================================================================ le miroir

    /** L'armure complete de chaque echelon, en points : cuir, maille, fer, diamant, diamant. */
    private static final double[] SET_ARMOUR = {7, 12, 15, 20, 20};

    /**
     * L'echelon d'un monstre : l'armure la plus lourde qui reste SOUS celle des joueurs, avec
     * un dixieme de marge -- un joueur en fer croise de la maille, un joueur en Arcencium
     * ameliore croise du diamant.
     */
    static int rungFor(double armourPoints) {
        int rung = 0;
        for (int i = 0; i < SET_ARMOUR.length; i++) {
            if (SET_ARMOUR[i] <= armourPoints * 0.9) {
                rung = i;
            }
        }
        if (armourPoints >= 26.0) {
            rung = LADDER.length - 1;               // et l'epee de netherite
        }
        return rung;
    }

    /**
     * Habille un monstre sous la reference des joueurs (cahier §104) : rarete de 0 a la leur,
     * amelioration de +0 a la leur, une rune de rang 1 au leur sur l'arme, le casque et chaque
     * piece d'armure. Tous les monstres, meme sans mains : l'equipement ne se voit pas sur
     * une araignee, mais il compte (choix du joueur).
     */
    public static void dress(Mob mob, MobScaling.Reference ref, RandomSource random) {
        Kit kit = LADDER[rungFor(ref.armourPoints())];
        // le casque toujours : sans lui, un mort-vivant brule a midi, et le mode se joue a midi
        mirror(mob, EquipmentSlot.HEAD, kit.helmet(), ref, 1.0, RuneFamily.WEAPON, random);
        mirror(mob, EquipmentSlot.CHEST, kit.chest(), ref, 1.0, RuneFamily.ARMOR, random);
        mirror(mob, EquipmentSlot.LEGS, kit.legs(), ref, 0.85, RuneFamily.ARMOR, random);
        mirror(mob, EquipmentSlot.FEET, kit.boots(), ref, 0.85, RuneFamily.ARMOR, random);
        // l'arme, seulement a qui n'en a pas : un squelette garde son arc, un vindicateur sa hache
        if (mob.getMainHandItem().isEmpty()) {
            mirror(mob, EquipmentSlot.MAINHAND, kit.weapon(), ref, 1.0, RuneFamily.WEAPON, random);
        }
    }

    private static void mirror(Mob mob, EquipmentSlot slot, Item item, MobScaling.Reference ref, double odds,
                               RuneFamily family, RandomSource random) {
        if (random.nextDouble() > odds) {
            return;
        }
        boolean weapon = slot == EquipmentSlot.MAINHAND;
        ItemStack stack = new ItemStack(item);
        int upgrade = upTo(weapon ? ref.weaponUpgrade() : ref.armourUpgrade(), random);
        if (upgrade > 0) {
            Upgrade.set(stack, upgrade);
        }
        int rank = upTo(weapon ? ref.weaponRarity() : ref.armourRarity(), random);
        if (rank > 0) {
            GearRarity.set(stack, GearRarity.values()[Math.min(8, rank)]);
        }
        int runeTop = (int) Math.round(ref.runeRank());
        if (runeTop >= 1) {
            Runes.engrave(stack, RuneMark.roll(family, 1 + random.nextInt(runeTop), random));
        }
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, DROP_CHANCE);
    }

    /** Un entier de 0 a la valeur de reference arrondie, au hasard. */
    private static int upTo(double reference, RandomSource random) {
        int top = (int) Math.round(reference);
        return top <= 0 ? 0 : random.nextInt(top + 1);
    }

    /**
     * Le boss final (cahier §104) : netherite, rarete 8, +8 a +10, une rune de rang 8 a l'arme
     * et a chaque piece d'armure, et PAS DE CASQUE -- le casque porte une rune d'arme, et le
     * joueur n'en veut pas sur le boss.
     */
    public static void dressFinalBoss(Mob mob, RandomSource random) {
        mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        boss(mob, EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, RuneFamily.ARMOR, random);
        boss(mob, EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, RuneFamily.ARMOR, random);
        boss(mob, EquipmentSlot.FEET, Items.NETHERITE_BOOTS, RuneFamily.ARMOR, random);
        if (mob.getMainHandItem().isEmpty()) {
            boss(mob, EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, RuneFamily.WEAPON, random);
        }
    }

    private static void boss(Mob mob, EquipmentSlot slot, Item item, RuneFamily family, RandomSource random) {
        ItemStack stack = new ItemStack(item);
        Upgrade.set(stack, 8 + random.nextInt(3));
        GearRarity.set(stack, GearRarity.PHENOMENAL);
        Runes.engrave(stack, RuneMark.roll(family, 8, random));
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, 0.0F);
    }

    private static void put(Mob mob, EquipmentSlot slot, Item item,
                            double stage, double odds, RandomSource random) {
        if (random.nextDouble() > odds) {
            return;
        }
        ItemStack stack = new ItemStack(item);
        boolean weapon = slot == EquipmentSlot.MAINHAND;

        int upgrade = roll(stage * (weapon ? WEAPON_MAX_UPGRADE : ARMOUR_MAX_UPGRADE), random);
        if (upgrade > 0) {
            Upgrade.set(stack, upgrade);
        }

        // LA RARETE : jusqu'au Mysterieux sur une arme, l'Excellent sur une
        // piece. Ni le Legendaire ni le Phenomenal ne se croisent : ce sont les
        // deux rangs que le joueur poursuit, et les voir sur un zombie les
        // banaliserait.
        int rank = roll(stage * (weapon ? WEAPON_MAX_RANK : ARMOUR_MAX_RANK), random);
        if (rank > 0) {
            GearRarity.set(stack, GearRarity.values()[rank]);
        }

        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, DROP_CHANCE);
    }

    /**
     * Un tirage autour d'une cible, jamais exactement dessus.
     *
     * On tire entre la moitie et la totalite de la cible : la difficulte monte
     * donc franchement avec le stade sans que deux monstres voisins soient
     * jumeaux. Un tirage uniforme de zero a la cible aurait rendu la moitie des
     * assaillants nus meme en fin de partie.
     */
    private static int roll(double target, RandomSource random) {
        int high = (int) Math.floor(target);
        if (high <= 0) {
            return 0;
        }
        int low = high / 2;
        return low + random.nextInt(high - low + 1);
    }
}
