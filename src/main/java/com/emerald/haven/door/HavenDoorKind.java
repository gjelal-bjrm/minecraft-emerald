package com.emerald.haven.door;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Les trois portes de Jak 3 de Haven (cahier §95), et ce qui les distingue.
 *
 * « J'aimerais implementer les portes de Jak 3 en porte Minecraft et qu'elles s'ouvrent de la
 * meme facon que dans le jeu original » (le joueur, 24 sept.). Dans le jeu, une porte est deux
 * battants qui coulissent dans le mur quand on arrive a quelques metres, et qui se referment
 * derriere (airlock.gc) ; le modele et son animation sont cuits par tools/jak_door.py.
 *
 * <ul>
 * <li>HIP : la porte du Hip Hog (hip-door-a), deux battants de bois, 3,45 x 4,17 m -- une
 *     ouverture de trois blocs sur quatre, celle des appartements ; 1,57 m par battant en
 *     1,67 s ; les sons du jeu sont ceux d'une porte en bois.</li>
 * <li>PETITE : la meme, reduite a une porte Minecraft, un bloc sur deux, pour les interieurs
 *     de l'atelier.</li>
 * <li>SAS : le grand sas du port (com-airlock-outer), 12 x 12 m et 4 m d'epaisseur. Dans les
 *     cinq secondes de son ouverture, les trois barres glissent en travers et tournent d'abord
 *     (les deux premiers tiers : le sas se deverrouille), puis les deux battants de metal
 *     s'ecartent de 4 m chacun. Ouvert, chaque battant couvre encore deux metres de
 *     l'ouverture : le passage fait huit blocs, et ses bords restent pleins.</li>
 * </ul>
 *
 * LE PASSAGE SUIT LES BATTANTS : une cellule de la porte ne s'ouvre que quand leur bord s'en est
 * ecarte assez pour laisser passer un joueur (son milieu plus une demi-largeur de joueur). Le
 * bord avance lineairement a partir de la course {@link #openFrom} -- en dessous de la vraie
 * courbe du sas, qui accelere puis ralentit : le passage ne s'ouvre jamais avant l'image.
 */
public enum HavenDoorKind implements StringRepresentable {
    HIP("hip_door_a", 3, 4, 1, 0.0F, 1.57, 33, 3.0, 1.0F, 1.0F,
            SoundEvents.WOODEN_DOOR_OPEN, SoundEvents.WOODEN_DOOR_CLOSE),
    PETITE("hip_door_a", 1, 2, 1, 0.0F, 1.57 / 3.45, 20, 2.0, 1.0F / 3.45F, 2.0F / 4.17F,
            SoundEvents.WOODEN_DOOR_OPEN, SoundEvents.WOODEN_DOOR_CLOSE),
    SAS("com_airlock_outer", 12, 12, 4, 0.66F, 4.02, 100, 6.0, 1.0F, 1.0F,
            SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_DOOR_CLOSE);

    /** La demi-largeur d'un joueur : un passage plus etroit que deux fois cela ne se traverse pas. */
    private static final double PLAYER_HALF = 0.3;

    /** Le modele cuit (assets/emeraldweapons/jak_doors/<modele>.bin et .anim.json). */
    public final String model;
    /** L'ouverture, en blocs : la largeur (le long des battants) et la hauteur. */
    public final int width;
    public final int height;
    /** L'epaisseur, en blocs : les cellules de collision la remplissent. */
    public final int depth;
    /** La course (de 0 a 1) ou les battants commencent a s'ecarter : 0,66 au sas, deverrouille d'abord. */
    public final float openFrom;
    /** De combien chaque battant s'ecarte du milieu, grand ouvert, en blocs. */
    public final double travel;
    /** La duree de l'ouverture, en tiques. */
    public final int duration;
    /** Jusqu'a cette distance devant ou derriere la porte, un joueur l'ouvre. */
    public final double trigger;
    /** L'echelle du modele, en largeur et en hauteur (la petite porte est le modele du Hip Hog reduit). */
    public final float scaleX;
    public final float scaleY;
    public final SoundEvent openSound;
    public final SoundEvent closeSound;

    HavenDoorKind(String model, int width, int height, int depth, float openFrom, double travel, int duration,
                  double trigger, float scaleX, float scaleY, SoundEvent openSound, SoundEvent closeSound) {
        this.model = model;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.openFrom = openFrom;
        this.travel = travel;
        this.duration = duration;
        this.trigger = trigger;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.openSound = openSound;
        this.closeSound = closeSound;
    }

    /** Le bord d'un battant, a cette course : sa distance au milieu de la porte, en blocs. */
    public double leafEdge(float progress) {
        double t = (progress - this.openFrom) / (1.0 - this.openFrom);
        return this.travel * Math.max(0.0, Math.min(1.0, t));
    }

    /** Un joueur passe-t-il a cette distance du milieu, a cette course ? */
    public boolean passable(double lateral, float progress) {
        return leafEdge(progress) >= Math.abs(lateral) + PLAYER_HALF;
    }

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
