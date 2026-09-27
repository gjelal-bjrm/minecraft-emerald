package com.emerald.jak;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * LES MODELES DE JAK EN CUBES (cahier §110).
 *
 * Le joueur, 27 sept. : « est-ce que ce serait difficile de les faire en version Minecraft ? [...] je
 * trouve que ca deconnecte un petit peu de la realite du jeu, qui est cense etre cubique ». Puis, sur
 * les photos du JET-Board : des cubes de 1/16 de bloc (le pixel de Minecraft), et ensuite les
 * voitures, les motos et le Morph Gun.
 *
 * tools/jak_cubes.py cuit <modele>_c16.bin dans le dossier cubes/, a cote du modele de Jak 3. Ce qui
 * est range ici se dessine en cubes ; le reste, comme dans Jak 3. Pour les essais,
 * EMERALDWEAPONS_JAK_CUBES force l'un ou l'autre : « jak » (tout comme dans Jak 3), ou une taille
 * (16, 32...) pour tout ce qui a une version de cette taille.
 */
public final class JakCubes {

    /** Les modeles passes en cubes pour de bon, et la taille de leurs cubes (cubes par bloc). */
    private static final Map<String, Integer> DEFAULT = Map.ofEntries(Map.entry("jet_board", 16),
            Map.entry("cara", 16), Map.entry("carb", 16), Map.entry("carc", 16),
            Map.entry("bikea", 16), Map.entry("bikeb", 16), Map.entry("bikec", 16),
            // le Morph Gun, ses douze armes (des poses d'un meme squelette) et ce qu'il tire
            Map.entry("morph_gun", 16), Map.entry("gun_ammo_red", 16), Map.entry("gun_ammo_yellow", 16),
            Map.entry("gun_ammo_blue", 16), Map.entry("gun_ammo_dark", 16), Map.entry("gun_grenade", 16),
            Map.entry("gun_saucer", 16), Map.entry("gun_nuke", 16));
    /**
     * AU LOIN, DES CUBES PLUS GROS. Une voiture en 1/16 fait vingt mille rectangles (celle de Jak,
     * douze cents triangles) : redessinee a chaque image, plusieurs dans le trafic de Haven feraient
     * ramer le jeu. A plus de FAR_DISTANCE blocs, ou la difference ne se voit plus, elle se dessine en
     * 1/8 -- quatre fois moins. Le joueur au volant, et ce qui l'entoure, restent en 1/16.
     */
    private static final Map<String, Integer> FAR = Map.of(
            "cara", 8, "carb", 8, "carc", 8, "bikea", 8, "bikeb", 8, "bikec", 8);
    public static final double FAR_DISTANCE = 24.0;
    private static final String OVERRIDE = System.getenv("EMERALDWEAPONS_JAK_CUBES");

    /**
     * Les cubes des vehicules disent un atlas de 16 x 16 : leur couleur est dans les sommets, et le
     * rendu leur donne une texture blanche (l'atlas des vehicules n'a pas un texel blanc).
     */
    public static final int CUBE_ATLAS = 16;
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            EmeraldWeaponsMod.MODID, "textures/entity/jak_cubes.png");

    private JakCubes() {
    }

    /** Le suffixe du fichier en cubes de ce modele (« _c16 »), ou null : celui de Jak 3. */
    @Nullable
    public static String suffix(String model) {
        if (OVERRIDE != null && !OVERRIDE.isBlank()) {
            String forced = OVERRIDE.trim();
            return "jak".equalsIgnoreCase(forced) ? null : "_c" + forced;
        }
        Integer size = DEFAULT.get(model);
        return size == null ? null : "_c" + size;
    }

    /** Le suffixe du modele dessine au loin (« _c8 ») ; celui de pres s'il n'y en a pas d'autre. */
    @Nullable
    public static String farSuffix(String model) {
        if (OVERRIDE != null && !OVERRIDE.isBlank()) {
            return suffix(model);                   // les essais : une seule taille, de pres comme de loin
        }
        Integer size = FAR.get(model);
        return size == null ? suffix(model) : "_c" + size;
    }

    /** Un modele cuit en cubes, a son en-tete (l'atlas de 16 x 16 qu'il annonce). */
    public static boolean isCubed(int atlasWidth, int atlasHeight) {
        return atlasWidth == CUBE_ATLAS && atlasHeight == CUBE_ATLAS;
    }
}
