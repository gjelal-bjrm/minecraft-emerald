package com.emerald.client;

import com.emerald.hero.HeroLevel;
import com.emerald.hero.HeroStat;
import com.emerald.network.HeroSpendPayload;
import com.emerald.network.HeroSyncPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * La fiche du Heros : ce qu'on a gagne, et ou le mettre.
 *
 * Elle s'ouvre sur une touche et se lit en un coup d'oeil : le niveau et sa
 * progression en haut, les quatre voies en dessous. Chaque voie montre son
 * niveau sur cent, le prix du prochain, ce qu'elle donne, et ce que ses
 * paliers ont deja accorde. On monte par un, par cinq ou par dix niveaux --
 * cent niveaux un par un serait une corvee, et n'offrir que le gros lot
 * empecherait d'ajuster.
 *
 * TOUT LE TEXTE PORTE UNE OMBRE, et le panneau est OPAQUE. Ce sont les deux
 * seules raisons pour lesquelles la fiche paraissait floue : le vide sous mes
 * glyphes et les sept pour cent de monde flou qui remontaient au travers. Les
 * boutons, eux, etaient nets -- ils dessinent leur libelle avec ombre, comme
 * tout le reste de l'interface du jeu, et c'est cet ecart qui a designe la
 * cause. Une police de seize pixels sans ombre sur un fond sombre n'a plus de
 * contour : l'oeil ne lit pas un texte mal contraste, il lit un texte flou.
 *
 * ELLE NE DECIDE DE RIEN. Chaque clic n'est qu'une demande envoyee au serveur ;
 * les chiffres affiches viennent de la derniere fiche recue. On voit donc le
 * resultat reel et non ce que le client aurait aime -- c'est ce qui evite
 * qu'un aller-retour rate laisse a l'ecran des points qu'on ne possede pas.
 */
public class HeroScreen extends Screen {

    private static final int PANEL_W = 268;
    private static final int PANEL_H = 242;

    private static final int GOLD = 0xFFFFD24A;
    private static final int VIOLET = 0xFFB98CFF;
    private static final int DIM = 0xFF8A8A9C;

    /**
     * Les lots proposes, EN NIVEAUX et non en points.
     *
     * Un clic monte la voie d'un cran et paie le prix courant : c'est le geste
     * de NosTale, et c'est le seul lisible ici. Proposer des lots de points
     * bruts obligerait le joueur a diviser de tete par un cout qui change tous
     * les dix niveaux.
     *
     * Dix est aussi la taille d'un palier : le bouton le plus a droite avance
     * donc toujours d'exactement un palier.
     */
    private static final int[] STEPS = {1, 5, 10};

    private final List<Button> buttons = new ArrayList<>();

    private int left;
    private int top;

    public HeroScreen() {
        super(Component.translatable("hero.emeraldweapons.sheet"));
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;
        this.buttons.clear();

        int rowY = this.top + 68;
        for (HeroStat stat : HeroStat.values()) {
            int bx = this.left + PANEL_W - 10 - (STEPS.length * 24 + (STEPS.length - 1) * 3);
            for (int step : STEPS) {
                Button button = Button.builder(Component.literal("+" + step),
                                b -> send(stat, step))
                        .bounds(bx, rowY + 10, 24, 16)
                        .build();
                this.buttons.add(button);
                this.addRenderableWidget(button);
                bx += 27;
            }
            rowY += 40;
        }
        refresh();
    }

    private void send(HeroStat stat, int amount) {
        PacketDistributor.sendToServer(new HeroSpendPayload(stat.ordinal(), amount));
        // On ne touche a rien ici : le serveur repond par une fiche complete,
        // et c'est elle qui rallume ou eteint les boutons.
    }

    /**
     * Eteint ce qui n'est pas possible.
     *
     * Un bouton "+10" cliquable quand il reste trois points libres promet ce
     * qu'il ne peut pas tenir. On le grise plutot que de laisser le serveur
     * refuser en silence : le joueur doit voir ce qu'il PEUT faire, pas
     * decouvrir ce qu'il ne pouvait pas.
     */
    private void refresh() {
        HeroSyncPayload sheet = HeroHudClient.sheet();
        int free = sheet == null ? 0 : sheet.free();
        int i = 0;
        for (HeroStat stat : HeroStat.values()) {
            int level = HeroHudClient.path(stat);
            for (int step : STEPS) {
                // On SIMULE l'achat plutot que de comparer a un cout moyen : le
                // prix change en cours de route, et un lot de dix peut franchir
                // une tranche. Dix additions valent mieux qu'une approximation
                // qui allumerait un bouton incapable de tenir sa promesse.
                int purse = free;
                int at = level;
                int bought = 0;
                while (bought < step && at < HeroStat.MAX_PATH
                        && purse >= HeroStat.cost(at)) {
                    purse -= HeroStat.cost(at);
                    at++;
                    bought++;
                }
                this.buttons.get(i++).active = bought == step;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        refresh();
        // LE FOND ET LES BOUTONS D'ABORD, LE TEXTE ENSUITE. Depuis la 1.20.2,
        // Screen.render() appelle lui-meme renderBackground(), et c'est la que
        // le flou du menu s'applique a tout ce qui est deja dessine. En
        // appelant renderBackground moi-meme AVANT mes textes puis super.render
        // APRES, je floutais deux fois -- et la seconde fois, mon texte etait
        // deja dans l'image pendant que les boutons ne l'etaient pas encore.
        // D'ou une fiche floue aux boutons nets. Tout ce qui est dessine ici
        // vient maintenant apres l'unique passage du fond.
        super.render(graphics, mouseX, mouseY, partial);

        HeroSyncPayload sheet = HeroHudClient.sheet();
        graphics.drawString(this.font, this.title, this.left + 10, this.top + 9, GOLD, true);

        if (sheet == null) {
            graphics.drawString(this.font,
                    Component.translatable("hero.emeraldweapons.waiting"),
                    this.left + 10, this.top + 30, DIM, true);
            return;
        }

        boolean maxed = sheet.level() >= HeroLevel.MAX_LEVEL;
        int percent = maxed ? 100
                : (int) Math.floor(100.0 * sheet.xp() / Math.max(1, sheet.needed()));

        // --- le niveau et sa jauge
        graphics.drawString(this.font, Component.translatable(
                        "hero.emeraldweapons.hud", sheet.level()),
                this.left + 10, this.top + 26, 0xFFFFFFFF, true);
        String right = maxed ? "MAX" : percent + " %";
        graphics.drawString(this.font, right,
                this.left + PANEL_W - 10 - this.font.width(right), this.top + 26,
                0xFFC8C8D4, true);

        int bx = this.left + 10;
        int bw = PANEL_W - 20;
        int by = this.top + 38;
        graphics.fill(bx, by, bx + bw, by + 6, 0xFF1C1C28);
        int fill = maxed ? bw
                : (int) (bw * Math.min(1.0, sheet.xp() / (double) Math.max(1, sheet.needed())));
        if (fill > 0) {
            graphics.fill(bx, by, bx + fill, by + 6, maxed ? VIOLET : GOLD);
        }
        String detail = maxed ? Component.translatable("hero.emeraldweapons.capped").getString()
                : sheet.xp() + " / " + sheet.needed();
        graphics.drawString(this.font, detail, bx, by + 10, DIM, true);
        Component pool = Component.translatable("hero.emeraldweapons.pool", sheet.free());
        graphics.drawString(this.font, pool,
                this.left + PANEL_W - 10 - this.font.width(pool), by + 10,
                sheet.free() > 0 ? VIOLET : DIM, true);

        // --- les quatre voies
        int rowY = this.top + 68;
        for (HeroStat stat : HeroStat.values()) {
            int level = sheet.path(stat.ordinal());
            boolean full = level >= HeroStat.MAX_PATH;
            graphics.fill(this.left + 10, rowY, this.left + PANEL_W - 10, rowY + 38, 0x30FFFFFF);
            graphics.fill(this.left + 10, rowY, this.left + 13, rowY + 38,
                    0xFF000000 | stat.colour().getColor());

            graphics.drawString(this.font, stat.label().copy().withStyle(stat.colour()),
                    this.left + 19, rowY + 4, 0xFFFFFFFF, true);
            String count = level + " / " + HeroStat.MAX_PATH;
            graphics.drawString(this.font, count, this.left + 92, rowY + 4, 0xFFC8C8D4, true);
            // Les niveaux OFFERTS par les runes, en vert, a cote de l'achete.
            // Les separer est indispensable : le prix du niveau suivant se
            // calcule sur ce qu'on a paye, jamais sur le total.
            int gift = sheet.sl(stat.ordinal());
            if (gift > 0) {
                graphics.drawString(this.font, "+" + gift,
                        this.left + 92 + this.font.width(count) + 4, rowY + 4,
                        0xFF78E8AE, true);
            }
            // Le PRIX DU PROCHAIN NIVEAU se lit sur la ligne, sans quoi le
            // joueur ne comprend pas pourquoi ses dix points achetent dix
            // niveaux au debut et deux a la fin.
            String price = full
                    ? Component.translatable("hero.emeraldweapons.full").getString()
                    : Component.translatable("hero.emeraldweapons.next_cost",
                            HeroStat.cost(level)).getString();
            graphics.drawString(this.font, price, this.left + 138, rowY + 4, DIM, true);

            // La ligne du bas dit ce que la voie DONNE, pas ce qu'elle vaut :
            // "niveau 42" n'apprend rien, "+2,3 degats" se compare.
            graphics.drawString(this.font, stat.summary(level + gift),
                    this.left + 19, rowY + 15, 0xFFA0A0B4, true);
            // Et la derniere ce que les PALIERS ont deja donne : c'est la
            // moitie de ce qu'une voie rapporte, et elle serait invisible.
            graphics.drawString(this.font, stat.tierSummary(level + gift),
                    this.left + 19, rowY + 26, 0xFF8C9CC0, true);
            if (!full) {
                String tier = Component.translatable("hero.emeraldweapons.next_tier",
                        HeroStat.toNextTier(level)).getString();
                graphics.drawString(this.font, tier,
                        this.left + PANEL_W - 14 - this.font.width(tier), rowY + 26, DIM, true);
            }
            rowY += 40;
        }

        graphics.drawString(this.font, Component.translatable("hero.emeraldweapons.reset_hint"),
                this.left + 10, this.top + PANEL_H - 14, DIM, true);
    }

    /**
     * Le panneau se pose dans le FOND, pas dans le rendu.
     *
     * C'est le seul endroit qui soit a la fois apres le flou du menu et avant
     * les boutons : le panneau y est net, et les boutons se dessinent par-dessus
     * lui au lieu d'etre recouverts.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.renderBackground(graphics, mouseX, mouseY, partial);
        graphics.fill(this.left, this.top, this.left + PANEL_W, this.top + PANEL_H, 0xFF0A0A12);
        frame(graphics, this.left, this.top, PANEL_W, PANEL_H, 0x80B98CFF);
    }

    private static void frame(GuiGraphics graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y, x + 1, y + h, colour);
        graphics.fill(x + w - 1, y, x + w, y + h, colour);
    }

    /**
     * AUCUN FLOU DERRIERE CET ECRAN.
     *
     * Depuis la 1.20.2, ouvrir un ecran declenche un floutage post-traitement de
     * toute l'image deja rendue -- le monde, mais aussi l'interface de jeu. Ce
     * n'est pas un fond, c'est un filtre applique au tampon, et son rayon suit
     * un reglage du joueur qui peut monter tres haut.
     *
     * La fiche s'ouvre en pleine partie, sans mettre le jeu en pause : flouter
     * le monde pendant qu'on repartit ses points reviendrait a aveugler le
     * joueur au milieu d'un siege. On rend donc la methode muette, et le decor
     * reste net derriere le panneau.
     */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;                 // un siege ne s'interrompt pas pour une fiche
    }
}
