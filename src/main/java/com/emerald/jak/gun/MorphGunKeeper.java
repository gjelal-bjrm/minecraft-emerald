package com.emerald.jak.gun;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenRules;
import com.emerald.item.ModItems;
import com.emerald.item.Stash;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Le gardien du Morph Gun : l'arme est donnee dans Haven pendant le lobby, et
 * n'existe nulle part ailleurs.
 *
 * L'INVARIANT, controle a l'arrivee, a la reapparition, a la reconnexion, puis
 * toutes les {@value #GUARD_TICKS} tiques pour chaque joueur en ligne :
 *  - dans Haven, lobby ouvert, vrai joueur hors chantier et vivant : EXACTEMENT
 *    UNE arme -- dans l'inventaire, sous un curseur ou rangee cote serveur --,
 *    comptee PAR IDENTITE, compartiment par compartiment (Stash.everything
 *    recompte la case choisie et la main gauche : l'additionner a l'inventaire
 *    compterait la meme pile deux ou trois fois) ;
 *  - partout ailleurs : ZERO.
 *
 * « Les joueurs spawnent dans une partie avec les armes mais ils perdent tout
 * des qu'ils choisissent le mode libre ou defi. » Le choix effectif est le
 * DEPART (un vote reste modifiable jusqu'au compte a rebours). Tous les departs
 * passent par une teleportation hors de la dimension -- vote unanime, skip, la
 * Lame, /arcencium start ou open (closeIfStarted), le retardataire, /haven back,
 * les teleportations des autres mods -- donc par ServerPlayer.changeDimension,
 * qui publie EntityTravelToDimensionEvent AVANT tout (ServerPlayer.java:888) :
 * l'arme est retiree la, quel que soit le kit. Sans toucher HavenArrival ni
 * HavenRules : ce chantier ne s'y branche que par ses propres abonnes.
 *
 * LES COUCHES DE LA CRITIQUE DU PLAN, chacune ici :
 *  - jet depuis un ecran (clic hors fenetre, jet d'une case) : Player.drop puis
 *    ItemTossEvent, sans onDroppedByPlayer. On annule et on REMET LA MEME PILE :
 *    pas de recharge gratuite ;
 *  - deconnexion avec l'arme sous le curseur : PlayerList.remove sauvegarde
 *    (:371) avant que Player.remove ne lache le curseur ; PlayerLoggedOutEvent
 *    passe avant la sauvegarde (:368) et remet l'arme dans l'inventaire ;
 *  - teleportation menu ouvert : changeDimension ne ferme rien, on vide les deux
 *    curseurs (containerMenu et inventoryMenu) ;
 *  - grille d'artisanat 2x2 de l'ecran d'inventaire (inventoryMenu.getCraftSlots) :
 *    comptee et retiree comme l'inventaire. Sans cela, l'arme posee dans la grille
 *    partait dans l'autre dimension (la grille ne se vide qu'a la fermeture de
 *    l'ecran) et le gardien, qui ne la voyait pas, en donnait une seconde ;
 *  - conteneurs de n'importe quel mod : pendant TOUT menu serveur etranger
 *    (PlayerContainerEvent.Open), l'arme est rangee cote serveur, hors de
 *    l'inventaire et du curseur ; rendue a la fermeture ou a la deconnexion. Il
 *    ne reste rien a deposer, pas meme dans un terminal sans case ;
 *  - sac porte (Sophisticated Backpacks ignore canFitInsideContainerItems) et
 *    coffre de l'Ender : le gardien en ressort l'arme, le depart les vide ;
 *  - mort : retrait en HIGHEST sur LivingDeathEvent (avant la chute des objets),
 *    filtre des drops en HIGHEST (avant le HIGH de Tombstone), arme rendue a la
 *    reapparition avec ses formes et des reserves pleines ; une mort annulee
 *    par un autre mod est rattrapee par le gardien, reserves intactes ;
 *  - inventaire plein : aucun objet du joueur n'est deplace ; premiere case
 *    libre, sinon main gauche vide, sinon l'arme reste rangee, un message, et
 *    un nouvel essai a chaque passage du gardien ;
 *  - lobby ferme (PARTI, ABSENTE, pose echouee) avec un joueur reste dans la
 *    ville : le gardien retire l'arme ; lobby rouvert : une pile d'un autre
 *    numero (MorphGunState) est remplacee par une neuve ;
 *  - toute pile hors de Haven : retiree cote serveur (gardien, inventoryTick) ;
 *  - l'operateur en chantier ne recoit rien.
 *
 * LES FAKEPLAYER ne sont pas de vrais joueurs : rien ne leur est donne, sauf aux
 * cobayes que le banc d'essai inscrit ({@link #addSubject}) -- ils ne sont pas
 * dans PlayerList, le gardien les parcourt a part.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class MorphGunKeeper {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Intervalle du gardien, en tiques. */
    public static final int GUARD_TICKS = 20;

    /** L'arme rangee cote serveur : pendant un menu etranger, ou faute de place. */
    private static final Map<UUID, ItemStack> HELD = new HashMap<>();
    /** Les joueurs dont un menu etranger est ouvert. */
    private static final Set<UUID> IN_MENU = new HashSet<>();
    /** Le dernier etat connu de l'arme de chaque joueur : rendre sans recharger. */
    private static final Map<UUID, MorphGunData> LAST = new HashMap<>();
    /** Les cobayes du banc d'essai. */
    private static final Map<UUID, ServerPlayer> SUBJECTS = new LinkedHashMap<>();

    private static int ticks;

    private MorphGunKeeper() {
    }

    // ================================================================ regles

    public static boolean isGun(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.MORPH_GUN.get());
    }

    /** L'arme peut-elle exister la ou se tient ce joueur ? Dans Haven, lobby ouvert. */
    public static boolean allowed(ServerPlayer player) {
        return Haven.is(player.level()) && HavenArrival.lobbyOpen(player.server);
    }

    /** Ce joueur doit-il en avoir une ? Vrai joueur (ou cobaye), hors chantier, vivant, dans Haven lobby ouvert. */
    public static boolean entitled(ServerPlayer player) {
        return (!player.isFakePlayer() || SUBJECTS.containsKey(player.getUUID()))
                && allowed(player) && !HavenRules.chantier(player)
                && player.isAlive() && !player.hasDisconnected();
    }

    /** Le numero du lobby ouvert (MorphGunState). */
    public static long lobby(MinecraftServer server) {
        return MorphGunState.get(server).lobby();
    }

    // ================================================================ lecture

    /** Le nombre d'armes du joueur, par identite : inventaire (3 compartiments), deux curseurs, reserve. */
    public static int count(ServerPlayer player) {
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Inventory inventory = player.getInventory();
        for (NonNullList<ItemStack> list : List.of(inventory.items, inventory.armor, inventory.offhand)) {
            for (ItemStack stack : list) {
                if (isGun(stack)) {
                    seen.add(stack);
                }
            }
        }
        if (isGun(player.containerMenu.getCarried())) {
            seen.add(player.containerMenu.getCarried());
        }
        if (isGun(player.inventoryMenu.getCarried())) {
            seen.add(player.inventoryMenu.getCarried());
        }
        CraftingContainer grid = player.inventoryMenu.getCraftSlots();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            if (isGun(grid.getItem(i))) {
                seen.add(grid.getItem(i));
            }
        }
        ItemStack held = HELD.get(player.getUUID());
        if (isGun(held)) {
            seen.add(held);
        }
        return seen.size();
    }

    /** Les armes du coffre de l'Ender et des sacs portes (le gardien les en ressort). */
    public static int countStored(ServerPlayer player) {
        int n = 0;
        PlayerEnderChestContainer ender = player.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) {
            if (isGun(ender.getItem(i))) {
                n++;
            }
        }
        for (IItemHandler bag : Stash.bags(player)) {
            for (int slot = 0; slot < bag.getSlots(); slot++) {
                if (isGun(bag.getStackInSlot(slot))) {
                    n++;
                }
            }
        }
        return n;
    }

    /** L'arme rangee cote serveur, ou null. */
    @Nullable
    public static ItemStack held(ServerPlayer player) {
        return HELD.get(player.getUUID());
    }

    public static boolean inMenu(ServerPlayer player) {
        return IN_MENU.contains(player.getUUID());
    }

    @Nullable
    public static MorphGunData lastKnown(UUID player) {
        return LAST.get(player);
    }

    /**
     * La pile de l'arme du joueur la ou elle est : inventaire, curseurs, reserve ; null s'il n'en a pas.
     *
     * C'est la pile que le rendu, le HUD et le tir doivent lire et ecrire.
     */
    @Nullable
    public static ItemStack find(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        if (isGun(inventory.getSelected())) {
            return inventory.getSelected();
        }
        for (NonNullList<ItemStack> list : List.of(inventory.items, inventory.offhand, inventory.armor)) {
            for (ItemStack stack : list) {
                if (isGun(stack)) {
                    return stack;
                }
            }
        }
        if (isGun(player.containerMenu.getCarried())) {
            return player.containerMenu.getCarried();
        }
        if (isGun(player.inventoryMenu.getCarried())) {
            return player.inventoryMenu.getCarried();
        }
        CraftingContainer grid = player.inventoryMenu.getCraftSlots();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            if (isGun(grid.getItem(i))) {
                return grid.getItem(i);
            }
        }
        ItemStack held = HELD.get(player.getUUID());
        return isGun(held) ? held : null;
    }

    // ================================================================ cobayes du banc

    public static void addSubject(ServerPlayer player) {
        SUBJECTS.put(player.getUUID(), player);
    }

    public static void removeSubject(UUID player) {
        SUBJECTS.remove(player);
        HELD.remove(player);
        IN_MENU.remove(player);
        LAST.remove(player);
    }

    // ================================================================ donner

    /** Une arme trouvee, ou elle est, et comment l'en retirer. Plus le rang est bas, plus on la garde. */
    private record Spot(ItemStack stack, Runnable clear, int rank) {
        static final int MAIN_HAND = 0;
        static final int INVENTORY = 1;
        static final int CURSOR = 2;
        static final int HELD = 3;
        static final int LOOSE = 4;
    }

    /**
     * L'invariant « exactement une » pour un joueur qui y a droit.
     *
     * Compte par identite ; ressort l'arme des sacs portes et du coffre de
     * l'Ender ; retire les piles d'un autre lobby ; garde celle en main s'il y en
     * a plusieurs ; sinon en donne une -- le dernier etat connu du meme lobby,
     * ou une neuve (formes du jalon, reserves pleines). Une arme a poser va dans
     * la premiere case libre de la barre, puis de l'inventaire, puis dans la main
     * gauche vide ; sinon elle reste rangee, avec un message. Pendant un menu
     * etranger, l'arme gardee va dans la reserve.
     */
    public static void ensure(ServerPlayer player) {
        if (!entitled(player)) {
            return;
        }
        long lobby = lobby(player.server);
        UUID id = player.getUUID();
        Inventory inventory = player.getInventory();
        List<Spot> spots = new ArrayList<>();
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        collect(inventory.items, true, inventory.selected, spots, seen);
        collect(inventory.armor, false, -1, spots, seen);
        collect(inventory.offhand, false, -1, spots, seen);
        for (AbstractContainerMenu menu : menus(player)) {
            ItemStack carried = menu.getCarried();
            if (isGun(carried) && seen.add(carried)) {
                spots.add(new Spot(carried, () -> menu.setCarried(ItemStack.EMPTY), Spot.CURSOR));
            }
        }
        CraftingContainer grid = player.inventoryMenu.getCraftSlots();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack in = grid.getItem(i);
            if (isGun(in) && seen.add(in)) {
                int slot = i;
                spots.add(new Spot(in, () -> grid.setItem(slot, ItemStack.EMPTY), Spot.INVENTORY));
            }
        }
        ItemStack stashed = HELD.get(id);
        if (isGun(stashed) && seen.add(stashed)) {
            spots.add(new Spot(stashed, () -> HELD.remove(id), Spot.HELD));
        }
        int pulled = 0;
        for (ItemStack loose : pullStored(player)) {
            spots.add(new Spot(loose, () -> {
            }, Spot.LOOSE));
            pulled++;
        }

        Spot keep = null;
        int stale = 0;
        List<Spot> valid = new ArrayList<>();
        for (Spot spot : spots) {
            MorphGunData data = MorphGunData.of(spot.stack());
            if (data == null || data.lobby() != lobby) {
                spot.clear().run();
                stale++;
                continue;
            }
            valid.add(spot);
            if (keep == null || spot.rank() < keep.rank()) {
                keep = spot;
            }
        }
        int extra = 0;
        for (Spot spot : valid) {
            if (spot != keep) {
                spot.clear().run();
                extra++;
            }
        }

        ItemStack gun;
        boolean created = false;
        if (keep == null) {
            MorphGunData last = LAST.get(id);
            MorphGunData data = last != null && last.lobby() == lobby ? last : MorphGunData.fresh(lobby, GunForm.BASE_MASK);
            gun = new ItemStack(ModItems.MORPH_GUN.get());
            MorphGunData.write(gun, data);
            created = true;
        } else {
            gun = keep.stack();
        }

        boolean warned = false;
        if (IN_MENU.contains(id)) {
            if (keep == null || keep.rank() != Spot.HELD) {
                if (keep != null) {
                    keep.clear().run();
                }
                HELD.put(id, gun);
            }
        } else if (keep == null || keep.rank() >= Spot.HELD) {
            if (keep != null) {
                keep.clear().run();
            }
            if (!place(player, gun)) {
                HELD.put(id, gun);
                player.displayClientMessage(Component.translatable("game.emeraldweapons.morph_gun.full")
                        .withStyle(ChatFormatting.RED), true);
                warned = true;
            }
        }
        MorphGunData data = MorphGunData.of(gun);
        if (data != null) {
            LAST.put(id, data);
        }
        if (created || stale > 0 || extra > 0 || pulled > 0) {
            LOGGER.info("Morph Gun : {} {}{}{}{}{}", player.getGameProfile().getName(),
                    created ? "recoit l'arme (lobby " + lobby + ")" : "garde son arme",
                    stale > 0 ? ", " + stale + " pile(s) d'un autre lobby retiree(s)" : "",
                    extra > 0 ? ", " + extra + " doublon(s) retire(s)" : "",
                    pulled > 0 ? ", " + pulled + " ressortie(s) d'un sac ou du coffre de l'Ender" : "",
                    warned ? ", inventaire plein : rangee" : "");
        }
    }

    private static void collect(NonNullList<ItemStack> list, boolean hotbar, int selected,
                                List<Spot> spots, Set<ItemStack> seen) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = list.get(i);
            if (isGun(stack) && seen.add(stack)) {
                int slot = i;
                spots.add(new Spot(stack, () -> list.set(slot, ItemStack.EMPTY),
                        hotbar && i == selected ? Spot.MAIN_HAND : Spot.INVENTORY));
            }
        }
    }

    private static List<AbstractContainerMenu> menus(ServerPlayer player) {
        return player.containerMenu == player.inventoryMenu
                ? List.of(player.inventoryMenu) : List.of(player.containerMenu, player.inventoryMenu);
    }

    /** Sort toutes les armes du coffre de l'Ender et des sacs portes (Curios compris). */
    private static List<ItemStack> pullStored(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        PlayerEnderChestContainer ender = player.getEnderChestInventory();
        boolean changed = false;
        for (int i = 0; i < ender.getContainerSize(); i++) {
            if (isGun(ender.getItem(i))) {
                out.add(ender.removeItemNoUpdate(i));
                changed = true;
            }
        }
        if (changed) {
            ender.setChanged();
        }
        for (IItemHandler bag : Stash.bags(player)) {
            for (int slot = 0; slot < bag.getSlots(); slot++) {
                ItemStack in = bag.getStackInSlot(slot);
                if (isGun(in)) {
                    ItemStack got = bag.extractItem(slot, in.getCount(), false);
                    if (!got.isEmpty()) {
                        out.add(got);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Pose l'arme sans deplacer aucun objet : premiere case libre (barre puis
     * inventaire), sinon main gauche vide.
     */
    static boolean place(ServerPlayer player, ItemStack gun) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (inventory.items.get(i).isEmpty()) {
                inventory.items.set(i, gun);
                return true;
            }
        }
        if (inventory.offhand.get(0).isEmpty()) {
            inventory.offhand.set(0, gun);
            return true;
        }
        return false;
    }

    // ================================================================ retirer

    /**
     * Retire toutes les armes du joueur.
     *
     * Toujours : la reserve, les deux curseurs, l'inventaire (objets, armure,
     * main gauche). Avec « full » : ferme un menu etranger, et vide le coffre de
     * l'Ender et les sacs portes. Le dernier etat connu n'est pas touche
     * (voir {@link #forget}).
     *
     * @return le nombre d'armes retirees
     */
    public static int strip(ServerPlayer player, boolean full) {
        UUID id = player.getUUID();
        int removed = 0;
        if (isGun(HELD.remove(id))) {
            removed++;
        }
        IN_MENU.remove(id);
        for (AbstractContainerMenu menu : menus(player)) {
            if (isGun(menu.getCarried())) {
                menu.setCarried(ItemStack.EMPTY);
                removed++;
            }
        }
        if (full && player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        CraftingContainer grid = player.inventoryMenu.getCraftSlots();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            if (isGun(grid.getItem(i))) {
                // setItem previent le menu : la case de resultat est recalculee
                grid.setItem(i, ItemStack.EMPTY);
                removed++;
            }
        }
        Inventory inventory = player.getInventory();
        for (NonNullList<ItemStack> list : List.of(inventory.items, inventory.armor, inventory.offhand)) {
            for (int i = 0; i < list.size(); i++) {
                if (isGun(list.get(i))) {
                    list.set(i, ItemStack.EMPTY);
                    removed++;
                }
            }
        }
        if (full) {
            removed += pullStored(player).size();
        }
        return removed;
    }

    /** Oublie le dernier etat connu : l'arme suivante sera neuve. La gachette et une charge en cours aussi. */
    public static void forget(UUID player) {
        LAST.remove(player);
        GunFire.forget(player);
    }

    /** Une pile trouvee par Item.inventoryTick hors de la ville : supprimee cote serveur. */
    static void discardOutside(ServerPlayer player, ItemStack stack) {
        stack.setCount(0);
        LOGGER.info("Morph Gun : pile de {} supprimee hors de la ville ou lobby ferme ({})",
                player.getGameProfile().getName(), player.level().dimension().location());
    }

    /**
     * Un passage du gardien sur un joueur.
     *
     * Dans Haven lobby ouvert : l'invariant pour qui y a droit, rien pour
     * l'operateur en chantier ni pour un mort (sa reapparition s'en charge).
     * Ailleurs : retrait -- complet (sacs, coffre de l'Ender) si le joueur est
     * encore dans la ville lobby ferme, sur lui seulement sinon : l'arme n'entre
     * dans un sac que dans la ville, et chaque sortie vide les sacs.
     */
    public static void guard(ServerPlayer player) {
        if (allowed(player)) {
            if (entitled(player)) {
                ensure(player);
            }
            return;
        }
        int removed = strip(player, Haven.is(player.level()));
        forget(player.getUUID());
        if (removed > 0) {
            LOGGER.info("Morph Gun : gardien, {} arme(s) retiree(s) a {} ({}, lobby {})", removed,
                    player.getGameProfile().getName(), player.level().dimension().location(),
                    HavenArrival.lobbyOpen(player.server) ? "ouvert" : "ferme");
        }
    }

    // ================================================================ changer d'arme

    /** La reponse du serveur a une fleche de la croix. */
    public enum Selection { OK, SAME, NOT_IN_HAVEN, NO_GUN, NOT_OWNED, CHARGING }

    /**
     * Change la forme de l'arme tenue, en UNE ecriture de composant.
     *
     * Revalide tout : la ville lobby ouvert, l'arme en main droite, pas de boule
     * du Peace Maker en charge au canon (target-gun.gc:1558, GunFire), une forme
     * possedee de la famille.
     */
    public static Selection select(ServerPlayer player, GunForm.Family family) {
        if (!allowed(player)) {
            return Selection.NOT_IN_HAVEN;
        }
        ItemStack stack = player.getMainHandItem();
        MorphGunData data = isGun(stack) ? MorphGunData.of(stack) : null;
        if (data == null) {
            return Selection.NO_GUN;
        }
        if (GunFire.isCharging(player)) {
            return Selection.CHARGING;
        }
        GunForm next = GunForm.select(data.form(), family, data.owned());
        if (next == null) {
            return Selection.NOT_OWNED;
        }
        if (next == data.form()) {
            return Selection.SAME;
        }
        MorphGunData after = data.withForm(next, player.level().getGameTime());
        MorphGunData.write(stack, after);
        LAST.put(player.getUUID(), after);
        return Selection.OK;
    }

    public static void onSelectRequest(ServerPlayer player, GunSelectPayload payload) {
        GunForm.Family family = GunForm.Family.byIndex(payload.family());
        if (family == null) {
            return;
        }
        if (select(player, family) == Selection.NOT_OWNED) {
            player.displayClientMessage(Component.translatable("game.emeraldweapons.morph_gun.not_owned",
                    Component.translatable(family.translationKey())).withStyle(ChatFormatting.RED), true);
        }
    }

    // ================================================================ chemins d'entree et de sortie

    /** Connexion : dans la ville lobby ouvert, l'invariant ; ailleurs, retrait complet. */
    static void login(ServerPlayer player) {
        if (allowed(player)) {
            ensure(player);
        } else {
            strip(player, true);
            forget(player.getUUID());
        }
    }

    /**
     * Deconnexion, AVANT la sauvegarde (PlayerList.remove :368 puis :371) : les
     * curseurs et l'arme rangee reviennent dans l'inventaire, pour ne pas etre
     * laches au sol par Player.remove. Sans place, l'arme reste dans le dernier
     * etat connu et sera rendue a la reconnexion.
     */
    static void logout(ServerPlayer player) {
        UUID id = player.getUUID();
        IN_MENU.remove(id);
        List<ItemStack> back = new ArrayList<>();
        ItemStack stashed = HELD.remove(id);
        if (isGun(stashed)) {
            back.add(stashed);
        }
        for (AbstractContainerMenu menu : menus(player)) {
            if (isGun(menu.getCarried())) {
                back.add(menu.getCarried());
                menu.setCarried(ItemStack.EMPTY);
            }
        }
        // la grille 2x2 n'est pas sauvegardee : InventoryMenu.removed la jetterait au sol apres la sauvegarde
        CraftingContainer grid = player.inventoryMenu.getCraftSlots();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            if (isGun(grid.getItem(i))) {
                back.add(grid.getItem(i));
                grid.setItem(i, ItemStack.EMPTY);
            }
        }
        for (ItemStack gun : back) {
            MorphGunData data = MorphGunData.of(gun);
            if (data != null) {
                LAST.put(id, data);
            }
            if (allowed(player) && count(player) == 0 && place(player, gun)) {
                continue;
            }
            LOGGER.info("Morph Gun : {} se deconnecte, arme non posee (sans place ou hors de la ville)",
                    player.getGameProfile().getName());
        }
    }

    /** Reapparition : dans la ville, formes gardees et reserves pleines ; ailleurs, retrait complet. */
    static void respawn(ServerPlayer player) {
        UUID id = player.getUUID();
        if (Haven.is(player.level())) {
            MorphGunData last = LAST.get(id);
            if (last != null) {
                LAST.put(id, last.refilled());
            }
            ensure(player);
        } else {
            strip(player, true);
            forget(id);
        }
    }

    // ================================================================ evenements

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (MorphGunState.get(server).observe(server)) {
            LOGGER.info("Morph Gun : lobby numero {} ouvert", lobby(server));
        }
        if (++ticks % GUARD_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            guard(player);
        }
        for (ServerPlayer subject : List.copyOf(SUBJECTS.values())) {
            guard(subject);
        }
    }

    /**
     * Toute sortie de la dimension, AVANT la teleportation (ServerPlayer.java:888).
     * En LOWEST et sans les evenements annules : si un autre mod refuse le voyage,
     * le joueur reste dans la ville avec son arme.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Haven.is(player.level())
                && !event.getDimension().equals(Haven.LEVEL)) {
            int removed = strip(player, true);
            forget(player.getUUID());
            LOGGER.info("Morph Gun : {} quitte la ville vers {}, {} arme(s) retiree(s)",
                    player.getGameProfile().getName(), event.getDimension().location(), removed);
        }
    }

    /** Ceinture apres coup, et l'arrivee dans la ville. */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getTo().equals(Haven.LEVEL)) {
            ensure(player);
        } else if (event.getFrom().equals(Haven.LEVEL)) {
            strip(player, true);
            forget(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            login(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            logout(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            respawn(player);
        }
    }

    /** La mort, avant la chute des objets (ServerPlayer.die : l'evenement puis dropAllDeathLoot). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack gun = find(player);
        MorphGunData data = gun == null ? null : MorphGunData.of(gun);
        if (data != null && allowed(player)) {
            LAST.put(player.getUUID(), data);
        }
        int removed = strip(player, true);
        if (removed > 0) {
            LOGGER.info("Morph Gun : {} meurt, {} arme(s) retiree(s) avant la chute des objets",
                    player.getGameProfile().getName(), removed);
        }
    }

    /** Ceinture : aucune entite d'objet Morph Gun ne sort d'une mort (Tombstone ramasse en HIGH). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDrops(LivingDropsEvent event) {
        event.getDrops().removeIf(entity -> isGun(entity.getItem()));
    }

    /**
     * Un jet : touche Q menu ouvert, clic hors de la fenetre, jet d'une case.
     * On annule, et la MEME pile revient (reserves intactes) ; hors de la ville
     * elle est simplement detruite.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onToss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (!isGun(stack)) {
            return;
        }
        event.setCanceled(true);
        if (!(event.getPlayer() instanceof ServerPlayer player) || !allowed(player)) {
            return;
        }
        UUID id = player.getUUID();
        if (IN_MENU.contains(id) || !place(player, stack)) {
            if (!isGun(HELD.get(id))) {
                HELD.put(id, stack);
            }
        }
    }

    /**
     * Un menu serveur s'ouvre dans la ville : l'arme est rangee cote serveur tant
     * qu'il est ouvert, quel que soit le mod. Seuls les menus du mod sans case
     * (la borne du QG) n'y changent rien.
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !entitled(player)) {
            return;
        }
        AbstractContainerMenu menu = event.getContainer();
        if (menu == player.inventoryMenu || exempt(menu)) {
            return;
        }
        IN_MENU.add(player.getUUID());
        ensure(player);
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player && IN_MENU.remove(player.getUUID())) {
            ensure(player);
        }
    }

    /** Un menu du mod sans aucune case : rien a deposer. */
    static boolean exempt(AbstractContainerMenu menu) {
        return menu.slots.isEmpty() && menu.getClass().getName().startsWith("com.emerald.");
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HELD.clear();
        IN_MENU.clear();
        LAST.clear();
        SUBJECTS.clear();
        ticks = 0;
    }
}
