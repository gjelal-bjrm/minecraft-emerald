package com.emerald.weather;

import com.emerald.init.Jak3Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Un eclair d'Arcencium : la foudre vanilla, mais coloree.
 *
 * L'entite ne porte AUCUN effet de jeu -- feu, gel, onde, marque et cicatrice
 * sont appliques par {@link WeatherEffects} au moment de la frappe, cote
 * serveur. Elle n'existe que pour etre dessinee : le renderer vanilla de la
 * foudre a sa couleur en dur, il fallait donc une entite a nous pour porter
 * la variante (voir com.emerald.client.ArcenciumBoltRenderer).
 *
 * Sa couleur ANNONCE son effet, sur les cinq cristaux de l'epee : rouge le feu,
 * bleu le gel, jaune l'onde, rose la marque, vert la cicatrice. On apprend a
 * lire le ciel.
 */
public class ArcenciumBoltEntity extends Entity {

    public enum Variant {
        RED(0xFF616B, 1.0F),
        BLUE(0x61C4FF, 1.0F),
        YELLOW(0xFFF06B, 1.0F),
        PINK(0xFF7DD6, 1.0F),
        GREEN(0x78E8AE, 1.0F),
        /** La frappe de l'Orage Prismatique : plus large, violette. */
        ORAGE(0xB98CFF, 1.8F);

        public final int color;
        public final float width;

        Variant(int color, float width) {
            this.color = color;
            this.width = width;
        }
    }

    private static final EntityDataAccessor<Byte> VARIANT =
            SynchedEntityData.defineId(ArcenciumBoltEntity.class, EntityDataSerializers.BYTE);

    private static final int LIFE_TICKS = 10;

    /** Graine du zigzag, retiree a chaque scintillement comme la foudre vanilla. */
    public long seed;

    public ArcenciumBoltEntity(EntityType<? extends ArcenciumBoltEntity> type, Level level) {
        super(type, level);
        // la boite de collision est nulle : sans cela, le test de frustum
        // eliminerait un eclair dont seule la base est hors champ
        this.noCulling = true;
        this.seed = level.random.nextLong();
    }

    public ArcenciumBoltEntity(Level level, double x, double y, double z, Variant variant) {
        this(Jak3Registry.ARCENCIUM_BOLT.get(), level);
        setPos(x, y, z);
        this.entityData.set(VARIANT, (byte) variant.ordinal());
    }

    public Variant variant() {
        return Variant.values()[Math.floorMod(this.entityData.get(VARIANT), Variant.values().length)];
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount % 2 == 0) {
            this.seed = this.level().random.nextLong();
        }
        if (!this.level().isClientSide && this.tickCount > LIFE_TICKS) {
            discard();
        }
    }

    /** Un eclair se voit de tres loin : la portee par defaut depend de la boite, nulle ici. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 300.0 * 300.0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VARIANT, (byte) 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // jamais sauvegarde : l'entite vit une demi-seconde
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
