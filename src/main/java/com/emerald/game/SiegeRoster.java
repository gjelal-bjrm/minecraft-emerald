package com.emerald.game;

import net.minecraft.world.entity.EntityType;

import java.util.List;

/**
 * Qui compose une vague, selon ce qui est installe.
 *
 * Les identifiants du modpack sont cites en TEXTE et resolus a l'execution :
 * citer les classes rendrait le mod incapable de demarrer sans elles. Une
 * faction absente disparait simplement du tirage.
 *
 * Factions visees, dans l'esprit du cahier :
 *   Cour Noyee et Legion Draugr .. L_Ender's Cataclysm
 *   Cercle Arcanique ............. Iron's Spellbooks
 *   Oublies ...................... The Undergarden
 *   Horde Gobeline ............... Twilight Forest
 *   Sculk ........................ Deeper Darker (reserve a l'arene finale)
 *
 * Du vanilla est melange a chaque palier : une vague entierement composee de
 * creatures etrangeres se lit mal, et perd le joueur qui ne connait pas le
 * modpack par coeur.
 */
public final class SiegeRoster {

    private SiegeRoster() {
    }

    /**
     * Le prologue n'utilise QUE du vanilla, et du plus faible.
     *
     * Il se joue en armure de fer, sans artefact ni equipement d'Arcencium :
     * c'est la ou l'on apprend a se battre, pas ou l'on meurt. Les factions du
     * modpack -- draugr, cultistes, deeplings -- sont taillees pour du jeu tres
     * avance et massacraient les defenseurs en quelques secondes.
     */
    public static List<String> prologue() {
        return List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:husk");
    }

    /**
     * ATTENTION AU VIVIER D'IRON'S SPELLBOOKS : le Pretre, le Pyromancien et
     * l'Apothicaire heritent de NeutralWizard -- ce sont des MARCHANDS, ils ne
     * se battent pas. Le joueur les a vus rester plantes au milieu d'un siege.
     * N'utiliser que ceux qui implementent Enemy : cultist, keeper, cryomancer,
     * necromancer, archevoker.
     */
    public static List<String> forTier(int tier) {
        return switch (tier) {
            case 1 -> List.of(
                    "cataclysm:deepling", "undergarden:rotling", "twilightforest:kobold",
                    "minecraft:zombie", "minecraft:skeleton", "minecraft:pillager",
                    "minecraft:husk", "minecraft:stray");
            case 2 -> List.of(
                    "cataclysm:deepling_brute", "cataclysm:deepling_angler",
                    "cataclysm:elite_draugr", "cataclysm:koboleton",
                    "irons_spellbooks:cultist", "irons_spellbooks:cryomancer",
                    "irons_spellbooks:keeper", "undergarden:rotwalker",
                    "twilightforest:blockchain_goblin",
                    "minecraft:vindicator", "minecraft:witch", "minecraft:stray");
            default -> List.of(
                    "cataclysm:deepling_warlock", "cataclysm:royal_draugr",
                    "cataclysm:kobolediator", "cataclysm:coral_golem",
                    "irons_spellbooks:necromancer", "irons_spellbooks:archevoker",
                    "undergarden:forgotten_guardian", "twilightforest:armored_giant",
                    "minecraft:evoker", "minecraft:ravager", "minecraft:wither_skeleton");
        };
    }

    public static EntityType<?>[] vanillaFallback(int tier) {
        if (tier >= 3) {
            return new EntityType<?>[]{EntityType.PILLAGER, EntityType.VINDICATOR,
                    EntityType.EVOKER, EntityType.RAVAGER, EntityType.WITHER_SKELETON};
        }
        if (tier == 2) {
            return new EntityType<?>[]{EntityType.PILLAGER, EntityType.VINDICATOR,
                    EntityType.HUSK, EntityType.STRAY, EntityType.WITCH};
        }
        return new EntityType<?>[]{EntityType.ZOMBIE, EntityType.SKELETON,
                EntityType.SPIDER, EntityType.PILLAGER};
    }
}
