package com.emerald.entity;

import com.emerald.quest.QuestManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;

/*
 * Wastelander — PNJ neutre habitant de Spargus City.
 *
 * Comportement :
 *   - Neutre : ne attaque pas le joueur sauf si frappé
 *   - Dialogue : en cliquant dessus, propose une quête ou donne un indice
 *   - Erre dans un rayon limité (WaterAvoidingRandomStrolling)
 *   - Fuit si en dessous de 30% de vie (AvoidEntityGoal)
 *   - Peut devenir hostile temporairement si attaqué (HurtByTarget)
 *
 * Types de rôles (stockés en NBT, attribués à la génération) :
 *   0 = Habitant  — dialogue d'ambiance, pas de quête
 *   1 = Marchand  — propose un échange (implémentation à venir)
 *   2 = Sentinelle — dialogue de garde, quête de défense
 *   3 = Guide     — donne la direction de lieux importants
 */
public class WastelanderEntity extends PathfinderMob {

    private static final String NBT_ROLE    = "WastelanderRole";
    private static final String NBT_QUEST   = "WastelanderQuestId";
    private static final String NBT_TALKED  = "WastelanderTalked";

    private int role      = 0;
    private int questId   = -1;  // -1 = pas de quête disponible
    private boolean talked = false;

    // Dialogues par rôle — index = role, tableau = lignes de dialogue aléatoires
    private static final String[][] DIALOGUES = {
        { // Habitant
            "Les sables du Wasteland cachent bien des dangers...",
            "Damas nous protège. Spargus tient bon.",
            "J'ai vu des lueurs étranges du côté des ruines la nuit dernière.",
            "Méfie-toi des Maraudeurs à l'est."
        },
        { // Marchand
            "J'ai des marchandises rares, voyageur. Reviens avec des ressources.",
            "Le commerce est difficile par ici, mais on survit.",
        },
        { // Sentinelle
            "Halt. Qui va là ? ... Bien, tu peux passer.",
            "Spargus ne tombe pas. Pas ce soir.",
            "Mes hommes ont repéré une incursion KG au nord. Sois vigilant."
        },
        { // Guide
            "Haven City est au nord-est, à travers le Wasteland.",
            "Les ruines Precursor ? Tu ne peux pas les manquer, elles brillent dans le noir.",
            "Suis les balises de pierre jusqu'à la tour de guet. Elle indique Haven City."
        }
    };

    public WastelanderEntity(EntityType<? extends WastelanderEntity> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void registerGoals() {
        // Priorité 1 : fuir si PV bas
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 6.0f, 1.0, 1.2));

        // Priorité 2 : errance naturelle
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Cible : riposte si frappé
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // -------------------------------------------------------------------------
    // Interaction — dialogue + quête
    // -------------------------------------------------------------------------
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (this.level().isClientSide) return InteractionResult.SUCCESS;

        // Si une quête est disponible et non encore donnée
        if (questId >= 0 && !talked) {
            talked = true;
            QuestManager.startQuest(player, questId);
            player.sendSystemMessage(Component.literal(
                    "[" + getRoleName() + "] " + getQuestOfferLine()));
            return InteractionResult.SUCCESS;
        }

        // Dialogue normal
        String[] lines = DIALOGUES[role];
        String line = lines[this.level().random.nextInt(lines.length)];
        player.sendSystemMessage(Component.literal("[" + getRoleName() + "] " + line));

        // Tourne vers le joueur pendant le dialogue
        this.getLookControl().setLookAt(player, 30f, 30f);

        return InteractionResult.SUCCESS;
    }

    private String getRoleName() {
        return switch (role) {
            case 1 -> "Marchand";
            case 2 -> "Sentinelle";
            case 3 -> "Guide";
            default -> "Habitant";
        };
    }

    private String getQuestOfferLine() {
        return switch (questId) {
            case 0 -> "J'ai besoin d'aide. Des Metalheads rôdent près de l'arène. Peux-tu les éliminer ?";
            case 1 -> "Rapporte-moi 5 minerais de fer des ruines Precursor, je te récompenserai.";
            case 2 -> "La tour de guet est tombée. Va la réparer avant la nuit.";
            default -> "J'ai quelque chose pour toi, ami.";
        };
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(NBT_ROLE,   role);
        tag.putInt(NBT_QUEST,  questId);
        tag.putBoolean(NBT_TALKED, talked);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        role    = tag.getInt(NBT_ROLE);
        questId = tag.getInt(NBT_QUEST);
        talked  = tag.getBoolean(NBT_TALKED);
    }

    // Assigne un rôle et une quête à la génération
    public void setRole(int role) {
        this.role = role;
    }

    public void setQuestId(int questId) {
        this.questId = questId;
    }

    // -------------------------------------------------------------------------
    // Sons
    // -------------------------------------------------------------------------
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

}
