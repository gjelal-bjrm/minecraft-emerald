package com.emerald.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Un siege : des vagues de monstres a tenir autour d'un point.
 *
 * Sert aussi bien au prologue du village qu'aux rituels d'ancre, avec pour
 * seule difference le palier. Les monstres sont ATTACHES au lieu du siege par
 * restrictTo : sans cela ils s'eparpillent et le joueur passe la fin de la
 * vague a chercher le dernier survivant dans la foret.
 *
 * Le vivier est resolu a l'execution (voir SiegeRoster) : les factions du
 * modpack sont utilisees si elles sont installees, le vanilla sert de repli.
 * Aucune dependance n'est donc obligatoire.
 */
public class Siege {

    /** Rayon dans lequel les monstres sont tenus, et au-dela duquel ils ne vont pas. */
    public static final int LEASH = 40;

    private static final int SPAWN_MIN = 14;
    private static final int SPAWN_MAX = 26;
    private static final int WAVE_GAP = 5 * 20;

    /**
     * Ce qui fait perdre un siege.
     *
     * VILLAGERS : le prologue. On ne perd que si les villageois sont tous
     * tombes -- la mort d'un joueur ne compte pas, il reapparait et revient.
     * DEFENDERS : les rituels d'ancre. La disparition de tous les defenseurs
     * rompt le rituel, comme prevu au cahier.
     */
    public enum Failure { VILLAGERS, DEFENDERS }

    private final ServerLevel level;
    private final BlockPos center;
    private final int tier;
    private final Failure failure;
    private final int[] waveSizes;
    private final ServerBossEvent bar;
    private final List<UUID> alive = new ArrayList<>();

    private int wave;
    private int gap = 3 * 20;          // un temps de respiration avant la premiere vague
    private boolean done;
    private boolean won;

    public Siege(ServerLevel level, BlockPos center, int tier, int[] waveSizes,
                 Component title, BossEvent.BossBarColor color, Failure failure) {
        this.level = level;
        this.center = center;
        this.tier = tier;
        this.failure = failure;
        this.waveSizes = waveSizes;
        this.bar = new ServerBossEvent(title, color, BossEvent.BossBarOverlay.NOTCHED_10);
        this.bar.setProgress(1.0F);
        for (ServerPlayer player : level.players()) {
            this.bar.addPlayer(player);
        }
    }

    public boolean isDone() {
        return this.done;
    }

    public boolean isWon() {
        return this.won;
    }

    public BlockPos center() {
        return this.center;
    }

    public int remaining() {
        return this.alive.size();
    }

    /**
     * Avance le siege d'un tick.
     *
     * L'echec n'est pas la mort d'un joueur mais la disparition de TOUS les
     * defenseurs de la zone : en multijoueur, un joueur tombe doit pouvoir etre
     * couvert par les autres.
     */
    public void tick() {
        if (this.done) {
            return;
        }
        this.alive.removeIf(id -> {
            Entity entity = this.level.getEntity(id);
            return entity == null || !entity.isAlive();
        });
        refreshBar();

        if (this.failure == Failure.VILLAGERS) {
            if (villagersGone()) {
                end(false);
                return;
            }
            if (defendersGone()) {
                return;          // personne sur place : on suspend, on n'echoue pas
            }
        } else if (defendersGone()) {
            end(false);
            return;
        }
        if (!this.alive.isEmpty()) {
            return;
        }
        if (this.gap > 0) {
            this.gap--;
            return;
        }
        if (this.wave >= this.waveSizes.length) {
            end(true);
            return;
        }
        spawnWave(this.waveSizes[this.wave]);
        this.wave++;
        this.gap = WAVE_GAP;
    }

    /**
     * Le village est-il perdu ?
     *
     * C'est la seule condition d'echec du prologue : tant qu'il reste un
     * villageois debout, la defense continue. Un joueur tombe reapparait et
     * revient -- sa mort ne doit pas condamner l'equipe.
     */
    private boolean villagersGone() {
        return this.level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                new net.minecraft.world.phys.AABB(this.center).inflate(LEASH),
                e -> e.isAlive()).isEmpty();
    }

    private boolean defendersGone() {
        for (ServerPlayer player : this.level.players()) {
            if (player.isAlive() && !player.isSpectator()
                    && player.blockPosition().closerThan(this.center, LEASH * 1.5)) {
                return false;
            }
        }
        return true;
    }

    private void refreshBar() {
        int total = 0;
        for (int size : this.waveSizes) {
            total += size;
        }
        int killed = spawnedSoFar() - this.alive.size();
        this.bar.setProgress(1.0F - Math.min(1.0F, killed / (float) Math.max(1, total)));
        this.bar.setName(Component.translatable("game.emeraldweapons.siege.remaining",
                this.alive.size(), this.wave, this.waveSizes.length));
    }

    private int spawnedSoFar() {
        int n = 0;
        for (int i = 0; i < this.wave; i++) {
            n += this.waveSizes[i];
        }
        return n;
    }

    private void spawnWave(int count) {
        this.level.playSound(null, this.center, SoundEvents.RAID_HORN.value(),
                SoundSource.HOSTILE, 2.0F, 0.9F + 0.1F * this.tier);
        EntityType<?>[] roster = roster();
        for (int i = 0; i < count; i++) {
            EntityType<?> type = roster[this.level.random.nextInt(roster.length)];
            BlockPos pos = spawnPos();
            Entity entity = type.spawn(this.level, pos, MobSpawnType.EVENT);
            if (!(entity instanceof PathfinderMob mob)) {
                continue;
            }
            // c'est cette contrainte qui garantit que la vague reste trouvable
            mob.restrictTo(this.center, LEASH);
            mob.setPersistenceRequired();
            reinforce(mob);
            this.alive.add(mob.getUUID());
        }
        refreshBar();
    }

    /**
     * Durcit un monstre selon le palier.
     *
     * On passe par des modificateurs d'attribut plutot que par des valeurs en
     * dur : le renfort suit alors les regles du jeu, se lit dans les outils de
     * debogage, et n'ecrase pas ce que d'autres mods auraient applique.
     */
    private void reinforce(Mob mob) {
        if (this.tier <= 1) {
            return;
        }
        double bonus = 0.25 * (this.tier - 1);
        apply(mob, Attributes.MAX_HEALTH, "siege_health", bonus);
        apply(mob, Attributes.ATTACK_DAMAGE, "siege_damage", bonus * 0.6);
        apply(mob, Attributes.MOVEMENT_SPEED, "siege_speed", bonus * 0.2);
        mob.setHealth(mob.getMaxHealth());
    }

    private static void apply(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                              String name, double amount) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.addPermanentModifier(new AttributeModifier(
                ResourceLocation.fromNamespaceAndPath("emeraldweapons", name), amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private BlockPos spawnPos() {
        double angle = this.level.random.nextDouble() * Math.PI * 2;
        double radius = SPAWN_MIN + this.level.random.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
        int x = this.center.getX() + (int) Math.round(Math.cos(angle) * radius);
        int z = this.center.getZ() + (int) Math.round(Math.sin(angle) * radius);
        // hauteur prise sur le chunk lui-meme : getHeight rend -64 tant qu'il
        // n'est pas charge, et les monstres apparaitraient au fond du monde
        return new BlockPos(x, com.emerald.game.WorldSetup.surfaceY(this.level, x, z), z);
    }

    /**
     * Le vivier de la vague.
     *
     * Les factions du modpack sont tentees d'abord, et le vanilla sert de
     * repli. C'est ce qui permet au mode de tourner tel quel en developpement
     * comme sur un serveur nu, tout en devenant bien meilleur dans All the
     * Mods 10 -- sans qu'aucune de ces dependances ne soit obligatoire.
     */
    private EntityType<?>[] roster() {
        List<EntityType<?>> pool = new ArrayList<>();
        for (String id : SiegeRoster.forTier(this.tier)) {
            EntityType.byString(id).ifPresent(pool::add);
        }
        if (pool.size() >= 3) {
            return pool.toArray(new EntityType<?>[0]);
        }
        return SiegeRoster.vanillaFallback(this.tier);
    }

    private void end(boolean victory) {
        this.done = true;
        this.won = victory;
        this.bar.removeAllPlayers();
        if (!victory) {
            // un siege perdu ne laisse pas ses monstres derriere lui : la zone
            // doit redevenir abordable pour une seconde tentative
            for (UUID id : this.alive) {
                Entity entity = this.level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
        this.alive.clear();
    }

    /** Abandonne le siege sans le resoudre (arret de partie, dechargement). */
    public void cancel() {
        end(false);
    }

    public void addPlayer(ServerPlayer player) {
        this.bar.addPlayer(player);
    }
}
