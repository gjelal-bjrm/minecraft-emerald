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
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * LE RETOUR DU DEFI (lot 2, cahier §81) ajoute deux etapes : la DEUXIEME ARRIVEE,
 * dans la ville envahie (« envahie » : son titre a ete joue), et LES RUES REPRISES
 * (« reprise » : l'equipe a abattu les monstres qu'il fallait pendant qu'il etait la).
 *
 * LES QUETES DES HEROS (lot 3, cahier §86) ajoutent la bourse du joueur : ses ORBES
 * PRECURSEURS (gagnes aux quetes et ramasses dans la ville, depenses chez Tess), les orbes
 * caches deja trouves, ses medailles, et ce qu'il a achete (sceaux, provisions, munitions
 * illimitees...). Comme le reste, ils suivent le joueur de partie en partie, dans ce monde.
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
     * armes (lot 3, cahier §86) : toutes, sauf le contrat de Torn qui se refait (« port »).
     */
    public static final List<String> REQUIRED_QUESTS = List.of(
            "rues", "patrouille",
            "chasse", "brutes", "marche",
            "anneaux", "taxi", "chauffard",
            "tir1", "tir2", "tir3",
            "ecos", "plateforme", "elite",
            "peche", "coffres", "mouettes");

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
        /** La deuxieme arrivee, dans la ville envahie, a eu lieu : titre joue (lot 2). */
        public boolean invaded;
        /** Il a repris les rues avec l'equipe : la ville envahie ne l'attend plus (lot 2). */
        public boolean reprise;
        /** Les quetes des heros faites (lot 3). */
        public final Set<String> quests = new LinkedHashSet<>();
        /** Ses orbes precurseurs : la monnaie de la ville. */
        public int orbs;
        /** Les orbes caches deja trouves, par numero (jak/haven_orbs.json). */
        public final BitSet found = new BitSet();
        /** Ses medailles aux epreuves : 1 bronze, 2 argent, 3 or. */
        public final Map<String, Integer> medals = new LinkedHashMap<>();
        /** Ce qu'il a achete chez Tess et qui se garde ou s'use : sceaux, provisions, munitions illimitees. */
        public final Map<String, Integer> bonus = new LinkedHashMap<>();
        /** Fiche d'un cobaye de banc : jamais ecrite. */
        boolean temporary;

        boolean blank() {
            return this.forms == 0 && !this.welcomed && !this.hq && this.departures == 0 && !this.invaded
                    && !this.reprise && this.quests.isEmpty() && this.orbs == 0 && this.found.isEmpty()
                    && this.medals.isEmpty() && this.bonus.isEmpty();
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

    /**
     * Revenu d'un Defi sans avoir repris les rues : a la reouverture du lobby, la ville
     * l'attend envahie (HavenJourney.reopenMode).
     */
    public static boolean awaitsReprise(UUID id) {
        Entry entry = ENTRIES.get(id);
        return entry != null && entry.departures > 0 && !entry.reprise;
    }

    /** Ses orbes. */
    public static int orbs(UUID id) {
        Entry entry = ENTRIES.get(id);
        return entry == null ? 0 : entry.orbs;
    }

    /** A-t-il deja trouve cet orbe cache ? */
    public static boolean found(UUID id, int orb) {
        Entry entry = ENTRIES.get(id);
        return entry != null && entry.found.get(orb);
    }

    /** Combien d'orbes caches il a trouves. */
    public static int foundCount(UUID id) {
        Entry entry = ENTRIES.get(id);
        return entry == null ? 0 : entry.found.cardinality();
    }

    /** A-t-il fait cette quete ? */
    public static boolean done(UUID id, String quest) {
        Entry entry = ENTRIES.get(id);
        return entry != null && entry.quests.contains(quest);
    }

    /** Sa meilleure medaille a cette epreuve (0 : aucune). */
    public static int medal(UUID id, String quest) {
        Entry entry = ENTRIES.get(id);
        return entry == null ? 0 : entry.medals.getOrDefault(quest, 0);
    }

    /** Combien il a de ce bonus (sceaux, provisions...) ; 1 pour un bonus acquis une fois pour toutes. */
    public static int bonus(UUID id, String key) {
        Entry entry = ENTRIES.get(id);
        return entry == null ? 0 : entry.bonus.getOrDefault(key, 0);
    }

    // ================================================================ ecriture

    /** Ajoute (ou retire, si negatif) des orbes, et ecrit. @return le nouveau solde */
    public static int addOrbs(UUID id, int amount) {
        Entry entry = get(id);
        entry.orbs = Math.max(0, entry.orbs + amount);
        save();
        return entry.orbs;
    }

    /** Depense des orbes s'il en a assez, et ecrit. @return faux s'il n'en a pas assez */
    public static boolean spendOrbs(UUID id, int amount) {
        Entry entry = get(id);
        if (amount < 0 || entry.orbs < amount) {
            return false;
        }
        entry.orbs -= amount;
        save();
        return true;
    }

    /** Note un orbe cache trouve. @return vrai s'il ne l'avait pas encore */
    public static boolean markFound(UUID id, int orb) {
        Entry entry = get(id);
        if (entry.found.get(orb)) {
            return false;
        }
        entry.found.set(orb);
        save();
        return true;
    }

    /** Note une quete faite. @return vrai si c'est la premiere fois */
    public static boolean completeQuest(UUID id, String quest) {
        boolean first = get(id).quests.add(quest);
        save();
        return first;
    }

    /** Garde la meilleure medaille. @return la medaille d'avant */
    public static int setMedal(UUID id, String quest, int medal) {
        Entry entry = get(id);
        int before = entry.medals.getOrDefault(quest, 0);
        if (medal > before) {
            entry.medals.put(quest, medal);
            save();
        }
        return before;
    }

    /** Ajoute un bonus (ou en fixe un acquis une fois pour toutes a 1). */
    public static void addBonus(UUID id, String key, int amount) {
        Entry entry = get(id);
        entry.bonus.merge(key, amount, Integer::sum);
        save();
    }

    /** Consomme un bonus s'il en a un, et ecrit. @return vrai s'il en avait */
    public static boolean takeBonus(UUID id, String key) {
        Entry entry = ENTRIES.get(id);
        if (entry == null || entry.bonus.getOrDefault(key, 0) <= 0) {
            return false;
        }
        int left = entry.bonus.get(key) - 1;
        if (left <= 0) {
            entry.bonus.remove(key);
        } else {
            entry.bonus.put(key, left);
        }
        save();
        return true;
    }

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

    /**
     * La maitrise d'un coup : les douze armes et toutes les quetes demandees -- les rues
     * reprises comprises, la premiere d'entre elles. Pour l'operateur.
     */
    public static void grantMastery(UUID id) {
        Entry entry = get(id);
        entry.forms = ALL_FORMS;
        entry.quests.addAll(REQUIRED_QUESTS);
        entry.invaded = true;
        entry.reprise = true;
        save();
    }

    /**
     * Le joueur tel qu'il revient de son premier Defi : accueilli, QG vu, un depart, AUCUNE
     * arme, ni deuxieme arrivee ni rues reprises. Pour l'operateur : essayer le lot 2
     * sans jouer un Defi entier (puis /arcencium haven ouvrir).
     */
    public static void markReturned(UUID id) {
        Entry entry = get(id);
        entry.forms = 0;
        entry.welcomed = true;
        entry.hq = true;
        entry.departures = Math.max(1, entry.departures);
        entry.invaded = false;
        entry.reprise = false;
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
                entry.invaded = o.has("envahie") && o.get("envahie").getAsBoolean();
                entry.reprise = o.has("reprise") && o.get("reprise").getAsBoolean();
                if (o.has("quetes")) {
                    for (JsonElement quest : o.getAsJsonArray("quetes")) {
                        entry.quests.add(quest.getAsString());
                    }
                }
                entry.orbs = o.has("orbes") ? Math.max(0, o.get("orbes").getAsInt()) : 0;
                if (o.has("orbes_trouves")) {
                    for (JsonElement orb : o.getAsJsonArray("orbes_trouves")) {
                        entry.found.set(orb.getAsInt());
                    }
                }
                if (o.has("medailles")) {
                    for (Map.Entry<String, JsonElement> m : o.getAsJsonObject("medailles").entrySet()) {
                        entry.medals.put(m.getKey(), m.getValue().getAsInt());
                    }
                }
                if (o.has("bonus")) {
                    for (Map.Entry<String, JsonElement> b : o.getAsJsonObject("bonus").entrySet()) {
                        entry.bonus.put(b.getKey(), b.getValue().getAsInt());
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
            o.addProperty("envahie", entry.invaded);
            o.addProperty("reprise", entry.reprise);
            JsonArray quests = new JsonArray();
            for (String quest : entry.quests) {
                quests.add(quest);
            }
            o.add("quetes", quests);
            o.addProperty("orbes", entry.orbs);
            JsonArray found = new JsonArray();
            for (int i = entry.found.nextSetBit(0); i >= 0; i = entry.found.nextSetBit(i + 1)) {
                found.add(i);
            }
            o.add("orbes_trouves", found);
            JsonObject medals = new JsonObject();
            entry.medals.forEach(medals::addProperty);
            o.add("medailles", medals);
            JsonObject bonus = new JsonObject();
            entry.bonus.forEach(bonus::addProperty);
            o.add("bonus", bonus);
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
