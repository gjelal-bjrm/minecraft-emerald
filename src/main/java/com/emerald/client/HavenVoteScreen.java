package com.emerald.client;

import com.emerald.menu.HavenVoteMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * L'ecran de l'urne du QG.
 *
 * Deux boutons, puis ce qui empeche de partir : le decompte, qui est dans le
 * bar, qui n'a pas vote. Le joueur doit voir en un coup d'oeil QUI il attend,
 * pas seulement combien.
 *
 * APPARENCE PROVISOIRE : un panneau dessine, sans texture, en attendant celle
 * que le joueur choisira.
 */
public class HavenVoteScreen extends AbstractContainerScreen<HavenVoteMenu> {

    private static final int ROW_H = 11;
    private static final int LIST_Y = 100;
    private static final int GOLD = 0xFFFFC24A;
    private static final int GREEN = 0xFF78E8AE;
    private static final int RED = 0xFFFF6B6B;
    private static final int WHITE = 0xFFF0F0F0;
    private static final int GREY = 0xFF9A9A9A;

    private Button defi;
    private Button libre;

    public HavenVoteScreen(HavenVoteMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        int rows = Math.min(menu.roster().size(), HavenVoteMenu.MAX_ROSTER)
                + (menu.roster().size() > HavenVoteMenu.MAX_ROSTER ? 1 : 0);
        this.imageWidth = 220;
        this.imageHeight = LIST_Y + Math.max(rows, 1) * ROW_H + 8;
        this.titleLabelX = 8;
        this.titleLabelY = 7;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        this.defi = Button.builder(Component.translatable("gui.emeraldweapons.haven_vote.defi"),
                        button -> click(HavenVoteMenu.BUTTON_DEFI))
                .bounds(this.leftPos + 8, this.topPos + 20, 98, 20)
                .tooltip(Tooltip.create(Component.translatable("game.emeraldweapons.mode.defi.hover")))
                .build();
        this.libre = Button.builder(Component.translatable("gui.emeraldweapons.haven_vote.libre"),
                        button -> click(HavenVoteMenu.BUTTON_LIBRE))
                .bounds(this.leftPos + 114, this.topPos + 20, 98, 20)
                .tooltip(Tooltip.create(Component.translatable("game.emeraldweapons.mode.libre.hover")))
                .build();
        this.addRenderableWidget(this.defi);
        this.addRenderableWidget(this.libre);
    }

    private void click(int button) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int mine = this.menu.value(HavenVoteMenu.DATA_MINE);
        boolean eligible = this.menu.value(HavenVoteMenu.DATA_ELIGIBLE) != 0;
        this.defi.setMessage(label("gui.emeraldweapons.haven_vote.defi", mine == HavenVoteMenu.VOTE_DEFI));
        this.libre.setMessage(label("gui.emeraldweapons.haven_vote.libre", mine == HavenVoteMenu.VOTE_LIBRE));
        this.defi.active = eligible && mine != HavenVoteMenu.VOTE_DEFI;
        this.libre.active = eligible && mine != HavenVoteMenu.VOTE_LIBRE;
    }

    /** Le bouton du vote en cours est encadre, et grise : le recliquer ne changerait rien. */
    private static Component label(String key, boolean chosen) {
        Component name = Component.translatable(key);
        return chosen ? Component.literal("> ").append(name).append(" <") : name;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF3A2418);
        graphics.fill(x + 2, y + 2, x + this.imageWidth - 2, y + this.imageHeight - 2, 0xF0141014);
        graphics.fill(x + 6, y + LIST_Y - 5, x + this.imageWidth - 6, y + LIST_Y - 4, 0xFF5A4030);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, GOLD, false);
        int defi = this.menu.value(HavenVoteMenu.DATA_DEFI);
        int libre = this.menu.value(HavenVoteMenu.DATA_LIBRE);
        int electors = this.menu.value(HavenVoteMenu.DATA_ELECTORS);
        int inBar = this.menu.value(HavenVoteMenu.DATA_IN_BAR);
        int countdown = this.menu.value(HavenVoteMenu.DATA_COUNTDOWN);
        int mine = this.menu.value(HavenVoteMenu.DATA_MINE);
        boolean eligible = this.menu.value(HavenVoteMenu.DATA_ELIGIBLE) != 0;

        graphics.drawString(this.font, Component.translatable("gui.emeraldweapons.haven_vote.tally",
                defi, libre, electors), 8, 46, WHITE, false);
        graphics.drawString(this.font, Component.translatable("gui.emeraldweapons.haven_vote.bar",
                inBar, electors), 8, 58, inBar == electors ? GREEN : RED, false);
        Component self = !eligible
                ? Component.translatable("gui.emeraldweapons.haven_vote.chantier")
                : mine == HavenVoteMenu.VOTE_NONE
                ? Component.translatable("gui.emeraldweapons.haven_vote.mine.none")
                : Component.translatable("gui.emeraldweapons.haven_vote.mine", voteName(mine));
        graphics.drawString(this.font, self, 8, 70, eligible ? WHITE : GREY, false);

        Component status;
        int statusColor;
        if (countdown > 0) {
            status = Component.translatable("gui.emeraldweapons.haven_vote.countdown", (countdown + 19) / 20);
            statusColor = GOLD;
        } else if (electors > 0 && (defi == electors || libre == electors)) {
            status = Component.translatable("gui.emeraldweapons.haven_vote.waiting.bar");
            statusColor = RED;
        } else {
            status = Component.translatable("gui.emeraldweapons.haven_vote.waiting.votes");
            statusColor = GREY;
        }
        graphics.drawString(this.font, status, 8, 84, statusColor, false);

        int shown = Math.min(this.menu.roster().size(), HavenVoteMenu.MAX_ROSTER);
        for (int i = 0; i < shown; i++) {
            int y = LIST_Y + i * ROW_H;
            int entry = this.menu.value(HavenVoteMenu.DATA_ROSTER + i);
            String name = i < this.menu.names().size() ? this.menu.names().get(i) : "?";
            boolean gone = (entry & HavenVoteMenu.ENTRY_GONE) != 0;
            graphics.drawString(this.font, name, 8, y, gone ? GREY : WHITE, false);
            if (gone) {
                continue;
            }
            int vote = entry & HavenVoteMenu.ENTRY_VOTE_MASK;
            Component right = vote == HavenVoteMenu.VOTE_NONE
                    ? Component.translatable("gui.emeraldweapons.haven_vote.entry.none")
                    : voteName(vote);
            int rightColor = vote == HavenVoteMenu.VOTE_DEFI ? GOLD : vote == HavenVoteMenu.VOTE_LIBRE ? GREEN : GREY;
            if ((entry & HavenVoteMenu.ENTRY_IN_BAR) == 0) {
                Component away = Component.translatable("gui.emeraldweapons.haven_vote.entry.away");
                int awayX = this.imageWidth - 8 - this.font.width(away);
                graphics.drawString(this.font, away, awayX, y, RED, false);
                graphics.drawString(this.font, right, awayX - 6 - this.font.width(right), y, rightColor, false);
            } else {
                graphics.drawString(this.font, right, this.imageWidth - 8 - this.font.width(right), y,
                        rightColor, false);
            }
        }
        if (this.menu.roster().size() > HavenVoteMenu.MAX_ROSTER) {
            graphics.drawString(this.font, Component.translatable("gui.emeraldweapons.haven_vote.more",
                    this.menu.roster().size() - HavenVoteMenu.MAX_ROSTER), 8, LIST_Y + shown * ROW_H, GREY, false);
        } else if (shown == 0) {
            graphics.drawString(this.font, Component.translatable("gui.emeraldweapons.haven_vote.empty"),
                    8, LIST_Y, GREY, false);
        }
    }

    private static Component voteName(int vote) {
        return Component.translatable(vote == HavenVoteMenu.VOTE_LIBRE
                ? "gui.emeraldweapons.haven_vote.libre" : "gui.emeraldweapons.haven_vote.defi");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
