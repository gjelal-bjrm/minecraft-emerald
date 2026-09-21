package com.emerald.haven.journey;

import com.emerald.jak.gun.GunForm;
import com.emerald.main.EmeraldWeaponsMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Le parcours de chaque joueur dans Haven (cahier §79) : ce qu'il a debloque, et ou il
 * en est. Il SURVIT A LA PARTIE, pas au monde, comme la Specialisation :
 *
 *   <dossier du monde>/emeraldweapons/haven_parcours.json
 *
 * Avant, l'arme appartenait au LOBBY (MorphGunData, par partie) et arrivait avec
 * ses douze formes ; desormais les formes appartiennent au JOUEUR : aucune a la
 * premiere arrivee, le Scatter Gun au coffre du QG, les autres par les quetes.
 *
 * LA MAITRISE -- les douze armes ET les quetes des heros -- ouvre le bouton du QG
 * a ce joueur. Les quetes n'existent pas encore (lot 3 du §79.5) : tant que
 * {@link #REQUIRED_QUESTS} est vide, la maitrise, ce sont les douze armes.
 *
 * LES COBAYES DES BANCS ont une fiche TEMPORAIRE, jamais ecrite : un joueur factice
 * n'a rien a faire dans la sauvegarde d'un monde.
 */
public final class HavenProgress {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Toutes les formes du Morph Gun. */
    public static final int ALL_FORMS = GunForm.ALL_MASK;

    /**
     * Les quetes des heros qu'il faut avoir faites pour la maitrise, en plus des douze
     * armes. Vide jusqu'au lot 3 (les PNJ de Haven).
     */
    public static final List<String> REQUIRED_QUESTS = List.of();

    /** Ce qu'on sait d'un joueur. */
    public static final class Entry {
        /** Les formes debloquees du Morph Gun, un bit par ordinal de GunForm. */
        public int forms;
        /** La premiere arrivee a eu lieu : titre joue, succes accorde. */
        public boolean welcomed;
        /** Il a deja rejoint le QG une fois. */
        public boolean hq;
        /** Ses departs de la ville vers une partie. */
        public int departures;
        /** Les quetes des heros faites (lot 3). */
        public final Set<String> quests = new LinkedHashSet<>();
        /** Fiche d'un cobaye de banc : jamais ecrite. */
        boolean temporary;

        boolean blank() {
            return this.forms == 0 && !this.welcomed && !this.hq && this.departures == 0 && this.quests.isEmpty();
        }
    }

    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    @Nullable
    private static Path file;

    private HavenProgress() {
    }

    // ================================================================ lecture

    /** La fiche d'un joueur, creee vide s'il n'en avait pas. */
    public static Entry get(UUID id) {
        return ENTRIES.computeIfAbsent(id, k -> new Entry());
    }

    /** La fiche d'un joueur, ou null s'il n'en a pas. */
    @Nullable
    public static Entry peek(UUID id) {
        return ENTRIES.get(id);
    }

    /** Ses formes debloquees ; 0 pour un joueur jamais vu. */
    public static int forms(UUID id) {
        Entry entry = ENTRIES.get(id);
        return entry == null ? 0 : entry.forms & ALL_FORMS;
    }

    /** Les armes qui lui manquent, sur douze. */
    public static int missingWeapons(UUID id) {
        return GunForm.values().length - Integer.bitCount(forms(id));
    }

    /** Les quetes des heros qui lui manquent pour la maitrise. */
    public static int missingQuests(UUID id) {
        Entry entry = ENTRIES.get(id);
        int missing = 0;
        for (String quest : REQUIRED_QUESTS) {
            if (entry == null || !entry.quests.contains(quest)) {
                missing++;
            }
        }
        return missing;
    }

    /** Les douze armes et les quetes des heros : le bouton du QG lui obeit. */
    public static boolean mastery(UUID id) {
        return missingWeapons(id) == 0 && missingQuests(id) == 0;
    }

    // ================================================================ ecriture

    /** Remplace ses formes, et ecrit. */
    public static void setForms(UUID id, int mask) {
        get(id).forms = mask & ALL_FORMS;
        save();
    }

    /** Ajoute des formes a celles qu'il a, et ecrit. */
    public static void grantForms(UUID id, int mask) {
        Entry entry = get(id);
        entry.forms = (entry.forms | mask) & ALL_FORMS;
        save();
    }

    /** La maitrise d'un coup : les douze armes et toutes les quetes demandees. Pour l'operateur. */
    public static void grantMastery(UUID id) {
        Entry entry = get(id);
        entry.forms = ALL_FORMS;
        entry.quests.addAll(REQUIRED_QUESTS);
        save();
    }

    /** Oublie tout : le joueur redevient un nouveau venu. */
    public static void reset(UUID id) {
        ENTRIES.remove(id);
        save();
    }

    /** Une fiche de cobaye, jamais ecrite ; les formes donnees remplacent celles qu'il avait. */
    public static Entry temporary(UUID id, int forms) {
        Entry entry = get(id);
        entry.temporary = true;
        entry.forms = forms & ALL_FORMS;
        return entry;
    }

    /** Retire la fiche d'un cobaye (une vraie fiche n'est jamais touchee). */
    public static void dropTemporary(UUID id) {
        Entry entry = ENTRIES.get(id);
        if (entry != null && entry.temporary) {
            ENTRIES.remove(id);
        }
    }

    // ================================================================ disque

    public static void load(MinecraftServer server) {
        ENTRIES.clear();
        file = server.getWorldPath(LevelResource.ROOT).resolve("emeraldweapons").resolve("haven_parcours.json");
        int read = read(file, ENTRIES);
        if (read > 0) {
            LOGGER.info("Parcours de Haven : {} joueur(s) charges depuis {}", read, file);
        }
    }

    public static void save() {
        if (file != null) {
            write(file, ENTRIES);
        }
    }

    /** Oublie le fichier et les fiches : a l'arret du serveur. */
    public static void unload() {
        ENTRIES.clear();
        file = null;
    }

    /**
     * Lit un fichier de parcours dans une table.
     *
     * @return le nombre de joueurs lus ; 0 si le fichier manque ou est illisible
     */
    static int read(Path path, Map<UUID, Entry> into) {
        if (!Files.exists(path)) {
            return 0;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            int read = 0;
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                Entry entry = new Entry();
                entry.forms = o.has("formes") ? o.get("formes").getAsInt() & ALL_FORMS : 0;
                entry.welcomed = o.has("accueilli") && o.get("accueilli").getAsBoolean();
                entry.hq = o.has("qg") && o.get("qg").getAsBoolean();
                entry.departures = o.has("departs") ? o.get("departs").getAsInt() : 0;
                if (o.has("quetes")) {
                    for (JsonElement quest : o.getAsJsonArray("quetes")) {
                        entry.quests.add(quest.getAsString());
                    }
                }
                into.put(UUID.fromString(e.getKey()), entry);
                read++;
            }
            return read;
        } catch (Exception ex) {
            LOGGER.error("Parcours de Haven : fichier illisible {} -- on repart de zero", path, ex);
            return 0;
        }
    }

    /** Ecrit une table de parcours : ni les fiches temporaires, ni les fiches vides. */
    static void write(Path path, Map<UUID, Entry> from) {
        JsonObject root = new JsonObject();
        for (Map.Entry<UUID, Entry> e : from.entrySet()) {
            Entry entry = e.getValue();
            if (entry.temporary || entry.blank()) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("formes", entry.forms & ALL_FORMS);
            o.addProperty("accueilli", entry.welcomed);
            o.addProperty("qg", entry.hq);
            o.addProperty("departs", entry.departures);
            JsonArray quests = new JsonArray();
            for (String quest : entry.quests) {
                quests.add(quest);
            }
            o.add("quetes", quests);
            root.add(e.getKey().toString(), o);
        }
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.error("Parcours de Haven : impossible d'ecrire {}", path, ex);
        }
    }
}
