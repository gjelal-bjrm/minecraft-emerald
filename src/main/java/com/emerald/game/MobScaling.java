package com.emerald.game;

import com.emerald.hero.HeroEvents;
import com.emerald.hero.HeroLevel;
import com.emerald.hero.HeroStat;
import com.emerald.item.GearEligibility;
import com.emerald.item.GearRarity;
import com.emerald.item.Upgrade;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.rune.RuneEvents;
import com.emerald.rune.RuneFamily;
import com.emerald.rune.RuneMark;
import com.emerald.rune.Runes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LES MONSTRES S'ALIGNENT SUR LES JOUEURS (25 sept. 2026, cahier §104).
 *
 * « Au fur et a mesure que le joueur evolue, les monstres apparaissent de facon equilibree :
 * un joueur en arme et armure +3, avec des runes de rarete 2, rencontre des monstres de
 * rarete 0 a 2, en +0 a +3, runes de rarete 1 a 2, et d'un niveau egal au sien ou de 10 a
 * 15 % plus faible, au hasard. S'il y a plusieurs joueurs, une moyenne entre le plus faible
 * et le plus fort. » Et pour le boss final : equipement de rarete 8, de +8 a +10, runes de
 * rarete 8 a l'arme et a l'armure, niveau 99 reparti a parts egales, sans casque.
 *
 * Avant, MobGear suivait l'HORLOGE de la partie, jamais ce que portait le joueur ; ni les
 * runes ni la fiche du Heros ne s'appliquaient aux monstres. Un joueur en +7 a la trentieme
 * minute tuait d'un coup ce qu'on lui envoyait, et le disait : « je les one-shot ».
 *
 * CE QUI SE PASSE. Tout monstre hostile qui apparait pendant une partie est habille UNE
 * fois, a la tique qui suit son apparition (les autres systemes ont alors fini de le poser
 * et de le marquer) :
 *   - la REFERENCE est lue sur les joueurs : leur meilleure arme, leur armure portee, le
 *     rang de leurs runes, leur niveau Heros ; le milieu entre le plus faible et le plus fort ;
 *   - le monstre tire sa rarete de 0 a la reference, son amelioration de +0 a la reference,
 *     une rune de rang 1 a la reference sur l'arme et sur chaque piece d'armure, et un niveau
 *     de 85 a 100 % du niveau de reference ;
 *   - ses points de Heros se repartissent AUTOUR de l'equilibre, au hasard (choix du joueur) :
 *     certains plus costauds, d'autres plus offensifs ;
 *   - la matiere de son armure suit l'armure des joueurs (cuir, maille, fer, diamant), un cran
 *     en dessous ;
 *   - les monstres sans mains (araignees, golems, loups) recoivent le meme equipement : il ne
 *     se voit pas, mais il compte (choix du joueur) ;
 *   - les boss des autres mods ne recoivent que le niveau (choix du joueur) ; le boss final,
 *     tout ce que le joueur a decrit.
 * L'equipement peut tomber a la mort, comme avant (1,5 %) : « ca ne vaut pas de l'Arcencium ».
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class MobScaling {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Marque un monstre deja habille : il ne l'est jamais deux fois. */
    public static final String TAG = "emeraldweapons_scaled";
    /** Ses niveaux dans les quatre voies, lus par HeroCombat et RuneEvents. */
    private static final String DATA = "EmeraldHero";
    /** Au-dela de ces points de vie de base, un monstre est un boss : le niveau seul. */
    public static final double BOSS_HEALTH = 150.0;
    /** Le niveau du boss final. */
    public static final int FINAL_BOSS_LEVEL = 99;

    /** Ce que valent les joueurs, a un instant. */
    public record Reference(double weaponUpgrade, double weaponRarity, double armourUpgrade,
                            double armourRarity, double runeRank, double heroLevel, double armourPoints,
                            double damage, int players) {
        static final Reference NONE = new Reference(0, 0, 0, 0, 0, 1, 0, 1, 0);

        /** La meme reference, sans les degats d'un coup (pour le banc, qui fixe les bornes a la main). */
        public Reference with(double weaponUpgrade, double weaponRarity, double armourUpgrade, double armourRarity,
                              double runeRank, double heroLevel) {
            return new Reference(weaponUpgrade, weaponRarity, armourUpgrade, armourRarity, runeRank, heroLevel,
                    this.armourPoints, this.damage, this.players);
        }
    }

    /**
     * COMBIEN DE COUPS DU JOUEUR POUR TUER UN MONSTRE DE SON NIVEAU. Le miroir seul ne
     * suffisait pas, et c'est le banc qui l'a dit : un zombie habille comme un joueur en +7
     * mourait en 1,8 coup (1,5 avant), parce qu'un +7 frappe 18 et qu'un zombie a vingt points
     * de vie -- la fiche du Heros ne lui en ajoute que deux ou trois, et l'armure plafonne.
     * Les points de vie suivent donc la force du joueur : un monstre ordinaire de son niveau
     * encaisse environ QUATRE de ses coups, un peu plus s'il a mis ses points en Vitalite,
     * plus aussi pour les grandes especes (la racine de leurs points de vie de base).
     *
     * Le calcul part des degats BRUTS d'un coup (arme, fiche, runes). Les critiques, l'element
     * et les declenchements y ajoutent en moyenne quarante a soixante pour cent : 4 coups
     * BRUTS en faisaient 2,8 au banc, 5,6 en faisaient de 2,9 a 3,6 pour un zombie. D'ou 6,4
     * coups bruts, qui en font environ quatre vrais.
     */
    public static final double HITS_TO_KILL = 6.4;
    private static final net.minecraft.resources.ResourceLocation HEALTH_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "scaling_health");

    private static final Map<ServerLevel, List<UUID>> PENDING = new HashMap<>();
    private static Reference cached = Reference.NONE;
    private static long cachedAt = Long.MIN_VALUE;

    private MobScaling() {
    }

    // ================================================================ la reference

    /** La reference d'un joueur seul. */
    public static Reference of(Player player) {
        ItemStack weapon = ItemStack.EMPTY;
        int best = -1;
        List<ItemStack> carried = new ArrayList<>(player.getInventory().items);
        carried.addAll(player.getInventory().offhand);
        for (ItemStack stack : carried) {
            if (GearEligibility.isWeapon(stack)) {
                int score = Upgrade.of(stack) * 2 + GearRarity.of(stack).rank();
                if (score > best) {
                    best = score;
                    weapon = stack;
                }
            }
        }
        double armourUp = 0;
        double armourRank = 0;
        int runeRank = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = player.getItemBySlot(slot);
            armourUp += Upgrade.of(worn);
            armourRank += GearRarity.of(worn).rank();
            runeRank = Math.max(runeRank, bestRune(worn));
        }
        runeRank = Math.max(runeRank, bestRune(weapon));
        return new Reference(Upgrade.of(weapon), GearRarity.of(weapon).rank(), armourUp / 4.0, armourRank / 4.0,
                runeRank, HeroLevel.level(player), player.getArmorValue(), strike(player, weapon), 1);
    }

    /**
     * Les degats d'un coup de cette arme, pour ce joueur : sa fiche et ses runes comptent,
     * l'arme qu'il tient a l'instant non (il peut tenir une pioche). La formule du jeu :
     * (base + ajouts) x (1 + multiplicateurs de base) x multiplicateurs du total.
     */
    public static double strike(Player player, ItemStack weapon) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) {
            return 1.0;
        }
        java.util.Set<net.minecraft.resources.ResourceLocation> held = new java.util.HashSet<>();
        player.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (a, m) -> held.add(m.id()));
        double add = attack.getBaseValue();
        double base = 0.0;
        double total = 1.0;
        // ce que porte le joueur, SANS ce que tient sa main ; puis l'arme, une seule fois
        List<net.minecraft.world.entity.ai.attributes.AttributeModifier> all = new ArrayList<>();
        for (var modifier : attack.getModifiers()) {
            if (!held.contains(modifier.id())) {
                all.add(modifier);
            }
        }
        weapon.forEachModifier(EquipmentSlot.MAINHAND, (a, m) -> {
            if (a.is(Attributes.ATTACK_DAMAGE)) {
                all.add(m);
            }
        });
        for (var modifier : all) {
            switch (modifier.operation()) {
                case ADD_VALUE -> add += modifier.amount();
                case ADD_MULTIPLIED_BASE -> base += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> total *= 1.0 + modifier.amount();
            }
        }
        return Math.max(1.0, add * (1.0 + base) * total);
    }

    private static int bestRune(ItemStack stack) {
        int best = 0;
        for (RuneMark mark : Runes.on(stack)) {
            best = Math.max(best, mark.rank());
        }
        return best;
    }

    /**
     * La reference d'un groupe : pour chaque grandeur, le MILIEU entre le plus faible et le
     * plus fort (« une moyenne entre le joueur le plus faible et le joueur le plus fort »).
     */
    public static Reference of(List<? extends Player> players) {
        List<Reference> refs = new ArrayList<>();
        for (Player player : players) {
            refs.add(of(player));
        }
        if (refs.isEmpty()) {
            return Reference.NONE;
        }
        return new Reference(
                mid(refs, Reference::weaponUpgrade), mid(refs, Reference::weaponRarity),
                mid(refs, Reference::armourUpgrade), mid(refs, Reference::armourRarity),
                mid(refs, Reference::runeRank), mid(refs, Reference::heroLevel),
                mid(refs, Reference::armourPoints), mid(refs, Reference::damage), refs.size());
    }

    private static double mid(List<Reference> refs, java.util.function.ToDoubleFunction<Reference> get) {
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (Reference r : refs) {
            double v = get.applyAsDouble(r);
            low = Math.min(low, v);
            high = Math.max(high, v);
        }
        return (low + high) / 2.0;
    }

    /** La reference de la partie en cours : les joueurs hors de Haven, relue au plus une fois par seconde. */
    public static Reference current(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now - cachedAt < 20) {
            return cached;
        }
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator() && !com.emerald.haven.Haven.is(player.level())) {
                players.add(player);
            }
        }
        cached = of(players);
        cachedAt = now;
        return cached;
    }

    // ============================================================ l'apparition

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof Mob mob) || !(mob instanceof Enemy)
                || mob.getTags().contains(TAG)) {
            return;
        }
        PENDING.computeIfAbsent(level, l -> new ArrayList<>()).add(mob.getUUID());
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<UUID> waiting = PENDING.remove(level);
        if (waiting == null || waiting.isEmpty() || !inGame(level)) {
            return;
        }
        for (UUID id : waiting) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Mob mob && mob.isAlive()) {
                scaleNow(mob, level.random);
            }
        }
    }

    /** Pour le banc : habille tout de suite ce qui attend la tique suivante. */
    public static void flushForAutotest(ServerLevel level) {
        cachedAt = Long.MIN_VALUE;                  // la reference du moment, pas celle d'avant
        List<UUID> waiting = PENDING.remove(level);
        if (waiting == null || !inGame(level)) {
            return;
        }
        for (UUID id : waiting) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Mob mob && mob.isAlive()) {
                scaleNow(mob, level.random);
            }
        }
    }

    /** Une partie en cours (prologue compris), mode allume, hors de Haven. */
    private static boolean inGame(ServerLevel level) {
        if (ModeSwitch.off() || com.emerald.haven.Haven.is(level)) {
            return false;
        }
        GameState.Status status = GameState.get(level.getServer().overworld()).status();
        return status == GameState.Status.RUNNING || status == GameState.Status.PROLOGUE;
    }

    /**
     * Habille un monstre selon la reference des joueurs, s'il ne l'est pas deja. Appele a
     * l'apparition, et par MobGear.equip pour les systemes qui posent leurs monstres eux-memes.
     */
    public static void scaleNow(Mob mob, RandomSource random) {
        if (mob.getTags().contains(TAG) || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        if (mob instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
            return;                                 // ce qu'un joueur a invoque ou apprivoise
        }
        Reference ref = current(level.getServer());
        if (mob.getTags().contains(Finale.TAG_BOSS)) {
            scaleFinalBoss(mob, random);
        } else if (isBoss(mob)) {
            scaleLevelOnly(mob, ref, random);
        } else {
            scale(mob, ref, random);
        }
    }

    /** Un boss : tag commun des boss, boss final, ou un monstre de base tres robuste. */
    public static boolean isBoss(LivingEntity entity) {
        if (entity.getTags().contains(Finale.TAG_BOSS) || entity.getType().is(Tags.EntityTypes.BOSSES)) {
            return true;
        }
        AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
        return health != null && health.getBaseValue() >= BOSS_HEALTH;
    }

    // ================================================================ l'habillage

    /** Un monstre ordinaire : equipement, runes et niveau, tires sous la reference. */
    public static void scale(Mob mob, Reference ref, RandomSource random) {
        int level = rollLevel(ref.heroLevel(), random);
        int[] paths = allocate(totalPoints(level), weights(random, false));
        MobGear.dress(mob, ref, random);
        finish(mob, level, paths);
        toughen(mob, ref, level, paths);
    }

    /** Un boss d'un autre mod : le niveau seul, sans equipement. */
    public static void scaleLevelOnly(Mob mob, Reference ref, RandomSource random) {
        int level = rollLevel(ref.heroLevel(), random);
        // LE NIVEAU SEUL, comme convenu : ni equipement, ni points de vie en plus. Ces boss ont
        // deja des centaines de points de vie ; les gonfler encore les rendrait interminables.
        finish(mob, level, allocate(totalPoints(level), weights(random, true)));
    }

    /** Le boss final : rarete 8, +8 a +10, runes de rang 8, niveau 99 a parts egales, sans casque. */
    public static void scaleFinalBoss(Mob mob, RandomSource random) {
        MobGear.dressFinalBoss(mob, random);
        finish(mob, FINAL_BOSS_LEVEL, allocate(totalPoints(FINAL_BOSS_LEVEL), weights(random, true)));
    }

    /**
     * LES POINTS DE VIE QUI SUIVENT LE JOUEUR (voir HITS_TO_KILL). On pose d'abord les
     * attributs de l'equipement (le jeu ne le fait qu'a la tique suivante, et le calcul a
     * besoin de l'armure), puis on vise : quatre coups du joueur apres l'armure du monstre,
     * a proportion de son niveau, plus pour la Vitalite et les grandes especes. On n'abaisse
     * jamais : un monstre deja robuste le reste.
     */
    static void toughen(Mob mob, Reference ref, int level, int[] paths) {
        wearNow(mob);
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }
        double average = Math.max(1.0, (paths[0] + paths[1] + paths[2] + paths[3]) / 4.0);
        double vitality = Math.max(0.8, Math.min(1.4, 0.8 + 0.2 * paths[3] / average));
        double species = Math.max(0.6, Math.min(2.5, Math.sqrt(health.getBaseValue() / 20.0)));
        double share = ref.heroLevel() <= 1.0 ? 1.0 : Math.min(1.0, level / ref.heroLevel());
        double hits = HITS_TO_KILL * vitality * species * share;
        float armour = mob.getArmorValue();
        float toughness = (float) mob.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float landed = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(mob, (float) ref.damage(),
                mob.damageSources().generic(), armour, toughness);
        double wanted = hits * landed;
        health.removeModifier(HEALTH_ID);
        double missing = wanted - health.getValue();
        if (missing > 0.0) {
            health.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    HEALTH_ID, missing, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
        mob.setHealth(mob.getMaxHealth());
    }

    /** Les attributs de l'equipement porte, poses tout de suite ; le jeu les reposera a l'identique. */
    public static void wearNow(LivingEntity entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            stack.forEachModifier(slot, (attribute, modifier) -> {
                AttributeInstance instance = entity.getAttribute(attribute);
                if (instance != null) {
                    instance.addOrUpdateTransientModifier(modifier);
                }
            });
        }
    }

    private static void finish(Mob mob, int level, int[] paths) {
        CompoundTag data = new CompoundTag();
        data.putInt("Level", level);
        data.putIntArray("Paths", paths);
        mob.getPersistentData().put(DATA, data);
        mob.addTag(TAG);
        HeroEvents.applyPaths(mob, paths[0], paths[1], paths[2], paths[3]);
        RuneEvents.apply(mob);
        mob.setHealth(mob.getMaxHealth());
    }

    /** Un niveau de 85 a 100 % de la reference, au hasard (« soit le meme, soit 10 a 15 % plus faible »). */
    static int rollLevel(double reference, RandomSource random) {
        double factor = 0.85 + random.nextDouble() * 0.15;
        return Math.max(1, Math.min(HeroLevel.MAX_LEVEL, (int) Math.round(reference * factor)));
    }

    /** Les points qu'un joueur aurait gagnes jusqu'a ce niveau. */
    public static int totalPoints(int level) {
        int total = 0;
        for (int l = 2; l <= level; l++) {
            total += HeroLevel.pointsFor(l);
        }
        return total;
    }

    /** Les poids des quatre voies : egaux pour un boss, autour de l'equilibre sinon. */
    private static double[] weights(RandomSource random, boolean even) {
        double[] w = new double[4];
        for (int i = 0; i < 4; i++) {
            w[i] = even ? 1.0 : Math.max(0.25, Math.min(2.2, 1.0 + random.nextGaussian() * 0.45));
        }
        return w;
    }

    /**
     * Achete des niveaux dans les quatre voies (Attaque, Element, Defense, Vitalite), au prix
     * du joueur, en gardant chaque voie proche de sa part : on achete toujours dans la voie la
     * plus en retard sur son poids.
     */
    public static int[] allocate(int points, double[] weights) {
        int[] levels = new int[4];
        while (true) {
            int pick = -1;
            double lowest = Double.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                if (levels[i] >= HeroStat.MAX_PATH || HeroStat.cost(levels[i]) > points) {
                    continue;
                }
                double lag = (levels[i] + 1) / weights[i];
                if (lag < lowest) {
                    lowest = lag;
                    pick = i;
                }
            }
            if (pick < 0) {
                break;
            }
            points -= HeroStat.cost(levels[pick]);
            levels[pick]++;
        }
        return levels;
    }

    // ================================================================= lecture

    /** Le niveau d'un monstre habille dans une voie ; zero sinon. */
    public static int path(LivingEntity entity, HeroStat stat) {
        CompoundTag data = entity.getPersistentData().getCompound(DATA);
        int[] paths = data.getIntArray("Paths");
        if (paths.length != 4) {
            return 0;
        }
        return switch (stat) {
            case ATTAQUE -> paths[0];
            case ELEMENT -> paths[1];
            case DEFENSE -> paths[2];
            case VITALITE -> paths[3];
        };
    }

    /** Le niveau de Heros d'un monstre habille ; zero sinon. */
    public static int level(LivingEntity entity) {
        return entity.getPersistentData().getCompound(DATA).getInt("Level");
    }

    /** Vrai pour un monstre que cette classe a habille : ses runes et sa fiche comptent. */
    public static boolean scaled(@Nullable Entity entity) {
        return entity instanceof Mob && entity.getTags().contains(TAG);
    }
}
