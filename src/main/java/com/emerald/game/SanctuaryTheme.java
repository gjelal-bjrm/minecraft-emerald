package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LES TROIS SANCTUAIRES (cahier §90) : une matiere, une garnison et une brume par sanctuaire.
 *
 * « Peu importe si c'est le premier ou le deuxieme ou le troisieme, les monstres sont
 * toujours les memes. [...] Chaque sanctuaire devrait etre different, au moins au niveau des
 * monstres et au mieux aussi au niveau du visuel » (le joueur, 24 sept.). Mesure : les trois
 * garnisons piochaient dans le meme vivier (SiegeRoster, palier 2), les trois sieges d'ancre
 * aussi, et les trois forteresses posaient les memes briques de gangue autour de la meme
 * pyramide.
 *
 * Choix du joueur : MEME PLAN, TROIS MATIERES, TROIS GARNISONS -- et les themes TIRES AU SORT
 * a chaque partie, la difficulte suivant le rang de prise (GameState.nextTier).
 *
 * LA MATIERE PASSE PAR UNE TABLE. Le chantier pose toujours ses blocs « canoniques » --
 * gangue, briques corrompues, gres de la pyramide -- et chaque pose est traduite au dernier
 * moment (Sanctuary.set, le rhabillage, le processeur de la pyramide, le calque). Le registre
 * de la Sonde (SanctuaryLedger) garde, lui, le bloc canonique : une correction relevee dans un
 * sanctuaire de glace vaut ainsi pour les trois. Une table ne transporte que la MATIERE : la
 * forme (escalier tourne, dalle haute, muret relie) est recopiee telle quelle.
 *
 * Deux tables de plus, plus etroites : la PEAU de la pyramide (le rhabillage, qui sans elle
 * habillerait la pyramide de la meme pierre que la muraille) et l'ENCEINTE -- muraille, tours,
 * portes --, ou l'arcencium des pilastres et des bagues prend l'accent du theme : l'or des
 * Sables, la glace bleue du Givre, le magma des Braises. Ailleurs -- la couronne de l'ancre,
 * le tombeau, l'escalier du faite, le verre prismatique, les lanternes, les coffres, les
 * sceaux -- l'arcencium reste : c'est la marque du mode, la ou l'ancre attend.
 *
 * Une cible peut en nommer plusieurs, « mod:bloc|minecraft:bloc » : la premiere qui existe.
 */
public enum SanctuaryTheme {

    /**
     * LES SABLES : gres et or. Muraille de gres clair a pilastres d'or, tours de gres rouge
     * baguees d'or, cour en damier clair et rouge ; la pyramide garde son gres dedans et prend
     * dehors un degrade du gres rouge au pied au gres clair au faite. L'or est celui des
     * decors du Crepuscule (twilightforest:fake_gold) : il brille comme l'autre, et ne se
     * revend pas -- une muraille d'or vrai se serait demontee lingot par lingot. Les morts des
     * sables et les kobolds de Cataclysm.
     */
    SABLES("sables", 0.09F, 0.05F, 0.45F, 0.92F,
            new String[][]{
                    // muraille, tours, cour : la gangue devient gres
                    {"emeraldweapons:gangue_bricks", "minecraft:sandstone"},
                    {"emeraldweapons:gangue_brick_stairs", "minecraft:sandstone_stairs"},
                    {"emeraldweapons:gangue_brick_slab", "minecraft:sandstone_slab"},
                    {"emeraldweapons:gangue_brick_wall", "minecraft:sandstone_wall"},
                    {"emeraldweapons:corrupted_bricks", "minecraft:smooth_red_sandstone"},
                    {"emeraldweapons:corrupted_brick_stairs", "minecraft:smooth_red_sandstone_stairs"},
                    {"emeraldweapons:corrupted_brick_slab", "minecraft:smooth_red_sandstone_slab"},
                    {"emeraldweapons:corrupted_brick_wall", "minecraft:red_sandstone_wall"},
                    {"emeraldweapons:polished_gangue", "minecraft:chiseled_sandstone"},
                    {"emeraldweapons:polished_gangue_stairs", "minecraft:smooth_sandstone_stairs"},
                    {"emeraldweapons:polished_gangue_slab", "minecraft:cut_sandstone_slab"},
                    {"emeraldweapons:veined_stone", "minecraft:smooth_sandstone"},
                    {"emeraldweapons:veined_stone_stairs", "minecraft:smooth_sandstone_stairs"},
                    {"emeraldweapons:veined_stone_slab", "minecraft:smooth_sandstone_slab"},
                    {"emeraldweapons:veined_stone_wall", "minecraft:sandstone_wall"},
                    {"emeraldweapons:gangue_stone", "minecraft:cut_red_sandstone"},
                    {"emeraldweapons:gangue_stone_stairs", "minecraft:red_sandstone_stairs"},
                    {"emeraldweapons:gangue_stone_slab", "minecraft:cut_red_sandstone_slab"},
                    {"emeraldweapons:gangue_stone_wall", "minecraft:red_sandstone_wall"},
                    {"minecraft:polished_deepslate", "minecraft:cut_red_sandstone"},
                    // la pyramide : son gres est deja celui du theme
            },
            // la peau : celle de la table suffit, du gres rouge au gres clair
            new String[][]{},
            new String[][]{
                    {"emeraldweapons:arcencium_bricks", "twilightforest:fake_gold|minecraft:yellow_terracotta"},
                    {"emeraldweapons:chiseled_arcencium", "twilightforest:fake_gold|minecraft:yellow_terracotta"}},
            new String[][]{
                    {"minecraft:husk", "minecraft:skeleton", "minecraft:spider", "twilightforest:kobold",
                            "cataclysm:koboleton", "undergarden:rotling"},
                    {"minecraft:husk", "cataclysm:koboleton", "cataclysm:wadjet", "twilightforest:kobold",
                            "twilightforest:skeleton_druid", "eternal_starlight:lonestar_skeleton", "alexsmobs:guster",
                            "eternal_starlight:thirst_walker"},
                    {"cataclysm:kobolediator", "cataclysm:wadjet", "cataclysm:koboleton",
                            "eternal_starlight:thirst_walker", "twilightforest:skeleton_druid",
                            "irons_spellbooks:necromancer", "undergarden:rotbeast"}}),

    /**
     * LE GIVRE : glace et ardoise. Courtines de glace tassee a pilastres de glace bleue sur un
     * socle d'ardoise, tours d'ardoise baguees de glace bleue, chemins de neige ; la pyramide
     * blanchit dedans (calcite, diorite) et se givre dehors : la pierre givree de la Prison de
     * Cataclysm en bas, un chapeau de quartz en haut. Rien de glissant la ou l'on marche : les
     * sols sont de neige et de pierre, la glace reste aux parois. Les draugr de la Prison
     * Givree, les loups d'hiver, les cryomanciens.
     */
    GIVRE("givre", 0.55F, 0.06F, 0.22F, 0.97F,
            new String[][]{
                    {"emeraldweapons:gangue_bricks", "minecraft:packed_ice"},
                    {"emeraldweapons:gangue_brick_stairs", "minecraft:quartz_stairs"},
                    {"emeraldweapons:gangue_brick_slab", "minecraft:quartz_slab"},
                    {"emeraldweapons:gangue_brick_wall", "minecraft:diorite_wall"},
                    {"emeraldweapons:corrupted_bricks", "minecraft:deepslate_tiles"},
                    {"emeraldweapons:corrupted_brick_stairs", "minecraft:deepslate_tile_stairs"},
                    {"emeraldweapons:corrupted_brick_slab", "minecraft:deepslate_tile_slab"},
                    {"emeraldweapons:corrupted_brick_wall", "minecraft:deepslate_tile_wall"},
                    {"emeraldweapons:polished_gangue", "minecraft:polished_diorite"},
                    {"emeraldweapons:polished_gangue_stairs", "minecraft:polished_diorite_stairs"},
                    {"emeraldweapons:polished_gangue_slab", "minecraft:polished_diorite_slab"},
                    {"emeraldweapons:veined_stone", "minecraft:snow_block"},
                    {"emeraldweapons:veined_stone_stairs", "minecraft:diorite_stairs"},
                    {"emeraldweapons:veined_stone_slab", "minecraft:diorite_slab"},
                    {"emeraldweapons:veined_stone_wall", "minecraft:diorite_wall"},
                    {"emeraldweapons:gangue_stone", "minecraft:deepslate_bricks"},
                    {"emeraldweapons:gangue_stone_stairs", "minecraft:deepslate_brick_stairs"},
                    {"emeraldweapons:gangue_stone_slab", "minecraft:deepslate_brick_slab"},
                    {"emeraldweapons:gangue_stone_wall", "minecraft:deepslate_brick_wall"},
                    {"minecraft:wall_torch", "minecraft:soul_wall_torch"},
                    // la pyramide, dedans
                    {"minecraft:smooth_sandstone", "minecraft:calcite"},
                    {"minecraft:smooth_sandstone_stairs", "minecraft:polished_diorite_stairs"},
                    {"minecraft:smooth_sandstone_slab", "minecraft:polished_diorite_slab"},
                    {"minecraft:sandstone", "minecraft:diorite"},
                    {"minecraft:sandstone_stairs", "minecraft:diorite_stairs"},
                    {"minecraft:sandstone_slab", "minecraft:diorite_slab"},
                    {"minecraft:sandstone_wall", "minecraft:diorite_wall"},
                    {"minecraft:cut_sandstone", "minecraft:polished_diorite"},
                    {"minecraft:cut_sandstone_slab", "minecraft:polished_diorite_slab"},
                    {"minecraft:chiseled_sandstone", "minecraft:packed_ice"},
                    {"cataclysm:polished_sandstone", "minecraft:quartz_bricks"},
                    {"minecraft:smooth_red_sandstone", "minecraft:packed_ice"},
                    {"minecraft:smooth_red_sandstone_stairs", "minecraft:diorite_stairs"},
                    {"minecraft:smooth_red_sandstone_slab", "minecraft:diorite_slab"},
                    {"minecraft:red_sandstone", "minecraft:packed_ice"},
                    {"minecraft:red_sandstone_stairs", "minecraft:diorite_stairs"},
                    {"minecraft:red_sandstone_slab", "minecraft:diorite_slab"},
                    {"minecraft:red_sandstone_wall", "minecraft:diorite_wall"},
                    {"minecraft:cut_red_sandstone", "minecraft:packed_ice"},
                    {"minecraft:cut_red_sandstone_slab", "minecraft:diorite_slab"},
                    {"minecraft:sand", "minecraft:snow_block"},
                    {"minecraft:magma_block", "minecraft:packed_ice"},
                    // les pieges de gres trahiraient leur place dans la glace
                    {"cataclysm:sandstone_falling_trap", "minecraft:calcite"},
                    {"cataclysm:sandstone_ignite_trap", "minecraft:calcite"},
                    {"cataclysm:sandstone_poison_dart_trap", "minecraft:calcite"},
            },
            new String[][]{
                    {"emeraldweapons:corrupted_bricks", "cataclysm:frosted_stone_bricks|minecraft:polished_diorite"},
                    {"emeraldweapons:corrupted_brick_stairs",
                            "cataclysm:frosted_stone_brick_stairs|minecraft:polished_diorite_stairs"},
                    {"emeraldweapons:corrupted_brick_slab",
                            "cataclysm:frosted_stone_brick_slab|minecraft:polished_diorite_slab"},
                    {"emeraldweapons:gangue_bricks", "minecraft:quartz_bricks"},
                    {"emeraldweapons:gangue_brick_stairs", "minecraft:quartz_stairs"},
                    {"emeraldweapons:gangue_brick_slab", "minecraft:quartz_slab"}},
            new String[][]{
                    {"emeraldweapons:arcencium_bricks", "minecraft:blue_ice"},
                    {"emeraldweapons:chiseled_arcencium", "minecraft:blue_ice"}},
            new String[][]{
                    {"minecraft:stray", "variantsandventures:gelid", "cataclysm:draugr", "eternal_starlight:freeze",
                            "twilightforest:snow_guardian"},
                    {"cataclysm:draugr", "cataclysm:elite_draugr", "minecraft:stray", "twilightforest:winter_wolf",
                            "irons_spellbooks:cryomancer", "twilightforest:snow_guardian", "eternal_starlight:freeze",
                            "twilightforest:stable_ice_core"},
                    {"cataclysm:royal_draugr", "cataclysm:elite_draugr", "cataclysm:aptrgangr",
                            "twilightforest:winter_wolf", "irons_spellbooks:cryomancer", "twilightforest:yeti",
                            "twilightforest:snow_guardian", "twilightforest:stable_ice_core"}}),

    /**
     * LES BRAISES : basalte et magma. Pierre noire a pilastres de magma, tours noires baguees
     * de magma, aretes et bordures de brique du Nether rouge, sols de basalte lisse ; la
     * pyramide noircit, ses pieges de feu deviennent du magma. Le magma ne se pose qu'aux
     * parois : on ne marche sur aucun. Les revenants embrases de Cataclysm, les blazes, les
     * zoglins.
     */
    BRAISES("braises", 0.02F, 0.04F, 0.70F, 0.72F,
            new String[][]{
                    {"emeraldweapons:gangue_bricks", "minecraft:polished_blackstone_bricks"},
                    {"emeraldweapons:gangue_brick_stairs", "minecraft:polished_blackstone_brick_stairs"},
                    {"emeraldweapons:gangue_brick_slab", "minecraft:polished_blackstone_brick_slab"},
                    {"emeraldweapons:gangue_brick_wall", "minecraft:polished_blackstone_brick_wall"},
                    {"emeraldweapons:corrupted_bricks", "minecraft:blackstone"},
                    {"emeraldweapons:corrupted_brick_stairs", "minecraft:blackstone_stairs"},
                    {"emeraldweapons:corrupted_brick_slab", "minecraft:blackstone_slab"},
                    {"emeraldweapons:corrupted_brick_wall", "minecraft:blackstone_wall"},
                    {"emeraldweapons:polished_gangue", "minecraft:red_nether_bricks"},
                    {"emeraldweapons:polished_gangue_stairs", "minecraft:red_nether_brick_stairs"},
                    {"emeraldweapons:polished_gangue_slab", "minecraft:red_nether_brick_slab"},
                    {"emeraldweapons:veined_stone", "minecraft:smooth_basalt"},
                    {"emeraldweapons:veined_stone_stairs", "minecraft:polished_blackstone_stairs"},
                    {"emeraldweapons:veined_stone_slab", "minecraft:polished_blackstone_slab"},
                    {"emeraldweapons:veined_stone_wall", "minecraft:polished_blackstone_wall"},
                    {"emeraldweapons:gangue_stone", "minecraft:polished_blackstone"},
                    {"emeraldweapons:gangue_stone_stairs", "minecraft:polished_blackstone_stairs"},
                    {"emeraldweapons:gangue_stone_slab", "minecraft:polished_blackstone_slab"},
                    {"emeraldweapons:gangue_stone_wall", "minecraft:polished_blackstone_wall"},
                    {"minecraft:polished_deepslate", "minecraft:chiseled_polished_blackstone"},
                    // la pyramide, dedans
                    {"minecraft:smooth_sandstone", "minecraft:polished_blackstone"},
                    {"minecraft:smooth_sandstone_stairs", "minecraft:polished_blackstone_stairs"},
                    {"minecraft:smooth_sandstone_slab", "minecraft:polished_blackstone_slab"},
                    {"minecraft:sandstone", "minecraft:blackstone"},
                    {"minecraft:sandstone_stairs", "minecraft:blackstone_stairs"},
                    {"minecraft:sandstone_slab", "minecraft:blackstone_slab"},
                    {"minecraft:sandstone_wall", "minecraft:blackstone_wall"},
                    {"minecraft:cut_sandstone", "minecraft:polished_basalt"},
                    {"minecraft:cut_sandstone_slab", "minecraft:polished_blackstone_brick_slab"},
                    {"minecraft:chiseled_sandstone", "minecraft:chiseled_polished_blackstone"},
                    {"cataclysm:polished_sandstone", "minecraft:polished_blackstone_bricks"},
                    {"minecraft:smooth_red_sandstone", "minecraft:nether_bricks"},
                    {"minecraft:smooth_red_sandstone_stairs", "minecraft:nether_brick_stairs"},
                    {"minecraft:smooth_red_sandstone_slab", "minecraft:nether_brick_slab"},
                    {"minecraft:red_sandstone", "minecraft:nether_bricks"},
                    {"minecraft:red_sandstone_stairs", "minecraft:nether_brick_stairs"},
                    {"minecraft:red_sandstone_slab", "minecraft:nether_brick_slab"},
                    {"minecraft:red_sandstone_wall", "minecraft:nether_brick_wall"},
                    {"minecraft:cut_red_sandstone", "minecraft:red_nether_bricks"},
                    {"minecraft:cut_red_sandstone_slab", "minecraft:red_nether_brick_slab"},
                    {"minecraft:sand", "minecraft:soul_soil"},
                    {"cataclysm:sandstone_falling_trap", "minecraft:polished_blackstone"},
                    {"cataclysm:sandstone_ignite_trap", "minecraft:magma_block"},
                    {"cataclysm:sandstone_poison_dart_trap", "minecraft:polished_blackstone_bricks"},
            },
            new String[][]{},
            new String[][]{
                    {"emeraldweapons:arcencium_bricks", "minecraft:magma_block"},
                    {"emeraldweapons:chiseled_arcencium", "minecraft:magma_block"}},
            new String[][]{
                    {"minecraft:magma_cube", "twilightforest:fire_beetle", "eternal_starlight:tiny_creteor",
                            "alexsmobs:crimson_mosquito"},
                    {"cataclysm:ignited_revenant", "minecraft:blaze", "minecraft:zoglin", "minecraft:magma_cube",
                            "twilightforest:fire_beetle", "eternal_starlight:creteor", "alexsmobs:soul_vulture",
                            "alexsmobs:crimson_mosquito"},
                    {"cataclysm:ignited_berserker", "cataclysm:ignited_revenant", "minecraft:blaze",
                            "minecraft:wither_skeleton", "minecraft:zoglin", "eternal_starlight:creteor",
                            "alexsmobs:soul_vulture"}});

    public final String id;
    /** La teinte de la brume : centre, amplitude de la derive, saturation, luminosite (TSL de java.awt). */
    final float hue;
    final float hueSpan;
    final float saturation;
    final float brightness;
    private final String[][] table;
    private final String[][] skinTable;
    private final String[][] shellTable;
    private final String[][] garrison;
    @Nullable
    private Map<Block, Block> resolved;
    @Nullable
    private Map<Block, Block> skins;
    @Nullable
    private Map<Block, Block> shells;
    /** Les blocs d'arrivee de la table : le rhabillage les interroge sept cent mille fois. */
    private java.util.Set<Block> targets = java.util.Set.of();

    SanctuaryTheme(String id, float hue, float hueSpan, float saturation, float brightness,
                   String[][] table, String[][] skinTable, String[][] shellTable, String[][] garrison) {
        this.id = id;
        this.hue = hue;
        this.hueSpan = hueSpan;
        this.saturation = saturation;
        this.brightness = brightness;
        this.table = table;
        this.skinTable = skinTable;
        this.shellTable = shellTable;
        this.garrison = garrison;
    }

    /** Le nom du sanctuaire, a traduire : « le Sanctuaire du Givre ». */
    public Component displayName() {
        return Component.translatable("game.emeraldweapons.sanctuaire.theme." + this.id);
    }

    /** Les monstres de ce theme a ce palier (1 a 3) : garnison et siege de l'ancre. */
    public List<String> monsters(int tier) {
        return List.of(this.garrison[Math.max(1, Math.min(3, tier)) - 1]);
    }

    /** La particule propre a la brume de ce theme -- aucune autre systeme ne s'en sert. */
    ParticleOptions accent() {
        return switch (this) {
            case SABLES -> new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.FALLING_DUST,
                    Blocks.SAND.defaultBlockState());
            case GIVRE -> ParticleTypes.WHITE_ASH;
            case BRAISES -> ParticleTypes.LAVA;
        };
    }

    /**
     * Ce bloc, dans la matiere du theme : meme forme, autre matiere. Un bloc absent de la
     * table (l'ancre, un coffre, l'air) passe tel quel.
     */
    public BlockState apply(BlockState state) {
        Block target = resolved().get(state.getBlock());
        return target == null ? state : copyShape(state, target.defaultBlockState());
    }

    /** Un bloc de la PEAU de la pyramide : sa table propre d'abord, puis celle du theme. */
    public BlockState skin(BlockState state) {
        if (this.skins == null) {
            this.skins = resolve(this.skinTable);
        }
        Block target = this.skins.get(state.getBlock());
        return target == null ? apply(state) : copyShape(state, target.defaultBlockState());
    }

    /** Un bloc de l'ENCEINTE (muraille, tours, portes) : l'accent du theme a la place de l'arcencium. */
    public BlockState shell(BlockState state) {
        if (this.shells == null) {
            this.shells = resolve(this.shellTable);
        }
        Block target = this.shells.get(state.getBlock());
        return target == null ? apply(state) : copyShape(state, target.defaultBlockState());
    }

    /** Un bloc que ce theme a mis dans la pyramide : le rhabillage doit pouvoir le repeindre. */
    public boolean isPyramidMaterial(Block block) {
        resolved();
        return this.targets.contains(block);
    }

    private Map<Block, Block> resolved() {
        Map<Block, Block> map = this.resolved;
        if (map == null) {
            map = resolve(this.table);
            this.targets = java.util.Set.copyOf(map.values());
            this.resolved = map;
        }
        return map;
    }

    /** Une table lue une fois : les lignes dont un bloc manque (un mod absent) tombent. */
    private static Map<Block, Block> resolve(String[][] rows) {
        Map<Block, Block> map = new HashMap<>();
        for (String[] row : rows) {
            Block from = block(row[0]);
            Block to = block(row[1]);
            if (from != null && to != null) {
                map.put(from, to);
            }
        }
        return map;
    }

    /** Le premier bloc de « a|b|c » qui existe, ou rien. */
    @Nullable
    private static Block block(String ids) {
        for (String id : ids.split("\\|")) {
            ResourceLocation key = ResourceLocation.tryParse(id.trim());
            if (key != null && BuiltInRegistries.BLOCK.containsKey(key)) {
                Block block = BuiltInRegistries.BLOCK.get(key);
                if (block != Blocks.AIR) {
                    return block;
                }
            }
        }
        return null;
    }

    /** Recopie la forme (facing, moitie, type de dalle, liaisons de muret...) d'un bloc a l'autre. */
    static BlockState copyShape(BlockState from, BlockState to) {
        BlockState out = to;
        for (Property<?> property : from.getProperties()) {
            if (out.hasProperty(property)) {
                out = transfer(from, out, property);
            }
        }
        return out;
    }

    private static <T extends Comparable<T>> BlockState transfer(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    public static SanctuaryTheme byId(@Nullable String id) {
        for (SanctuaryTheme theme : values()) {
            if (theme.id.equals(id)) {
                return theme;
            }
        }
        return SABLES;
    }

    /** Les trois themes dans un ordre tire au sort : un par ancre, chaque partie les melange. */
    public static List<SanctuaryTheme> shuffled(RandomSource random) {
        List<SanctuaryTheme> out = new ArrayList<>(List.of(values()));
        for (int i = out.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            SanctuaryTheme swap = out.get(i);
            out.set(i, out.get(j));
            out.set(j, swap);
        }
        return out;
    }

    /** Pour le journal. */
    @Override
    public String toString() {
        return EmeraldWeaponsMod.MODID + ":" + this.id;
    }
}
