package com.emerald.weather;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CE QUI DONNE ENVIE DE SE BATTRE PENDANT LA BATTUE : la Proie et la Serie.
 *
 * LA PROIE. Une seule par Battue, tiree au sort entre le cerf et le sanglier
 * du Twilight Forest -- deux betes terrestres, ni nageuses ni volantes. Elle
 * n'attaque pas : ELLE FUIT, a la vitesse de sprint du joueur ou un peu plus,
 * si bien qu'en ligne droite on ne la rattrape pas. Il faut l'acculer, ou
 * sortir ses outils -- une fleche, la Ruee, les ailes. Le cerf est le plus
 * rapide ; le sanglier, accule, CHARGE une fois puis refuit. Elle brame toutes
 * les vingt secondes, sa position est sur la boussole, et une laisse de cent
 * vingt blocs la garde autour de son point d'apparition : elle tourne, elle ne
 * fuit pas dans l'infini. L'abattre sonne l'hallali et rend une rune au plafond
 * de la phase, des plumes et une grosse part d'experience. La rater ne coute
 * rien : « La Proie s'est echappee », c'est tout.
 *
 * LA SERIE. Deux kills a moins de huit secondes d'ecart ouvrent une serie :
 * x1,5 de butin, x2 a cinq kills, x3 a dix. Elle s'applique a tout ce que la
 * Battue rend deja -- plumes, pierres, cristaux, runes, experience -- et elle
 * TOMBE si l'on s'arrete. C'est ce qui fait enchainer au lieu de piocher un
 * monstre et rentrer. Le client la dessine (StreakHudClient) depuis le tic du
 * dernier kill : on ne lui parle qu'aux kills.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class BattueHunt {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final String DEER = "twilightforest:deer";
    private static final String BOAR = "twilightforest:boar";
    private static final String[] DEER_NAMES = {"Cerf Noir", "Seize-Cors", "Cerf de Prisme", "Grand Brocard"};
    private static final String[] BOAR_NAMES = {"Solitaire", "Vieille Bête", "Écorché", "Ragot"};

    /** Ou elle apparait : assez loin pour etre une traque, assez pres pour etre vue. */
    private static final double SPAWN_MIN = 70.0;
    private static final double SPAWN_SPAN = 20.0;
    /** La laisse : au-dela, elle revient vers son point d'apparition. */
    private static final double LEASH = 120.0;
    private static final float PREY_HEALTH = 60.0F;
    private static final double PREY_SCALE = 1.6;
    /** Le brame : toutes les vingt secondes. */
    private static final int BUGLE_EVERY = 400;
    /** La charge du sanglier : portee et repit. */
    private static final double CHARGE_REACH = 2.8;
    private static final int CHARGE_REST = 80;
    private static final float CHARGE_DAMAGE = 6.0F;

    /** La serie : huit secondes entre deux kills, et les paliers. */
    public static final int STREAK_WINDOW = 160;

    private record Streak(int count, long lastKill) {
    }

    private static final Map<UUID, Streak> streaks = new HashMap<>();

    @Nullable
    private static UUID prey;
    private static BlockPos preyHome = BlockPos.ZERO;
    private static boolean preyIsBoar;
    private static String preyName = "";
    private static long lastCharge;

    private BattueHunt() {
    }

    // ------------------------------------------------------------- cycle

    static void begin(ServerLevel level) {
        streaks.clear();
        prey = null;
        if (level.players().isEmpty()) {
            return;
        }
        spawnPrey(level, level.players().get(0));
    }

    static void tick(ServerLevel level) {
        long now = level.getGameTime();
        tickPrey(level, now);
        expireStreaks(level, now);
    }

    static void end(ServerLevel level) {
        LivingEntity beast = preyEntity(level);
        if (beast != null && beast.isAlive()) {
            for (ServerPlayer player : level.players()) {
                player.sendSystemMessage(Component.translatable("game.emeraldweapons.battue.prey.escaped")
                        .withStyle(ChatFormatting.GRAY));
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    beast.getX(), beast.getY() + 0.8, beast.getZ(), 24, 0.6, 0.5, 0.6, 0.02);
            beast.discard();
        }
        prey = null;
        for (UUID id : streaks.keySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                PacketDistributor.sendToPlayer(player,
                        new com.emerald.network.StreakPayload(0, 10, 0L));
            }
        }
        streaks.clear();
    }

    // ------------------------------------------------------------ la Proie

    private static void spawnPrey(ServerLevel level, ServerPlayer near) {
        preyIsBoar = level.random.nextBoolean();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
                ResourceLocation.parse(preyIsBoar ? BOAR : DEER)).orElse(null);
        if (type == null) {
            LOGGER.info("Battue : pas de gibier dans ce pack ({}), pas de Proie", preyIsBoar ? BOAR : DEER);
            return;
        }
        double angle = level.random.nextDouble() * Math.PI * 2;
        double distance = SPAWN_MIN + level.random.nextDouble() * SPAWN_SPAN;
        int x = (int) Math.round(near.getX() + Math.cos(angle) * distance);
        int z = (int) Math.round(near.getZ() + Math.sin(angle) * distance);
        BlockPos spot = com.emerald.game.WorldSetup.findOpenGround(level,
                new BlockPos(x, com.emerald.game.WorldSetup.surfaceY(level, x, z), z), 8);
        Entity created = type.create(level);
        if (!(created instanceof PathfinderMob beast)) {
            return;
        }
        String[] names = preyIsBoar ? BOAR_NAMES : DEER_NAMES;
        preyName = names[level.random.nextInt(names.length)];
        beast.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                level.random.nextFloat() * 360.0F, 0.0F);
        beast.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
        beast.setCustomName(Component.literal(preyName));
        beast.setCustomNameVisible(true);
        beast.setPersistenceRequired();
        beast.addTag(BattueScene.TAG_PREY);
        // PLUS GROSSE, PLUS SOLIDE : l'attribut d'echelle du jeu, sans retexture
        setBase(beast, Attributes.SCALE, PREY_SCALE);
        setBase(beast, Attributes.MAX_HEALTH, PREY_HEALTH);
        beast.setHealth(PREY_HEALTH);
        // SA VITESSE EST CELLE DU SPRINT DU JOUEUR, OU UN PEU PLUS.
        //
        // L'attribut vaut ~0,10 pour un pas ; le sprint du joueur vaut ~0,13.
        // Le cerf fuit a 0,145 (on ne le rattrape pas en ligne droite), le
        // sanglier a 0,13 (a egalite : on le rattrape en coupant).
        setBase(beast, Attributes.MOVEMENT_SPEED, 0.10);
        beast.goalSelector.removeAllGoals(goal -> true);
        beast.targetSelector.removeAllGoals(goal -> true);
        beast.goalSelector.addGoal(0, new FloatGoal(beast));
        beast.goalSelector.addGoal(1, new AvoidEntityGoal<>(beast, Player.class, 24.0F,
                preyIsBoar ? 1.1 : 1.2, preyIsBoar ? 1.3 : 1.45));
        beast.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(beast, 1.0));
        beast.goalSelector.addGoal(6, new LookAtPlayerGoal(beast, Player.class, 12.0F));
        level.addFreshEntity(beast);
        prey = beast.getUUID();
        preyHome = spot;
        lastCharge = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.battue.prey.spawned",
                            Component.literal(preyName).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.YELLOW));
        }
        LOGGER.info("Battue : la Proie « {} » ({}) en {}", preyName, preyIsBoar ? "sanglier" : "cerf", spot);
    }

    private static void setBase(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        var instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Nullable
    private static LivingEntity preyEntity(ServerLevel level) {
        if (prey == null) {
            return null;
        }
        Entity entity = level.getEntity(prey);
        return entity instanceof LivingEntity living ? living : null;
    }

    private static void tickPrey(ServerLevel level, long now) {
        LivingEntity beast = preyEntity(level);
        if (beast == null || !beast.isAlive()) {
            return;
        }
        // la laisse : trop loin de chez elle, elle y revient
        if (beast instanceof PathfinderMob mob && beast.distanceToSqr(preyHome.getX(), preyHome.getY(),
                preyHome.getZ()) > LEASH * LEASH && now % 20 == 0) {
            mob.getNavigation().moveTo(preyHome.getX(), preyHome.getY(), preyHome.getZ(), 1.2);
        }
        // le brame : on l'entend, on sait ou chercher
        if (now % BUGLE_EVERY == 0 && beast instanceof net.minecraft.world.entity.Mob mob) {
            mob.playAmbientSound();
        }
        // la position sur la boussole, toutes les deux secondes
        if (now % 40 == 0) {
            for (ServerPlayer player : level.players()) {
                PacketDistributor.sendToPlayer(player, new com.emerald.network.VeinSyncPayload(
                        List.of(beast.blockPosition().asLong()), com.emerald.network.VeinSyncPayload.KIND_PREY));
            }
        }
        // LA CHARGE DU SANGLIER : accule, il rend un coup, puis il refuit
        if (preyIsBoar && now - lastCharge >= CHARGE_REST) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceTo(beast) <= CHARGE_REACH && !player.isCreative() && !player.isSpectator()) {
                    player.hurt(level.damageSources().mobAttack(beast), CHARGE_DAMAGE);
                    player.knockback(1.2, beast.getX() - player.getX(), beast.getZ() - player.getZ());
                    level.playSound(null, beast.blockPosition(), SoundEvents.HOGLIN_ATTACK,
                            SoundSource.HOSTILE, 1.0F, 0.8F);
                    beast.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 2, true, false, false));
                    lastCharge = now;
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------ la Serie

    /** Le multiplicateur de butin du joueur, 1 hors serie. */
    public static double multiplier(Player player) {
        Streak streak = streaks.get(player.getUUID());
        if (streak == null) {
            return 1.0;
        }
        return multiplier(streak.count());
    }

    private static double multiplier(int count) {
        return count >= 10 ? 3.0 : count >= 5 ? 2.0 : count >= 2 ? 1.5 : 1.0;
    }

    private static void expireStreaks(ServerLevel level, long now) {
        var it = streaks.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (now - entry.getValue().lastKill() <= STREAK_WINDOW) {
                continue;
            }
            it.remove();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                PacketDistributor.sendToPlayer(player, new com.emerald.network.StreakPayload(0, 10, 0L));
            }
        }
    }

    /**
     * Au kill : la serie AVANT le butin.
     *
     * Priorite haute pour passer avant HeroEvents (l'experience) -- et les
     * drops (LivingDropsEvent) viennent de toute facon apres la mort. Le kill
     * qui ouvre un palier en profite donc lui-meme.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
                || WeatherManager.current() != Weather.BATTUE
                || !(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || victim.getTags().contains(BattueScene.TAG_RAVEN)) {
            return;
        }
        long now = level.getGameTime();
        Streak before = streaks.get(killer.getUUID());
        int count = before != null && now - before.lastKill() <= STREAK_WINDOW ? before.count() + 1 : 1;
        streaks.put(killer.getUUID(), new Streak(count, now));
        PacketDistributor.sendToPlayer(killer, new com.emerald.network.StreakPayload(
                count, (int) Math.round(multiplier(count) * 10.0), now));

        if (victim.getTags().contains(BattueScene.TAG_PREY)) {
            hallali(level, killer, victim);
        }
    }

    /** L'hallali : le cor a la mort, et la paie de la Proie. */
    private static void hallali(ServerLevel level, ServerPlayer killer, LivingEntity beast) {
        prey = null;
        var horns = SoundEvents.GOAT_HORN_SOUND_VARIANTS;
        for (ServerPlayer player : level.players()) {
            player.playNotifySound(horns.get(5).value(), SoundSource.WEATHER, 1.0F, 1.0F);
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.battue.prey.killed",
                            Component.literal(preyName).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.YELLOW));
        }
        double x = beast.getX();
        double y = beast.getY() + 0.5;
        double z = beast.getZ();
        level.addFreshEntity(new ItemEntity(level, x, y, z,
                com.emerald.rune.RuneDrops.preyRune(level, level.random)));
        level.addFreshEntity(new ItemEntity(level, x, y, z,
                new ItemStack(com.emerald.item.ModItems.ARCENCIUM_FEATHER.get(),
                        3 + level.random.nextInt(3))));
        com.emerald.hero.HeroEvents.award(killer, 60);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                x, y + 0.5, z, 30, 0.8, 0.6, 0.8, 0.05);
    }
}
