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
 * La specialisation SURVIT a la partie : elle ne peut donc pas vivre dans
 * la sauvegarde du monde, qu'une nouvelle partie remplace. Elle vit ici,
 * dans un fichier du serveur, par joueur, charge au demarrage et ecrit a
 * chaque changement.
 *
 *   <dossier du serveur>/emeraldweapons/specialization.json
 *
 * En solo, le dossier du serveur est celui du jeu : la specialisation suit
 * le joueur d'un monde a l'autre sur la meme machine. Sur un serveur, elle
 * appartient au serveur -- c'est une progression de compte.
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

    private SpecializationStore() {
    }

    public static Entry get(UUID id) {
        return ENTRIES.computeIfAbsent(id, k -> new Entry());
    }

    public static void load(MinecraftServer server) {
        ENTRIES.clear();
        file = server.getServerDirectory().resolve("emeraldweapons").resolve("specialization.json");
        if (!Files.exists(file)) {
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
