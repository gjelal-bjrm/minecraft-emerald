package com.emerald.specialization;

import com.emerald.hero.HeroEvents;
import com.emerald.hero.HeroLevel;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.WingsSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * La specialisation du personnage : un palier de +0 a +20, des ailes qui
 * grandissent jusqu'a +15, une apparence, et des points de heros a chaque
 * palier. Elle SURVIT a la partie (voir SpecializationStore).
 *
 * L'amelioration se tente avec des Plumes d'Arcencium, comme une arme avec
 * des pierres de forge : un cout qui monte, une chance qui baisse, jamais
 * de retrogradation -- l'echec consomme les plumes, c'est tout. Chaque
 * palier reussi rend des points de heros : 3 par palier de +1 a +5, 5 de +6
 * a +10, 7 de +11 a +15, 9 de +16 a +20, soit 120 en tout.
 *
 * A +15 les ailes ont leur envergure pleine et acceptent une APPARENCE
 * (voir WingSkin), obtenue par une Plume d'apparence ; chaque apparence a
 * son bonus (voir SkinBonus).
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class Specialization {

    public static final int MAX = 20;
    public static final int WINGS_FULL = 15;

    /** Plumes qu'il faut pour tenter le palier vise (indice = palier vise). */
    /**
     * Le cout en plumes, PALIER PAR PALIER : 296 plumes de +0 a +20 si tout
     * reussissait. Avec les chances, il en faut environ 70 pour +10, 190 pour
     * +15 et 650 pour +20 -- et une partie en rapporte 150 a 180. Le joueur
     * l'a demande ainsi : facile parce qu'on en ramasse beaucoup, pas parce
     * qu'il en faut peu. Le +20 se gagne sur plusieurs parties, et il se garde.
     */
    public static final int[] COST = {0, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 12, 14, 16, 18, 20};
    /** Chance de reussite du palier vise, en pour cent. */
    public static final int[] ODDS = {0, 100, 95, 90, 85, 80, 75, 70, 65, 60, 55, 50, 45, 40, 35, 30, 25, 22, 19, 16, 13};

    private static final ResourceLocation ATTACK_ID = id("wings_attack");
    private static final ResourceLocation ARMOR_ID = id("wings_armor");
    private static final ResourceLocation HEALTH_ID = id("wings_health");
    private static final ResourceLocation SPEED_ID = id("wings_speed");
    private static final ResourceLocation CADENCE_ID = id("wings_cadence");

    private Specialization() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, path);
    }

    /** Les points de heros rendus par un palier. */
    public static int pointsFor(int level) {
        if (level <= 5) {
            return 3;
        }
        if (level <= 10) {
            return 5;
        }
        if (level <= 15) {
            return 7;
        }
        return 9;
    }

    // ---------------------------------------------------------------- lecture

    public static int level(Player player) {
        return SpecializationStore.get(player.getUUID()).level;
    }

    public static WingSkin skin(Player player) {
        WingSkin skin = WingSkin.byId(SpecializationStore.get(player.getUUID()).skin);
        return skin == null ? WingSkin.PRISMATIQUES : skin;
    }

    public static boolean unlocked(Player player, WingSkin skin) {
        return skin == WingSkin.PRISMATIQUES
                || SpecializationStore.get(player.getUUID()).unlocked.contains(skin.id());
    }

    // ---------------------------------------------------------------- ecriture

    /** Pour l'essai : pose directement un palier et une apparence. */
    public static void set(ServerPlayer player, int level, WingSkin skin) {
        SpecializationStore.Entry entry = SpecializationStore.get(player.getUUID());
        entry.level = Math.max(0, Math.min(MAX, level));
        if (skin != null) {
            entry.skin = skin.id();
            entry.unlocked.add(skin.id());
        }
        SpecializationStore.save();
        applyBonuses(player);
        sync(player);
    }

    /** Le resultat d'une tentative, pour le message et le son. */
    public enum Attempt { MAX, NOT_ENOUGH, SUCCESS, FAILURE }

    /**
     * Une tentative d'amelioration : prend les plumes, tire, et monte d'un
     * palier ou non. Les plumes partent dans les deux cas.
     */
    public static Attempt tryUpgrade(ServerPlayer player) {
        SpecializationStore.Entry entry = SpecializationStore.get(player.getUUID());
        if (entry.level >= MAX) {
            player.displayClientMessage(Component.translatable("specialization.emeraldweapons.max")
                    .withStyle(ChatFormatting.GOLD), true);
            return Attempt.MAX;
        }
        int target = entry.level + 1;
        int cost = COST[target];
        if (count(player) < cost) {
            player.displayClientMessage(Component.translatable("specialization.emeraldweapons.need", cost)
                    .withStyle(ChatFormatting.RED), true);
            return Attempt.NOT_ENOUGH;
        }
        consume(player, cost);
        boolean success = player.getRandom().nextInt(100) < ODDS[target];
        if (success) {
            entry.level = target;
            int points = pointsFor(target);
            HeroLevel.grantPoints(player, points);
            HeroEvents.sync(player);
            SpecializationStore.save();
            applyBonuses(player);
            sync(player);
            player.displayClientMessage(Component.translatable("specialization.emeraldweapons.success",
                    target, points).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 1.0F, 1.1F);
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 1.2F, 0.8F);
            com.emerald.util.Celebration.specialization(player, target, points);
            return Attempt.SUCCESS;
        }
        entry.failures++;
        SpecializationStore.save();
        player.displayClientMessage(Component.translatable("specialization.emeraldweapons.failure", cost)
                .withStyle(ChatFormatting.GRAY), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS, 0.8F, 0.9F);
        return Attempt.FAILURE;
    }

    /**
     * Une Plume d'apparence : a +15 et au-dela, debloque l'apparence (la
     * plume est alors consommee) ou y revient si elle l'est deja (la plume
     * reste). En dessous de +15, rien.
     */
    public static boolean applySkin(ServerPlayer player, WingSkin skin, ItemStack feather) {
        SpecializationStore.Entry entry = SpecializationStore.get(player.getUUID());
        if (entry.level < WINGS_FULL) {
            player.displayClientMessage(Component.translatable("specialization.emeraldweapons.skin_locked",
                    WINGS_FULL).withStyle(ChatFormatting.RED), true);
            return false;
        }
        boolean fresh = entry.unlocked.add(skin.id());
        entry.skin = skin.id();
        SpecializationStore.save();
        applyBonuses(player);
        sync(player);
        if (fresh) {
            feather.shrink(1);
        }
        player.displayClientMessage(Component.translatable("specialization.emeraldweapons.skin",
                Component.translatable("wings.emeraldweapons." + skin.id())).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    private static int count(Player player) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.ARCENCIUM_FEATHER.get())) {
                n += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(ModItems.ARCENCIUM_FEATHER.get())) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static void consume(Player player, int count) {
        int left = count;
        for (ItemStack stack : player.getInventory().items) {
            if (left > 0 && stack.is(ModItems.ARCENCIUM_FEATHER.get())) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (left > 0 && stack.is(ModItems.ARCENCIUM_FEATHER.get())) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
    }

    // ------------------------------------------------------------------ bonus

    /** Les bonus d'attribut de l'apparence : attaque, defense, vie, vitesse, cadence, en pour cent du total. */
    public static void applyBonuses(Player player) {
        SkinBonus.Bonus bonus = SkinBonus.active(player);
        modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, bonus.attack() / 100.0);
        modifier(player, Attributes.ARMOR, ARMOR_ID, bonus.defense() / 100.0);
        modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, bonus.health() / 100.0);
        modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, bonus.speed() / 100.0);
        modifier(player, Attributes.ATTACK_SPEED, CADENCE_ID, bonus.cadence() / 100.0);
    }

    private static void modifier(Player player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                 ResourceLocation id, double value) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (value != 0.0) {
            instance.addPermanentModifier(new AttributeModifier(id, value,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    // ------------------------------------------------------------------ reseau

    /** Envoie les ailes du joueur a lui-meme et a tous ceux qui le voient. */
    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new WingsSyncPayload(player.getId(), level(player), skin(player).ordinal()));
    }

    // -------------------------------------------------------------- evenements

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        SpecializationStore.load(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SpecializationStore.save();
    }

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyBonuses(player);
            sync(player);
            // Le personnage appartient desormais au MONDE. Celui qui en avait un
            // avant doit pouvoir le reprendre, et ne peut pas deviner comment.
            if (SpecializationStore.legacyPending()) {
                player.sendSystemMessage(Component.translatable(
                                "specialization.emeraldweapons.legacy")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyBonuses(player);
            sync(player);
        }
    }

    /** Un nouveau spectateur : il recoit les ailes de celui qu'il commence a voir. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer seen && event.getEntity() instanceof ServerPlayer watcher) {
            PacketDistributor.sendToPlayer(watcher,
                    new WingsSyncPayload(seen.getId(), level(seen), skin(seen).ordinal()));
        }
    }

    /** La regeneration de l'Aurore : hors combat, un demi-coeur toutes les cinq secondes. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 100 != 0) {
            return;
        }
        if (SkinBonus.active(player).regen() && player.getHealth() < player.getMaxHealth()
                && player.tickCount - player.getLastHurtByMobTimestamp() > 200) {
            player.heal(1.0F);
        }
    }
}
