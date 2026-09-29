package com.emerald.client;

import com.emerald.menu.ArcInventoryMenu;
import com.emerald.menu.curio.CurioRef;
import com.emerald.menu.curio.CurioWindowSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

/**
 * L'ecran de l'inventaire d'Arcencium (cahier §111).
 *
 * De gauche a droite : la fenetre des artefacts (deux colonnes de douze, qui defilent), le
 * corps -- la texture de l'inventaire du jeu, telle quelle, avec le personnage --, le
 * panneau du sac ; le corps et le sac sont centres en hauteur sur les artefacts. Sous la grille d'artisanat, trois choses : le livre ouvre l'inventaire classique
 * (livre de recettes, effets), la poubelle, et le tri de l'inventaire.
 *
 * LE LIVRE EST NOTRE BOUTON, pas celui du livre de recettes du jeu : pose sur cet ecran, le
 * bouton du jeu (ImageButton) ne s'affichait pas dans le modpack (photo du 29 sept.).
 */
public class ArcInventoryScreen extends AbstractContainerScreen<ArcInventoryMenu> {

    private static final ResourceLocation INVENTORY =
            ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final int CLASSIC_X = 104;
    private static final int SORT_X = 152;
    private static final int ROW_Y = 61;

    private BagPanelView bagView;
    @Nullable
    private CurioPanelView curioView;
    private Button classic;
    private Button sort;
    private boolean swallowRelease;

    public ArcInventoryScreen(ArcInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = menu.width;
        this.imageHeight = menu.height;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = Math.max(0, this.leftPos);
        this.topPos = Math.max(0, this.topPos);
        this.bagView = new BagPanelView(this.menu);
        this.curioView = this.menu.curios() == null ? null : new CurioPanelView(this.menu, this.menu.curios());
        int bodyX = this.leftPos + this.menu.mainX;
        int bodyY = this.topPos + this.menu.bodyY;
        this.classic = Button.builder(Component.empty(), button -> ArcInventoryClient.openClassic())
                .bounds(bodyX + CLASSIC_X, bodyY + ROW_Y, 20, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.emeraldweapons.inventory.classic")))
                .build();
        this.addRenderableWidget(this.classic);
        this.sort = Button.builder(Component.empty(), button -> BagPanelView.sortInventory(this.menu))
                .bounds(bodyX + SORT_X, bodyY + ROW_Y, 18, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.emeraldweapons.inventory.sort")))
                .build();
        this.addRenderableWidget(this.sort);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.bagView.tick();
        if (this.curioView != null) {
            this.curioView.tick();
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        if (this.curioView != null) {
            this.curioView.renderBg(g, left, top, mouseX, mouseY);
        }
        int bodyX = left + this.menu.mainX;
        int bodyY = top + this.menu.bodyY;
        g.blit(INVENTORY, bodyX, bodyY, 0, 0, ArcInventoryMenu.BODY_W, ArcInventoryMenu.BODY_H);
        int tx = bodyX + ArcInventoryMenu.TRASH_X - 1;
        int ty = bodyY + ArcInventoryMenu.TRASH_Y - 1;
        ArcGui.slot(g, tx, ty);
        if (this.menu.trashed().isEmpty()) {
            ArcGui.trashIcon(g, tx + 1, ty + 1, 0xFF6B6B6B);
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, bodyX + 26, bodyY + 8, bodyX + 75, bodyY + 78,
                    30, 0.0625F, mouseX, mouseY, this.minecraft.player);
        }
        this.bagView.renderBg(g, left, top, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int bodyX = this.menu.mainX;
        // comme l'inventaire du jeu : le titre de la grille seul (la case du personnage occupe
        // la place de l'etiquette « Inventaire »)
        g.drawString(this.font, Component.translatable("container.crafting"), bodyX + 97, this.menu.bodyY + 6,
                0x404040, false);
        this.bagView.renderLabels(g, this.font);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ArcGui.sortIcon(g, this.sort.getX(), this.sort.getY(), this.sort.isHoveredOrFocused() ? 0xFFFFFFA0 : 0xFFE0E0E0);
        g.renderItem(new ItemStack(Items.KNOWLEDGE_BOOK), this.classic.getX() + 2, this.classic.getY() + 1);
        this.renderTooltip(g, mouseX, mouseY);
        Slot hovered = this.hoveredSlot;
        if (hovered != null && !hovered.hasItem() && this.menu.getCarried().isEmpty()) {
            if (hovered.index == ArcInventoryMenu.SLOT_TRASH) {
                g.renderTooltip(this.font, this.font.split(Component.translatable("gui.emeraldweapons.trash"), 180),
                        mouseX, mouseY);
            } else if (hovered instanceof CurioWindowSlot window && this.curioView != null) {
                CurioRef ref = this.curioView.refAt(window.windowSlot());
                if (ref != null) {
                    g.renderTooltip(this.font, Component.translatable("curios.identifier." + ref.identifier()),
                            mouseX, mouseY);
                }
            }
        }
        this.bagView.renderTooltip(g, this.font, mouseX, mouseY, this.leftPos, this.topPos);
    }

    @Override
    protected void renderSlotContents(GuiGraphics graphics, ItemStack stack, Slot slot, @Nullable String countString) {
        if (!BagPanelView.renderCount(graphics, this.font, stack, slot, countString, this.imageWidth)) {
            super.renderSlotContents(graphics, stack, slot, countString);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.bagView.mouseClicked(mouseX, mouseY, button, this.leftPos, this.topPos)
                || this.curioView != null && this.curioView.mouseClicked(mouseX, mouseY, button, this.leftPos, this.topPos)) {
            this.swallowRelease = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.bagView.mouseDragged(mouseX, mouseY, button, this.leftPos, this.topPos)
                || this.curioView != null && this.curioView.mouseDragged(mouseX, mouseY, this.leftPos, this.topPos)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean dragged = this.bagView.mouseReleased(button);
        dragged |= this.curioView != null && this.curioView.mouseReleased(button);
        if (this.swallowRelease || dragged) {
            this.swallowRelease = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.bagView.mouseScrolled(mouseX, mouseY, scrollY, this.leftPos, this.topPos)
                || this.curioView != null && this.curioView.mouseScrolled(mouseX, mouseY, scrollY, this.leftPos, this.topPos)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
