package com.emerald.client;

import com.emerald.item.Upgrade;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.menu.ArcenciumForgeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * L'ecran de la Forge d'Arcencium.
 *
 * Tout ce que le joueur demandait de savoir tient sur un seul ecran : la piece
 * et son cran, l'echelle entiere des dix crans avec le metal, la quantite, ce
 * qu'il porte et la chance, la ligne du prochain cran en surbrillance, et un
 * bouton. Rien n'est cache dans une infobulle.
 *
 * La mise en page se joue au pixel : 176 x 240, la hauteur maximale qui tient
 * encore a l'echelle 2 sur une fenetre de 480. Le cartouche du haut tient sur
 * deux lignes a gauche du bouton, le verdict a droite de la seconde, et
 * l'echelle occupe dix lignes de dix pixels ; l'etiquette « Inventaire » est
 * retiree, elle mordait la derniere ligne.
 */
public class ArcenciumForgeScreen extends AbstractContainerScreen<ArcenciumForgeMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            EmeraldWeaponsMod.MODID, "textures/gui/container/arcencium_forge.png");

    private static final int LADDER_X = 8;
    private static final int HEADER_Y = 45;
    private static final int LADDER_Y = 55;
    private static final int ROW_H = 10;
    private static final int GOLD = 0xFFFFD36B;
    private static final int GREEN = 0xFF3FA65B;
    private static final int RED = 0xFFD94848;
    private static final int GREY = 0xFF6F6F6F;
    /**
     * L'Heure Doree vue du CLIENT.
     *
     * Le compteur de meteo du serveur n'existe pas ici : l'ecran interroge donc
     * sa propre copie. Sans cela l'atelier afficherait dix pour cent pendant
     * que le serveur en roule vingt-cinq -- et une interface qui ment sur un
     * pari est pire qu'une interface muette.
     */
    private static boolean golden() {
        return com.emerald.client.WeatherClient.current()
                == com.emerald.weather.Weather.HEURE_DOREE;
    }

    private static final int INK = 0xFF3F3F3F;
    private static final int PALE = 0xFF8A8A8A;

    private static final int BODY_W = ArcenciumForgeMenu.BODY_W;

    private Button forge;
    private BagPanelView bagView;
    private boolean swallowRelease;

    public ArcenciumForgeScreen(ArcenciumForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // le corps de la forge, puis le panneau du sac a droite (cahier §111)
        this.imageWidth = BODY_W + 2 + com.emerald.menu.bag.BagPanel.WIDTH;
        this.imageHeight = 240;
        this.titleLabelY = 6;
        this.inventoryLabelY = -1000;                  // pas d'etiquette : la place est a l'echelle
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        this.forge = Button.builder(Component.translatable("forge.emeraldweapons.button"),
                        button -> {
                            if (this.minecraft != null && this.minecraft.gameMode != null) {
                                this.minecraft.gameMode.handleInventoryButtonClick(
                                        this.menu.containerId, ArcenciumForgeMenu.BUTTON_FORGE);
                            }
                        })
                .bounds(x + 126, y + 15, 42, 16)
                .tooltip(Tooltip.create(Component.translatable("forge.emeraldweapons.button.tip")))
                .build();
        this.addRenderableWidget(this.forge);
        this.bagView = new BagPanelView(this.menu);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.bagView.tick();
        ItemStack gear = this.menu.gear();
        this.forge.active = ArcenciumForgeMenu.isGear(gear)
                && Upgrade.of(gear) < com.emerald.item.GearEligibility.upgradeMax(gear);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(TEXTURE, x, y, 0, 0, BODY_W, this.imageHeight);
        this.bagView.renderBg(graphics, x, y, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        this.bagView.renderLabels(graphics, this.font);
        ItemStack gear = this.menu.gear();
        boolean placed = ArcenciumForgeMenu.isGear(gear);
        int level = placed ? Upgrade.of(gear) : -1;
        int stones = this.menu.stonesCarried();          // compte du serveur : le sac compris

        // ---- le cartouche : la piece, le prochain cran et sa chance ; la pierre
        int tx = 30;
        if (!placed) {
            graphics.drawString(this.font, Component.translatable("forge.emeraldweapons.empty"),
                    tx, 19, PALE, false);
        } else if (level >= com.emerald.item.GearEligibility.upgradeMax(gear)
                && com.emerald.item.GearEligibility.isBorrowedGear(gear)) {
            // TROP FAIBLE POUR ALLER PLUS LOIN : on le dit, plutot que de
            // laisser un bouton gris sans explication
            graphics.drawString(this.font, Component.translatable("forge.emeraldweapons.weak",
                    com.emerald.item.GearEligibility.BORROWED_UPGRADE_MAX), tx, 19, INK, false);
        } else if (level >= Upgrade.MAX) {
            graphics.drawString(this.font, Component.translatable("forge.emeraldweapons.max"),
                    tx, 19, GOLD, false);
        } else {
            graphics.drawString(this.font, Component.translatable("forge.emeraldweapons.next",
                    level, level + 1, Upgrade.odds(level, golden())), tx, 19, INK, false);
        }
        graphics.drawString(this.font, Component.translatable("forge.emeraldweapons.stone", stones),
                tx, 33, stones > 0 ? GREEN : RED, false);

        // ---- le verdict du dernier coup, a droite de la ligne de la pierre
        int result = this.menu.lastResult();
        if (result != ArcenciumForgeMenu.RESULT_NONE) {
            Component verdict = switch (result) {
                case ArcenciumForgeMenu.RESULT_WON -> Component.translatable(
                        "forge.emeraldweapons.result.won", this.menu.lastLevel());
                case ArcenciumForgeMenu.RESULT_KEPT -> Component.translatable(
                        "forge.emeraldweapons.result.kept");
                default -> Component.translatable("forge.emeraldweapons.result.missing");
            };
            int color = result == ArcenciumForgeMenu.RESULT_WON ? GREEN : RED;
            int w = this.font.width(verdict);
            graphics.drawString(this.font, verdict, BODY_W - 8 - w, 33, color, false);
        }

        // ---- l'echelle : dix crans, metal, ce qu'on porte / ce qu'il faut, chance
        int hx = LADDER_X;
        graphics.drawString(this.font, Component.translatable("forge.emeraldweapons.column.level"),
                hx, HEADER_Y, GREY, false);
        Component chanceHead = Component.translatable("forge.emeraldweapons.column.chance");
        ArcGui.drawFit(graphics, this.font, Component.translatable("forge.emeraldweapons.column.material"),
                hx + 32, HEADER_Y, GREY, BODY_W - 8 - this.font.width(chanceHead) - 3 - (hx + 32));
        graphics.drawString(this.font, chanceHead, BODY_W - 8 - this.font.width(chanceHead),
                HEADER_Y, GREY, false);
        for (int target = 1; target <= Upgrade.MAX; target++) {
            int ry = LADDER_Y + (target - 1) * ROW_H;
            boolean done = placed && target <= level;
            boolean next = placed && target == level + 1;
            if (next) {
                graphics.fill(hx - 2, ry - 1, BODY_W - 6, ry + ROW_H - 1, 0x50FFD36B);
            }
            Upgrade.Cost cost = Upgrade.cost(target);
            int carried = this.menu.carried(target);
            int ink = done ? PALE : INK;
            graphics.drawString(this.font, "+" + target, hx, ry, next ? GOLD : ink, false);
            // l'icone du metal, a demi-taille pour tenir dans la ligne
            graphics.pose().pushPose();
            graphics.pose().translate(hx + 30, ry - 1, 0);
            graphics.pose().scale(0.5F, 0.5F, 1.0F);
            graphics.renderItem(new ItemStack(cost.material()), 0, 0);
            graphics.pose().popPose();
            String amount = done ? Component.translatable("forge.emeraldweapons.done").getString()
                    : String.format(Locale.ROOT, "%d/%d", carried, cost.amount());
            int amountColor = done ? GREEN : next ? (carried >= cost.amount() ? GREEN : RED) : ink;
            // les reserves comptees avec le sac vont a trois chiffres (« 300/4 ») : la colonne du
            // metal recule de deux pixels, et un nombre plus long se reduit pour tenir
            ArcGui.drawFit(graphics, this.font, amount, hx + 41, ry, amountColor, 30);
            graphics.drawString(this.font, metalName(cost.material()), hx + 74, ry, ink, false);
            String chance = Upgrade.odds(target - 1, golden()) + " %";
            graphics.drawString(this.font, chance, BODY_W - 8 - this.font.width(chance),
                    ry, next ? GOLD : ink, false);
        }
    }

    /** Le metal en un mot : « Lingot de netherite » ne tient pas dans la colonne. */
    private String metalName(Item material) {
        String key = material == Items.IRON_INGOT ? "iron"
                : material == Items.GOLD_INGOT ? "gold"
                : material == Items.DIAMOND ? "diamond"
                : material == Items.NETHERITE_INGOT ? "netherite"
                : material == com.emerald.item.ModItems.ARCENCIUM_INGOT.get() ? "arcencium" : null;
        if (key != null) {
            return Component.translatable("forge.emeraldweapons.metal." + key).getString();
        }
        return this.font.plainSubstrByWidth(material.getDescription().getString(), 54);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        this.bagView.renderTooltip(graphics, this.font, mouseX, mouseY, this.leftPos, this.topPos);
    }

    @Override
    protected void renderSlotContents(GuiGraphics graphics, ItemStack stack, Slot slot, @Nullable String countString) {
        if (!BagPanelView.renderCount(graphics, this.font, stack, slot, countString, this.imageWidth)) {
            super.renderSlotContents(graphics, stack, slot, countString);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.bagView.mouseClicked(mouseX, mouseY, button, this.leftPos, this.topPos)) {
            this.swallowRelease = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.bagView.mouseDragged(mouseX, mouseY, button, this.leftPos, this.topPos)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean dragged = this.bagView.mouseReleased(button);
        if (this.swallowRelease || dragged) {
            this.swallowRelease = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.bagView.mouseScrolled(mouseX, mouseY, scrollY, this.leftPos, this.topPos)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
