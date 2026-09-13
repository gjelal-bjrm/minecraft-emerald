package com.emerald.haven;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Les regles de la ville : on n'en sort pas, et rien ne s'y casse.
 *
 * FILTREES SUR LA DIMENSION, ET INDEPENDANTES DE L'INTERRUPTEUR DU MODE.
 * « /arcencium mode off » eteint la partie ; il ne rend pas la ville cassable.
 * Seule la derogation « chantier » les leve : volatile, nominative, reservee a
 * l'operateur qui l'ouvre pour lui-meme.
 *
 * LE MODE AVENTURE NE SUFFIT PAS. Il interdit de casser et de poser, mais un
 * cadre lache son objet a la premiere tape, un tableau tombe sans que le jeu
 * demande si l'on a le droit de batir, un porte-armure echange son equipement,
 * et un pot se vide au clic. Chacun de ces cas est donc refuse ici.
 *
 * ON N'EN SORT PAS : un controle de position cote serveur, toutes les dix
 * tiques, ramene a la derniere position au sol quiconque quitte la grille ou
 * passe au-dessus de son sommet. Il rattrape ce qu'aucun evenement ne voit :
 * les teleporteurs et les sorts des mods, les ailes.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenRules {

    /** Intervalle du controle de position : une demi-seconde, sans lutte avec le client. */
    private static final int CHECK = 10;

    /** Les operateurs en chantier. Volatil : un redemarrage referme tous les chantiers. */
    private static final Set<UUID> CHANTIER = new HashSet<>();

    /** La derniere position au sol de chaque joueur dans la ville. */
    private static final Map<UUID, Vec3> GROUND = new HashMap<>();

    private HavenRules() {
    }

    public static boolean chantier(@Nullable Player player) {
        return player != null && CHANTIER.contains(player.getUUID());
    }

    /** Vrai si l'acteur n'est pas un operateur en chantier : la regle s'applique a lui. */
    private static boolean guarded(@Nullable Entity actor) {
        return !(actor instanceof Player player && chantier(player));
    }

    private static boolean inHaven(LevelAccessor level) {
        return level instanceof Level real && Haven.is(real);
    }

    /** Cadres, tableaux, porte-armures : les decors qu'un joueur en aventure abimerait. */
    private static boolean isDecor(@Nullable Entity entity) {
        return entity instanceof HangingEntity || entity instanceof ArmorStand;
    }

    /** Ouvre ou ferme le chantier d'un operateur. */
    public static void setChantier(ServerPlayer player, boolean on) {
        if (on) {
            CHANTIER.add(player.getUUID());
        } else {
            CHANTIER.remove(player.getUUID());
        }
        if (Haven.is(player.level())) {
            enter(player);
        }
    }

    // ------------------------------------------------------------- mode de jeu

    /** A l'entree dans la ville : le mode d'origine est retenu, l'aventure imposee. */
    private static void enter(ServerPlayer player) {
        GameType current = player.gameMode.getGameModeForPlayer();
        HavenState.get(player.server).rememberMode(player.getUUID(), current);
        GameType wanted = chantier(player) ? GameType.CREATIVE : GameType.ADVENTURE;
        if (current != wanted) {
            player.setGameMode(wanted);
        }
    }

    /** Hors de la ville : le mode d'origine est rendu, s'il avait ete retenu. */
    private static void leave(ServerPlayer player) {
        GROUND.remove(player.getUUID());
        GameType original = HavenState.get(player.server).forgetMode(player.getUUID());
        if (original != null && player.gameMode.getGameModeForPlayer() != original) {
            player.setGameMode(original);
        }
    }

    /**
     * A la connexion, dans un sens comme dans l'autre.
     *
     * Un joueur deconnecte dans la ville et reconnecte au village -- apres le
     * depart, ou renvoye par un operateur -- ne change pas de dimension sous nos
     * yeux : c'est ici qu'il retrouve son mode.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (Haven.is(player.level())) {
            enter(player);
        } else {
            leave(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHANTIER.remove(event.getEntity().getUUID());
        GROUND.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getTo().equals(Haven.LEVEL)) {
            enter(player);
        } else if (event.getFrom().equals(Haven.LEVEL)) {
            leave(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (Haven.is(player.level())) {
            enter(player);
        } else {
            leave(player);
        }
    }

    // ------------------------------------------------------------- blocs

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!inHaven(event.getLevel()) || chantier(event.getPlayer())) {
            return;
        }
        event.setCanceled(true);
        if (event.getPlayer() instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.protected")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (inHaven(event.getLevel()) && guarded(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (inHaven(event.getLevel()) && guarded(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Un fluide qui fabrique un bloc -- pierre, obsidienne, feu -- ne change pas la ville. */
    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (inHaven(event.getLevel())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (inHaven(event.getLevel()) && guarded(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Hache, pelle, houe : ecorcer, aplanir, labourer. */
    @SubscribeEvent
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (inHaven(event.getLevel()) && guarded(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (inHaven(event.getLevel())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (Haven.is(event.getEntity().level())) {
            event.setCanGrief(false);
        }
    }

    /**
     * Une explosion ne detruit rien, et ne touche ni joueurs ni decors.
     *
     * L'evenement ne s'annule pas, mais sa liste de blocs est celle que
     * l'explosion va detruire : la vider suffit.
     */
    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!Haven.is(event.getLevel())) {
            return;
        }
        event.getAffectedBlocks().clear();
        event.getAffectedEntities().removeIf(entity -> entity instanceof Player || isDecor(entity));
    }

    // ------------------------------------------------------------- teleportations

    @SubscribeEvent
    public static void onEnderPearl(EntityTeleportEvent.EnderPearl event) {
        if (Haven.is(event.getPlayer().level()) && !chantier(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChorusFruit(EntityTeleportEvent.ChorusFruit event) {
        LivingEntity entity = event.getEntityLiving();
        if (Haven.is(entity.level()) && guarded(entity)) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------- decors et combat

    /** Ni combat entre joueurs, ni coup sur un decor. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !Haven.is(player.level())) {
            return;
        }
        Entity target = event.getTarget();
        if (target instanceof Player || (isDecor(target) && !chantier(player))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide() && Haven.is(event.getLevel())
                && isDecor(event.getTarget()) && !chantier(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Le porte-armure echange son equipement par ce clic-la, au point vise. */
    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getLevel().isClientSide() && Haven.is(event.getLevel())
                && isDecor(event.getTarget()) && !chantier(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Une fleche ne decroche pas un cadre. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Entity projectile = event.getProjectile();
        if (projectile.level().isClientSide() || !Haven.is(projectile.level())) {
            return;
        }
        if (event.getRayTraceResult() instanceof EntityHitResult hit && isDecor(hit.getEntity())
                && guarded(event.getProjectile().getOwner())) {
            event.setCanceled(true);
        }
    }

    /**
     * Les blocs qu'un clic modifie sans rien casser.
     *
     * Portes, trappes et boutons restent permis : la ville se visite.
     * Le lit est refuse au clic : la dimension le laisse fonctionner -- sinon il
     * explose -- mais on ne dort pas dans la ville.
     */
    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide() || !Haven.is(level) || chantier(event.getEntity())) {
            return;
        }
        Block block = level.getBlockState(event.getPos()).getBlock();
        if (block instanceof FlowerPotBlock || block instanceof DecoratedPotBlock
                || block instanceof LecternBlock || block instanceof ChiseledBookShelfBlock
                || block instanceof CakeBlock || block instanceof CandleCakeBlock
                || block instanceof BedBlock) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        if (Haven.is(event.getLevel())) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }

    /**
     * Pas de degats dans la ville.
     *
     * Sauf ceux qui passent outre l'invulnerabilite -- le vide, /kill -- : un
     * operateur doit pouvoir tuer un joueur coince.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || !Haven.is(target.level())
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (target instanceof Player
                || (target instanceof ArmorStand && guarded(event.getSource().getEntity()))) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------- faim et limites

    /**
     * La faim, et la limite de la ville.
     *
     * On TELEPORTE a la derniere position au sol enregistree plutot qu'au point
     * libre le plus proche : dans une ville, le point libre qui voit le ciel est
     * un toit, et l'on ramenait les joueurs sur les toits.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Haven.is(player.level())
                || player.tickCount % CHECK != 0) {
            return;
        }
        FoodData food = player.getFoodData();
        if (food.getFoodLevel() < 20) {
            food.setFoodLevel(20);
        }
        if (food.getSaturationLevel() < 5.0F) {
            food.setSaturation(5.0F);
        }
        if (chantier(player)) {
            return;
        }
        HavenState state = HavenState.get(player.server);
        BlockPos origin = state.origin();
        double x = player.getX();
        double z = player.getZ();
        boolean inside = x >= origin.getX() && x < origin.getX() + state.width()
                && z >= origin.getZ() && z < origin.getZ() + state.depth()
                && player.getY() <= origin.getY() + state.height();
        if (inside) {
            if (player.onGround()) {
                GROUND.put(player.getUUID(), player.position());
            }
            return;
        }
        Vec3 back = GROUND.get(player.getUUID());
        if (back == null) {
            back = Vec3.atBottomCenterOf(origin.offset(Haven.BAR_FRONT_CELL));
        }
        player.stopRiding();
        player.teleportTo(back.x, back.y, back.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.bounds")
                .withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CHANTIER.clear();
        GROUND.clear();
    }
}
