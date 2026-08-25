package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.menu.SocketBenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

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

    public SocketBenchScreen(SocketBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.titleLabelY = 6;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
