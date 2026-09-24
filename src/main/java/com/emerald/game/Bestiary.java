package com.emerald.game;

import com.emerald.weather.Weather;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.common.Tags;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * UN BESTIAIRE PAR METEO (24 sept. 2026, cahier §89).
 *
 * « J'aimerais aussi qu'on augmente la variete de monstres, je trouve qu'il n'y en a pas
 * assez de differents. » Mesure : la Traque et les quatre meteos agressives piochaient
 * toutes dans les trois memes listes (SiegeRoster.forTier, 8 a 12 monstres), alors que le
 * profil compte quelque deux cent vingt creatures. Puis, a la question « est-ce des
 * monstres d'horreur ? » : « je n'ai pas envie qu'on mette des monstres d'horreur en
 * dehors de la meteo d'horreur ». Donc ici, rien qui soit spectre, loup-garou, esprit ou
 * sculk -- et par securite, tout type du tag eclipse_horrors est ecarte au tirage.
 *
 * Chaque meteo agressive a un THEME, en trois paliers, melange de vanilla pour rester
 * lisible :
 *   Nuit d'Arcencium ...... les morts (draugr, druides squelettes, pourrissants, necromancien)
 *   Meteores .............. ce qui tombe du ciel et ce qui brule (creteors, revenants embrases)
 *   Dechirure ............. le vide (endermapteres, enderiophages, golem de l'End)
 *   Orage ................. golems et mages (golems de carminite, cryomanciens, archevokers)
 *   Battue ................ les betes (loups de brume, araignees, ours-gouttes, centipedes)
 * La Traque, elle, garde le vivier de siege et y ajoute des VARIANTES DU BIOME : le creeper
 * de la jungle dans la jungle, le zombie gele sur la neige (Creeper Overhaul, Variants &
 * Ventures).
 *
 * Les identifiants sont du texte, resolus a l'execution : un mod absent disparait du tirage.
 * Le banc (ArcenciumAutotest, epreuve 10) verifie que chacun est la et se bat : il a ecarte
 * le rat zombifie, le golem de pierre sombre, le serpent a sonnette et la brute de
 * l'Undergarden, neutres, et corrige deux identifiants (centipede_head, citadel_keeper).
 */
public final class Bestiary {

    private Bestiary() {
    }

    /** Le vivier d'une meteo agressive a ce palier ; vide si la meteo n'a pas de theme. */
    public static List<String> forWeather(Weather weather, int tier) {
        return switch (weather) {
            case NUIT -> pick(tier,
                    List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:husk", "minecraft:stray",
                            "undergarden:rotling", "cataclysm:draugr", "variantsandventures:murk",
                            "variantsandventures:gelid"),
                    List.of("cataclysm:draugr", "cataclysm:elite_draugr", "undergarden:rotwalker",
                            "twilightforest:skeleton_druid", "eternal_starlight:lonestar_skeleton",
                            "irons_spellbooks:necromancer", "minecraft:bogged", "minecraft:stray"),
                    List.of("cataclysm:royal_draugr", "cataclysm:elite_draugr", "cataclysm:aptrgangr",
                            "undergarden:rotbeast", "irons_spellbooks:necromancer", "minecraft:wither_skeleton"));
            case METEORES -> pick(tier,
                    List.of("eternal_starlight:tiny_creteor", "twilightforest:fire_beetle", "minecraft:magma_cube",
                            "minecraft:husk", "minecraft:zombie"),
                    List.of("eternal_starlight:creteor", "cataclysm:ignited_revenant", "minecraft:blaze",
                            "twilightforest:fire_beetle", "minecraft:magma_cube"),
                    List.of("cataclysm:ignited_berserker", "cataclysm:ignited_revenant", "eternal_starlight:creteor",
                            "minecraft:blaze", "minecraft:wither_skeleton"));
            case DECHIRURE -> pick(tier,
                    List.of("minecraft:endermite", "cataclysm:endermaptera", "alexsmobs:enderiophage",
                            "minecraft:zombie", "minecraft:skeleton"),
                    List.of("cataclysm:endermaptera", "alexsmobs:enderiophage", "minecraft:phantom",
                            "minecraft:vindicator", "minecraft:endermite"),
                    List.of("cataclysm:endermaptera", "alexsmobs:enderiophage", "cataclysm:ender_golem",
                            "minecraft:evoker", "minecraft:phantom"));
            case ORAGE -> pick(tier,
                    List.of("irons_spellbooks:cultist", "twilightforest:redcap", "twilightforest:kobold",
                            "minecraft:pillager", "minecraft:witch"),
                    List.of("irons_spellbooks:cryomancer", "irons_spellbooks:citadel_keeper",
                            "twilightforest:carminite_broodling", "twilightforest:blockchain_goblin",
                            "cataclysm:koboleton", "minecraft:vindicator"),
                    List.of("twilightforest:carminite_golem", "irons_spellbooks:archevoker",
                            "cataclysm:kobolediator", "minecraft:evoker",
                            "minecraft:ravager"));
            case BATTUE -> pick(tier,
                    List.of("twilightforest:hostile_wolf", "twilightforest:hedge_spider",
                            "twilightforest:swarm_spider", "minecraft:spider",
                            "minecraft:cave_spider"),
                    List.of("twilightforest:mist_wolf", "twilightforest:winter_wolf", "alexsmobs:dropbear",
                            "alexsmobs:centipede_head", "eternal_starlight:nightfall_spider"),
                    List.of("twilightforest:king_spider", "twilightforest:winter_wolf", "alexsmobs:dropbear",
                            "undergarden:nargoyle", "eternal_starlight:nightfall_spider"));
            default -> List.of();
        };
    }

    private static List<String> pick(int tier, List<String> one, List<String> two, List<String> three) {
        return tier >= 3 ? three : tier == 2 ? two : one;
    }

    /**
     * La variante du biome, ou null : le creeper et le zombie ou squelette du lieu. Sous
     * terre, les creepers de caverne ; dans les grottes luxuriantes, le squelette verdoyant.
     */
    @Nullable
    public static String biomeVariant(ServerLevel level, BlockPos pos, boolean zombieSide) {
        Holder<Biome> biome = level.getBiome(pos);
        if (pos.getY() < 40) {
            if (biome.is(Biomes.LUSH_CAVES)) {
                return zombieSide ? "variantsandventures:verdant" : "creeperoverhaul:cave_creeper";
            }
            return biome.is(Biomes.DRIPSTONE_CAVES) ? "creeperoverhaul:dripstone_creeper" : "creeperoverhaul:cave_creeper";
        }
        if (biome.is(Tags.Biomes.IS_SNOWY)) {
            return zombieSide ? "variantsandventures:gelid" : "creeperoverhaul:snowy_creeper";
        }
        if (biome.is(Tags.Biomes.IS_SWAMP)) {
            return zombieSide ? "variantsandventures:murk" : "creeperoverhaul:swamp_creeper";
        }
        if (biome.is(Biomes.BAMBOO_JUNGLE)) {
            return zombieSide ? "variantsandventures:thicket" : "creeperoverhaul:bamboo_creeper";
        }
        if (biome.is(BiomeTags.IS_JUNGLE)) {
            return zombieSide ? "variantsandventures:thicket" : "creeperoverhaul:jungle_creeper";
        }
        if (biome.is(Biomes.MUSHROOM_FIELDS)) {
            return "creeperoverhaul:mushroom_creeper";
        }
        if (biome.is(BiomeTags.IS_BADLANDS)) {
            return "creeperoverhaul:badlands_creeper";
        }
        if (biome.is(Tags.Biomes.IS_DESERT)) {
            return zombieSide ? "minecraft:husk" : "creeperoverhaul:desert_creeper";
        }
        if (biome.is(BiomeTags.IS_SAVANNA)) {
            return "creeperoverhaul:savannah_creeper";
        }
        if (biome.is(BiomeTags.IS_BEACH)) {
            return "creeperoverhaul:beach_creeper";
        }
        if (biome.is(BiomeTags.IS_TAIGA)) {
            return "creeperoverhaul:spruce_creeper";
        }
        if (biome.is(Biomes.DARK_FOREST)) {
            return zombieSide ? "variantsandventures:thicket" : "creeperoverhaul:dark_oak_creeper";
        }
        if (biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
            return "creeperoverhaul:birch_creeper";
        }
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) {
            return "creeperoverhaul:hills_creeper";
        }
        if (biome.is(BiomeTags.IS_FOREST)) {
            return zombieSide ? "variantsandventures:thicket" : null;
        }
        return null;
    }

    /** Les identifiants d'un vivier resolus, sans les absents ni les horreurs de l'Eclipse. */
    public static List<EntityType<?>> resolve(List<String> ids) {
        List<EntityType<?>> out = new ArrayList<>();
        for (String id : ids) {
            EntityType.byString(id).ifPresent(type -> {
                if (!type.is(com.emerald.weather.Eclipse.HORRORS)) {
                    out.add(type);
                }
            });
        }
        return out;
    }

    /** Tous les identifiants cites ici, pour le banc. */
    public static List<String> allIds() {
        List<String> out = new ArrayList<>();
        for (Weather w : new Weather[]{Weather.NUIT, Weather.METEORES, Weather.DECHIRURE, Weather.ORAGE,
                Weather.BATTUE}) {
            for (int tier = 1; tier <= 3; tier++) {
                for (String id : forWeather(w, tier)) {
                    if (!out.contains(id)) {
                        out.add(id);
                    }
                }
            }
        }
        for (String id : List.of("creeperoverhaul:jungle_creeper", "creeperoverhaul:bamboo_creeper",
                "creeperoverhaul:desert_creeper", "creeperoverhaul:badlands_creeper", "creeperoverhaul:hills_creeper",
                "creeperoverhaul:savannah_creeper", "creeperoverhaul:mushroom_creeper", "creeperoverhaul:swamp_creeper",
                "creeperoverhaul:dripstone_creeper", "creeperoverhaul:cave_creeper", "creeperoverhaul:dark_oak_creeper",
                "creeperoverhaul:spruce_creeper", "creeperoverhaul:beach_creeper", "creeperoverhaul:snowy_creeper",
                "creeperoverhaul:birch_creeper", "variantsandventures:gelid", "variantsandventures:murk",
                "variantsandventures:thicket", "variantsandventures:verdant")) {
            if (!out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }
}
