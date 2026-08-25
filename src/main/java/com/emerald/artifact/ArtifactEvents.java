package com.emerald.artifact;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.particles.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * Les effets des artefacts qui ne dependent pas d'une arme precise.
 *
 * Chaque famille passe par le mecanisme qui lui convient plutot que par un tick
 * generique : les bonus permanents par les attributs d'objet, donc visibles dans
 * l'infobulle et retires proprement ; les reactions au combat par les evenements
 * de degats ; les effets d'ambiance par un balayage periodique.
 *
 * Les artefacts propres a une arme (Tension Rapide, Fleche Fourchue, Lame de
 * Chaine...) sont lus par l'arme elle-meme, la ou son comportement est decrit.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public class ArtifactEvents {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, path);
    }

    private static final ResourceLocation SPEED_ID = id("artifact_semelle");
    private static final ResourceLocation KNOCKBACK_ID = id("artifact_lest");

    private static final String TAG_PLATE_USED = "ArcenciumPlateUsed";
    private static final String TAG_RESONANCE = "ArcenciumResonance";
    private static final String TAG_RESONANCE_AT = "ArcenciumResonanceAt";
    private static final String TAG_SHELL = "ArcenciumShell";
    private static final String TAG_HURT_AT = "ArcenciumHurtAt";

    private static final int PLATE_COOLDOWN = 3 * 60 * 20;
    private static final float LIFESTEAL = 0.15F;

    private static final int RESONANCE_MAX = 10;             // 10 x 5 % = +50 %
    private static final int RESONANCE_DECAY = 8 * 20;       // hors combat
    private static final float SHELL_CAPACITY = 40.0F;
    private static final int OUT_OF_COMBAT = 8 * 20;

    private static final double CHAMP_RADIUS = 4.0;
    private static final double SIEGE_RADIUS = 8.0;
    private static final int SIEGE_THRESHOLD = 3;
    private static final double REPERE_RADIUS = 24.0;
    private static final double LENTILLE_RADIUS = 40.0;

    // --------------------------------------------------------- bonus permanents

    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        Artifact artifact = Artifacts.of(event.getItemStack());
        if (artifact == null) {
            return;
        }
        switch (artifact) {
            case SEMELLE_DE_PRISME -> event.addModifier(Attributes.MOVEMENT_SPEED,
                    new AttributeModifier(SPEED_ID, 0.20,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                    EquipmentSlotGroup.FEET);
            case LEST_DE_GANGUE -> event.addModifier(Attributes.KNOCKBACK_RESISTANCE,
                    new AttributeModifier(KNOCKBACK_ID, 1.0,
                            AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.LEGS);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ balayage

    /**
     * Les effets d'ambiance sont renouveles toutes les demi-secondes, avec une
     * duree bien superieure a l'intervalle : reappliques au dernier moment, ils
     * feraient clignoter l'ecran et les icones d'effet a chaque cycle.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide) {
            return;
        }
        walkOnFluid(player);
        if (player.tickCount % 10 != 0) {
            return;
        }
        if (Artifacts.wearing(player, Artifact.LENTILLE_D_AURORE)) {
            player.addEffect(effect(MobEffects.NIGHT_VISION, 400, 0));
        }
        if (Artifacts.wearing(player, Artifact.RESERVOIR_DE_PRISME)) {
            boolean calm = level.getGameTime() - player.getPersistentData().getLong(TAG_HURT_AT)
                    > OUT_OF_COMBAT;
            player.addEffect(effect(MobEffects.REGENERATION, 60, calm ? 1 : 0));
        }
        if (Artifacts.wearing(player, Artifact.CHAMP_DE_CRISTAL)) {
            for (LivingEntity foe : hostiles(player, CHAMP_RADIUS)) {
                foe.addEffect(effect(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
            }
        }
        if (Artifacts.wearing(player, Artifact.RENFORT_DE_SIEGE)
                && hostiles(player, SIEGE_RADIUS).size() >= SIEGE_THRESHOLD) {
            player.addEffect(effect(MobEffects.DAMAGE_RESISTANCE, 40, 0));
        }
        if (Artifacts.wearing(player, Artifact.REPERE_D_ECHO)) {
            markToughest(player);
        }
        if (Artifacts.wearing(player, Artifact.LENTILLE_DU_PRISME)) {
            revealArtifacts(player);
        }
    }

    private static MobEffectInstance effect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> type,
                                            int ticks, int amplifier) {
        return new MobEffectInstance(type, ticks, amplifier, true, false, false);
    }

    private static List<LivingEntity> hostiles(Player player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        return player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e instanceof Enemy && e.isAlive() && e.distanceTo(player) <= radius);
    }

    /**
     * Repere d'Echo : designe l'ennemi le plus coriace des environs.
     *
     * Dans une melee, savoir lequel abattre en premier vaut plus que n'importe
     * quel bonus de degats. Le mode de jeu remplacera ce critere par le meneur
     * d'escouade ; en attendant, la vie maximale est le meilleur indice de qui
     * tient la vague debout.
     */
    private static void markToughest(Player player) {
        LivingEntity best = null;
        for (LivingEntity foe : hostiles(player, REPERE_RADIUS)) {
            if (best == null || foe.getMaxHealth() > best.getMaxHealth()) {
                best = foe;
            }
        }
        if (best != null) {
            best.addEffect(effect(MobEffects.GLOWING, 40, 0));
        }
    }

    /** Lentille du Prisme : les artefacts au sol luisent a travers les murs. */
    private static void revealArtifacts(Player player) {
        AABB box = player.getBoundingBox().inflate(LENTILLE_RADIUS);
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, box)) {
            if (Artifacts.of(item.getItem()) != null) {
                item.setGlowingTag(true);
            }
        }
    }

    /**
     * Semelle Vaporeuse : le porteur tient sur l'eau et sur la lave.
     *
     * On annule la chute au contact plutot que de geler le fluide : geler
     * transformerait le terrain, ce qui se verrait de loin et generait les
     * autres joueurs. S'accroupir laisse volontairement replonger.
     */
    private static void walkOnFluid(Player player) {
        if (!Artifacts.wearing(player, Artifact.SEMELLE_VAPOREUSE)
                || player.isShiftKeyDown() || player.getDeltaMovement().y > 0.0) {
            return;
        }
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
        FluidState fluid = player.level().getFluidState(below);
        if (fluid.isEmpty()) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, 0.0, motion.z);
        player.setPos(player.getX(), below.getY() + fluid.getHeight(player.level(), below),
                      player.getZ());
        player.setOnGround(true);
        player.fallDistance = 0.0F;
        player.clearFire();
    }

    // -------------------------------------------------------------- au combat

    /** Drain de Cristal, et alimentation de la Resonance et de la Coque. */
    @SubscribeEvent
    public static void onDealDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker.level().isClientSide) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        if (!Artifacts.has(weapon, Artifact.DRAIN_DE_CRISTAL)) {
            return;
        }
        float healed = event.getNewDamage() * LIFESTEAL;
        if (healed > 0.0F) {
            attacker.heal(healed);
            if (attacker.level() instanceof ServerLevel server) {
                server.sendParticles(ModParticles.CRYSTAL_RED.get(),
                        attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6,
                        attacker.getZ(), 5, 0.25, 0.35, 0.25, 0.02);
            }
        }
    }

    /**
     * Tout ce qui reagit a un coup RECU, avant que les points ne soient retires.
     *
     * L'ordre compte : la Plaque doit pouvoir annuler un coup fatal, ce qui est
     * impossible une fois la vie tombee a zero.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide) {
            return;
        }
        entity.getPersistentData().putLong(TAG_HURT_AT, level.getGameTime());

        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        Artifact worn = Artifacts.of(chest);

        if (worn == Artifact.PLASTRON_DE_RESONANCE) {
            int stacks = Math.min(RESONANCE_MAX,
                    entity.getPersistentData().getInt(TAG_RESONANCE) + 1);
            entity.getPersistentData().putInt(TAG_RESONANCE, stacks);
            entity.getPersistentData().putLong(TAG_RESONANCE_AT, level.getGameTime());
        } else if (worn == Artifact.COQUE_PRISMATIQUE) {
            chargeShell(entity, event.getAmount());
        }

        if (worn == Artifact.PLAQUE_DE_GANGUE && event.getAmount() >= entity.getHealth()) {
            absorbFatalBlow(entity, event);
        }
    }

    /** Coque Prismatique : les degats encaisses s'accumulent, puis se rendent. */
    private static void chargeShell(LivingEntity entity, float amount) {
        float stored = entity.getPersistentData().getFloat(TAG_SHELL) + amount;
        if (stored < SHELL_CAPACITY) {
            entity.getPersistentData().putFloat(TAG_SHELL, stored);
            return;
        }
        entity.getPersistentData().putFloat(TAG_SHELL, 0.0F);
        Level level = entity.level();
        DamageSource source = level.damageSources().magic();
        for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class,
                entity.getBoundingBox().inflate(5.0),
                e -> e != entity && e.isAlive() && e instanceof Enemy)) {
            foe.hurt(source, SHELL_CAPACITY * 0.25F);
            Vec3 away = foe.position().subtract(entity.position()).normalize();
            foe.knockback(1.4, -away.x, -away.z);
        }
        level.playSound(null, entity.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                SoundSource.PLAYERS, 0.9F, 1.5F);
        if (level instanceof ServerLevel server) {
            for (int i = 0; i < 80; i++) {
                double a = i / 80.0 * Math.PI * 2;
                server.sendParticles(ModParticles.PRISM_MOTE.get(),
                        entity.getX() + Math.cos(a) * 1.2, entity.getY() + 0.4,
                        entity.getZ() + Math.sin(a) * 1.2, 0, Math.cos(a), 0.05, Math.sin(a), 0.7);
            }
        }
    }

    private static void absorbFatalBlow(LivingEntity entity, LivingIncomingDamageEvent event) {
        long now = entity.level().getGameTime();
        long last = entity.getPersistentData().getLong(TAG_PLATE_USED);
        if (last != 0 && now - last < PLATE_COOLDOWN) {
            return;
        }
        entity.getPersistentData().putLong(TAG_PLATE_USED, now);
        event.setAmount(0.0F);
        entity.setHealth(1.0F);
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 2));
        entity.level().playSound(null, entity.blockPosition(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
        if (entity.level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.PRISM_MOTE.get(),
                    entity.getX(), entity.getY() + 1.0, entity.getZ(), 40, 0.5, 0.8, 0.5, 0.15);
        }
        if (entity instanceof Player player) {
            player.displayClientMessage(
                    Component.translatable("artifact.emeraldweapons.plaque_de_gangue.saved")
                            .withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** Eclat Final : la mort d'un ennemi devient une detonation. */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)
                || killer.level().isClientSide
                || !Artifacts.has(killer.getMainHandItem(), Artifact.ECLAT_FINAL)) {
            return;
        }
        LivingEntity dead = event.getEntity();
        Level level = dead.level();
        DamageSource source = level.damageSources().magic();
        for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class,
                dead.getBoundingBox().inflate(3.5),
                e -> e != dead && e != killer && e.isAlive() && e instanceof Enemy)) {
            foe.hurt(source, 5.0F);
        }
        level.playSound(null, dead.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.PLAYERS, 1.0F, 0.8F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                    dead.getX(), dead.getY() + 0.8, dead.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            server.sendParticles(ModParticles.PRISM_MOTE.get(),
                    dead.getX(), dead.getY() + 0.8, dead.getZ(), 45, 0.6, 0.6, 0.6, 0.22);
        }
    }

    /**
     * Bonus de degats de la Resonance, et son extinction hors combat.
     *
     * Le multiplicateur est lu ici plutot que pose en attribut : il change a
     * chaque coup recu, et un attribut recalcule aussi souvent alourdirait
     * chaque entite pour rien.
     */
    public static float resonanceMultiplier(LivingEntity attacker) {
        if (!Artifacts.has(attacker.getItemBySlot(EquipmentSlot.CHEST),
                Artifact.PLASTRON_DE_RESONANCE)) {
            return 1.0F;
        }
        long since = attacker.level().getGameTime()
                - attacker.getPersistentData().getLong(TAG_RESONANCE_AT);
        if (since > RESONANCE_DECAY) {
            attacker.getPersistentData().putInt(TAG_RESONANCE, 0);
            return 1.0F;
        }
        return 1.0F + 0.05F * attacker.getPersistentData().getInt(TAG_RESONANCE);
    }

    // -------------------------------------------------------------- infobulle

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Artifact artifact = Artifacts.of(stack);
        // l'objet artefact affiche deja son nom et sa description lui-meme
        if (artifact == null || stack.getItem() instanceof ArtifactItem) {
            return;
        }
        event.getToolTip().add(Component.translatable(artifact.translationKey())
                .withStyle(style -> style.withColor(artifact.color())));
        event.getToolTip().add(Component.translatable(artifact.descriptionKey())
                .withStyle(ChatFormatting.GRAY));
    }
}
