package com.emerald.jak.vehicle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;

import javax.annotation.Nullable;

/**
 * Une des trois boites qui donnent a la voiture sa longueur.
 *
 * POURQUOI DES PARTIES. Une boite d'entite Minecraft est un pave aligne sur
 * les axes, qui ne tourne pas. Les voitures font 7,6 a 8,4 blocs de long : une
 * boite unique assez longue pour les couvrir ferait 8 x 8 et boucherait les
 * rues du port des qu'on tourne ; une boite de 3 laissait traverser l'avant et
 * l'arriere -- les voitures « fantomatiques » du premier jalon. Trois carres
 * poses le long de l'axe de la voiture suivent son lacet et couvrent sa
 * longueur dans toutes les directions.
 *
 * NeoForge sait deja les gerer (c'est le motif du dragon de l'End) : le
 * niveau les rend a Level.getEntities, donc aux collisions des autres entites
 * et au viseur ; ServerLevel.getEntityOrPart les retrouve pour le clic droit ;
 * le client les reconstruit lui-meme, avec des identifiants qui suivent celui
 * de la voiture. Elles ne sont ni envoyees, ni sauvegardees, ni dessinees.
 *
 * LEUR RACINE EST CELLE DE LA VOITURE. getRootVehicle renvoie celle de la
 * voiture : la voiture ne se cogne pas a ses propres parties, et ses passagers
 * ne les visent pas.
 */
public class VehiclePart extends PartEntity<JakVehicleEntity> {

    public final int index;

    public VehiclePart(JakVehicleEntity parent, int index) {
        super(parent);
        this.index = index;
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    /** Le parent n'existe pas encore pendant le constructeur d'Entity, qui pose deja la boite. */
    @Nullable
    private JakVehicleEntity parentOrNull() {
        return this.getParent();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        JakVehicleEntity parent = this.parentOrNull();
        return parent == null ? super.getDimensions(pose) : parent.getDimensions(pose);
    }

    /** Meme hauteur que la voiture : du bas du modele au-dessus du siege. */
    @Override
    protected AABB makeBoundingBox() {
        JakVehicleEntity parent = this.parentOrNull();
        if (parent == null) {
            return super.makeBoundingBox();
        }
        return this.getDimensions(Pose.STANDING)
                .makeBoundingBox(this.getX(), this.getY() + parent.spec().boxBottom, this.getZ());
    }

    @Override
    public boolean isPickable() {
        JakVehicleEntity parent = this.parentOrNull();
        return parent != null && !parent.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return true;
    }

    /** Le clic droit sur une partie est un clic sur la voiture. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        JakVehicleEntity parent = this.parentOrNull();
        return parent == null ? InteractionResult.PASS : parent.interact(player, hand);
    }

    @Override
    public Entity getRootVehicle() {
        JakVehicleEntity parent = this.parentOrNull();
        return parent == null ? this : parent.getRootVehicle();
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || this.getParent() == entity;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return null;
    }
}
