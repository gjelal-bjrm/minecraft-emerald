package com.emerald.weather;

import com.emerald.game.GamePhase;
import com.emerald.game.GameManager;
import com.emerald.game.GameState;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.WeatherSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Le cycle meteorologique du Mode Arcencium.
 *
 * La meteo est GLOBALE -- elle touche toute la zone en meme temps -- et
 * PROGRESSIVE : le tirage suit la phase de la partie, si bien que les douces
 * ouvrent le jeu et que l'Assaut ne connait plus que les tempetes. Toute
 * meteo agressive s'annonce quinze secondes a l'avance et se termine par
 * l'Embellie, une accalmie ou plus rien n'apparait.
 *
 * L'etat est volatil comme celui des sieges : une tempete interrompue par un
 * arret du serveur disparait, sans rien laisser derriere elle a nettoyer.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class WeatherManager {

    /** Le preavis avant toute meteo tiree au sort. */
    private static final int WARNING_TICKS = 15 * 20;

    private static Weather current = Weather.CLEAR;
    private static int remaining;
    private static long startedAt;
    @Nullable
    private static Weather pending;
    private static int warningTicks;
    private static int gapTicks = 200;
    private static Weather lastRolled = Weather.CLEAR;
    private static long savedDayTime = -1;

    private WeatherManager() {
    }

    public static Weather current() {
        return current;
    }

    public static boolean isEmbellie() {
        return current == Weather.EMBELLIE;
    }

    // ------------------------------------------------------------- le tick

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)
                || com.emerald.game.ModeSwitch.off()) {
            return;
        }
        if (current.real()) {
            WeatherEffects.tick(level, current);
            if (--remaining <= 0) {
                end(level, true);
            }
        } else if (pending != null) {
            if (warningTicks % 20 == 0) {
                Component warning = Component.translatable("game.emeraldweapons.weather.incoming",
                                Component.translatable(pending.translationKey()), warningTicks / 20)
                        .withStyle(style -> style.withColor(pending.color));
                for (ServerPlayer player : level.players()) {
                    player.displayClientMessage(warning, true);
                }
            }
            if (--warningTicks <= 0) {
                Weather next = pending;
                pending = null;
                begin(level, next, next.rollDuration(level.random));
            }
        } else if (current == Weather.EMBELLIE) {
            if (--remaining <= 0) {
                current = Weather.CLEAR;
                scheduleGap(level);
            }
        } else if (GameState.get(level).status() == GameState.Status.RUNNING) {
            if (--gapTicks <= 0) {
                Weather next = roll(level);
                if (next != null) {
                    pending = next;
                    warningTicks = WARNING_TICKS;
                    level.playSound(null, level.getSharedSpawnPos(),
                            SoundEvents.BELL_RESONATE, SoundSource.WEATHER, 0.8F, 0.7F);
                } else {
                    scheduleGap(level);
                }
            }
        }
        if (level.getGameTime() % 20 == 0) {
            sync(level);
        }
    }

    @Nullable
    private static Weather roll(ServerLevel level) {
        GamePhase phase = GameState.get(level).phase(level);
        List<Weather> pool = Weather.poolFor(phase);
        if (pool.isEmpty()) {
            return null;
        }
        // jamais deux fois la meme de suite : la variete est le contrat du mode
        List<Weather> filtered = pool.size() > 1
                ? pool.stream().filter(w -> w != lastRolled).toList() : pool;
        Weather next = filtered.get(level.random.nextInt(filtered.size()));
        lastRolled = next;
        return next;
    }

    private static void scheduleGap(ServerLevel level) {
        GamePhase phase = GameState.get(level).phase(level);
        gapTicks = switch (phase) {
            // l'orage permanent de l'Assaut : presque pas de repit entre deux tirages
            case ASSAUT -> 400 + level.random.nextInt(400);
            case PRESSION -> 1800 + level.random.nextInt(1800);
            default -> 2400 + level.random.nextInt(2400);
        };
    }

    // -------------------------------------------------------- debut et fin

    private static void begin(ServerLevel level, Weather weather, int duration) {
        current = weather;
        remaining = duration;
        startedAt = level.getGameTime();
        if (weather == Weather.NUIT) {
            // la nuit en plein jour : on deplace l'horloge, ce qui declenche
            // naturellement les apparitions nocturnes, et on la rendra a la fin
            savedDayTime = level.getDayTime();
            level.setDayTime(18000L);
            level.setWeatherParameters(0, duration, true, true);
        }
        WeatherEffects.begin(level, weather);
        GameManager.announce(level,
                Component.translatable(weather.translationKey())
                        .withStyle(style -> style.withColor(weather.color)),
                Component.translatable(weather.subtitleKey()).withStyle(ChatFormatting.GRAY));
        sync(level);
    }

    private static void end(ServerLevel level, boolean natural) {
        Weather ended = current;
        WeatherEffects.end(level, ended);
        if (ended == Weather.NUIT && savedDayTime >= 0) {
            // l'horloge reprend la ou elle serait sans la Nuit
            level.setDayTime(savedDayTime + (level.getGameTime() - startedAt));
            level.setWeatherParameters(12000, 0, false, false);
            savedDayTime = -1;
        }
        if (natural && ended.aggressive) {
            current = Weather.EMBELLIE;
            remaining = Weather.EMBELLIE.rollDuration(level.random);
            GameManager.announce(level,
                    Component.translatable(Weather.EMBELLIE.translationKey())
                            .withStyle(style -> style.withColor(Weather.EMBELLIE.color)),
                    Component.translatable(Weather.EMBELLIE.subtitleKey())
                            .withStyle(ChatFormatting.GRAY));
        } else {
            current = Weather.CLEAR;
            scheduleGap(level);
        }
        sync(level);
    }

    // ------------------------------------------------------------ commandes

    /** Declenche immediatement, sans preavis : c'est l'outil de test. */
    public static void force(ServerLevel level, Weather weather, int durationTicks) {
        pending = null;
        if (current.real()) {
            end(level, false);
        }
        current = Weather.CLEAR;
        begin(level, weather,
                durationTicks > 0 ? durationTicks : weather.rollDuration(level.random));
    }

    public static void stop(ServerLevel level) {
        pending = null;
        if (current.real()) {
            end(level, false);
        } else {
            current = Weather.CLEAR;
            scheduleGap(level);
        }
        sync(level);
    }

    // --------------------------------------------------------------- reseau

    private static void sync(ServerLevel level) {
        WeatherSyncPayload payload = new WeatherSyncPayload(
                current.ordinal(), remaining,
                pending == null ? -1 : pending.ordinal(), warningTicks);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    // ----------------------------------------------------------- evenements

    /** Miner une cicatrice d'eclair vert dans les temps paie en Arcencium. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)) {
            return;         // une cicatrice n'existe que la ou la meteo se joue
        }
        if (WeatherEffects.claimScar(level, event.getPos())) {
            net.minecraft.world.level.block.Block.popResource(level, event.getPos(),
                    new net.minecraft.world.item.ItemStack(ModItems.RAW_ARCENCIUM.get(),
                            1 + level.random.nextInt(2)));
            level.playSound(null, event.getPos(), SoundEvents.AMETHYST_BLOCK_BREAK,
                    SoundSource.BLOCKS, 1.0F, 1.5F);
        }
    }

    /** Un eclat de Dechirure qui revient hors tempete retrouve sa gravite. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            WeatherEffects.rescueShard(event.getEntity());
        }
    }

    /**
     * L'arret du serveur remet tout a zero.
     *
     * En solo, la machine virtuelle survit au retour au menu : sans ce
     * nettoyage, une Nuit en cours continuait de s'ecouler dans le monde
     * SUIVANT, dont l'horloge se voyait alors rendre celle du precedent. On
     * restitue d'abord le temps du monde qu'on quitte -- il est encore
     * ouvert -- puis on oublie tout.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerLevel level = event.getServer().overworld();
        if (current == Weather.NUIT && savedDayTime >= 0) {
            level.setDayTime(savedDayTime + (level.getGameTime() - startedAt));
            level.setWeatherParameters(12000, 0, false, false);
        }
        if (current.real()) {
            WeatherEffects.end(level, current);
        }
        current = Weather.CLEAR;
        pending = null;
        remaining = 0;
        warningTicks = 0;
        gapTicks = 200;
        lastRolled = Weather.CLEAR;
        savedDayTime = -1;
        startedAt = 0L;
        WeatherEffects.clearAll();
    }

    /**
     * L'Embellie tient sa promesse : rien n'apparait. Seules les apparitions
     * NATURELLES sont coupees -- les sieges passent par EVENT et continuent.
     */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (isEmbellie()
                && event.getSpawnType() == MobSpawnType.NATURAL
                && event.getEntity() instanceof Enemy
                && event.getEntity().level().dimension().equals(Level.OVERWORLD)) {
            event.setSpawnCancelled(true);
        }
    }
}
