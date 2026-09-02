package com.emerald.game;

import com.emerald.compat.ApotheosisTiers;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.weather.WeatherEffects;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Le pont vers Apotheosis : de la Chance, et de quoi forger.
 *
 * Apotheosis se joue normalement sur la duree -- on accumule ses materiaux au
 * fil des heures, et le pack de quetes ouvre ses paliers un a un. Une partie
 * dure soixante minutes : rien de tout cela n'a le temps d'arriver, et un
 * systeme d'equipement entier resterait decoratif. On le deverrouille donc a
 * la main, au rythme des phases.
 *
 * Deux leviers, et le premier compte plus que le second :
 *
 * 1. La CHANCE. C'est le levier documente d'Apotheosis pour la rarete de son
 *    butin : plus elle est haute, plus les objets a affixes tombent rares. On
 *    l'accorde par phase, ce qui fait monter tout le systeme d'un coup sans
 *    toucher a ses tables.
 * 2. Les MATERIAUX. Reforger, sertir, augmenter -- tout se paie en materiaux
 *    par rarete. Ils tombent des monstres nes des tempetes et de ceux de la
 *    Maree, la ou le joueur prend des risques.
 *
 * Tout passe par une resolution au nom, comme {@link SiegeRoster} : le mode
 * tourne sans Apotheosis, il est seulement plus riche avec.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class ApotheosisLoot {

    private static final ResourceLocation LUCK_ID =
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "phase_luck");

    /**
     * Les materiaux d'Apotheosis, du plus commun au plus rare.
     *
     * L'ordre compte : c'est lui qui sert d'echelle de progression. Le dernier,
     * la Manifestation de l'Infini, ne tombe jamais d'un monstre ordinaire.
     */
    private static final String[] MATERIALS = {
            "apotheosis:common_material",
            "apotheosis:uncommon_material",
            "apotheosis:rare_material",
            "apotheosis:epic_material",
            "apotheosis:mythic_material",
    };

    /** Ce qui sert a MODIFIER un objet : le vrai plaisir du systeme. */
    private static final String[] SIGILS = {
            "apotheosis:sigil_of_socketing",
            "apotheosis:sigil_of_rebirth",
            "apotheosis:sigil_of_enhancement",
            "apotheosis:sigil_of_withdrawal",
            "apotheosis:vial_of_extraction",
            "apotheosis:gem_dust",
            "apotheosis:gem_fused_slate",
    };

    /**
     * Les paliers de monde d'Apotheosis, dans l'ordre.
     *
     * C'est LE systeme de progression du mod : il gouverne la rarete du butin,
     * la force des monstres, et surtout l'apparition des Envahisseurs -- ces
     * « boss » qui sont des monstres ordinaires nommes, rares et equipes. Au
     * palier Haven, leur chance d'apparition vaut zero : tant qu'on y reste,
     * il n'en sort aucun.
     *
     * Chaque palier s'ouvre par un avancement, normalement gagne en portant un
     * jeu complet d'objets a affixes. En soixante minutes personne n'y arrive,
     * alors on les accorde au rythme des phases.
     */
    private ApotheosisLoot() {
    }

    // ------------------------------------------------------- les paliers

    /** Le palier vise par la phase courante. */
    private static int tierFor(GamePhase phase) {
        return switch (phase) {
            case EXPLORATION -> 1;             // Frontier : les Envahisseurs commencent
            case MONTEE -> 2;                  // Ascent
            case PRESSION -> 3;                // Summit
            case ASSAUT -> 4;                  // Pinnacle
            default -> 0;
        };
    }

    /** Vrai si Apotheosis est la : sans quoi on ne touche pas a la classe de pont. */
    private static boolean present() {
        return net.neoforged.fml.ModList.get().isLoaded("apotheosis");
    }

    /**
     * Porte le joueur au palier de sa phase, sans rien lui demander.
     *
     * Deux gestes, et les deux comptent. On ECRIT le palier actif, ce qui est
     * le seul moyen de ne rien exiger du joueur : accorder l'avancement se
     * contente d'ouvrir la porte, et laissait un CTRL+T que personne ne devine.
     * Et on accorde quand meme les avancements, faute de quoi l'ecran de
     * selection d'Apotheosis afficherait comme verrouille le palier ou le
     * joueur se trouve deja.
     */
    private static void unlockTiers(ServerLevel level, ServerPlayer player, GamePhase phase) {
        if (!present()) {
            return;
        }
        int target = Math.min(tierFor(phase), ApotheosisTiers.count() - 1);
        for (int i = 0; i <= target; i++) {
            AdvancementHolder holder = advancement(level, ApotheosisTiers.unlockAdvancement(i));
            if (holder != null) {
                grant(player, holder);
            }
        }
        if (ApotheosisTiers.raiseTo(player, target)) {
            player.displayClientMessage(Component
                    .translatable("game.emeraldweapons.tier_unlocked",
                            Component.translatable(
                                    "text.apotheosis.world_tier." + ApotheosisTiers.name(target)))
                    .withStyle(net.minecraft.ChatFormatting.GOLD), false);
            player.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.4F);
        }
    }

    @Nullable
    private static AdvancementHolder advancement(ServerLevel level, @Nullable ResourceLocation key) {
        return key == null ? null : level.getServer().getAdvancements().get(key);
    }

    private static void grant(ServerPlayer player, AdvancementHolder holder) {
        var progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) {
            return;
        }
        // une copie : award() modifie la progression pendant qu'on la parcourt,
        // et getRemainingCriteria n'est qu'un Iterable, pas une collection
        List<String> remaining = new ArrayList<>();
        progress.getRemainingCriteria().forEach(remaining::add);
        for (String criterion : remaining) {
            player.getAdvancements().award(holder, criterion);
        }
    }

    // ------------------------------------------------------------ la Chance

    /**
     * La Chance accordee par la phase.
     *
     * Elle monte franchement : deux points ne se voient pas sur une table de
     * butin, six changent ce qu'on ramasse. C'est le remplacant assume des
     * quetes du pack, qu'on n'a pas le temps de faire.
     */
    public static int luckFor(GamePhase phase) {
        return switch (phase) {
            case EXPLORATION -> 2;
            case MONTEE -> 4;
            case PRESSION -> 7;
            case ASSAUT -> 10;
            default -> 0;
        };
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)
                || level.getGameTime() % 40 != 0
                || ModeSwitch.off()) {
            return;
        }
        GameState state = GameState.get(level);
        if (state.status() != GameState.Status.RUNNING) {
            // hors partie la Chance retombe d'elle-meme : rien a nettoyer a la
            // main, et aucun moyen de l'emporter dans la partie suivante
            clearLuck(level);
            return;
        }
        GamePhase phase = state.phase(level);
        double luck = luckFor(phase);
        for (ServerPlayer player : level.players()) {
            applyLuck(player, luck);
            unlockTiers(level, player, phase);
        }
    }

    /**
     * Le modificateur est TRANSITOIRE et repose a chaque palier.
     *
     * Permanent, il se serait cumule d'une partie a l'autre dans le meme monde
     * et aurait survecu a la fin de la partie -- une chance de dix points
     * emportee dans le monde d'apres.
     */
    private static void applyLuck(ServerPlayer player, double luck) {
        AttributeInstance attribute = player.getAttribute(Attributes.LUCK);
        if (attribute == null) {
            return;
        }
        AttributeModifier existing = attribute.getModifier(LUCK_ID);
        if (existing != null && existing.amount() == luck) {
            return;
        }
        if (existing != null) {
            attribute.removeModifier(LUCK_ID);
        }
        attribute.addTransientModifier(new AttributeModifier(
                LUCK_ID, luck, AttributeModifier.Operation.ADD_VALUE));
    }

    /** Retire la Chance de partie : appele quand la partie s'arrete. */
    public static void clearLuck(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            AttributeInstance attribute = player.getAttribute(Attributes.LUCK);
            if (attribute != null) {
                attribute.removeModifier(LUCK_ID);
            }
        }
    }

    // --------------------------------------------------------- les materiaux

    @Nullable
    private static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return null;                       // Apotheosis absent : on s'abstient
        }
        return BuiltInRegistries.ITEM.get(key);
    }

    /**
     * Un materiau dont la rarete suit la phase.
     *
     * On tire autour d'un palier plutot que sur lui : la phase donne le
     * centre, le hasard donne un cran au-dessus ou en dessous. Sans cela, une
     * phase entiere ne rend qu'un seul materiau et la progression se voit
     * comme un escalier.
     */
    @Nullable
    public static ItemStack materialFor(RandomSource random, GamePhase phase) {
        int center = switch (phase) {
            case EXPLORATION -> 0;
            case MONTEE -> 1;
            case PRESSION -> 2;
            case ASSAUT -> 3;
            default -> 0;
        };
        int tier = Math.max(0, Math.min(MATERIALS.length - 1,
                center + random.nextInt(3) - 1));
        Item found = item(MATERIALS[tier]);
        return found == null ? null : new ItemStack(found, 1 + random.nextInt(2));
    }

    @Nullable
    public static ItemStack sigilFor(RandomSource random) {
        List<Item> pool = new ArrayList<>();
        for (String id : SIGILS) {
            Item found = item(id);
            if (found != null) {
                pool.add(found);
            }
        }
        return pool.isEmpty() ? null
                : new ItemStack(pool.get(random.nextInt(pool.size())));
    }

    /**
     * Ce que lache un monstre de tempete ou de Maree.
     *
     * Les chances sont volontairement hautes -- un materiau une fois sur
     * quatre, un sigil une fois sur douze. En soixante minutes, une table de
     * butin ordinaire ne rendrait presque rien, et le systeme resterait un
     * decor qu'on n'a jamais les moyens d'utiliser.
     */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        boolean storm = entity.getTags().contains(WeatherEffects.TAG_STORM);
        boolean tide = entity.getTags().contains(PrismaticTide.TAG_TIDE);

        // L'ECLAT DU DESTIN TOMBE DE TOUT CE QUI SE BAT.
        //
        // Il se compte avant la porte des tempetes, car il ne doit pas dependre
        // d'une meteo : c'est la matiere des tentatives de rarete, et une
        // matiere qu'on ne trouve que par temps d'orage ferait attendre le
        // joueur au lieu de le faire jouer. Une fois sur douze au combat
        // ordinaire, une fois sur quatre sous la Maree ou l'orage -- ceux-la
        // frappent plus fort, ils paient davantage.
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player
                && entity instanceof net.minecraft.world.entity.Mob
                && !(entity instanceof net.minecraft.world.entity.npc.AbstractVillager)
                && !(entity instanceof net.minecraft.world.entity.animal.Animal)) {
            int odds = storm || tide ? 4 : 12;
            if (level.random.nextInt(odds) == 0) {
                event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                        level, entity.getX(), entity.getY() + 0.5, entity.getZ(),
                        new ItemStack(com.emerald.item.ModItems.FATE_SHARD.get())));
            }
            // L'ARCENCIUM TOMBE AUSSI DES MONSTRES.
            //
            // La mine seule ne suffit pas : elle demande de descendre, de
            // s'eclairer et de remonter, quand le reste du mode se joue en
            // surface. Un monstre sur huit en donne un morceau, un sur trois
            // sous la tempete ou dans la Maree -- de quoi payer une ancre en
            // se battant, ce qui est la facon de jouer que le mode recompense.
            int oreOdds = storm || tide ? 3 : 8;
            if (level.random.nextInt(oreOdds) == 0) {
                int amount = storm || tide ? 1 + level.random.nextInt(2) : 1;
                event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                        level, entity.getX(), entity.getY() + 0.5, entity.getZ(),
                        new ItemStack(com.emerald.item.ModItems.RAW_ARCENCIUM.get(), amount)));
            }
        }

        if (!storm && !tide) {
            return;
        }
        GamePhase phase = GameState.get(level).phase(level);
        RandomSource random = level.random;
        // la Maree est plus dangereuse que les tempetes : elle paie mieux
        int materialOdds = tide ? 2 : 4;
        int sigilOdds = tide ? 5 : 12;

        List<ItemStack> extra = new ArrayList<>();
        if (random.nextInt(materialOdds) == 0) {
            ItemStack material = materialFor(random, phase);
            if (material != null) {
                extra.add(material);
            }
        }
        if (random.nextInt(sigilOdds) == 0) {
            ItemStack sigil = sigilFor(random);
            if (sigil != null) {
                extra.add(sigil);
            }
        }
        for (ItemStack stack : extra) {
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                    level, entity.getX(), entity.getY() + 0.5, entity.getZ(), stack));
        }
    }
}
