package com.emerald.haven.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * UN HEROS DE HAVEN (lot 3, cahier §86) : Torn, Tess, Sig, Keira, Samos ou le Pecheur.
 *
 * « Les heros de Jak 3 sur un corps de villageois » (choix du joueur, 19 sept., §71) : une
 * entite A NOUS -- les zombies de l'invasion visent les villageois, pas elle --, dessinee
 * avec le modele du villageois, son nom au-dessus de la tete. Elle ne bouge pas de sa place
 * (HavenNpcs l'y tient), regarde les joueurs, ne prend aucun coup, ne se pousse pas. Un clic
 * droit ouvre sa carte du chat (HavenQuests.talk).
 *
 * JAMAIS SAUVEGARDEE : HavenNpcs la repose quand son troncon est charge, comme les points
 * d'eco -- rien d'une ancienne ville ne revient d'une sauvegarde.
 */
public class HavenNpcEntity extends PathfinderMob {

    private static final EntityDataAccessor<String> HERO = SynchedEntityData.defineId(HavenNpcEntity.class,
            EntityDataSerializers.STRING);

    public HavenNpcEntity(EntityType<? extends HavenNpcEntity> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HERO, HavenHero.TORN.id);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    public HavenHero hero() {
        HavenHero hero = HavenHero.byId(this.entityData.get(HERO));
        return hero == null ? HavenHero.TORN : hero;
    }

    public void setHero(HavenHero hero) {
        this.entityData.set(HERO, hero.id);
        this.setCustomName(hero.displayName().copy().withStyle(hero.color));
        this.setCustomNameVisible(true);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            HavenQuests.talk(server, hero());
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(com.emerald.jak.vehicle.VehicleImpacts.DAMAGE_TYPE)) {
            return super.hurt(source, amount);
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Heros", this.entityData.get(HERO));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Heros")) {
            this.entityData.set(HERO, tag.getString("Heros"));
        }
    }
}
