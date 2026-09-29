package com.emerald.item;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.menu.bag.Bag;
import com.emerald.menu.bag.Bags;
import com.emerald.network.BagAmmoPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * LES FLECHES DU SAC (cahier §111). Le Sac d'Arcencium ramasse les fleches ; l'arc,
 * lui, ne les cherchait que dans l'inventaire, et le joueur devait les en sortir.
 *
 * Quand l'inventaire n'a rien a tirer (LivingGetProjectileEvent sans projectile),
 * on tire dans le sac :
 *  - SERVEUR : l'arc recoit une COPIE de la case du sac, jamais la pile du sac elle-meme
 *    (Sophisticated Backpacks n'enregistre que ce qu'on lui ecrit). A la fin de la tique,
 *    ce que l'arc a pris a la copie est retire de la case (Handout) : une fleche par tir,
 *    aucune avec Infinite ;
 *  - CLIENT : il ne voit pas le contenu du sac, et un arc sans fleche ne se bande pas chez
 *    lui -- le serveur tirerait seul, sans que le bouton relache soit jamais envoye. Le
 *    serveur lui envoie donc, toutes les secondes quand elle change, la liste des sortes
 *    de munitions presentes dans les sacs (BagAmmoPayload) ; le client tend l'arc sur une
 *    copie d'une fleche de cette liste, le serveur fait le vrai tir.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class BagAmmo {

    private static final int SYNC_TICKS = 20;
    private static final int MAX_KINDS = 8;
    private static final int MAX_BAGS = 4;

    /** Les copies donnees a une arme pendant cette tique. */
    private static final List<Handout> HANDOUTS = new ArrayList<>();
    /** La derniere liste envoyee a chaque joueur. */
    private static final Map<UUID, List<ItemStack>> SENT = new HashMap<>();
    /** Cote client : les sortes de munitions que le serveur dit presentes dans les sacs. */
    private static volatile List<ItemStack> known = List.of();

    private record Handout(Bag bag, int slot, ItemStack given, int before) {
    }

    private BagAmmo() {
    }

    /** Une munition que le client doit connaitre : les fleches, et les fusees des arbaletes. */
    public static boolean isAmmo(ItemStack stack) {
        return stack.is(ItemTags.ARROWS) || stack.is(Items.FIREWORK_ROCKET);
    }

    /** Cote client : la liste recue du serveur. */
    public static void accept(BagAmmoPayload payload) {
        known = List.copyOf(payload.kinds());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onGetProjectile(LivingGetProjectileEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.getProjectileItemStack().isEmpty()) {
            return;
        }
        ItemStack weapon = event.getProjectileWeaponItemStack();
        if (!(weapon.getItem() instanceof ProjectileWeaponItem shooter)) {
            return;
        }
        Predicate<ItemStack> accepts = shooter.getAllSupportedProjectiles(weapon);
        if (player.level().isClientSide) {
            for (ItemStack kind : known) {
                if (accepts.test(kind)) {
                    event.setProjectileItemStack(kind.copy());
                    return;
                }
            }
            return;
        }
        for (Bag bag : Bags.scan(player, MAX_BAGS)) {
            for (int slot = 0; slot < bag.size(); slot++) {
                ItemStack stack = bag.get(slot);
                if (!stack.isEmpty() && accepts.test(stack)) {
                    HANDOUTS.add(new Handout(bag, slot, stack, stack.getCount()));
                    event.setProjectileItemStack(stack);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        settle();
    }

    /** Ce que les armes ont pris aux copies est retire des sacs (chaque tique ; le banc l'appelle aussi). */
    public static void settle() {
        if (HANDOUTS.isEmpty()) {
            return;
        }
        for (Handout handout : HANDOUTS) {
            int used = handout.before() - handout.given().getCount();
            if (used > 0) {
                take(handout, used);
            }
        }
        HANDOUTS.clear();
    }

    /** Retire tant de cette munition du sac : de sa case d'abord, sinon de toute case pareille. */
    private static void take(Handout handout, int used) {
        Bag bag = handout.bag();
        int left = used;
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int slot = 0; slot < bag.size() && left > 0; slot++) {
                if (pass == 0 && slot != handout.slot()) {
                    continue;
                }
                ItemStack here = bag.get(slot);
                if (here.isEmpty() || !ItemStack.isSameItemSameComponents(here, handout.given())) {
                    continue;
                }
                int taken = Math.min(left, here.getCount());
                here.shrink(taken);
                bag.set(slot, here.isEmpty() ? ItemStack.EMPTY : here);
                left -= taken;
            }
        }
    }

    /** Toutes les secondes : les sortes de munitions des sacs, au client, si elles ont change. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % SYNC_TICKS != 7) {
            return;
        }
        List<ItemStack> kinds = kinds(player);
        List<ItemStack> last = SENT.get(player.getUUID());
        if (last != null && same(last, kinds)) {
            return;
        }
        SENT.put(player.getUUID(), kinds);
        PacketDistributor.sendToPlayer(player, new BagAmmoPayload(kinds));
    }

    private static List<ItemStack> kinds(Player player) {
        List<ItemStack> out = new ArrayList<>();
        for (Bag bag : Bags.scan(player, MAX_BAGS)) {
            for (int slot = 0; slot < bag.size(); slot++) {
                ItemStack stack = bag.get(slot);
                if (stack.isEmpty() || !isAmmo(stack)) {
                    continue;
                }
                boolean seen = false;
                for (ItemStack kind : out) {
                    if (ItemStack.isSameItemSameComponents(kind, stack)) {
                        seen = true;
                        break;
                    }
                }
                if (!seen) {
                    out.add(stack.copyWithCount(1));
                    if (out.size() >= MAX_KINDS) {
                        return out;
                    }
                }
            }
        }
        return out;
    }

    private static boolean same(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SENT.clear();
        HANDOUTS.clear();
    }
}
