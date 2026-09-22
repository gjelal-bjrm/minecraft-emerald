package com.emerald.client;

import com.emerald.network.HavenShopBuyPayload;
import com.emerald.network.HavenShopPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * La boutique de Tess (lot 3, cahier §86), sur le modele de la fiche du Heros (HeroScreen) :
 * un panneau opaque, trois onglets -- les armes, l'eco illimite, les bonus du Defi -- et une
 * ligne par article : sa couleur, son nom, ce qu'il en a deja, son prix, et « Acheter ».
 * Survoler une ligne dit ce que fait l'article, ou ce qui le ferme.
 *
 * ELLE NE DECIDE DE RIEN : un clic est une demande ; le serveur revalide (pres de Tess, le
 * solde, l'ordre des armes) et renvoie la boutique, que l'ecran montre telle quelle.
 */
public class HavenShopScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 250;
    private static final int ROW_H = 16;
    private static final int ROWS_TOP = 52;

    private static final int GOLD = 0xFFFFD24A;
    private static final int PINK = 0xFFF08CE0;
    private static final int DIM = 0xFF8A8A9C;
    private static final int GREEN = 0xFF78E8AE;

    private static int section;

    private final List<Button> tabs = new ArrayList<>();
    private final List<Button> buys = new ArrayList<>();
    private final List<HavenShopPayload.Article> shown = new ArrayList<>();

    private int left;
    private int top;

    public HavenShopScreen() {
        super(Component.translatable("game.emeraldweapons.haven.boutique.titre"));
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;
        this.tabs.clear();
        String[] names = {"armes", "munitions", "defi"};
        int tabW = (PANEL_W - 20 - 8) / 3;
        for (int i = 0; i < names.length; i++) {
            int which = i;
            Button tab = Button.builder(Component.translatable("game.emeraldweapons.haven.boutique.onglet." + names[i]),
                    b -> {
                        section = which;
                        this.rebuild();
                    }).bounds(this.left + 10 + i * (tabW + 4), this.top + 26, tabW, 18).build();
            this.tabs.add(tab);
            this.addRenderableWidget(tab);
        }
        rebuild();
    }

    /** Les boutons « Acheter » de l'onglet ouvert, d'apres la derniere boutique recue. */
    private void rebuild() {
        for (Button button : this.buys) {
            this.removeWidget(button);
        }
        this.buys.clear();
        this.shown.clear();
        HavenShopPayload shop = HavenShopClient.last();
        if (shop == null) {
            return;
        }
        int y = this.top + ROWS_TOP;
        for (HavenShopPayload.Article article : shop.articles()) {
            if (article.section() != section) {
                continue;
            }
            this.shown.add(article);
            String id = article.id();
            Button buy = Button.builder(Component.translatable("game.emeraldweapons.haven.boutique.acheter"),
                            b -> PacketDistributor.sendToServer(new HavenShopBuyPayload(id)))
                    .bounds(this.left + PANEL_W - 10 - 52, y, 52, ROW_H - 2).build();
            this.buys.add(buy);
            this.addRenderableWidget(buy);
            y += ROW_H;
        }
    }

    private void refresh() {
        HavenShopPayload shop = HavenShopClient.last();
        for (int i = 0; i < this.tabs.size(); i++) {
            this.tabs.get(i).active = i != section;
        }
        if (shop == null) {
            return;
        }
        // la boutique a change (un achat) : les lignes suivent
        List<HavenShopPayload.Article> now = new ArrayList<>();
        for (HavenShopPayload.Article article : shop.articles()) {
            if (article.section() == section) {
                now.add(article);
            }
        }
        if (!now.equals(this.shown)) {
            rebuild();
        }
        for (int i = 0; i < this.buys.size() && i < this.shown.size(); i++) {
            HavenShopPayload.Article article = this.shown.get(i);
            Button buy = this.buys.get(i);
            buy.visible = article.state() == HavenShopPayload.BUYABLE;
            buy.active = shop.orbs() >= article.price();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        refresh();
        super.render(graphics, mouseX, mouseY, partial);
        graphics.drawString(this.font, this.title, this.left + 10, this.top + 9, PINK, true);
        HavenShopPayload shop = HavenShopClient.last();
        if (shop == null) {
            return;
        }
        Component purse = Component.translatable("game.emeraldweapons.haven.boutique.solde.court", shop.orbs());
        graphics.drawString(this.font, purse, this.left + PANEL_W - 10 - this.font.width(purse), this.top + 9, GOLD, true);

        int y = this.top + ROWS_TOP;
        HavenShopPayload.Article hovered = null;
        for (HavenShopPayload.Article article : this.shown) {
            boolean over = mouseX >= this.left + 10 && mouseX < this.left + PANEL_W - 10 && mouseY >= y && mouseY < y + ROW_H - 2;
            graphics.fill(this.left + 10, y, this.left + PANEL_W - 10, y + ROW_H - 2, over ? 0x40FFFFFF : 0x24FFFFFF);
            graphics.fill(this.left + 10, y, this.left + 13, y + ROW_H - 2, article.color() | 0xFF000000);
            int nameColor = article.state() == HavenShopPayload.LOCKED ? DIM : 0xFFFFFFFF;
            graphics.drawString(this.font, article.name(), this.left + 18, y + 3, nameColor, true);
            if (article.count() > 0) {
                String count = "×" + article.count();
                graphics.drawString(this.font, count, this.left + 18 + this.font.width(article.name()) + 5, y + 3, GREEN, true);
            }
            int priceRight = this.left + PANEL_W - 10 - 52 - 6;
            if (article.state() == HavenShopPayload.OWNED) {
                Component owned = Component.translatable("game.emeraldweapons.haven.boutique.possede.court");
                graphics.drawString(this.font, owned, this.left + PANEL_W - 14 - this.font.width(owned), y + 3, GREEN, true);
            } else {
                String price = article.price() + " ◆";
                graphics.drawString(this.font, price, priceRight - this.font.width(price), y + 3,
                        shop.orbs() >= article.price() ? GOLD : 0xFFB05050, true);
                if (article.state() == HavenShopPayload.LOCKED) {
                    Component closed = Component.translatable("game.emeraldweapons.haven.boutique.ferme.court");
                    graphics.drawString(this.font, closed, this.left + PANEL_W - 14 - this.font.width(closed), y + 3, DIM, true);
                }
            }
            if (over) {
                hovered = article;
            }
            y += ROW_H;
        }
        graphics.drawString(this.font, Component.translatable("game.emeraldweapons.haven.boutique.aide"),
                this.left + 10, this.top + PANEL_H - 14, DIM, true);
        if (hovered != null) {
            graphics.renderTooltip(this.font, this.font.split(hovered.info(), 200), mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.renderBackground(graphics, mouseX, mouseY, partial);
        graphics.fill(this.left, this.top, this.left + PANEL_W, this.top + PANEL_H, 0xFF0A0A12);
        int frame = 0x80F08CE0;
        graphics.fill(this.left, this.top, this.left + PANEL_W, this.top + 1, frame);
        graphics.fill(this.left, this.top + PANEL_H - 1, this.left + PANEL_W, this.top + PANEL_H, frame);
        graphics.fill(this.left, this.top, this.left + 1, this.top + PANEL_H, frame);
        graphics.fill(this.left + PANEL_W - 1, this.top, this.left + PANEL_W, this.top + PANEL_H, frame);
    }

    /** Le monde reste net derriere le panneau, comme la fiche du Heros. */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
