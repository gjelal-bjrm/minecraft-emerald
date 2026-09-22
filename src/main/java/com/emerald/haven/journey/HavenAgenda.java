package com.emerald.haven.journey;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenState;
import com.emerald.haven.quest.HavenHero;
import com.emerald.haven.quest.HavenOrbs;
import com.emerald.haven.quest.HavenQuest;
import com.emerald.haven.quest.HavenQuestBook;
import com.emerald.haven.quest.HavenQuests;
import com.emerald.haven.quest.HavenShop;
import com.emerald.haven.quest.QuestRun;
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
import java.util.UUID;

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

    /** Les pages : le prochain rendez-vous, le carnet de route ; les rues reprises, les quetes et la bourse. */
    public static List<Component> pages(ServerPlayer player) {
        List<Component> out = new ArrayList<>();
        out.add(nextPage(player));
        out.add(roadPage(player));
        if (HavenProgress.get(player.getUUID()).reprise) {
            out.add(questPage(player, HavenHero.TORN, HavenHero.SIG, HavenHero.KEIRA));
            out.add(questPage(player, HavenHero.TESS, HavenHero.SAMOS, HavenHero.PECHEUR));
            out.add(pursePage(player));
        }
        return out;
    }

    /** Les quetes de trois heros : faites, en cours, a faire, fermees. */
    private static Component questPage(ServerPlayer player, HavenHero... heroes) {
        UUID id = player.getUUID();
        QuestRun mine = HavenQuests.runOf(id);
        MutableComponent page = Component.empty();
        page.append(Component.translatable("game.emeraldweapons.haven.agenda.quetes")
                .withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_BLUE));
        for (HavenHero hero : heroes) {
            page.append("\n\n");
            page.append(hero.displayName().copy().withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_GRAY));
            for (HavenQuest quest : HavenQuestBook.of(hero)) {
                page.append("\n");
                boolean done = HavenProgress.done(id, quest.id());
                HavenQuest before = HavenQuestBook.before(quest);
                boolean closed = !done && before != null && !HavenProgress.done(id, before.id());
                if (mine != null && mine.quest == quest) {
                    page.append(Component.literal("⟳ ").append(quest.title()).withStyle(ChatFormatting.DARK_AQUA));
                } else if (done) {
                    MutableComponent line = Component.literal("✓ ").append(quest.title()).withStyle(ChatFormatting.DARK_GREEN);
                    if (quest.medals()) {
                        line.append(" ").append(HavenQuests.medalName(HavenProgress.medal(id, quest.id())));
                    }
                    page.append(line);
                } else if (closed) {
                    page.append(Component.literal("✗ ").append(quest.title()).withStyle(ChatFormatting.GRAY));
                } else {
                    page.append(Component.literal("▶ ").append(quest.title()).withStyle(ChatFormatting.BLACK));
                }
            }
        }
        return page;
    }

    /** La bourse : les orbes, les orbes caches trouves, les bonus achetes chez Tess. */
    private static Component pursePage(ServerPlayer player) {
        UUID id = player.getUUID();
        MutableComponent page = Component.empty();
        page.append(Component.translatable("game.emeraldweapons.haven.agenda.bourse")
                .withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_BLUE));
        page.append("\n\n");
        page.append(Component.translatable("game.emeraldweapons.haven.agenda.orbes", HavenProgress.orbs(id))
                .withStyle(ChatFormatting.GOLD));
        page.append("\n");
        page.append(Component.translatable("game.emeraldweapons.haven.agenda.orbes.trouves", HavenProgress.foundCount(id),
                HavenOrbs.total(player.server)).withStyle(ChatFormatting.BLACK));
        page.append("\n\n");
        page.append(Component.translatable("game.emeraldweapons.haven.agenda.bonus")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.UNDERLINE));
        boolean any = false;
        for (String key : List.of(HavenShop.PROVISIONS, HavenShop.RUNE, HavenShop.SEAL_FORGE, HavenShop.SEAL_RARITY,
                HavenShop.SEAL_SPECIALIZATION)) {
            int count = HavenProgress.bonus(id, key);
            if (count > 0) {
                page.append("\n");
                page.append(Component.translatable("game.emeraldweapons.haven.boutique." + key).append(" ×" + count)
                        .withStyle(ChatFormatting.BLACK));
                any = true;
            }
        }
        for (GunForm.Family family : GunForm.Family.values()) {
            if (HavenShop.unlimited(id, family)) {
                page.append("\n");
                page.append(Component.translatable("game.emeraldweapons.haven.boutique." + HavenShop.unlimitedKey(family))
                        .withStyle(ChatFormatting.BLACK));
                any = true;
            }
        }
        if (!any) {
            page.append("\n");
            page.append(Component.translatable("game.emeraldweapons.haven.agenda.bonus.aucun")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        return page;
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
        if (HavenDeparture.isOpen()) {
            return Component.translatable("game.emeraldweapons.haven.agenda.rdv.depart");
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
            case QUETE -> {
                QuestRun run = HavenQuests.runOf(player.getUUID());
                yield run == null ? Component.translatable("game.emeraldweapons.haven.agenda.rdv.quetes")
                        : Component.translatable("game.emeraldweapons.haven.agenda.rdv.quete",
                        run.quest.title(), run.quest.giver().displayName(), run.objective());
            }
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
