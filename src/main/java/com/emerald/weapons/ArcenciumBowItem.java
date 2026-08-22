package com.emerald.weapons;

import com.emerald.particles.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Arcencium Bow -- "Tension Prismatique".
 *
 * Plus on bande longtemps, plus les cristaux s'allument, un a un :
 *   cran 1  rouge   (8 ticks)  fleche enflammee
 *   cran 2  orange  (18)       + repulsion violente a l'impact
 *   cran 3  bleu    (28)       + gel temporaire de la cible
 *   cran 4  rose    (38)       + poison
 *   cran 5  vert    (50)       Fleche Prismatique : eclate en 3 eclats a
 *                              effets aleatoires (un toujours colore) et
 *                              pose la Marque Prismatique (synergie epee)
 *
 * La PORTEE ne depend pas de la charge : on choisit son pouvoir, pas sa
 * portee. Seuls les degats et les effets montent avec la tension.
 *
 * Les effets d'impact sont appliques par {@link ArcenciumBowEvents}, qui lit
 * le stade pose sur la fleche dans ses donnees persistantes.
 */
public class ArcenciumBowItem extends BowItem {

    /** Cle NBT (persistent data NeoForge) posee sur la fleche : stade 0..5. */
    public static final String TAG_STAGE = "ArcenciumStage";
    /** Ticks de bandage pour atteindre chaque cristal : rouge, orange, bleu, rose, vert. */
    public static final int[] STAGE_TICKS = {8, 18, 28, 38, 50};
    public static final int FULL_CHARGE_TICKS = 50;
    public static final int MAX_STAGE = 5;
    /** En dessous, on ne tire pas (evite le tir accidentel au simple clic). */
    private static final int MIN_DRAW_TICKS = 3;

    public ArcenciumBowItem(Item.Properties properties) {
        super(properties);
    }

    public static int stageForTicks(int ticks) {
        int stage = 0;
        for (int t : STAGE_TICKS) {
            if (ticks >= t) stage++;
        }
        return stage;
    }

    /** Vitesse quasi pleine des le premier cran (vanilla pleine tension = 3.0). */
    public static float velocityForStage(int stage) {
        return 2.7F + 0.06F * stage;
    }

    /** Degats de base de la fleche (vanilla : 2.0). Pleine tension : 6.0, soit ~18 pv. */
    public static double baseDamageForStage(int stage) {
        return 2.0 + 0.8 * stage;
    }

    /** Particule de la couleur du cristal atteint (bleu = flocons vanilla). */
    public static ParticleOptions stageParticle(int stage) {
        return switch (stage) {
            case 1 -> ModParticles.CRYSTAL_RED.get();
            case 2 -> ModParticles.CRYSTAL_ORANGE.get();
            case 3 -> ParticleTypes.SNOWFLAKE;
            case 4 -> ModParticles.CRYSTAL_PINK.get();
            default -> ModParticles.CRYSTAL_GREEN.get();
        };
    }

    @Override
    public int getEnchantmentValue() {
        return 22;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    /** Feedback de charge : carillon + particules au passage exact de chaque cran. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server)) return;
        int ticks = this.getUseDuration(stack, entity) - remaining;
        int stage = stageForTicks(ticks);
        if (stage == 0 || ticks != STAGE_TICKS[stage - 1]) return;

        float pitch = 0.7F + 0.12F * stage;
        server.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, pitch);
        Vec3 p = entity.getEyePosition().add(entity.getLookAngle().scale(0.8));
        server.sendParticles(stageParticle(stage), p.x, p.y - 0.2, p.z, 6, 0.15, 0.15, 0.15, 0.02);
        if (stage >= MAX_STAGE) {
            server.sendParticles(ParticleTypes.END_ROD, p.x, p.y - 0.2, p.z, 10, 0.25, 0.25, 0.25, 0.03);
            server.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.6F);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        ItemStack ammo = player.getProjectile(stack);
        if (ammo.isEmpty()) return;
        int ticks = this.getUseDuration(stack, entity) - timeLeft;
        if (ticks < MIN_DRAW_TICKS) return;
        int stage = stageForTicks(ticks);

        List<ItemStack> projectiles = draw(stack, ammo, player);
        if (level instanceof ServerLevel server && !projectiles.isEmpty()) {
            shootPrismatic(server, player, stack, projectiles, stage);
        }

        float pitch = 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + 0.08F * stage;
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, pitch);
        if (stage >= MAX_STAGE) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    private void shootPrismatic(ServerLevel level, Player player, ItemStack bow,
                                List<ItemStack> projectiles, int stage) {
        float velocity = velocityForStage(stage);
        for (int i = 0; i < projectiles.size(); i++) {
            ItemStack ammo = projectiles.get(i);
            if (ammo.isEmpty()) continue;
            Projectile projectile = this.createProjectile(level, player, bow, ammo, stage >= MAX_STAGE);
            if (projectile instanceof AbstractArrow arrow) {
                arrow.setBaseDamage(baseDamageForStage(stage));
                arrow.getPersistentData().putInt(TAG_STAGE, stage);
                if (stage >= 1) {
                    arrow.igniteForSeconds(100);          // cristal rouge : feu
                }
                if (stage >= MAX_STAGE) {
                    arrow.setCritArrow(true);
                }
            }
            this.shootProjectile(player, projectile, i, velocity, 1.0F, 0.0F, null);
            level.addFreshEntity(projectile);
        }
        bow.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
    }
}
