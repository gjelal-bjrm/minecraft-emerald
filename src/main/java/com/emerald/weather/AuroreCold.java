package com.emerald.weather;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * LE GRAND FROID DE L'AURORE (22 sept., cahier §83).
 *
 * « L'aurore, bien qu'elle encourage a aller miner, ne decourage pas a rester a
 * l'exterieur. » Le joueur a choisi le Grand Froid : pendant l'Aurore, DEHORS, on gele.
 *
 * C'est le gel du jeu, celui de la neige poudreuse (Entity.ticksFrozen) : le givre
 * gagne l'ecran, le pas ralentit a mesure (LivingEntity.tryAddFrost), le personnage
 * grelotte une fois gele, et le gel blesse. Rien a dessiner, et tout se lit sans
 * explication.
 *
 *   - DEHORS (sous le ciel : lumiere du ciel a son maximum), le gel monte d'une
 *     tique toutes les deux : quatorze secondes pour geler tout a fait ;
 *   - A L'ABRI -- sous un toit, sous terre, ou a {@value #FIRE_RADIUS} blocs d'un feu
 *     (feu de camp allume, feu, lave, magma, four allume) --, il fond, deux tiques par
 *     tique, comme au sortir de la neige ;
 *   - GELE, on perd un coeur toutes les deux secondes. Le cuir, qui protege de la neige
 *     poudreuse, ne protege pas de l'Aurore : c'est elle qui gele, pas la neige.
 *
 * Le gel n'est jamais pose : on AJOUTE au compteur du jeu, qui le retire de lui-meme
 * (deux par tique) -- l'Aurore finie, tout fond en quelques secondes.
 */
public final class AuroreCold {

    /** Ce qu'on ajoute toutes les deux tiques : le jeu en retire deux par tique, reste +1. */
    private static final int ADD = 5;
    /** Au-dela du seuil du jeu : de quoi rester gele malgre le degel d'une tique. */
    private static final int OVERSHOOT = 6;
    /** Les degats du gel, en plus de ceux du jeu (qui ne blesse que qui peut geler). */
    private static final float DAMAGE = 2.0F;
    private static final int DAMAGE_EVERY = 40;
    /** Un feu rechauffe a cette distance. */
    static final int FIRE_RADIUS = 3;

    /** La chaleur trouvee autour de chacun, relue chaque seconde. */
    private static final Map<UUID, Boolean> WARM = new HashMap<>();
    /** Le dernier avertissement de chacun (tique du monde). */
    private static final Map<UUID, Long> WARNED = new HashMap<>();

    private AuroreCold() {
    }

    static void begin() {
        WARM.clear();
        WARNED.clear();
    }

    static void tick(ServerLevel level) {
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            tickPlayer(level, player, now);
        }
    }

    /** Une tique de froid pour un joueur ; publique pour le banc d'essai (ArcenciumAutotest). */
    public static void tickPlayer(ServerLevel level, ServerPlayer player, long now) {
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            return;
        }
        if (now % 20 == 0) {
            WARM.put(player.getUUID(), nearFire(level, player.blockPosition()));
        }
        if (!exposed(level, player)) {
            return;                                        // le jeu fait fondre le gel de lui-meme
        }
        int need = player.getTicksRequiredToFreeze();
        if (now % 2 == 0) {
            player.setTicksFrozen(Math.min(need + OVERSHOOT, player.getTicksFrozen() + ADD));
        }
        if (now % 20 == 0) {
            // le souffle gele
            level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getEyeY() - 0.1, player.getZ(),
                    3, 0.15, 0.08, 0.15, 0.01);
        }
        boolean frozen = player.getTicksFrozen() >= need;
        if (frozen && now % DAMAGE_EVERY == 0) {
            // le jeu blesse deja d'un demi-coeur qui peut geler ; on complete jusqu'a un coeur
            player.hurt(player.damageSources().freeze(), player.canFreeze() ? DAMAGE - 1.0F : DAMAGE);
        }
        warn(player, now, frozen);
    }

    /** Pour le banc : oublie la chaleur connue de ce joueur. */
    public static void forget(ServerPlayer player) {
        WARM.remove(player.getUUID());
        WARNED.remove(player.getUUID());
    }

    /** Dehors : sous le ciel (la lumiere du ciel y est entiere), et loin de tout feu. */
    public static boolean exposed(ServerLevel level, ServerPlayer player) {
        if (Boolean.TRUE.equals(WARM.get(player.getUUID()))) {
            return false;
        }
        BlockPos head = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        return level.getBrightness(LightLayer.SKY, head) >= level.getMaxLightLevel();
    }

    /** Un feu a portee : feu de camp allume, feu, lave, magma, four allume. */
    public static boolean nearFire(ServerLevel level, BlockPos at) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -FIRE_RADIUS; dx <= FIRE_RADIUS; dx++) {
                for (int dz = -FIRE_RADIUS; dz <= FIRE_RADIUS; dz++) {
                    pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (level.isLoaded(pos) && heat(level.getBlockState(pos))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean heat(BlockState state) {
        if (state.is(BlockTags.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.LAVA_CAULDRON)) {
            return true;
        }
        if (state.is(BlockTags.CAMPFIRES)) {
            return state.getValue(CampfireBlock.LIT);
        }
        return state.getBlock() instanceof AbstractFurnaceBlock && state.getValue(AbstractFurnaceBlock.LIT);
    }

    /** Une ligne au-dessus de la barre d'objets, a mi-gel puis gele, au plus toutes les dix secondes. */
    private static void warn(ServerPlayer player, long now, boolean frozen) {
        if (player.getPercentFrozen() < 0.5F) {
            return;
        }
        Long last = WARNED.get(player.getUUID());
        if (last != null && now - last < 200) {
            return;
        }
        WARNED.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable(frozen
                        ? "weather.emeraldweapons.aurore.cold.frozen"
                        : "weather.emeraldweapons.aurore.cold")
                .withStyle(ChatFormatting.AQUA), true);
    }
}
