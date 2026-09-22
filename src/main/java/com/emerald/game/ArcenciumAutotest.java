package com.emerald.game;

import com.emerald.haven.HavenAutotest;
import com.emerald.item.ArcenciumShieldItem;
import com.emerald.item.ModItems;
import com.emerald.item.Upgrade;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.specialization.Specialization;
import com.emerald.specialization.WingsFlight;
import com.emerald.weather.AuroreCold;
import com.emerald.weather.BattueHunt;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Le banc d'essai de la partie (cahier §83, retours du 22 sept.), INERTE sans
 * EMERALDWEAPONS_AUTOTEST=partie.
 *
 * Sur le serveur d'essai, SANS LES MODS DU PACK (run-server) : des creatures du jeu et des
 * joueurs factices, pres du point d'apparition.
 *   1. la forge : les chances des trois derniers crans, l'Heure Doree a +5, le metal perdu ;
 *   2. les coffres des sanctuaires : le butin suit l'avancee, et les anciennes tables y renvoient ;
 *   3. les ailes +20 : le vol d'elytre s'ouvre, tient apres la coupure du jeu, se pose au sol,
 *      et reste ferme sous +20 ;
 *   4. le bouclier d'Arcencium : pare des qu'il est leve (le bouclier du jeu non), riposte
 *      une fois par seconde ;
 *   5. le Grand Froid : on gele dehors, pas sous terre ni pres d'un feu de camp ;
 *   6. la Battue : la Proie ne brille que de pres, sa garde ne brille pas, tout s'en va a
 *      la fin, et les lueurs d'avant s'eteignent au rechargement.
 * Rapport dans partie_autotest.txt, puis arret.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class ArcenciumAutotest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static final boolean ENABLED = "partie".equalsIgnoreCase(
            Objects.requireNonNullElse(System.getenv(HavenAutotest.VARIABLE), "").trim());

    private static final int START_DELAY = 60;

    private static int waited;
    private static boolean done;
    private static final StringBuilder OUT = new StringBuilder();
    private static int passed;
    private static int failed;
    private static final List<Entity> SPAWNED = new ArrayList<>();

    private ArcenciumAutotest() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ENABLED || done || ++waited < START_DELAY) {
            return;
        }
        done = true;
        MinecraftServer server = event.getServer();
        line("autotest de la partie (cahier §83), " + LocalDateTime.now().withNano(0));
        try {
            ServerLevel level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            forge();
            loot(server, level, spawn);
            wings(level, spawn);
            shield(level, spawn);
            cold(level, spawn);
            battue(level, spawn);
        } catch (RuntimeException e) {
            LOGGER.error("autotest partie : exception", e);
            check("deroulement sans exception", false, e.toString());
        } finally {
            for (Entity entity : SPAWNED) {
                if (!entity.isRemoved()) {
                    entity.discard();
                }
            }
            SanctuariesTakenCondition.forcedProgress = null;
            end(server);
        }
    }

    // ================================================================ 1. la forge

    private static void forge() {
        line("--- la forge");
        check("vers +8, +9, +10 : 18, 9 et 4 % (26, 18 et 10 avant) ; les crans d'avant ne bougent pas",
                Upgrade.odds(7, false) == 18 && Upgrade.odds(8, false) == 9 && Upgrade.odds(9, false) == 4
                        && Upgrade.odds(6, false) == 36 && Upgrade.odds(0, false) == 90,
                Upgrade.odds(6, false) + " / " + Upgrade.odds(7, false) + " / " + Upgrade.odds(8, false)
                        + " / " + Upgrade.odds(9, false));
        check("l'Heure Doree : +15 jusqu'a +7, +5 seulement vers +8, +9 et +10",
                Upgrade.odds(6, true) == 51 && Upgrade.odds(7, true) == 23 && Upgrade.odds(8, true) == 14
                        && Upgrade.odds(9, true) == 9,
                Upgrade.odds(6, true) + " / " + Upgrade.odds(7, true) + " / " + Upgrade.odds(8, true)
                        + " / " + Upgrade.odds(9, true));
        check("un echec rend le metal jusqu'a +7, plus vers +8, +9 et +10",
                Upgrade.refunds(0) && Upgrade.refunds(6) && !Upgrade.refunds(7) && !Upgrade.refunds(9),
                "depuis +6 : " + Upgrade.refunds(6) + ", depuis +7 : " + Upgrade.refunds(7));
    }

    // ================================================================ 2. les coffres

    private static void loot(MinecraftServer server, ServerLevel level, BlockPos at) {
        line("--- les coffres des sanctuaires suivent l'avancee");
        LootTable table = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "chests/sanctuary")));
        LootTable old = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "chests/sanctuary_tier3")));
        int chests = 400;
        Map<String, Double> first = roll(level, at, table, 0, chests);
        Map<String, Double> second = roll(level, at, table, 1, chests);
        Map<String, Double> third = roll(level, at, table, 2, chests);
        Map<String, Double> oldFirst = roll(level, at, old, 0, chests);
        line("par coffre, en moyenne : 1er " + brief(first) + " ; 2e " + brief(second) + " ; 3e " + brief(third));
        check("le premier sanctuaire visite (aucun pris) : peu de fer, d'or, de diamant et d'Eclats ; ni netherite ni Arcencium",
                get(first, "minecraft:iron_ingot") < 1.5 && get(first, "minecraft:gold_ingot") < 0.6
                        && get(first, "minecraft:diamond") < 0.15 && get(first, "emeraldweapons:fate_shard") < 2.6
                        && get(first, "minecraft:iron_block") == 0.0 && get(first, "minecraft:netherite_ingot") == 0.0
                        && get(first, "emeraldweapons:arcencium_ingot") == 0.0,
                brief(first));
        check("le deuxieme (un pris) : l'ancien palier 2, le diamant pour de bon",
                get(second, "minecraft:diamond") > 2.0 && get(second, "emeraldweapons:fate_shard") > 5.0
                        && get(second, "minecraft:netherite_ingot") == 0.0,
                brief(second));
        check("le troisieme (deux pris) : le plus riche, netherite et Arcencium",
                get(third, "minecraft:netherite_ingot") > 0.5 && get(third, "emeraldweapons:arcencium_ingot") > 1.0
                        && get(third, "minecraft:diamond") > get(first, "minecraft:diamond"),
                brief(third));
        check("un coffre deja pose avec l'ancienne table du palier 3 suit l'avancee lui aussi (le premier : modeste)",
                get(oldFirst, "emeraldweapons:fate_shard") < 2.6 && get(oldFirst, "minecraft:netherite_ingot") == 0.0,
                brief(oldFirst));
    }

    private static Map<String, Double> roll(ServerLevel level, BlockPos at, LootTable table, int progress, int chests) {
        SanctuariesTakenCondition.forcedProgress = progress;
        Map<String, Double> sum = new HashMap<>();
        try {
            for (int i = 0; i < chests; i++) {
                LootParams params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(at))
                        .create(LootContextParamSets.CHEST);
                for (ItemStack stack : table.getRandomItems(params)) {
                    sum.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), (double) stack.getCount(), Double::sum);
                }
            }
        } finally {
            SanctuariesTakenCondition.forcedProgress = null;
        }
        sum.replaceAll((k, v) -> v / chests);
        return sum;
    }

    private static double get(Map<String, Double> map, String id) {
        return map.getOrDefault(id, 0.0);
    }

    private static String brief(Map<String, Double> m) {
        return String.format(Locale.ROOT, "fer %.2f, bloc de fer %.2f, or %.2f, diamant %.2f, netherite %.2f,"
                        + " Arcencium %.2f, Eclats %.2f",
                get(m, "minecraft:iron_ingot"), get(m, "minecraft:iron_block"), get(m, "minecraft:gold_ingot"),
                get(m, "minecraft:diamond"), get(m, "minecraft:netherite_ingot"),
                get(m, "emeraldweapons:arcencium_ingot"), get(m, "emeraldweapons:fate_shard"));
    }

    // ================================================================ 3. les ailes +20

    private static void wings(ServerLevel level, BlockPos spawn) {
        line("--- les ailes +20 : le vol d'elytre");
        BlockPos ground = surface(level, spawn.getX() + 6, spawn.getZ() + 6);
        FakePlayer fake = fake(level, "ailes", ground.above(12));
        try {
            fake.setOnGround(false);
            Specialization.set(fake, 19, null);
            WingsFlight.start(fake);
            boolean refused = !fake.isFallFlying() && !WingsFlight.flying(fake);
            Specialization.set(fake, Specialization.MAX, null);
            WingsFlight.start(fake);
            boolean opened = fake.isFallFlying() && WingsFlight.flying(fake);
            fake.stopFallFlying();                      // ce que fait le jeu a chaque tique, sans elytre
            WingsFlight.onPlayerTick(new PlayerTickEvent.Post(fake));
            boolean held = fake.isFallFlying();
            fake.setOnGround(true);
            fake.stopFallFlying();
            WingsFlight.onPlayerTick(new PlayerTickEvent.Post(fake));
            boolean landed = !fake.isFallFlying() && !WingsFlight.flying(fake);
            check("a +19 : refuse ; a +20 : le vol s'ouvre, tient apres la coupure du jeu, et se pose au sol",
                    refused && opened && held && landed,
                    "refuse " + refused + ", ouvert " + opened + ", tenu " + held + ", pose " + landed);
        } finally {
            Specialization.set(fake, 0, null);
        }
    }

    // ================================================================ 4. le bouclier

    private static void shield(ServerLevel level, BlockPos spawn) {
        line("--- le bouclier d'Arcencium");
        BlockPos at = surface(level, spawn.getX() - 6, spawn.getZ() - 6);
        Zombie blocker = zombie(level, at, 0.0F);
        Zombie wooden = zombie(level, at.east(3), 0.0F);
        Zombie attacker = zombie(level, at.south(2), 180.0F);
        if (blocker == null || wooden == null || attacker == null) {
            check("creatures d'essai posees", false, "zombie impossible");
            return;
        }
        blocker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.ARCENCIUM_SHIELD.get()));
        wooden.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        blocker.startUsingItem(InteractionHand.OFF_HAND);
        wooden.startUsingItem(InteractionHand.OFF_HAND);
        check("leve a l'instant : le bouclier d'Arcencium pare deja, celui du jeu pas encore",
                blocker.isBlocking() && !wooden.isBlocking(),
                "Arcencium " + blocker.isBlocking() + ", bois " + wooden.isBlocking());
        ItemStack shield = blocker.getItemInHand(InteractionHand.OFF_HAND);
        check("deux fois plus solide que le bouclier du jeu, repare a l'Arcencium",
                shield.getMaxDamage() == ArcenciumShieldItem.DURABILITY && shield.getMaxDamage() == 2 * new ItemStack(Items.SHIELD).getMaxDamage()
                        && shield.getItem().isValidRepairItem(shield, new ItemStack(ModItems.ARCENCIUM_INGOT.get())),
                "durabilite " + shield.getMaxDamage());
        float before = attacker.getHealth();
        float blockerBefore = blocker.getHealth();
        blocker.hurt(blocker.damageSources().mobAttack(attacker), 4.0F);
        float afterFirst = attacker.getHealth();
        blocker.hurt(blocker.damageSources().mobAttack(attacker), 4.0F);
        float afterSecond = attacker.getHealth();
        check("un coup pare : l'assaillant perd de la vie (riposte), le porteur rien ; un second coup dans la seconde : pas de riposte",
                afterFirst < before && blocker.getHealth() == blockerBefore && afterSecond == afterFirst,
                String.format(Locale.ROOT, "assaillant %.1f -> %.1f -> %.1f, porteur %.1f -> %.1f",
                        before, afterFirst, afterSecond, blockerBefore, blocker.getHealth()));
    }

    // ================================================================ 5. le Grand Froid

    private static void cold(ServerLevel level, BlockPos spawn) {
        line("--- le Grand Froid de l'Aurore");
        BlockPos ground = surface(level, spawn.getX() + 12, spawn.getZ() - 12);
        FakePlayer out = fake(level, "froid-dehors", ground);
        FakePlayer under = fake(level, "froid-sous-terre", new BlockPos(ground.getX(), level.getMinBuildHeight() + 12, ground.getZ()));
        long t0 = (level.getGameTime() / 40 + 1) * 40;
        for (long t = t0; t < t0 + 60; t++) {
            AuroreCold.tickPlayer(level, out, t);
            AuroreCold.tickPlayer(level, under, t);
        }
        int outside = out.getTicksFrozen();
        int below = under.getTicksFrozen();
        check("dehors, le gel monte (60 tiques : " + outside + ") ; sous terre, rien",
                outside >= 140 && below == 0, "dehors " + outside + ", sous terre " + below);
        // un feu de camp allume a deux blocs : on se rechauffe
        BlockPos fire = ground.east(2);
        level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 3);
        try {
            FakePlayer warm = fake(level, "froid-feu", ground);
            AuroreCold.forget(warm);
            for (long t = t0; t < t0 + 60; t++) {
                AuroreCold.tickPlayer(level, warm, t);
            }
            check("a deux blocs d'un feu de camp allume : pas de gel", warm.getTicksFrozen() == 0
                    && AuroreCold.nearFire(level, warm.blockPosition()), "gel " + warm.getTicksFrozen());
        } finally {
            level.setBlock(fire, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    // ================================================================ 6. la Battue

    private static void battue(ServerLevel level, BlockPos spawn) {
        line("--- la Battue : seule la Proie brille, de pres ; sa garde");
        BlockPos at = surface(level, spawn.getX() - 12, spawn.getZ() + 12);
        Entity made = EntityType.PIG.spawn(level, at, MobSpawnType.COMMAND);
        if (!(made instanceof net.minecraft.world.entity.LivingEntity beast)) {
            check("gibier d'essai pose", false, "cochon impossible");
            return;
        }
        SPAWNED.add(beast);
        BlockPos farSpot = surface(level, at.getX() + 40, at.getZ());
        FakePlayer hunter = fake(level, "battue", farSpot);
        BattueHunt.adoptForTest(level, beast, hunter);
        List<UUID> escort = BattueHunt.escortForTest();
        List<Entity> guards = new ArrayList<>();
        for (UUID id : escort) {
            Entity guard = level.getEntity(id);
            if (guard != null) {
                guards.add(guard);
                SPAWNED.add(guard);
            }
        }
        long t = (level.getGameTime() / 20 + 1) * 20;
        BattueHunt.tickForTest(level, t);
        boolean darkFar = !beast.hasGlowingTag();
        hunter.moveTo(at.getX() + 8.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        BattueHunt.tickForTest(level, t + 20);
        boolean litNear = beast.hasGlowingTag();
        boolean guardsDark = guards.stream().noneMatch(Entity::hasGlowingTag);
        boolean guardsTagged = guards.stream().allMatch(g -> g.getTags().contains(BattueHunt.TAG_ESCORT));
        check("la Proie : eteinte a 40 blocs, allumee a 8 ; sa garde (" + guards.size() + ") : marquee, jamais allumee",
                darkFar && litNear && guards.size() >= 2 && guardsDark && guardsTagged,
                "loin " + !darkFar + ", pres " + litNear + ", gardes " + guards.size() + ", eteintes " + guardsDark);
        BattueHunt.endForTest(level);
        boolean gone = beast.isRemoved() && guards.stream().allMatch(Entity::isRemoved);
        check("fin de la Battue : la Proie et sa garde s'en vont", gone,
                "Proie retiree " + beast.isRemoved() + ", gardes retirees "
                        + guards.stream().filter(Entity::isRemoved).count() + "/" + guards.size());

        // au rechargement d'un troncon : les lueurs d'avant s'eteignent, sauf les yeux des Echos ;
        // une Proie ou un garde d'une Battue finie ne revient pas
        Zombie old = EntityType.ZOMBIE.create(level);
        Zombie eye = EntityType.ZOMBIE.create(level);
        Zombie stray = EntityType.ZOMBIE.create(level);
        if (old == null || eye == null || stray == null) {
            check("creatures d'essai", false, "zombie impossible");
            return;
        }
        for (Zombie z : List.of(old, eye, stray)) {
            z.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 3.5, 0.0F, 0.0F);
            SPAWNED.add(z);
        }
        old.setGlowingTag(true);
        eye.setGlowingTag(true);
        eye.addTag(com.emerald.mine.Echoes.TAG_EYE);
        stray.addTag(BattueHunt.TAG_ESCORT);
        level.addFreshEntity(old);
        level.addFreshEntity(eye);
        boolean strayAdded = level.addFreshEntity(stray);
        check("rechargement : une lueur d'une ancienne Battue s'eteint, l'oeil des Echos garde la sienne,"
                        + " un garde d'une Battue finie ne revient pas",
                !old.hasGlowingTag() && eye.hasGlowingTag() && !strayAdded,
                "ancienne lueur " + old.hasGlowingTag() + ", oeil " + eye.hasGlowingTag() + ", garde ajoute " + strayAdded);
    }

    // ================================================================ outils

    private static BlockPos surface(ServerLevel level, int x, int z) {
        level.getChunkAt(new BlockPos(x, 0, z));
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
    }

    private static FakePlayer fake(ServerLevel level, String name, BlockPos feet) {
        FakePlayer fake = new FakePlayer(level, new GameProfile(
                UUID.nameUUIDFromBytes(("autotest-partie:" + name).getBytes(StandardCharsets.UTF_8)), "[Partie]"));
        level.getChunkAt(feet);
        fake.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 0.0F);
        return fake;
    }

    @javax.annotation.Nullable
    private static Zombie zombie(ServerLevel level, BlockPos feet, float yaw) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            return null;
        }
        zombie.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, 0.0F);
        zombie.setYHeadRot(yaw);
        zombie.setNoAi(true);
        level.addFreshEntity(zombie);
        SPAWNED.add(zombie);
        return zombie;
    }

    private static void end(MinecraftServer server) {
        line("RESULTAT : " + passed + " OK, " + failed + " KO");
        Path file = server.getServerDirectory().resolve("partie_autotest.txt");
        try {
            Files.writeString(file, OUT.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("autotest partie : rapport impossible a ecrire dans {}", file, e);
        }
        LOGGER.info("autotest partie : {} OK, {} KO, rapport dans {} ; arret du serveur",
                passed, failed, file.toAbsolutePath());
        server.halt(false);
    }

    private static void line(String text) {
        OUT.append(text).append('\n');
        LOGGER.info("autotest partie : {}", text);
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        line((ok ? "OK  " : "KO  ") + what + " -- " + detail);
    }
}
