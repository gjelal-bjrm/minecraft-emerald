package com.emerald.client;

import com.emerald.network.VeinSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * LE REPERE A L'ECRAN : le filon marque LA OU IL EST, meme derriere la roche.
 *
 * POURQUOI PAS UNE LUEUR D'ENTITE. C'est ce que faisait l'Aurore, et la mesure
 * l'a condamnee : une lueur d'entite ne se dessine que si l'entite est RENDUE,
 * et Sodium ne rend pas ce qui se trouve dans une section de terrain masquee.
 * Un filon enterre dans un massif plein est precisement cela. Capture a
 * l'appui : a quatre blocs, dans la pierre, on ne voit RIEN ; les deux memes
 * blocs otes, le contour cyan eclate. La promesse « les silhouettes brillent a
 * travers la roche » etait donc fausse partout ou elle servait.
 *
 * On projette donc nous-memes. Le repere est dessine dans l'interface, apres
 * tout le rendu du monde : ni la roche, ni Sodium, ni les shaders d'Iris n'ont
 * leur mot a dire. C'est le seul canal dont on soit maitre de bout en bout.
 *
 * Il ne s'affiche QUE si le filon est dans le champ : hors champ, la fleche du
 * panneau dit deja de quel cote tourner, et coller des losanges aux bords
 * encombrait l'ecran de six marques immobiles (capture a l'appui). Le repere
 * sert a viser, la fleche a s'orienter.
 */
public final class VeinMarkerClient {

    /** Au-dela, le repere n'aiderait plus : on ne creuse pas soixante blocs a l'aveugle. */
    private static final double RANGE = 64.0;
    /** Taille du losange, en pixels d'interface. */
    private static final int SIZE = 5;
    /** Marge du bord : au-dela, on laisse la fleche du panneau faire son travail. */
    private static final int EDGE = 14;

    private VeinMarkerClient() {
    }

    /** Dessine les reperes. Appele par {@link GameHudClient}, avant les panneaux. */
    public static void render(GuiGraphics graphics, Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        java.util.List<Long> veins = VeinHudClient.shown();
        if (veins.isEmpty()) {
            return;
        }
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        // la rotation INVERSE de la camera amene le monde dans le repere de la vue
        Quaternionf back = camera.rotation().conjugate(new Quaternionf());
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // la moitie de l'ouverture verticale, en tangente : le facteur d'echelle
        double half = Math.tan(Math.toRadians(mc.options.fov().get() / 2.0));

        for (int i : VeinHudClient.order()) {
            BlockPos pos = BlockPos.of(veins.get(i));
            Vec3 delta = new Vec3(pos.getX() + 0.5 - eye.x, pos.getY() + 0.5 - eye.y,
                    pos.getZ() + 0.5 - eye.z);
            double distance = delta.length();
            if (distance > RANGE) {
                continue;
            }
            Vector3f view = new Vector3f((float) delta.x, (float) delta.y, (float) delta.z);
            back.transform(view);
            // dans le repere de la vue, on regarde vers -Z
            float forward = -view.z;
            if (forward <= 0.05F) {
                continue;               // derriere soi : la fleche du panneau le dit deja
            }
            int sx = (int) Math.round(width / 2.0 + (view.x / forward) / half * (height / 2.0));
            int sy = (int) Math.round(height / 2.0 - (view.y / forward) / half * (height / 2.0));
            if (sx < EDGE || sx > width - EDGE || sy < EDGE || sy > height - EDGE) {
                continue;               // hors champ : on n'encombre pas les bords
            }
            // LE REPERE NE DIT QUE LA PLACE. La distance est dans le panneau, a
            // gauche ; la repeter sous chaque losange encombrait l'ecran, et
            // les deux chiffres se contredisaient -- l'un a plat, l'autre en
            // ligne droite.
            diamond(graphics, sx, sy,
                    VeinHudClient.colourOf(VeinSyncPayload.kindAt(VeinHudClient.kinds(), i)),
                    false);
        }
    }

    /**
     * Un losange creux : il marque la place sans cacher la paroi qu'on mine.
     */
    private static void diamond(GuiGraphics graphics, int x, int y, int colour, boolean filled) {
        int shadow = 0xAA000000;
        for (int d = -SIZE; d <= SIZE; d++) {
            int half = SIZE - Math.abs(d);
            if (filled) {
                graphics.fill(x - half - 1, y + d, x + half + 2, y + d + 1, shadow);
                graphics.fill(x - half, y + d, x + half + 1, y + d + 1, colour);
            } else {
                // seulement le contour : les deux pixels des bords
                graphics.fill(x - half - 1, y + d, x - half + 1, y + d + 1, shadow);
                graphics.fill(x + half, y + d, x + half + 2, y + d + 1, shadow);
                graphics.fill(x - half, y + d, x - half + 1, y + d + 1, colour);
                graphics.fill(x + half, y + d, x + half + 1, y + d + 1, colour);
            }
        }
    }
}
