package com.emerald.menu;

import com.emerald.game.ArcenciumBackpack;
import com.emerald.haven.furnish.HavenFurnish;
import com.emerald.haven.journey.HavenProgress;
import com.emerald.item.BagAmmo;
import com.emerald.item.CuriosStash;
import com.emerald.item.ModItems;
import com.emerald.item.Stash;
import com.emerald.jak.board.JetBoard;
import com.emerald.jak.board.JetBoardKeeper;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.menu.bag.Bag;
import com.emerald.menu.bag.BagPanel;
import com.emerald.menu.bag.Bags;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Le banc d'essai de l'inventaire d'Arcencium et du panneau du sac (cahier 111), INERTE
 * sans EMERALDWEAPONS_AUTOTEST=ecran.
 *
 * Un cobaye (FakePlayer) porte un vrai Sac d'Arcencium dans le dos ; le banc joue de
 * VRAIS clics sur les menus du serveur (clicked, clickMenuButton) : Maj+clic vers le sac
 * et depuis le sac, clic simple et clic droit sur une case de 256, touche 1, echange
 * refuse, poubelle, grille 2x2, tris, defilement, onglet d'une boite de Shulker, puis la
 * Forge, l'Autel et l'Etabli avec ce qu'il y a dans le sac, les fleches du sac, le
 * JET-Board range dans le sac, les meubles du coffre. A chaque pas, le compte des objets
 * (inventaire, sacs, curseur) ne doit ni monter ni descendre.
 *
 * Sophisticated Backpacks et Curios doivent etre sur le serveur des bancs :
 *     python tools/dev_mods.py --server sophisticatedbackpacks curios
 *     EMERALDWEAPONS_AUTOTEST=ecran ./gradlew runServer
 *     python tools/dev_mods.py --server --clean
 * Rapport dans run-server/ecran_autotest.txt, puis arret du serveur.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class ArcInventoryAutotest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static final boolean ENABLED = "ecran".equalsIgnoreCase(
            Objects.requireNonNullElse(System.getenv("EMERALDWEAPONS_AUTOTEST"), "").trim());
    /** Dix secondes : le demarrage finit de charger ses troncons avant qu'on arrete le serveur. */
    private static final int SETTLE_TICKS = 200;
    /**
     * PAS PENDANT QUE LA VILLE SE POSE. Arrete en pleine pose de Haven (tickets de troncons en
     * cours), le serveur ne finissait plus de decharger ses troncons : deux lancements sur trois
     * restaient bloques a « Saving worlds ». On attend comme les bancs de Haven, une minute au plus.
     */
    private static final int TIMEOUT_TICKS = 20 * 60;

    private static final StringBuilder OUT = new StringBuilder();
    private static boolean done;
    private static int waited;
    private static int passed;
    private static int failed;

    private ArcInventoryAutotest() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ENABLED || done) {
            return;
        }
        MinecraftServer server = event.getServer();
        waited++;
        boolean building = com.emerald.haven.HavenSite.busy() || com.emerald.haven.JakOverlay.pending() > 0;
        if (waited < SETTLE_TICKS || (building && waited < TIMEOUT_TICKS)) {
            return;
        }
        done = true;
        line("autotest de l'inventaire d'Arcencium et du panneau du sac, " + LocalDateTime.now().withNano(0));
        try {
            run(server);
        } catch (RuntimeException e) {
            check("banc sans exception", false, e.toString());
            LOGGER.error("autotest ecran : exception", e);
        }
        end(server);
    }

    // ================================================================ outils

    private static int total(Player player, AbstractContainerMenu menu, Item item) {
        ItemStack carried = menu.getCarried();
        return Stash.count(player, item) + (carried.is(item) ? carried.getCount() : 0);
    }

    private static int inInventory(Player player, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** Le sac tel qu'il est enregistre : relu par une nouvelle enveloppe. */
    private static Bag sac(Player player) {
        List<Bag> bags = Bags.scan(player, BagPanel.MAX_TABS);
        return bags.isEmpty() ? null : bags.get(0);
    }

    private static String show(ItemStack stack) {
        return stack.isEmpty() ? "vide" : stack.getCount() + " " + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static void clearBag(Bag bag) {
        for (int slot = 0; slot < bag.size(); slot++) {
            bag.set(slot, ItemStack.EMPTY);
        }
    }

    // ================================================================ le banc

    private static void run(MinecraftServer server) {
        if (!ModList.get().isLoaded("sophisticatedbackpacks") || !ModList.get().isLoaded("curios")) {
            check("Sophisticated Backpacks et Curios sur le serveur des bancs", false,
                    "absents : python tools/dev_mods.py --server sophisticatedbackpacks curios");
            return;
        }
        ServerLevel level = server.overworld();
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(
                UUID.nameUUIDFromBytes("autotest:ecran".getBytes(StandardCharsets.UTF_8)), "[Ecran]"));
        p.setPos(level.getSharedSpawnPos().getX() + 0.5, level.getSharedSpawnPos().getY() + 1, level.getSharedSpawnPos().getZ() + 0.5);
        Inventory inv = p.getInventory();
        inv.clearContent();

        ItemStack pack = ArcenciumBackpack.make();
        boolean worn = CuriosStash.equip(p, "back", pack);
        Bag bag = sac(p);
        check("le Sac d'Arcencium dans le dos du cobaye, vu comme un sac",
                worn && bag != null, "equipe " + worn + ", sacs " + Bags.scan(p, 4).size());
        if (bag == null) {
            return;
        }
        check("le sac : 120 cases, piles x4 (256 lingots par case)",
                bag.size() == 120 && bag.multiplier() == 4 && bag.limit(0, new ItemStack(Items.IRON_INGOT)) == 256,
                "cases " + bag.size() + ", multiplicateur " + bag.multiplier() + ", limite du fer "
                        + bag.limit(0, new ItemStack(Items.IRON_INGOT)));

        ArcInventoryMenu m = ArcInventoryMenu.create(1, p);
        p.containerMenu = m;
        BagPanel panel = m.bag();
        int first = panel.firstSlot();
        check("le menu : 47 cases fixes, les cases d'artefacts, puis 54 cases du sac",
                m.slots.size() == ArcInventoryMenu.SLOT_CURIOS + m.curioCount() + BagPanel.SIZE
                        && first == ArcInventoryMenu.SLOT_CURIOS + m.curioCount() && m.curioCount() > 0,
                m.slots.size() + " cases, " + m.curioCount() + " d'artefacts, sac a partir de " + first);
        check("le panneau : le sac montre, 14 rangees, un onglet",
                panel.present() && panel.size() == 120 && panel.rows() == 14 && panel.maxFirstRow() == 8
                        && panel.tabs() == 1 && panel.selected() == 0,
                "taille " + panel.size() + ", rangees " + panel.rows() + ", onglets " + panel.tabs());

        shiftInto(p, m, inv);
        clicks(p, m, inv, first);
        rules(p, m, first);
        trashAndCraft(p, m);
        sorting(p, m, inv);
        scrolling(p, m, first);
        tabs(p, m, inv, first);
        closing(p, m);
        stations(p, inv);
        arrows(p);
        board(p);
        furniture(p);
    }

    /** Maj+clic de l'inventaire vers le sac : tout le sac, pas seulement les cases visibles. */
    private static void shiftInto(FakePlayer p, ArcInventoryMenu m, Inventory inv) {
        line("--- Maj+clic vers le sac");
        for (int k = 0; k < 5; k++) {
            inv.setItem(9 + k, new ItemStack(Items.IRON_INGOT, 64));
        }
        int before = total(p, m, Items.IRON_INGOT);
        for (int k = 0; k < 5; k++) {
            m.clicked(ArcInventoryMenu.SLOT_MAIN + k, 0, ClickType.QUICK_MOVE, p);
        }
        Bag bag = sac(p);
        check("320 lingots ranges d'un Maj+clic par pile : 256 puis 64, rien dans l'inventaire",
                before == 320 && inInventory(p, Items.IRON_INGOT) == 0 && bag.get(0).getCount() == 256
                        && bag.get(1).getCount() == 64 && total(p, m, Items.IRON_INGOT) == 320,
                "case 0 " + show(bag.get(0)) + ", case 1 " + show(bag.get(1)) + ", inventaire "
                        + inInventory(p, Items.IRON_INGOT) + ", total " + total(p, m, Items.IRON_INGOT));
        check("la fenetre du panneau montre la case 0 du sac (256)",
                m.getSlot(m.bag().firstSlot()).getItem().getCount() == 256,
                show(m.getSlot(m.bag().firstSlot()).getItem()));

        inv.setItem(9, new ItemStack(Items.ROTTEN_FLESH, 16));
        m.clicked(ArcInventoryMenu.SLOT_MAIN, 0, ClickType.QUICK_MOVE, p);
        check("la chair putride rangee a la main n'est pas detruite par l'amelioration Vide",
                Stash.count(p, Items.ROTTEN_FLESH) == 16 && inInventory(p, Items.ROTTEN_FLESH) == 0,
                "dans le sac " + Stash.count(p, Items.ROTTEN_FLESH));
    }

    /** Les clics sur une case de 256. */
    private static void clicks(FakePlayer p, ArcInventoryMenu m, Inventory inv, int first) {
        line("--- clics sur une case qui tient plus qu'une pile");
        m.clicked(first, 0, ClickType.PICKUP, p);
        check("clic sur une case de 256 : une pile au curseur (64), 192 restent, ecrit dans le sac",
                m.getCarried().getCount() == 64 && sac(p).get(0).getCount() == 192
                        && total(p, m, Items.IRON_INGOT) == 320,
                "curseur " + show(m.getCarried()) + ", case " + show(sac(p).get(0)));
        m.clicked(ArcInventoryMenu.SLOT_MAIN + 20, 0, ClickType.PICKUP, p);
        check("poser la pile dans l'inventaire",
                m.getCarried().isEmpty() && inv.getItem(29).getCount() == 64 && total(p, m, Items.IRON_INGOT) == 320,
                "case 29 " + show(inv.getItem(29)));

        m.clicked(first, 1, ClickType.PICKUP, p);
        check("clic droit sur 192 : la moitie, plafonnee a une pile (64)",
                m.getCarried().getCount() == 64 && sac(p).get(0).getCount() == 128,
                "curseur " + show(m.getCarried()) + ", case " + show(sac(p).get(0)));
        m.clicked(first, 0, ClickType.PICKUP, p);
        check("reposer : la case completee au-dela d'une pile (192), curseur vide",
                m.getCarried().isEmpty() && sac(p).get(0).getCount() == 192 && total(p, m, Items.IRON_INGOT) == 320,
                "case " + show(sac(p).get(0)) + ", curseur " + show(m.getCarried()));

        m.setCarried(new ItemStack(Items.DIRT, 10));
        m.clicked(first, 0, ClickType.PICKUP, p);
        check("de la terre au curseur sur une case de 192 lingots : rien ne s'echange",
                m.getCarried().is(Items.DIRT) && m.getCarried().getCount() == 10 && sac(p).get(0).getCount() == 192,
                "curseur " + show(m.getCarried()) + ", case " + show(sac(p).get(0)));
        m.setCarried(ItemStack.EMPTY);

        inv.setItem(0, ItemStack.EMPTY);
        m.clicked(first, 0, ClickType.SWAP, p);
        check("touche 1 sur une case de 192 : une pile dans la barre, 128 restent",
                inv.getItem(0).getCount() == 64 && sac(p).get(0).getCount() == 128 && total(p, m, Items.IRON_INGOT) == 320,
                "barre " + show(inv.getItem(0)) + ", case " + show(sac(p).get(0)));

        int before = inInventory(p, Items.IRON_INGOT);
        m.clicked(first, 0, ClickType.QUICK_MOVE, p);
        check("Maj+clic sur la case (128) : elle sort pile par pile dans l'inventaire",
                sac(p).get(0).isEmpty() && inInventory(p, Items.IRON_INGOT) == before + 128
                        && total(p, m, Items.IRON_INGOT) == 320,
                "case " + show(sac(p).get(0)) + ", inventaire " + before + " -> " + inInventory(p, Items.IRON_INGOT));
    }

    /** Ce que le sac, la poubelle et la grille refusent. */
    private static void rules(FakePlayer p, ArcInventoryMenu m, int first) {
        line("--- ce qui n'entre pas");
        ItemStack gun = new ItemStack(ModItems.MORPH_GUN.get());
        ItemStack board = new ItemStack(ModItems.JET_BOARD.get());
        Slot bagSlot = m.getSlot(first + 10);
        Slot trash = m.getSlot(ArcInventoryMenu.SLOT_TRASH);
        Slot craft = m.getSlot(ArcInventoryMenu.SLOT_CRAFT);
        check("le Morph Gun et le JET-Board : refuses par le sac, la poubelle et la grille",
                !bagSlot.mayPlace(gun) && !bagSlot.mayPlace(board) && !trash.mayPlace(gun) && !trash.mayPlace(board)
                        && !craft.mayPlace(gun) && !craft.mayPlace(board),
                "sac " + bagSlot.mayPlace(gun) + "/" + bagSlot.mayPlace(board) + ", poubelle " + trash.mayPlace(gun)
                        + "/" + trash.mayPlace(board) + ", grille " + craft.mayPlace(gun) + "/" + craft.mayPlace(board));
        ItemStack other = ArcenciumBackpack.make();
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        check("un second sac refuse dans le sac ; sacs et boites refuses a la poubelle",
                !bagSlot.mayPlace(other) && !trash.mayPlace(other) && !trash.mayPlace(box),
                "sac/sac " + bagSlot.mayPlace(other) + ", poubelle/sac " + trash.mayPlace(other)
                        + ", poubelle/boite " + trash.mayPlace(box));
    }

    private static void trashAndCraft(FakePlayer p, ArcInventoryMenu m) {
        line("--- poubelle et grille 2x2");
        m.setCarried(new ItemStack(Items.COBBLESTONE, 5));
        m.clicked(ArcInventoryMenu.SLOT_TRASH, 0, ClickType.PICKUP, p);
        boolean in = m.trashed().is(Items.COBBLESTONE) && m.getCarried().isEmpty();
        m.setCarried(new ItemStack(Items.DIRT, 3));
        m.clicked(ArcInventoryMenu.SLOT_TRASH, 0, ClickType.PICKUP, p);
        boolean replaced = m.trashed().is(Items.DIRT) && m.getCarried().isEmpty();
        m.clicked(ArcInventoryMenu.SLOT_TRASH, 0, ClickType.PICKUP, p);
        boolean back = m.getCarried().is(Items.DIRT) && m.getCarried().getCount() == 3 && m.trashed().isEmpty();
        m.clicked(ArcInventoryMenu.SLOT_TRASH, 0, ClickType.PICKUP, p);
        check("poubelle : on y pose, un second objet detruit le premier, on reprend avant de fermer",
                in && replaced && back && m.trashed().is(Items.DIRT),
                "posee " + in + ", remplacee " + replaced + ", reprise " + back + ", dedans " + show(m.trashed()));

        m.getSlot(ArcInventoryMenu.SLOT_CRAFT).set(new ItemStack(Items.OAK_LOG));
        ItemStack result = m.getSlot(ArcInventoryMenu.SLOT_RESULT).getItem();
        check("grille 2x2 : une buche donne 4 planches", result.is(Items.OAK_PLANKS) && result.getCount() == 4,
                show(result));
        m.clicked(ArcInventoryMenu.SLOT_RESULT, 0, ClickType.QUICK_MOVE, p);
        check("Maj+clic sur le resultat : 4 planches dans l'inventaire, la grille videe",
                inInventory(p, Items.OAK_PLANKS) == 4 && m.getSlot(ArcInventoryMenu.SLOT_CRAFT).getItem().isEmpty(),
                "planches " + inInventory(p, Items.OAK_PLANKS));
        m.getSlot(ArcInventoryMenu.SLOT_CRAFT).set(new ItemStack(Items.OAK_LOG));      // rendue a la fermeture
    }

    private static void sorting(FakePlayer p, ArcInventoryMenu m, Inventory inv) {
        line("--- tris");
        for (int slot = 9; slot < 36; slot++) {
            inv.setItem(slot, ItemStack.EMPTY);
        }
        inv.setItem(9, new ItemStack(Items.COBBLESTONE, 10));
        inv.setItem(10, new ItemStack(Items.DIRT, 5));
        inv.setItem(11, new ItemStack(Items.COBBLESTONE, 10));
        inv.setItem(13, new ItemStack(Items.COBBLESTONE, 20));
        inv.setItem(20, new ItemStack(ModItems.MORPH_GUN.get()));
        inv.setItem(3, new ItemStack(Items.STONE, 7));
        m.clickMenuButton(p, BagPanel.BUTTON_SORT_INVENTORY);
        check("tri de l'inventaire : terre puis pierre taillee (40 en une pile), le Morph Gun et la barre en place",
                inv.getItem(9).is(Items.DIRT) && inv.getItem(9).getCount() == 5
                        && inv.getItem(10).is(Items.COBBLESTONE) && inv.getItem(10).getCount() == 40
                        && inv.getItem(11).isEmpty() && inv.getItem(20).is(ModItems.MORPH_GUN.get())
                        && inv.getItem(3).is(Items.STONE) && inv.getItem(3).getCount() == 7,
                "9 " + show(inv.getItem(9)) + ", 10 " + show(inv.getItem(10)) + ", 11 " + show(inv.getItem(11))
                        + ", 20 " + show(inv.getItem(20)) + ", barre 3 " + show(inv.getItem(3)));
        inv.setItem(20, ItemStack.EMPTY);

        Bag bag = sac(p);
        bag.set(40, new ItemStack(Items.GOLD_INGOT, 30));
        bag.set(5, new ItemStack(Items.GOLD_INGOT, 20));
        int iron = total(p, m, Items.IRON_INGOT);
        int gold = total(p, m, Items.GOLD_INGOT);
        int flesh = Stash.count(p, Items.ROTTEN_FLESH);
        m.clickMenuButton(p, BagPanel.BUTTON_SORT_BAG);
        check("tri du sac (celui de Sophisticated Backpacks) : rien de perdu",
                total(p, m, Items.IRON_INGOT) == iron && total(p, m, Items.GOLD_INGOT) == gold
                        && Stash.count(p, Items.ROTTEN_FLESH) == flesh && !sac(p).get(0).isEmpty(),
                "fer " + iron + " -> " + total(p, m, Items.IRON_INGOT) + ", or " + gold + " -> "
                        + total(p, m, Items.GOLD_INGOT) + ", case 0 " + show(sac(p).get(0)));
    }

    private static void scrolling(FakePlayer p, ArcInventoryMenu m, int first) {
        line("--- defilement");
        BagPanel panel = m.bag();
        sac(p).set(30, new ItemStack(Items.DIAMOND, 7));
        m.clickMenuButton(p, BagPanel.BUTTON_ROW + 3);
        ItemStack seen = m.getSlot(first + 3).getItem();
        check("rangee 3 : la case 3 du panneau montre la case 30 du sac (7 diamants)",
                panel.firstRow() == 3 && seen.is(Items.DIAMOND) && seen.getCount() == 7,
                "rangee " + panel.firstRow() + ", case " + show(seen));
        m.clicked(first + 3, 0, ClickType.PICKUP, p);
        boolean taken = m.getCarried().is(Items.DIAMOND) && sac(p).get(30).isEmpty();
        m.clicked(first + 3, 0, ClickType.PICKUP, p);
        check("prendre et reposer dans la rangee defilee : la case 30 du sac", taken && sac(p).get(30).getCount() == 7,
                "prise " + taken + ", case 30 " + show(sac(p).get(30)));
        m.clickMenuButton(p, BagPanel.BUTTON_ROW + 99);
        check("defiler trop loin : arrete a la derniere rangee (8), les cases au-dela de 120 cachees",
                panel.firstRow() == 8 && panel.shows(47) && !panel.shows(48) && !m.getSlot(first + 50).isActive(),
                "rangee " + panel.firstRow() + ", montre 47 " + panel.shows(47) + ", 48 " + panel.shows(48));
        m.clickMenuButton(p, BagPanel.BUTTON_ROW);
    }

    private static void tabs(FakePlayer p, ArcInventoryMenu m, Inventory inv, int first) {
        line("--- onglets");
        BagPanel panel = m.bag();
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 2))));
        inv.setItem(8, box);
        for (int i = 0; i < 12; i++) {
            m.broadcastChanges();                       // la liste des sacs est relue toutes les 10 tiques
        }
        check("une boite de Shulker dans la barre : un second onglet", panel.tabs() == 2, "onglets " + panel.tabs());
        m.clickMenuButton(p, BagPanel.BUTTON_TAB + 1);
        ItemStack seen = m.getSlot(first).getItem();
        check("second onglet : les 27 cases de la boite, ses 2 diamants",
                panel.selected() == 1 && panel.size() == 27 && seen.is(Items.DIAMOND) && seen.getCount() == 2,
                "onglet " + panel.selected() + ", taille " + panel.size() + ", case 0 " + show(seen));
        m.clicked(first, 0, ClickType.PICKUP, p);
        ItemContainerContents after = inv.getItem(8).get(DataComponents.CONTAINER);
        boolean emptied = m.getCarried().is(Items.DIAMOND) && after != null && after.copyOne().isEmpty();
        m.clicked(first, 0, ClickType.PICKUP, p);
        ItemContainerContents again = inv.getItem(8).get(DataComponents.CONTAINER);
        check("prendre dans la boite puis reposer : ecrit dans ses donnees",
                emptied && again != null && again.copyOne().is(Items.DIAMOND) && m.getCarried().isEmpty(),
                "videe " + emptied + ", rendue " + (again == null ? "?" : show(again.copyOne())));

        inv.setItem(8, ItemStack.EMPTY);                // la boite montree quitte le joueur
        m.clicked(first + 20, 0, ClickType.PICKUP, p);
        boolean picked = !m.getCarried().isEmpty();
        if (picked) {
            m.clicked(first + 20, 0, ClickType.PICKUP, p);
        }
        check("la boite retiree pendant qu'elle est montree : au clic suivant, le panneau revient au sac",
                panel.selected() == 0 && panel.size() == 120 && panel.tabs() == 1 && m.getCarried().isEmpty(),
                "onglet " + panel.selected() + ", taille " + panel.size() + ", onglets " + panel.tabs());
    }

    private static void closing(FakePlayer p, ArcInventoryMenu m) {
        line("--- fermeture");
        int logs = inInventory(p, Items.OAK_LOG);
        m.removed(p);
        p.containerMenu = p.inventoryMenu;
        check("fermer : la buche de la grille rendue, la terre de la poubelle detruite",
                inInventory(p, Items.OAK_LOG) == logs + 1 && Stash.count(p, Items.DIRT) == 5,
                "buches " + logs + " -> " + inInventory(p, Items.OAK_LOG) + ", terre " + Stash.count(p, Items.DIRT)
                        + " (les 5 de l'inventaire)");
    }

    /** La Forge, l'Autel et l'Etabli avec ce qui est dans le sac. */
    private static void stations(FakePlayer p, Inventory inv) {
        line("--- la Forge, l'Autel et l'Etabli");
        inv.clearContent();
        Bag bag = sac(p);
        clearBag(bag);
        bag.set(0, new ItemStack(ModItems.FORGE_STONE.get(), 3));
        bag.set(1, new ItemStack(Items.IRON_INGOT, 40));
        bag.set(2, new ItemStack(ModItems.ARCENCIUM_FEATHER.get(), 12));
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        boolean gear = ArcenciumForgeMenu.isGear(sword);
        bag.set(3, sword.copy());

        ArcenciumForgeMenu forge = new ArcenciumForgeMenu(2, inv, ContainerLevelAccess.NULL);
        p.containerMenu = forge;
        check("Forge : pierres et fer comptes par le serveur, sac compris (3 et 40)",
                forge.stonesCarried() == 3 && forge.carried(1) == 40,
                "pierres " + forge.stonesCarried() + ", fer " + forge.carried(1));
        int ff = forge.bag().firstSlot();
        forge.clicked(ff + 3, 0, ClickType.QUICK_MOVE, p);
        check("Forge : Maj+clic sur l'epee du sac la pose sur la forge (epee reconnue : " + gear + ")",
                gear && forge.gear().is(Items.DIAMOND_SWORD) && sac(p).get(3).isEmpty(),
                "forge " + show(forge.gear()) + ", case du sac " + show(sac(p).get(3)));
        if (gear) {
            int level = com.emerald.item.Upgrade.of(forge.gear());
            forge.clickMenuButton(p, ArcenciumForgeMenu.BUTTON_FORGE);
            int stones = Stash.count(p, ModItems.FORGE_STONE.get());
            int iron = Stash.count(p, Items.IRON_INGOT);
            int result = forge.lastResult();
            boolean paid = stones == 2 && (result == ArcenciumForgeMenu.RESULT_WON ? iron == 36 : iron == 40);
            check("Forge : une tentative payee depuis le sac (la pierre, et le fer si elle reussit)",
                    paid && (result == ArcenciumForgeMenu.RESULT_WON || result == ArcenciumForgeMenu.RESULT_KEPT),
                    "+" + level + " -> +" + com.emerald.item.Upgrade.of(forge.gear()) + ", verdict " + result
                            + ", pierres " + stones + ", fer " + iron);
        }
        forge.removed(p);
        p.containerMenu = p.inventoryMenu;

        SpecializationAltarMenu altar = new SpecializationAltarMenu(3, inv, ContainerLevelAccess.NULL);
        check("Autel : les plumes comptees par le serveur, sac compris (12)",
                altar.feathersCarried() == 12, "plumes " + altar.feathersCarried());

        SocketBenchMenu socket = new SocketBenchMenu(4, inv, ContainerLevelAccess.NULL);
        p.containerMenu = socket;
        int sf = socket.bag().firstSlot();
        int stoneSlot = -1;
        Bag now = sac(p);
        for (int slot = 0; slot < BagPanel.SIZE; slot++) {
            if (now.get(slot).is(ModItems.FORGE_STONE.get())) {
                stoneSlot = slot;
                break;
            }
        }
        if (stoneSlot >= 0) {
            socket.clicked(sf + stoneSlot, 0, ClickType.QUICK_MOVE, p);
        }
        ItemStack placed = socket.getSlot(SocketBenchMenu.SLOT_ARTIFACT).getItem();
        check("Etabli : Maj+clic sur les pierres de forge du sac, posees dans la case d'artefact",
                placed.is(ModItems.FORGE_STONE.get()), "case " + show(placed) + " (depuis la case " + stoneSlot + ")");
        socket.removed(p);
        p.containerMenu = p.inventoryMenu;
    }

    private static void arrows(FakePlayer p) {
        line("--- les fleches du sac");
        Bag bag = sac(p);
        clearBag(bag);
        bag.set(7, new ItemStack(Items.ARROW, 10));
        for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
            if (p.getInventory().getItem(slot).is(Items.ARROW)) {
                p.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        ItemStack got = p.getProjectile(new ItemStack(Items.BOW));
        check("un arc sans fleche dans l'inventaire tire dans le sac", got.is(Items.ARROW), show(got));
        if (got.is(Items.ARROW)) {
            got.shrink(1);                              // ce que fait l'arc au tir
            BagAmmo.settle();
        }
        check("la fleche tiree est retiree du sac (10 -> 9)", Stash.count(p, Items.ARROW) == 9,
                "fleches " + Stash.count(p, Items.ARROW));
    }

    private static void board(FakePlayer p) {
        line("--- le JET-Board range dans le sac");
        UUID id = p.getUUID();
        boolean owned = JetBoard.owns(id);
        if (!owned) {
            HavenProgress.addBonus(id, JetBoard.OWNED, 1);
        }
        try {
            Bag bag = sac(p);
            clearBag(bag);
            bag.set(9, new ItemStack(ModItems.JET_BOARD.get()));
            JetBoardKeeper.guard(p);
            int boards = Stash.count(p, ModItems.JET_BOARD.get());
            for (ItemStack worn : CuriosStash.worn(p)) {
                if (worn.is(ModItems.JET_BOARD.get())) {
                    boards += worn.getCount();
                }
            }
            check("le gardien voit la planche dans le sac : il n'en donne pas une seconde", boards == 1,
                    "planches " + boards);
        } finally {
            if (!owned) {
                HavenProgress.takeBonus(id, JetBoard.OWNED);
            }
        }
    }

    private static void furniture(FakePlayer p) {
        line("--- les meubles du coffre");
        Bag bag = sac(p);
        clearBag(bag);
        bag.set(11, HavenFurnish.stack(Items.OAK_PLANKS));
        int removed = HavenFurnish.strip(p);
        check("quitter Haven : un meuble du coffre range dans le sac est retire aussi",
                removed >= 1 && !HavenFurnish.marked(sac(p).get(11)), "retires " + removed + ", case " + show(sac(p).get(11)));
        clearBag(sac(p));
    }

    // ================================================================ rapport

    private static void end(MinecraftServer server) {
        line("RESULTAT : " + passed + " OK, " + failed + " KO");
        Path file = server.getServerDirectory().resolve("ecran_autotest.txt");
        try {
            Files.writeString(file, OUT.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("autotest ecran : rapport impossible a ecrire dans {}", file, e);
        }
        LOGGER.info("autotest ecran : {} OK, {} KO, rapport dans {} ; arret du serveur",
                passed, failed, file.toAbsolutePath());
        server.halt(false);
    }

    private static void line(String text) {
        OUT.append(text).append('\n');
        LOGGER.info("autotest ecran : {}", text);
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        line((ok ? "OK  " : "KO  ") + what + " -- " + detail);
    }
}
