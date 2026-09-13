package com.emerald.haven;

import com.emerald.jak.JakVolume;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * La ville du port de Haven : ou elle vit, ou elle se pose, et ce qu'on y lit.
 *
 * ELLE VIT DANS SA PROPRE DIMENSION, emeraldweapons:haven. Tous les systemes de
 * la partie -- meteo, Traque, Maree, sieges, sanctuaires -- filtrent deja sur
 * l'overworld : ils s'y taisent sans une ligne de plus, et la ville ne coute
 * rien pendant la partie.
 *
 * LE GENERATEUR EST PLAT : une couche de bedrock, cinquante-six de pierre, six
 * d'eau, soit la mer de Y 57 a Y 62. Posee a l'origine (0, 5, 0), la derniere
 * couche d'eau du volume (cellule 57) tombe exactement sur Y 62 : la rue
 * (cellule 65) est en Y 70, les pieds en Y 71, et la mer du generateur prolonge
 * la rade jusqu'a l'horizon sans chute d'eau au bord de la grille.
 */
public final class Haven {

    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "haven"));

    /** Le coin (minx, miny, minz) de la grille dans la dimension. Fige : les salles en dependront. */
    public static final BlockPos ORIGIN = new BlockPos(0, 5, 0);

    /** Le volume de la ville, dans data/emeraldweapons/jak/. */
    public static final String VOLUME = "ctyport";

    /** Taille de la grille, tant qu'aucune pose n'a donne la sienne. */
    public static final int GRID_WIDTH = 1227;
    public static final int GRID_HEIGHT = 158;
    public static final int GRID_DEPTH = 695;

    /** Le haut de la pierre et le haut de la mer du generateur. */
    public static final int STONE_TOP = 56;
    public static final int WATER_TOP = 62;

    /** Le pave de la rue devant le bar, en cellules du volume. */
    public static final BlockPos STREET_CELL = new BlockPos(375, 65, 211);
    /** Les pieds dans la rue devant le bar. */
    public static final BlockPos BAR_FRONT_CELL = new BlockPos(375, 66, 211);
    /** Le seuil de la porte du Hip Hog. */
    public static final BlockPos BAR_DOOR_CELL = new BlockPos(361, 66, 197);
    /** Le regard vers la porte depuis la rue : quatorze cellules a l'ouest et au nord. */
    public static final float BAR_FRONT_YAW = 135.0F;

    private Haven() {
    }

    public static boolean is(Level level) {
        return level.dimension().equals(LEVEL);
    }

    /** Le niveau de la ville, ou null si la dimension n'a pas ete chargee. */
    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    /**
     * Ce que le generateur a mis a une hauteur donnee.
     *
     * C'est l'etat que la boite reprend avant une nouvelle pose : de l'air
     * au-dessus de la mer, de l'eau de 57 a 62, de la pierre dessous.
     */
    public static BlockState generatorState(int y) {
        if (y <= 0) {
            return Blocks.BEDROCK.defaultBlockState();
        }
        if (y <= STONE_TOP) {
            return Blocks.STONE.defaultBlockState();
        }
        if (y <= WATER_TOP) {
            return Blocks.WATER.defaultBlockState();
        }
        return Blocks.AIR.defaultBlockState();
    }

    /**
     * L'empreinte du volume de la ville : le sha1 de ses DONNEES, lu dans
     * l'en-tete.
     *
     * C'est celle que portent haven_rooms.json et {@link JakVolume#sha1()}. On
     * prenait autrefois celle des octets du fichier, compresses compris : elle
     * ne correspondait a rien d'autre, si bien que la regle des salles (« si le
     * sha1 du volume pose differe... ») aurait toujours echoue, et elle change
     * avec la version de zlib a donnees identiques.
     *
     * @return l'empreinte en hexadecimal, ou une chaine vide si le fichier manque
     */
    public static String volumeSha1(MinecraftServer server) {
        String sha1 = JakVolume.dataSha1(server, VOLUME);
        return sha1 == null ? "" : sha1;
    }
}
