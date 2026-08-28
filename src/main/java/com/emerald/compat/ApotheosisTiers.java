package com.emerald.compat;

import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Le seul endroit du mode qui nomme une classe d'Apotheosis.
 *
 * Apotheosis est une dependance de COMPILATION seulement : le mod se charge
 * sans lui. Or une classe qui cite un type absent explose au chargement, pas a
 * l'appel -- toutes ces citations sont donc rassemblees ici, et l'appelant
 * verifie que le mod est charge avant de toucher a cette classe. Tant qu'il ne
 * la touche pas, elle n'est jamais chargee, et son absence ne coute rien.
 *
 * Pourquoi cette dependance : accorder l'avancement OUVRE un palier, mais
 * laisse au joueur un CTRL+T pour l'activer, et le mode ne doit rien exiger de
 * tel. {@code WorldTier.setTier} est la seule facon d'ecrire le palier actif --
 * elle pose l'attachement, previent le client, et remplace les augments du
 * palier precedent par ceux du nouveau. Rien de tout cela n'est reproductible
 * de l'exterieur.
 */
public final class ApotheosisTiers {

    private ApotheosisTiers() {
    }

    /** Le nombre de paliers, pour borner ce qu'on demande. */
    public static int count() {
        return WorldTier.values().length;
    }

    /**
     * Porte le joueur au palier demande, sans jamais le faire redescendre.
     *
     * La descente est exclue volontairement : un joueur qui a active un palier
     * plus haut de lui-meme -- c'est son droit, l'ecran de selection est
     * toujours la -- ne doit pas se le voir retirer par la phase suivante.
     *
     * @return vrai si le palier a change
     */
    public static boolean raiseTo(ServerPlayer player, int index) {
        WorldTier[] tiers = WorldTier.values();
        int wanted = Math.max(0, Math.min(tiers.length - 1, index));
        WorldTier target = tiers[wanted];
        WorldTier now = WorldTier.getTier(player);
        if (now != null && now.ordinal() >= target.ordinal()) {
            return false;
        }
        WorldTier.setTier(player, target);
        return true;
    }

    /** Le nom lisible du palier, pour l'annonce a l'ecran. */
    public static String name(int index) {
        WorldTier[] tiers = WorldTier.values();
        return tiers[Math.max(0, Math.min(tiers.length - 1, index))].getSerializedName();
    }

    /**
     * L'avancement qui ouvre ce palier, demande a Apotheosis plutot que devine.
     *
     * On continue de l'accorder en plus d'ecrire le palier : sans lui, l'ecran
     * de selection montrerait comme verrouille un palier ou le joueur se
     * trouve deja.
     */
    public static ResourceLocation unlockAdvancement(int index) {
        WorldTier[] tiers = WorldTier.values();
        return tiers[Math.max(0, Math.min(tiers.length - 1, index))].getUnlockAdvancement();
    }
}
