package com.emerald.artifact;

import com.emerald.item.ModArmorMaterials;
import com.emerald.weapons.ArcenciumBowItem;
import com.emerald.weapons.ArcenciumScepterItem;
import com.emerald.weapons.EmeraldWindblade;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Les artefacts, et l'emplacement qui les accepte.
 *
 * Convention de nommage : uniquement des OBJETS et des MECANISMES. Rien qui
 * evoque un etre vivant, une benediction, une malediction ou une divinite.
 * Un artefact est une piece qu'on sertit, pas une faveur qu'on recoit.
 *
 * Un artefact ne se contente pas d'ajouter des points : il change une facon de
 * jouer. C'est ce qui les distingue des gemmes d'Apotheosis, qui couvrent deja
 * tres bien le terrain des bonus chiffres, et c'est pourquoi le mod garde son
 * propre systeme plutot que de s'appuyer sur le leur.
 *
 * Six emplacements, un artefact serti par piece. Le choix se fait a l'Etabli de
 * Sertissage ; retirer un artefact le detruit, si bien qu'on peut changer d'avis
 * mais que cela coute.
 */
public enum Artifact implements StringRepresentable {

    // ------------------------------------------------ casque : la perception

    /** Voit ancres, coffres et artefacts a travers les murs, a 40 blocs. */
    LENTILLE_DU_PRISME(Socket.HELMET, 0xB98CFF),
    /** Immunise aux degats des meteos agressives. */
    FILTRE_DE_BRUME(Socket.HELMET, 0xB9C6D6),
    /** Fait luire l'ennemi le plus coriace des environs. */
    REPERE_D_ECHO(Socket.HELMET, 0xFFD36B),
    /** Vision nocturne permanente. */
    LENTILLE_D_AURORE(Socket.HELMET, 0x9CE8FF),

    // ------------------------------------------------- plastron : la survie

    /** Le coup fatal laisse a 1 PV (recharge 3 min). */
    PLAQUE_DE_GANGUE(Socket.CHEST, 0x78E8AE),
    /** Accumule les degats subis, puis libere une onde de choc. */
    COQUE_PRISMATIQUE(Socket.CHEST, 0x6BE0FF),
    /** Regeneration lente permanente, doublee hors combat. */
    RESERVOIR_DE_PRISME(Socket.CHEST, 0x9CFF8C),
    /** +5 % de degats par coup recu, jusqu'a +50 %. */
    PLASTRON_DE_RESONANCE(Socket.CHEST, 0xFF9C4A),

    // ---------------------------------------------- jambieres : le controle

    /** Immunite au recul. */
    LEST_DE_GANGUE(Socket.LEGS, 0xC9A26B),
    /** La Maree Prismatique ne ronge plus le porteur. */
    JAMBIERES_DE_MAREE(Socket.LEGS, 0x7DB8FF),
    /** Ralentit les ennemis a moins de quatre blocs. */
    CHAMP_DE_CRISTAL(Socket.LEGS, 0xA8B4FF),
    /** Armure renforcee tant que plusieurs ennemis pressent le porteur. */
    RENFORT_DE_SIEGE(Socket.LEGS, 0xD6D6C0),

    // ------------------------------------------------ bottes : le deplacement

    /** Vitesse de deplacement +20 %. */
    SEMELLE_DE_PRISME(Socket.FEET, 0xFF7DD6),
    /** Un second saut en plein vol. */
    BOTTES_D_ECLAIR(Socket.FEET, 0xFFF06B),
    /** Marche sur l'eau et sur la lave. */
    SEMELLE_VAPOREUSE(Socket.FEET, 0xC0E8FF),
    /** Retour au point de reapparition (recharge 2 min). */
    BOTTES_DE_RETOUR(Socket.FEET, 0xB08CFF),

    // -------------------------------------------------- epee : le corps-a-corps

    /** La Fureur Cristalline monte deux fois plus vite. */
    REGULATEUR_DE_LAME(Socket.SWORD, 0x78E8AE),
    /** Les coups touchent aussi les ennemis adjacents. */
    LAME_DE_CHAINE(Socket.SWORD, 0xE8E8F0),
    /** 15 % des degats infliges sont rendus en vie. */
    DRAIN_DE_CRISTAL(Socket.SWORD, 0xFF616B),
    /** Tuer un ennemi declenche une detonation prismatique. */
    ECLAT_FINAL(Socket.SWORD, 0xFF9C30),

    // ---------------------------------------------------- arc : la distance

    /** La Tension Prismatique monte deux fois plus vite. */
    TENSION_RAPIDE(Socket.BOW, 0x61C4FF),
    /** Le tir a pleine tension part en trois fleches. */
    FLECHE_FOURCHUE(Socket.BOW, 0x8CFFB0),
    /** La Marque Prismatique dure trois fois plus longtemps. */
    MARQUE_PROLONGEE(Socket.BOW, 0xE478FF),
    /** Les fleches inflechissent leur course vers la cible. */
    FLECHE_TRACANTE(Socket.BOW, 0xFFB84A);

    /** Ce qu'une piece doit etre pour accueillir un artefact donne. */
    public enum Socket {
        HELMET(stack -> isArcenciumArmor(stack, EquipmentSlot.HEAD)),
        CHEST(stack -> isArcenciumArmor(stack, EquipmentSlot.CHEST)),
        LEGS(stack -> isArcenciumArmor(stack, EquipmentSlot.LEGS)),
        FEET(stack -> isArcenciumArmor(stack, EquipmentSlot.FEET)),
        SWORD(stack -> stack.getItem() instanceof EmeraldWindblade),
        BOW(stack -> stack.getItem() instanceof ArcenciumBowItem),
        SCEPTER(stack -> stack.getItem() instanceof ArcenciumScepterItem);

        private final Predicate<ItemStack> accepts;

        Socket(Predicate<ItemStack> accepts) {
            this.accepts = accepts;
        }

        public boolean accepts(ItemStack stack) {
            return this.accepts.test(stack);
        }

        private static boolean isArcenciumArmor(ItemStack stack, EquipmentSlot slot) {
            return stack.getItem() instanceof ArmorItem armor
                    && armor.getEquipmentSlot() == slot
                    && armor.getMaterial().equals(ModArmorMaterials.ARCENCIUM);
        }
    }

    public static final Codec<Artifact> CODEC = StringRepresentable.fromEnum(Artifact::values);

    public static final StreamCodec<ByteBuf, Artifact> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], Artifact::ordinal);

    private final Socket socket;
    private final int color;
    private final String id;

    Artifact(Socket socket, int color) {
        this.socket = socket;
        this.color = color;
        this.id = name().toLowerCase(Locale.ROOT);
    }

    public Socket socket() {
        return this.socket;
    }

    /** Teinte du nom dans l'infobulle, et des particules au sertissage. */
    public int color() {
        return this.color;
    }

    public boolean fits(ItemStack stack) {
        return this.socket.accepts(stack);
    }

    public String translationKey() {
        return "artifact.emeraldweapons." + this.id;
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
