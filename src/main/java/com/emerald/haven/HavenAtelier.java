package com.emerald.haven;

import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * L'atelier de la ville : un monde ou le joueur retouche Haven, et d'ou ses
 * retouches repartent dans le mod.
 *
 * LE JOUEUR (21 sept.) : « Peut-etre tu pourrais me creer un monde qui contient
 * uniquement Haven City, dans lequel je peux me connecter en mode dev pour le
 * corriger, et ensuite tu le recuperes tel quel, car l'objectif c'est qu'il soit
 * meuble et qu'il soit un peu ameliore. »
 *
 * UN MONDE EN ATELIER (HavenState.atelier, sauvegarde avec lui) :
 * - chaque operateur y est en chantier (HavenRules.chantier) : creatif, les
 *   protections levees, libre de sortir de la grille, sans Morph Gun ;
 * - la ville y est VIDE : ni monstres, ni habitants, ni trafic, ni eco
 *   (HavenInvasion.cityOpen est faux) -- rien ne gene, rien ne se casse ;
 * - on y arrive devant le bar, pas dans un appartement (HavenArrival).
 *
 * LE RELEVE (JakCityCapture) : « /arcencium haven atelier releve », ou tout seul
 * quand le client de dev est lance avec EMERALDWEAPONS_ATELIER=releve (run
 * « atelier » de build.gradle) : il entre dans le monde, releve la ville, ecrit
 * run/arcencium_jak/ville.nbt et quitte. tools/jak_zone_apply.py le copie ensuite
 * dans le mod, ou JakOverlay le rejoue a chaque pose de la ville.
 *
 * LA CREATION DU MONDE : un serveur lance avec EMERALDWEAPONS_ATELIER=creer, sur un
 * monde neuf, pose la ville (HavenSite, comme tout monde neuf), passe le monde en
 * atelier, et s'arrete.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenAtelier {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /**
     * « creer » : poser la ville d'un monde neuf, le passer en atelier, arreter. « releve » : relever et quitter.
     * « portes » : reposer les portes d'office qui manquent, puis relever et quitter.
     */
    public static final String VARIABLE = "EMERALDWEAPONS_ATELIER";
    private static final String AUTO = Objects.requireNonNullElse(System.getenv(VARIABLE), "").trim()
            .toLowerCase(Locale.ROOT);
    /** Au-dela, l'automate abandonne : dix minutes. */
    private static final int AUTO_TIMEOUT = 20 * 60 * 10;

    private static int autoTicks;
    private static boolean autoStarted;
    /** Le releve automatique est fini (ou abandonne) : le client de dev se ferme (HavenAtelierClient). */
    private static volatile boolean autoFinished;

    private HavenAtelier() {
    }

    /** Ce monde est-il l'atelier de la ville ? */
    public static boolean on(MinecraftServer server) {
        return HavenState.get(server).atelier();
    }

    /** Un batisseur de l'atelier : un vrai joueur operateur, dans un monde en atelier. */
    public static boolean builder(ServerPlayer player) {
        return !player.isFakePlayer() && player.hasPermissions(2) && HavenState.get(player.server).atelier();
    }

    /** Le client de dev lance pour le releve automatique : doit-il se fermer ? */
    public static boolean autoReleve() {
        return "releve".equals(AUTO) || "portes".equals(AUTO);
    }

    public static boolean autoFinished() {
        return autoFinished;
    }

    /** Passe le monde en atelier, ou l'en sort ; les joueurs dans la ville changent de mode aussitot. */
    public static void set(MinecraftServer server, boolean on) {
        HavenState.get(server).setAtelier(on);
        ServerLevel level = Haven.level(server);
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                HavenRules.refresh(player);
            }
        }
        LOGGER.info("ville de Haven : atelier {}", on ? "ouvert" : "ferme");
    }

    /** Devant le bar, tourne vers sa porte : la ou l'on arrive dans l'atelier. */
    public static void toBarFront(ServerPlayer player) {
        ServerLevel level = Haven.level(player.server);
        if (level == null) {
            return;
        }
        BlockPos feet = HavenState.get(player.server).origin().offset(Haven.BAR_FRONT_CELL);
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, Haven.BAR_FRONT_YAW, 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    /** Le dossier des releves : run/arcencium_jak/ en dev. */
    public static Path directory(MinecraftServer server) {
        return JakDiff.directory(server);
    }

    // ================================================================ commandes

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("arcencium")
                .requires(source -> source.hasPermission(2));
        root.then(Commands.literal("haven")
                .then(Commands.literal("atelier")
                        .executes(ctx -> status(ctx.getSource()))
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), false)))
                        .then(Commands.literal("releve").executes(ctx -> capture(ctx.getSource())))
                        .then(Commands.literal("portes").executes(ctx -> doors(ctx.getSource())))));
        event.getDispatcher().register(root);
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Path file = directory(server).resolve(JakCityCapture.NAME + ".nbt");
        String last = "-";
        if (Files.isRegularFile(file)) {
            try {
                last = Files.getLastModifiedTime(file).toString();
            } catch (java.io.IOException e) {
                last = "?";
            }
        }
        String shown = last;
        source.sendSuccess(() -> Component.translatable(on(server)
                ? "command.emeraldweapons.haven.atelier.status.on"
                : "command.emeraldweapons.haven.atelier.status.off", file.toString(), shown), false);
        return 1;
    }

    private static int toggle(CommandSourceStack source, boolean on) {
        set(source.getServer(), on);
        source.sendSuccess(() -> Component.translatable(on
                ? "command.emeraldweapons.haven.atelier.on"
                : "command.emeraldweapons.haven.atelier.off"), true);
        return 1;
    }

    /** Les portes de Jak 3 d'office qui manquent (une cassee par megarde), reposees. */
    private static int doors(CommandSourceStack source) {
        int[] counts = com.emerald.haven.door.HavenDoors.replaceMissing(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.haven.atelier.portes",
                counts[0], counts[1], counts[2]), true);
        return counts[0];
    }

    private static int capture(CommandSourceStack source) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        ServerPlayer player = source.getPlayer();
        Component refused = JakCityCapture.start(server, player, directory(server), source.getTextName(), null);
        if (refused != null) {
            source.sendFailure(refused);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.haven.atelier.releve.started"), false);
        return 1;
    }

    // ================================================================ arrivee et rappel

    /**
     * A la connexion d'un batisseur : ou il en est, et comment rendre son travail.
     * Le placement lui-meme (devant le bar s'il n'est pas dans la ville) se fait dans
     * HavenArrival, a la tique suivante, avec les autres.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !builder(player)) {
            return;
        }
        String command = "/arcencium haven atelier releve";
        Component click = Component.literal(command).withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("game.emeraldweapons.haven.atelier.click"))));
        player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.atelier.welcome")
                .withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.atelier.howto", click)
                .withStyle(ChatFormatting.AQUA));
    }

    // ================================================================ automates

    /**
     * Les deux automates, sans effet si EMERALDWEAPONS_ATELIER n'est pas pose.
     *
     * « creer » attend que la pose du monde neuf soit finie, passe le monde en
     * atelier et arrete le serveur (qui sauvegarde). « releve » attend qu'un joueur
     * soit dans le monde -- le client de dev l'y a fait entrer --, lance le releve,
     * et ferme le client a la fin (HavenAtelierClient).
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (AUTO.isEmpty() || autoFinished) {
            return;
        }
        MinecraftServer server = event.getServer();
        autoTicks++;
        if (autoTicks > AUTO_TIMEOUT) {
            LOGGER.error("atelier ({}) : rien n'a abouti en dix minutes, abandon", AUTO);
            finishAuto(server);
            return;
        }
        HavenState state = HavenState.get(server);
        boolean ready = Haven.level(server) != null && state.built() && !HavenSite.busy();
        switch (AUTO) {
            case "creer" -> {
                if (ready) {
                    set(server, true);
                    LOGGER.info("atelier : ville posee (sha1 {}), monde passe en atelier ; arret du serveur",
                            state.sha1());
                    finishAuto(server);
                }
            }
            case "releve", "portes" -> {
                if (autoStarted || !ready || server.getPlayerList().getPlayerCount() == 0 || autoTicks < 100) {
                    return;
                }
                autoStarted = true;
                if (!state.atelier()) {
                    LOGGER.error("atelier : ce monde n'est pas un atelier, rien a relever");
                    finishAuto(server);
                    return;
                }
                if ("portes".equals(AUTO)) {
                    int[] counts = com.emerald.haven.door.HavenDoors.replaceMissing(server);
                    LOGGER.info("atelier : portes de Jak 3 d'office : {} reposee(s), {} deja la, {} impossible(s)",
                            counts[0], counts[1], counts[2]);
                }
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                Component refused = JakCityCapture.start(server, player, directory(server), "releve automatique",
                        result -> {
                            LOGGER.info("atelier : releve automatique {} : {} cellules, {} decors, dans {}",
                                    result.ok() ? "fini" : "ECHOUE", result.cells(), result.entities(), result.file());
                            finishAuto(server);
                        });
                if (refused != null) {
                    LOGGER.error("atelier : releve refuse : {}", refused.getString());
                    finishAuto(server);
                }
            }
            default -> {
                LOGGER.error("atelier : {}={} inconnu (creer, releve ou portes)", VARIABLE, AUTO);
                autoFinished = true;
            }
        }
    }

    private static void finishAuto(MinecraftServer server) {
        autoFinished = true;
        if (server.isDedicatedServer()) {
            server.halt(false);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        autoTicks = 0;
        autoStarted = false;
    }

}
