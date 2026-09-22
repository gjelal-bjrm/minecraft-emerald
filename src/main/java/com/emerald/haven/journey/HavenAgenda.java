package com.emerald.haven.journey;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenState;
import com.emerald.item.ModItems;
import com.emerald.jak.gun.GunForm;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * L'agenda de Haven (cahier §83) : le « systeme de quete, entre guillemets » voulu par le
 * joueur le 22 sept. -- « plutot que de dire aux utilisateurs ou aller ». A la premiere
 * arrivee, le joueur recoit l'agenda A LA PLACE du dictionnaire des animaux d'Alex's Mobs,
 * qui ne servait a rien ; il y lit son prochain rendez-vous, d'abord « Rendez-vous au
 * quartier general pour discuter avec l'equipe ».
 *
 * DEUX PAGES : le prochain rendez-vous (l'objectif du guide, HavenJourney.objective, en
 * phrase) et le carnet de route (les etapes du parcours, cochees ou barrees). Les textes
 * sont traduits chez le client. L'agenda se rend a chaque arrivee s'il manque.
 */
public final class HavenAgenda {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Le livre qu'Alex's Mobs donne a la premiere connexion : l'agenda le remplace. */
    private static final ResourceLocation ANIMAL_DICTIONARY = ResourceLocation.fromNamespaceAndPath("alexsmobs", "animal_dictionary");

    private HavenAgenda() {
    }

    // ================================================================ l'objet

    public static boolean isAgenda(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.HAVEN_AGENDA.get());
    }

    /** L'agenda est-il dans l'inventaire du joueur (ou sur son curseur) ? */
    public static boolean has(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isAgenda(inventory.getItem(i))) {
                return true;
            }
        }
        return isAgenda(player.containerMenu.getCarried());
    }

    /**
     * A l'arrivee dans un appartement : l'agenda s'il manque ; a la premiere arrivee, le
     * dictionnaire des animaux s'en va.
     *
     * @return vrai si l'agenda vient d'etre donne
     */
    public static boolean ensure(ServerPlayer player, boolean first) {
        markDictionaryGiven(player);
        if (first) {
            removeAnimalDictionary(player);
        }
        if ((player.isFakePlayer() && !HavenJourney.isSubject(player.getUUID())) || has(player)) {
            return false;
        }
        ItemStack agenda = new ItemStack(ModItems.HAVEN_AGENDA.get());
        if (!player.getInventory().add(agenda)) {
            player.drop(agenda, false);
        }
        LOGGER.info("Parcours de Haven : agenda donne a {}", player.getGameProfile().getName());
        return true;
    }

    /**
     * Alex's Mobs donne son dictionnaire a la connexion si un drapeau manque
     * (ServerEvents : PlayerPersisted.alexsmobs_has_book). Les abonnes a la connexion n'ont
     * pas d'ordre garanti : s'il passe apres nous, on pose le drapeau pour qu'il ne le donne
     * plus ; s'il est passe avant, removeAnimalDictionary le reprend.
     */
    private static void markDictionaryGiven(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag data = player.getPersistentData();
        net.minecraft.nbt.CompoundTag persisted = data.getCompound("PlayerPersisted");
        if (!persisted.getBoolean("alexsmobs_has_book")) {
            persisted.putBoolean("alexsmobs_has_book", true);
            data.put("PlayerPersisted", persisted);
        }
    }

    private static void removeAnimalDictionary(ServerPlayer player) {
        if (!BuiltInRegistries.ITEM.containsKey(ANIMAL_DICTIONARY)) {
            return;
        }
        Item dictionary = BuiltInRegistries.ITEM.get(ANIMAL_DICTIONARY);
        Inventory inventory = player.getInventory();
        int removed = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(dictionary)) {
                removed += stack.getCount();
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        if (removed > 0) {
            LOGGER.info("Parcours de Haven : dictionnaire des animaux retire a {}, remplace par l'agenda",
                    player.getGameProfile().getName());
        }
    }

    // ================================================================ les pages

    /** Ouvre l'agenda chez le joueur, avec ses rendez-vous du moment. */
    public static void open(ServerPlayer player) {
        if (!player.isFakePlayer()) {
            PacketDistributor.sendToPlayer(player, new HavenAgendaPayload(pages(player)));
        }
    }

    /** Les pages : le prochain rendez-vous, puis le carnet de route. */
    public static List<Component> pages(ServerPlayer player) {
        List<Component> out = new ArrayList<>();
        out.add(nextPage(player));
        out.add(roadPage(player));
        return out;
    }

    private static Component nextPage(ServerPlayer player) {
        MutableComponent page = Component.empty();
        page.append(Component.translatable("game.emeraldweapons.haven.agenda.titre")
                .withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_BLUE));
        page.append("\n");
        int[] apartment = HavenState.get(player.server).apartment(player.getUUID());
        if (apartment != null) {
            page.append(Component.translatable("game.emeraldweapons.haven.agenda.appartement", apartment[0] + 1)
                    .withStyle(ChatFormatting.DARK_GRAY));
            page.append("\n");
        }
        page.append("\n");
        page.append(Component.translatable("game.emeraldweapons.haven.agenda.prochain")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.UNDERLINE));
        page.append("\n\n");
        page.append(nextText(player).withStyle(ChatFormatting.BLACK));
        return page;
    }

    /** Le prochain rendez-vous en phrase : l'objectif du guide, ou la partie en cours. */
    static MutableComponent nextText(ServerPlayer player) {
        if (!Haven.is(player.level())) {
            return Component.translatable("game.emeraldweapons.haven.agenda.partie");
        }
        if (!HavenJourney.guided(player)) {
            return Component.translatable("game.emeraldweapons.haven.agenda.ferme");
        }
        return switch (HavenJourney.objective(player)) {
            case QG -> Component.translatable("game.emeraldweapons.haven.agenda.rdv.qg");
            case EQUIPE -> Component.translatable("game.emeraldweapons.haven.agenda.rdv.equipe",
                    HavenJourney.reachedHq(player.server), HavenJourney.teamSize(player.server));
            case BORNE -> Component.translatable("game.emeraldweapons.haven.agenda.rdv.mode");
            case ATTENTE -> Component.translatable("game.emeraldweapons.haven.agenda.rdv.attente");
            case ARME -> Component.translatable("game.emeraldweapons.haven.agenda.rdv.arme");
            case REPRISE -> Component.translatable("game.emeraldweapons.haven.agenda.rdv.reprise",
                    HavenJourney.repriseCount(player.server), HavenJourney.REPRISE_GOAL);
        };
    }

    private static Component roadPage(ServerPlayer player) {
        HavenProgress.Entry entry = HavenProgress.get(player.getUUID());
        boolean back = entry.departures > 0;
        boolean armed = back && (entry.forms & GunForm.RED_1.bit()) != 0;
        MutableComponent page = Component.empty();
        page.append(Component.translatable("game.emeraldweapons.haven.agenda.route")
                .withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_BLUE));
        page.append("\n\n");
        step(page, "arrivee", entry.welcomed);
        step(page, "qg", entry.hq);
        step(page, "depart", back);
        if (back) {
            step(page, "arme", armed);
            step(page, "reprise", entry.reprise);
        }
        if (entry.reprise) {
            page.append(Component.translatable("game.emeraldweapons.haven.agenda.etape.suite")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            page.append("\n");
        }
        return page;
    }

    /** Une etape du carnet : cochee et barree si faite, a faire sinon. */
    private static void step(MutableComponent page, String key, boolean done) {
        MutableComponent line = Component.translatable("game.emeraldweapons.haven.agenda.etape." + key);
        if (done) {
            page.append(Component.literal("✔ ").withStyle(ChatFormatting.DARK_GREEN));
            page.append(line.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH));
        } else {
            page.append(Component.literal("• ").withStyle(ChatFormatting.DARK_RED));
            page.append(line.withStyle(ChatFormatting.BLACK));
        }
        page.append("\n");
    }
}
