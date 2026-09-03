package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.menu.SpecializationAltarMenu;
import com.emerald.specialization.Specialization;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/**
 * L'ecran de l'Autel de Specialisation.
 *
 * Meme famille que la Forge : un cartouche en haut (palier actuel, prochain,
 * chance, plumes portees), un bouton, et l'echelle entiere en dessous. Vingt
 * paliers ne tiennent pas en une colonne de dix pixels par ligne : deux
 * colonnes, +1 a +10 a gauche, +11 a +20 a droite. Le palier suivant est
 * surligne, les paliers gagnes sont grises.
 */
public class SpecializationAltarScreen extends AbstractContainerScreen<SpecializationAltarMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            EmeraldWeaponsMod.MODID, "textures/gui/container/specialization_altar.png");

    private static final int HEADER_Y = 45;
    private static final int LADDER_Y = 55;
    private static final int ROW_H = 10;
    private static final int COL_LEFT = 8;
    private static final int COL_RIGHT = 90;
    private static final int COL_W = 78;
    private static final int VIOLET = 0xFF8B5CF6;
    private static final int GREEN = 0xFF3FA65B;
    private static final int RED = 0xFFD94848;
    private static final int GREY = 0xFF6F6F6F;
    private static final int INK = 0xFF3F3F3F;
    private static final int PALE = 0xFF8A8A8A;

    private Button attempt;

    public SpecializationAltarScreen(SpecializationAltarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 240;
        this.titleLabelY = 6;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        this.attempt = Button.builder(Component.translatable("altar.emeraldweapons.button"),
                        button -> {
                            if (this.minecraft != null && this.minecraft.gameMode != null) {
                                this.minecraft.gameMode.handleInventoryButtonClick(
                                        this.menu.containerId, SpecializationAltarMenu.BUTTON_ATTEMPT);
                            }
                        })
                .bounds(x + 126, y + 15, 42, 16)
                .tooltip(Tooltip.create(Component.translatable("altar.emeraldweapons.button.tip")))
                .build();
        this.addRenderableWidget(this.attempt);
    }

    private int level() {
        return this.minecraft == null || this.minecraft.player == null
                ? 0 : WingsClient.level(this.minecraft.player);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.attempt.active = level() < Specialization.MAX;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        int level = level();
        int feathers = this.minecraft == null || this.minecraft.player == null
                ? 0 : SpecializationAltarMenu.feathers(this.minecraft.player);

        // ---- le cartouche : palier, prochain, chance ; les plumes
        if (level >= Specialization.MAX) {
            graphics.drawString(this.font, Component.translatable("altar.emeraldweapons.max"),
                    8, 19, VIOLET, false);
        } else {
            graphics.drawString(this.font, Component.translatable("altar.emeraldweapons.next",
                    level, level + 1, Specialization.ODDS[level + 1]), 8, 19, INK, false);
        }
        graphics.drawString(this.font, Component.translatable("altar.emeraldweapons.feathers", feathers),
                8, 33, feathers > 0 ? GREEN : RED, false);

        // ---- le verdict, a droite de la ligne des plumes
        int result = this.menu.lastResult();
        if (result != SpecializationAltarMenu.RESULT_NONE) {
            Component verdict = switch (result) {
                case SpecializationAltarMenu.RESULT_WON -> Component.translatable(
                        "altar.emeraldweapons.result.won", this.menu.lastLevel(),
                        Specialization.pointsFor(this.menu.lastLevel()));
                case SpecializationAltarMenu.RESULT_KEPT -> Component.translatable(
                        "altar.emeraldweapons.result.kept");
                case SpecializationAltarMenu.RESULT_MAX -> Component.translatable(
                        "altar.emeraldweapons.max");
                default -> Component.translatable("altar.emeraldweapons.result.missing");
            };
            int color = result == SpecializationAltarMenu.RESULT_WON ? GREEN : RED;
            int w = this.font.width(verdict);
            graphics.drawString(this.font, verdict, this.imageWidth - 8 - w, 33, color, false);
        }

        // ---- l'echelle, en deux colonnes de dix
        for (int col = 0; col < 2; col++) {
            int cx = col == 0 ? COL_LEFT : COL_RIGHT;
            graphics.drawString(this.font, Component.translatable("altar.emeraldweapons.column.tier"),
                    cx, HEADER_Y, GREY, false);
            Component head = Component.translatable("altar.emeraldweapons.column.feathers");
            graphics.drawString(this.font, head, cx + 26, HEADER_Y, GREY, false);
            Component chance = Component.translatable("altar.emeraldweapons.column.chance");
            graphics.drawString(this.font, chance, cx + COL_W - this.font.width(chance), HEADER_Y, GREY, false);
            for (int i = 0; i < 10; i++) {
                int tier = col * 10 + i + 1;
                int ry = LADDER_Y + i * ROW_H;
                boolean done = tier <= level;
                boolean next = tier == level + 1;
                if (next) {
                    graphics.fill(cx - 2, ry - 1, cx + COL_W + 2, ry + ROW_H - 1, 0x508B5CF6);
                }
                int ink = done ? PALE : INK;
                graphics.drawString(this.font, "+" + tier, cx, ry, next ? VIOLET : ink, false);
                int cost = Specialization.COST[tier];
                String plumes = done ? Component.translatable("altar.emeraldweapons.done").getString()
                        : String.format(Locale.ROOT, "%d/%d", feathers, cost);
                int plumesColor = done ? GREEN : next ? (feathers >= cost ? GREEN : RED) : ink;
                graphics.drawString(this.font, plumes, cx + 26, ry, plumesColor, false);
                String odds = Specialization.ODDS[tier] + " %";
                graphics.drawString(this.font, odds, cx + COL_W - this.font.width(odds), ry,
                        next ? VIOLET : ink, false);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
