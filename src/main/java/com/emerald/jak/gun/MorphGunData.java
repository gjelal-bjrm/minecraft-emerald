package com.emerald.jak.gun;

import com.emerald.artifact.ModDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * L'etat du Morph Gun porte par sa pile : le composant emeraldweapons:morph_gun.
 *
 * SEULEMENT CE QUI CHANGE PAR EVENEMENT. Chaque modification de pile renvoie la
 * case a son porteur ET diffuse l'equipement a tous ceux qui le voient
 * (LivingEntity.equipmentHasChanged compare les piles, composants compris). La
 * rotation du canon, le delai, la charge et la gachette tenue restent donc en
 * memoire serveur (chantier du tir) ; ici : forme et forme precedente, tique du
 * changement, formes possedees, les quatre reserves, les tiques de debut et de
 * fin de gachette, et le numero du lobby qui a donne l'arme.
 *
 * QUATRE CHAMPS ENTIERS, PAS UN TABLEAU : un record compare ses tableaux par
 * identite, et deux copies d'une meme pile auraient ete « differentes » a
 * chaque tique -- renvoi de case et animation d'equipement sans fin.
 *
 * ECRIRE PAR {@link #write}. Il ne touche la pile que si l'etat change, et compte
 * les ecritures : le banc verifie qu'un changement d'arme en fait une seule.
 *
 * @param form         la forme tenue
 * @param previous     la forme d'avant le dernier changement (la transformation part d'elle)
 * @param changeTick   tique de jeu (level.getGameTime) du dernier changement
 * @param owned        formes possedees, un bit par ordinal de GunForm
 * @param triggerStart tique du dernier appui de gachette (0 : jamais)
 * @param triggerEnd   tique du dernier relachement (0 : jamais)
 * @param lobby        numero du lobby qui a donne l'arme (MorphGunState)
 */
public record MorphGunData(GunForm form, GunForm previous, long changeTick, int owned,
                           int ecoRed, int ecoYellow, int ecoBlue, int ecoDark,
                           long triggerStart, long triggerEnd, long lobby) {

    private static final Codec<GunForm> FORM_CODEC = Codec.STRING.xmap(GunForm::byId, form -> form.id);

    public static final Codec<MorphGunData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FORM_CODEC.fieldOf("form").forGetter(MorphGunData::form),
            FORM_CODEC.fieldOf("previous").forGetter(MorphGunData::previous),
            Codec.LONG.fieldOf("change_tick").forGetter(MorphGunData::changeTick),
            Codec.INT.fieldOf("owned").forGetter(MorphGunData::owned),
            Codec.INT.fieldOf("eco_red").forGetter(MorphGunData::ecoRed),
            Codec.INT.fieldOf("eco_yellow").forGetter(MorphGunData::ecoYellow),
            Codec.INT.fieldOf("eco_blue").forGetter(MorphGunData::ecoBlue),
            Codec.INT.fieldOf("eco_dark").forGetter(MorphGunData::ecoDark),
            Codec.LONG.optionalFieldOf("trigger_start", 0L).forGetter(MorphGunData::triggerStart),
            Codec.LONG.optionalFieldOf("trigger_end", 0L).forGetter(MorphGunData::triggerEnd),
            Codec.LONG.fieldOf("lobby").forGetter(MorphGunData::lobby)
    ).apply(instance, MorphGunData::new));

    public static final StreamCodec<ByteBuf, MorphGunData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> {
                ByteBufCodecs.VAR_INT.encode(buf, data.form.ordinal());
                ByteBufCodecs.VAR_INT.encode(buf, data.previous.ordinal());
                ByteBufCodecs.VAR_LONG.encode(buf, data.changeTick);
                ByteBufCodecs.VAR_INT.encode(buf, data.owned);
                ByteBufCodecs.VAR_INT.encode(buf, data.ecoRed);
                ByteBufCodecs.VAR_INT.encode(buf, data.ecoYellow);
                ByteBufCodecs.VAR_INT.encode(buf, data.ecoBlue);
                ByteBufCodecs.VAR_INT.encode(buf, data.ecoDark);
                ByteBufCodecs.VAR_LONG.encode(buf, data.triggerStart);
                ByteBufCodecs.VAR_LONG.encode(buf, data.triggerEnd);
                ByteBufCodecs.VAR_LONG.encode(buf, data.lobby);
            },
            buf -> new MorphGunData(
                    GunForm.byOrdinal(ByteBufCodecs.VAR_INT.decode(buf)),
                    GunForm.byOrdinal(ByteBufCodecs.VAR_INT.decode(buf)),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf)));

    /** Tique « jamais changee » : aucune transformation ne se joue sur une arme neuve. */
    public static final long NEVER = -1_000_000L;

    /** Nombre d'ecritures de composant faites par {@link #write}, depuis le demarrage. */
    private static final AtomicLong WRITES = new AtomicLong();

    /** Une arme neuve : Scatter Gun en main, formes donnees, reserves pleines. */
    public static MorphGunData fresh(long lobby, int owned) {
        GunForm start = (owned & GunForm.RED_1.bit()) != 0 ? GunForm.RED_1 : firstOwned(owned);
        return new MorphGunData(start, start, NEVER, owned,
                GunForm.Family.RED.capacity, GunForm.Family.YELLOW.capacity,
                GunForm.Family.BLUE.capacity, GunForm.Family.DARK.capacity, 0L, 0L, lobby);
    }

    private static GunForm firstOwned(int owned) {
        for (GunForm form : GunForm.values()) {
            if ((owned & form.bit()) != 0) {
                return form;
            }
        }
        return GunForm.RED_1;
    }

    // ------------------------------------------------------------- lecture

    /** L'etat d'une pile, ou null si ce n'est pas un Morph Gun renseigne. */
    @Nullable
    public static MorphGunData of(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(ModDataComponents.MORPH_GUN.get());
    }

    /** La reserve d'eco d'une famille. */
    public int eco(GunForm.Family family) {
        return switch (family) {
            case RED -> this.ecoRed;
            case YELLOW -> this.ecoYellow;
            case BLUE -> this.ecoBlue;
            case DARK -> this.ecoDark;
        };
    }

    public boolean owns(GunForm form) {
        return (this.owned & form.bit()) != 0;
    }

    public boolean full() {
        for (GunForm.Family family : GunForm.Family.values()) {
            if (eco(family) != family.capacity) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------- copies

    /** La reserve d'une famille remplacee, bornee a [0, capacite]. */
    public MorphGunData withEco(GunForm.Family family, int amount) {
        int v = Math.max(0, Math.min(family.capacity, amount));
        return new MorphGunData(this.form, this.previous, this.changeTick, this.owned,
                family == GunForm.Family.RED ? v : this.ecoRed,
                family == GunForm.Family.YELLOW ? v : this.ecoYellow,
                family == GunForm.Family.BLUE ? v : this.ecoBlue,
                family == GunForm.Family.DARK ? v : this.ecoDark,
                this.triggerStart, this.triggerEnd, this.lobby);
    }

    /** Toutes les reserves pleines, formes gardees : la reapparition. */
    public MorphGunData refilled() {
        return new MorphGunData(this.form, this.previous, this.changeTick, this.owned,
                GunForm.Family.RED.capacity, GunForm.Family.YELLOW.capacity,
                GunForm.Family.BLUE.capacity, GunForm.Family.DARK.capacity,
                this.triggerStart, this.triggerEnd, this.lobby);
    }

    /** Le changement d'arme : la forme tenue devient la precedente, a la tique donnee. */
    public MorphGunData withForm(GunForm next, long tick) {
        return new MorphGunData(next, this.form, tick, this.owned, this.ecoRed, this.ecoYellow,
                this.ecoBlue, this.ecoDark, this.triggerStart, this.triggerEnd, this.lobby);
    }

    public MorphGunData withOwned(int mask) {
        return new MorphGunData(this.form, this.previous, this.changeTick, mask, this.ecoRed, this.ecoYellow,
                this.ecoBlue, this.ecoDark, this.triggerStart, this.triggerEnd, this.lobby);
    }

    public MorphGunData withTrigger(long start, long end) {
        return new MorphGunData(this.form, this.previous, this.changeTick, this.owned, this.ecoRed,
                this.ecoYellow, this.ecoBlue, this.ecoDark, start, end, this.lobby);
    }

    public MorphGunData withLobby(long number) {
        return new MorphGunData(this.form, this.previous, this.changeTick, this.owned, this.ecoRed,
                this.ecoYellow, this.ecoBlue, this.ecoDark, this.triggerStart, this.triggerEnd, number);
    }

    // ------------------------------------------------------------- ecriture

    /**
     * Pose l'etat sur la pile, SEULEMENT s'il change.
     *
     * @return vrai si la pile a ete ecrite
     */
    public static boolean write(ItemStack stack, MorphGunData data) {
        if (data.equals(of(stack))) {
            return false;
        }
        stack.set(ModDataComponents.MORPH_GUN.get(), data);
        WRITES.incrementAndGet();
        return true;
    }

    /** Ecritures de composant depuis le demarrage (banc d'essai). */
    public static long writes() {
        return WRITES.get();
    }

    /**
     * Debite une reserve, en une ecriture.
     *
     * @return faux, sans rien ecrire, si la pile n'est pas un Morph Gun ou si la reserve ne suffit pas
     */
    public static boolean spend(ItemStack stack, GunForm.Family family, int amount) {
        MorphGunData data = of(stack);
        if (data == null || amount < 0 || data.eco(family) < amount) {
            return false;
        }
        if (amount > 0) {
            write(stack, data.withEco(family, data.eco(family) - amount));
        }
        return true;
    }

    /**
     * Ajoute a une reserve, plafonnee a la capacite, en une ecriture.
     *
     * @return ce qui a vraiment ete ajoute
     */
    public static int refill(ItemStack stack, GunForm.Family family, int amount) {
        MorphGunData data = of(stack);
        if (data == null || amount <= 0) {
            return 0;
        }
        int before = data.eco(family);
        MorphGunData after = data.withEco(family, before + amount);
        write(stack, after);
        return after.eco(family) - before;
    }
}
