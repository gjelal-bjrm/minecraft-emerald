package com.emerald.specialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * La specialisation SURVIT a la partie, PAS AU MONDE.
 *
 * Elle ne peut pas vivre dans la sauvegarde de partie (GameState), qu'une
 * nouvelle mise en place remplace : les ailes se gardent d'une partie a
 * l'autre, c'est tout leur interet. Mais elle vivait dans le dossier du
 * SERVEUR, ce qui en solo est le dossier du jeu -- si bien qu'un personnage
 * etait commun a tous les mondes de la machine. On remettait a zero dans un
 * monde d'essai, et le personnage du vrai monde disparaissait avec.
 *
 * Elle vit donc dans LE MONDE :
 *
 *   <dossier du monde>/emeraldweapons/specialization.json
 *
 * Chaque monde a son personnage, son niveau d'ailes et ses apparences, comme
 * il a deja son niveau de Heros (qui vit dans les donnees du joueur) et son
 * equipement. Sur un serveur dedie, le monde EST le serveur : rien ne change.
 *
 * L'ancien fichier global n'est ni lu ni efface : on le signale, et
 * `/arcencium personnage importer` le reprend dans le monde ou l'on est. Le
 * recopier tout seul aurait donne le meme personnage maxe a tous les mondes,
 * c'est-a-dire exactement ce qu'on vient de corriger.
 */
public final class SpecializationStore {

    /** Ce qu'on sait d'un joueur. */
    public static final class Entry {
        public int level;
        public String skin = WingSkin.PRISMATIQUES.id();
        public final Set<String> unlocked = new LinkedHashSet<>();
        /** Tentatives echouees en tout : pour les statistiques, et pour qu'un jour on puisse consoler. */
        public int failures;
    }

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    private static Path file;
    /** L'ancien fichier commun a tous les mondes, s'il traine encore. */
    private static Path legacy;

    private SpecializationStore() {
    }

    public static Entry get(UUID id) {
        return ENTRIES.computeIfAbsent(id, k -> new Entry());
    }

    public static void load(MinecraftServer server) {
        ENTRIES.clear();
        file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("emeraldweapons").resolve("specialization.json");
        legacy = server.getServerDirectory().resolve("emeraldweapons")
                .resolve("specialization.json");
        if (!Files.exists(file)) {
            if (legacyPending()) {
                LOGGER.info("Specialisation : ce monde n'a pas encore de personnage, "
                        + "et un ancien fichier commun existe ({}). "
                        + "/arcencium personnage importer pour le reprendre ici.", legacy);
            }
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                Entry entry = new Entry();
                entry.level = o.has("level") ? o.get("level").getAsInt() : 0;
                entry.skin = o.has("skin") ? o.get("skin").getAsString() : WingSkin.PRISMATIQUES.id();
                entry.failures = o.has("failures") ? o.get("failures").getAsInt() : 0;
                if (o.has("unlocked")) {
                    for (JsonElement s : o.getAsJsonArray("unlocked")) {
                        entry.unlocked.add(s.getAsString());
                    }
                }
                ENTRIES.put(UUID.fromString(e.getKey()), entry);
            }
            LOGGER.info("Specialisation : {} joueur(s) charges depuis {}", ENTRIES.size(), file);
        } catch (Exception ex) {
            LOGGER.error("Specialisation : fichier illisible {} -- on repart de zero", file, ex);
        }
    }

    /** Vrai quand un personnage global attend d'etre repris dans ce monde. */
    public static boolean legacyPending() {
        return legacy != null && file != null && Files.exists(legacy)
                && !Files.exists(file) && !legacy.equals(file);
    }

    /**
     * Reprend ICI le personnage global d'avant la separation par monde.
     *
     * L'ancien fichier n'est pas efface : un autre monde peut vouloir le
     * reprendre aussi, et surtout on ne detruit pas la seule copie d'une
     * progression sur la foi d'une commande tapee une fois.
     */
    public static boolean importLegacy(MinecraftServer server) {
        if (legacy == null || !Files.exists(legacy)) {
            return false;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.copy(legacy, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            LOGGER.error("Specialisation : impossible de reprendre {}", legacy, ex);
            return false;
        }
        load(server);
        for (net.minecraft.server.level.ServerPlayer player
                : server.getPlayerList().getPlayers()) {
            Specialization.applyBonuses(player);
            Specialization.sync(player);
        }
        LOGGER.info("Specialisation : personnage global repris dans {}", file);
        return true;
    }

    public static void save() {
        if (file == null) {
            return;
        }
        JsonObject root = new JsonObject();
        for (Map.Entry<UUID, Entry> e : ENTRIES.entrySet()) {
            Entry entry = e.getValue();
            if (entry.level <= 0 && entry.unlocked.isEmpty()) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("level", entry.level);
            o.addProperty("skin", entry.skin);
            o.addProperty("failures", entry.failures);
            JsonArray unlocked = new JsonArray();
            for (String s : entry.unlocked) {
                unlocked.add(s);
            }
            o.add("unlocked", unlocked);
            root.add(e.getKey().toString(), o);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.error("Specialisation : impossible d'ecrire {}", file, ex);
        }
    }
}
