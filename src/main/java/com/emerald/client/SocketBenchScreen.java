package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.menu.SocketBenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Ecran de l'Etabli de Sertissage.
 *
 * La disposition reprend celle de l'enclume : deux entrees et un resultat, sur
 * la meme grille. Un joueur la reconnait sans rien avoir a apprendre, ce qui
 * vaut mieux qu'une mise en page originale mais deroutante.
 */
public class SocketBenchScreen extends AbstractContainerScreen<SocketBenchMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            EmeraldWeaponsMod.MODID, "textures/gui/container/socket_bench.png");

    private BagPanelView bagView;
    private boolean swallowRelease;

    public SocketBenchScreen(SocketBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // le corps de l'etabli, puis le panneau du sac a droite (cahier §111)
        this.imageWidth = SocketBenchMenu.BODY_W + 2 + com.emerald.menu.bag.BagPanel.WIDTH;
        this.titleLabelY = 6;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.bagView = new BagPanelView(this.menu);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.bagView.tick();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        this.bagView.renderLabels(graphics, this.font);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(TEXTURE, x, y, 0, 0, SocketBenchMenu.BODY_W, this.imageHeight);
        this.bagView.renderBg(graphics, x, y, mouseX, mouseY);
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
