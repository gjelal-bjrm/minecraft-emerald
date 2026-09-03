package com.emerald.compat;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Les reliques de Relics arrivent ETUDIEES.
 *
 * Relics verrouille chaque capacite derriere une enigme -- relier des etoiles
 * a la table de recherche. Dans un mode d'une heure et demie ou l'objet vient
 * de tomber d'un monstre, l'enigme est un mur entre le joueur et sa
 * recompense. On marque donc toutes les capacites comme etudiees : a la
 * creation quand c'est nous qui donnons l'objet, et une fois par seconde dans
 * l'inventaire de chaque joueur pour tout ce qui arrive autrement (coffres,
 * echanges, fabrication).
 *
 * Tout passe par la reflexion sur IRelicItem : le mod n'est pas une
 * dependance, et sans lui cette classe ne fait rien.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class RelicResearch {

    @Nullable
    private static Class<?> relicClass;
    @Nullable
    private static Method getAbilitiesData;
    @Nullable
    private static Method getAbilities;
    @Nullable
    private static Method isResearched;
    @Nullable
    private static Method setResearched;
    private static boolean resolved;

    private RelicResearch() {
    }

    private static boolean resolve() {
        if (resolved) {
            return relicClass != null;
        }
        resolved = true;
        try {
            relicClass = Class.forName("it.hurts.sskirillss.relics.items.relics.base.IRelicItem");
            getAbilitiesData = relicClass.getMethod("getAbilitiesData");
            getAbilities = getAbilitiesData.getReturnType().getMethod("getAbilities");
            isResearched = relicClass.getMethod("isAbilityResearched", ItemStack.class, String.class);
            setResearched = relicClass.getMethod("setAbilityResearched", ItemStack.class, String.class,
                    boolean.class);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            relicClass = null;                     // Relics absent, ou une autre version : on se tait
            return false;
        }
    }

    public static boolean isRelic(Item item) {
        return resolve() && relicClass != null && relicClass.isInstance(item);
    }

    /** Marque toutes les capacites de la relique comme etudiees. Rend vrai si quelque chose a change. */
    public static boolean complete(ItemStack stack) {
        if (stack.isEmpty() || !isRelic(stack.getItem())) {
            return false;
        }
        boolean changed = false;
        try {
            Object data = getAbilitiesData.invoke(stack.getItem());
            if (data == null) {
                return false;
            }
            Object map = getAbilities.invoke(data);
            if (!(map instanceof Map<?, ?> abilities)) {
                return false;
            }
            for (Object key : abilities.keySet()) {
                String ability = String.valueOf(key);
                if (!(Boolean) isResearched.invoke(stack.getItem(), stack, ability)) {
                    setResearched.invoke(stack.getItem(), stack, ability, true);
                    changed = true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
        return changed;
    }

    /** Une fois par seconde : ce que le joueur porte est etudie, d'ou que cela vienne. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % 20 != 0 || !resolve()) {
            return;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            complete(inventory.getItem(slot));
        }
    }
}
