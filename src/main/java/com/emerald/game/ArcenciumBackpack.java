package com.emerald.game;

import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ContentsFilterLogic;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ContentsFilterType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogic;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IFilteredUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.PrimaryMatch;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.cooking.AutoCookingUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.magnet.MagnetUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.voiding.VoidUpgradeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * LE SAC D'ARCENCIUM : l'inventaire du mode, sans en ecrire un.
 *
 * « Je passe une grande partie de mon temps a faire le tri dans mon
 * inventaire. Tous les objets qu'on ramasserait, qui sont de notre mode ou
 * d'Apotheosis, seraient automatiquement places dans notre inventaire. » Le
 * pack a deja tout cela : Sophisticated Backpacks sait ramasser sur filtre,
 * attirer, detruire, cuire et nourrir. On ne reecrit pas un inventaire, on
 * PREREMPLIT un sac -- et Stash apprend aux stations a lire dedans.
 *
 * Un sac de netherite, sept ameliorations, dans l'ordre des cases :
 *   0. ramassage avance, filtre PAR MOD : nos objets, Apotheosis, Artefacts, Reliques ;
 *   1. ramassage avance, filtre par objet : les metaux de la Forge, le charbon,
 *      et les dechets que la case 3 detruit ;
 *   2. aimant avance, meme filtre par mod, qui prend aussi l'experience ;
 *   3. destruction avancee : chair putrefiee, oeil d'araignee, os, ficelle,
 *      pomme de terre empoisonnee -- ce qui encombrait sans jamais servir ;
 *   4. cuisson automatique : le brut d'Arcencium et les minerais bruts, avec
 *      le charbon du sac -- « je n'ai pas de four en deplacement » ;
 *   5. alimentation : le sac nourrit quand la faim vient ;
 *   6. piles agrandies.
 *
 * Cette classe touche l'API de Sophisticated Backpacks : on ne la charge
 * qu'apres avoir demande a ModList (voir available()).
 */
public final class ArcenciumBackpack {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);
    private static final int MAIN = 0x2F8F5A;      // le vert de l'Arcencium
    private static final int ACCENT = 0x9CE8FF;    // le bleu clair du mode
    private static final String CURIOS_BACK = "back";

    /** Un objet par mod : sous « par mod », c'est le mod qui compte, pas l'objet. */
    private static final String[] MOD_MARKS = {
            "emeraldweapons:arcencium_ingot", "apotheosis:gem", "artifacts:umbrella",
            "relics:magic_mirror"};
    private static final String[] MATERIALS = {
            "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:diamond",
            "minecraft:emerald", "minecraft:coal", "minecraft:raw_iron", "minecraft:raw_gold",
            "minecraft:arrow"};
    private static final String[] JUNK = {
            "minecraft:rotten_flesh", "minecraft:spider_eye", "minecraft:bone",
            "minecraft:string", "minecraft:poisonous_potato"};
    private static final String[] SMELT = {
            "emeraldweapons:raw_arcencium", "minecraft:raw_iron", "minecraft:raw_gold",
            "minecraft:raw_copper"};

    private ArcenciumBackpack() {
    }

    /** Vrai si Sophisticated Backpacks est la. A demander AVANT de toucher au reste. */
    public static boolean available() {
        return ModList.get().isLoaded("sophisticatedbackpacks");
    }

    /** Le sac, prerempli. A n'appeler que cote serveur : le contenu vit dans le monde. */
    public static ItemStack make() {
        ItemStack pack = new ItemStack(net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems.NETHERITE_BACKPACK.get());
        pack.set(DataComponents.CUSTOM_NAME, Component.literal("Sac d'Arcencium")
                .withStyle(style -> style.withColor(ACCENT).withItalic(false)));
        IBackpackWrapper wrapper = BackpackWrapper.fromStack(pack);
        // UN SAC NEUF N'A PAS D'IDENTITE : tant que la pile ne porte pas
        // d'UUID de contenu, l'enveloppe rend un conteneur d'ameliorations
        // factice a zero case (« Slot 0 not in valid range - [0,0) », lu au
        // banc, et confirme dans le bytecode de getUpgradeHandler). Le contenu
        // d'un sac vit dans le monde, sous cet UUID ; on le lui donne d'abord.
        wrapper.setContentsUuid(java.util.UUID.randomUUID());
        var item = (net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem) pack.getItem();
        wrapper.setSlotNumbers(item.getNumberOfSlots(), item.getNumberOfUpgradeSlots());
        wrapper.setColors(MAIN, ACCENT);
        UpgradeHandler upgrades = wrapper.getUpgradeHandler();
        upgrades.setStackInSlot(0, new ItemStack(net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems.ADVANCED_PICKUP_UPGRADE.get()));
        upgrades.setStackInSlot(1, new ItemStack(net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems.ADVANCED_PICKUP_UPGRADE.get()));
        upgrades.setStackInSlot(2, new ItemStack(net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems.ADVANCED_MAGNET_UPGRADE.get()));
        upgrades.setStackInSlot(3, new ItemStack(net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems.ADVANCED_VOID_UPGRADE.get()));
        upgrades.setStackInSlot(4, new ItemStack(net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems.AUTO_SMELTING_UPGRADE.get()));
        upgrades.setStackInSlot(5, new ItemStack(net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems.FEEDING_UPGRADE.get()));
        upgrades.setStackInSlot(6, new ItemStack(net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems.STACK_UPGRADE_TIER_2.get()));
        upgrades.refreshUpgradeWrappers();
        Map<Integer, IUpgradeWrapper> slots = upgrades.getSlotWrappers();

        filter(slots.get(0), PrimaryMatch.MOD, MOD_MARKS);
        filter(slots.get(1), PrimaryMatch.ITEM, concat(MATERIALS, JUNK));
        filter(slots.get(2), PrimaryMatch.MOD, MOD_MARKS);
        if (slots.get(2) instanceof MagnetUpgradeWrapper magnet) {
            magnet.setPickupItems(true);
            magnet.setPickupXp(true);
        }
        filter(slots.get(3), PrimaryMatch.ITEM, JUNK);
        if (slots.get(3) instanceof VoidUpgradeWrapper voiding) {
            voiding.setShouldVoidOverflow(false);
        }
        if (slots.get(4) instanceof AutoCookingUpgradeWrapper<?, ?, ?> cooking) {
            fill(cooking.getInputFilterLogic(), PrimaryMatch.ITEM, SMELT);
            cooking.setEnabled(true);
        }
        upgrades.saveInventory();
        LOGGER.info("Sac d'Arcencium prepare : {} ameliorations", slots.size());
        return wrapper.getBackpack();
    }

    /**
     * Donne le sac : dans le dos (case Curios) si elle existe et qu'elle est
     * libre, sinon dans l'inventaire. Et un peu de charbon pour la cuisson.
     */
    public static void give(ServerPlayer player) {
        ItemStack pack = make();
        boolean worn = ModList.get().isLoaded("curios") && equipBack(player, pack);
        if (!worn) {
            com.emerald.item.Stash.give(player, pack);
        }
        com.emerald.item.Stash.give(player, new ItemStack(Items.COAL, 16));
        player.sendSystemMessage(Component.literal(worn
                        ? "Le Sac d'Arcencium est dans votre dos : il ramasse, cuit et nourrit tout seul."
                        : "Le Sac d'Arcencium est dans votre inventaire : il ramasse, cuit et nourrit tout seul.")
                .withStyle(ChatFormatting.AQUA));
        LOGGER.info("Sac d'Arcencium donne a {} ({})", player.getName().getString(),
                worn ? "dans le dos" : "dans l'inventaire");
    }

    private static boolean equipBack(ServerPlayer player, ItemStack pack) {
        try {
            return com.emerald.item.CuriosStash.equip(player, CURIOS_BACK, pack);
        } catch (Throwable t) {                       // une API absente ne doit pas couter le sac
            LOGGER.warn("Curios : pas de case dos ({})", t.toString());
            return false;
        }
    }

    // ------------------------------------------------------------- filtres

    private static void filter(IUpgradeWrapper wrapper, PrimaryMatch match, String[] ids) {
        if (wrapper instanceof IFilteredUpgrade filtered) {
            fill(filtered.getFilterLogic(), match, ids);
        } else {
            LOGGER.warn("Sac d'Arcencium : une amelioration sans filtre ({})", wrapper);
        }
    }

    private static void fill(FilterLogic logic, PrimaryMatch match, String[] ids) {
        logic.setAllowList(true);
        logic.setPrimaryMatch(match);
        if (logic instanceof ContentsFilterLogic contents) {
            contents.setDepositFilterType(ContentsFilterType.ALLOW);
        }
        var handler = logic.getFilterHandler();
        int slot = 0;
        for (String id : ids) {
            if (slot >= handler.getSlots()) {
                break;
            }
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item == Items.AIR) {
                continue;                             // un mod absent : on passe
            }
            handler.setStackInSlot(slot++, new ItemStack(item));
        }
    }

    private static String[] concat(String[] a, String[] b) {
        String[] out = new String[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** Ce que le sac est cense contenir, pour la commande de controle. */
    public static List<Item> watched() {
        return List.of(ModItems.FATE_SHARD.get(), ModItems.FORGE_STONE.get(),
                ModItems.ARCENCIUM_INGOT.get(), ModItems.RAW_ARCENCIUM.get(),
                ModItems.ARCENCIUM_FEATHER.get(), Items.IRON_INGOT, Items.GOLD_INGOT,
                Items.COAL, Items.ROTTEN_FLESH);
    }
}
