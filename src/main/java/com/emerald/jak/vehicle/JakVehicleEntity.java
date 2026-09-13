package com.emerald.jak.vehicle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;

/**
 * Une voiture civile de Haven City, posee et immobile.
 *
 * C'est la premiere marche des voitures volantes : on veut d'abord VOIR les
 * modeles de Jak 3 en jeu -- orientation, couleurs, textures -- avant de les
 * faire voler. L'entite ne bouge donc pas : pas de gravite, rien ne la pousse,
 * rien ne l'abime. Seul le nom du modele est garde, synchronise au client pour
 * le rendu et sauvegarde avec le monde.
 *
 * L'origine de l'entite est celle du modele du jeu, pas le bas de la voiture :
 * les sieges et les propulseurs du code GOAL sont donnes dans ce repere, et le
 * pilotage les reprendra tels quels.
 */
public class JakVehicleEntity extends Entity {

    /** Les trois voitures civiles retenues par le joueur. */
    public static final List<String> MODELS = List.of("cara", "carb", "carc");

    /**
     * Profondeur du point le plus bas sous l'origine du modele, en blocs.
     * Mesuree par tools/jak_vehicle.py (« boite min », y) : la commande s'en
     * sert pour poser la voiture sur le sol plutot qu'a moitie dedans.
     */
    private static final Map<String, Float> BOTTOM = Map.of(
            "cara", 1.799F,
            "carb", 0.791F,
            "carc", 0.898F);

    /**
     * Demi-cote horizontal de la boite de rendu. La boite de l'entite ne fait
     * que 3 blocs quand les voitures en font 7,6 a 8,4 : sans cette boite
     * elargie, la voiture disparaitrait des qu'on ne regarde plus son centre.
     * carc va jusqu'a z = -5,09 avec x = 2,28, soit 5,6 blocs de l'origine.
     */
    private static final double CULL_RADIUS = 6.0;
    private static final double CULL_BELOW = 2.0;
    private static final double CULL_ABOVE = 2.0;

    private static final String TAG_MODEL = "Modele";

    private static final EntityDataAccessor<String> DATA_MODEL =
            SynchedEntityData.defineId(JakVehicleEntity.class, EntityDataSerializers.STRING);

    public JakVehicleEntity(EntityType<? extends JakVehicleEntity> type, Level level) {
        super(type, level);
        // sans gravite vanilla : c'est aussi ce qui evitera, au pilotage, que le
        // serveur dedie expulse le conducteur pour vol
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public static boolean isModel(String name) {
        return MODELS.contains(name);
    }

    public static float bottom(String model) {
        return BOTTOM.getOrDefault(model, 0.0F);
    }

    public String model() {
        return this.entityData.get(DATA_MODEL);
    }

    public void setModel(String model) {
        if (isModel(model)) {
            this.entityData.set(DATA_MODEL, model);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MODEL, MODELS.get(0));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains(TAG_MODEL, Tag.TAG_STRING)) {
            this.setModel(tag.getString(TAG_MODEL));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString(TAG_MODEL, this.model());
    }

    /** Indestructible : les coups ne font rien (un /kill la retire toujours). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return true;
    }

    /** Visable, pour qu'on puisse la designer du regard (F3, /kill @e[type=...,sort=nearest]). */
    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable("entity.emeraldweapons.jak_vehicle." + this.model());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(this.getX() - CULL_RADIUS, this.getY() - CULL_BELOW, this.getZ() - CULL_RADIUS,
                this.getX() + CULL_RADIUS, this.getY() + CULL_ABOVE, this.getZ() + CULL_RADIUS);
    }
}
