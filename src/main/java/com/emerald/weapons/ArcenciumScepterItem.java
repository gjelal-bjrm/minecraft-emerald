package com.emerald.weapons;

import com.emerald.entity.PrismaticBoltEntity;
import com.emerald.particles.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Sceptre d'Arcencium -- la Concorde.
 *
 * Troisieme membre de la famille : l'epee est la Fureur, l'arc la Tension.
 * Deliberement plus faible que les deux autres en degats, parce que sa valeur
 * est ailleurs -- il soigne, il repousse, il ouvre des breches pour les autres.
 *
 * Clic gauche : un trait prismatique. Il blesse peu, mais soigne un allie
 *               touche. Voir {@link PrismaticBoltEntity}.
 * Clic droit  : l'Onde de Concorde. Repousse les monstres alentour et donne
 *               aux allies regeneration et armure.
 *
 * Le rechargement se lit sur l'objet lui-meme : les cinq eclats du bandeau
 * s'allument un a un (predicat "charge", voir ArcenciumScepterClient).
 */
public class ArcenciumScepterItem extends Item {

    // --- Onde de Concorde
    public static final int WAVE_COOLDOWN = 25 * 20;      // 25 s
    public static final double WAVE_RADIUS = 10.0;
    public static final int REGEN_TICKS = 8 * 20;
    public static final int ARMOR_TICKS = 15 * 20;
    private static final double KNOCKBACK = 2.3;

    // --- trait prismatique
    // La cadence, et non la vitesse d'attaque, est ce qui bride reellement le
    // sceptre : l'objet ne porte aucun modificateur de vitesse, il frappe donc
    // deja au maximum. C'est ce delai-la qu'il fallait raccourcir.
    public static final int BOLT_COOLDOWN = 5;            // 0,25 s entre deux tirs
    private static final float BOLT_SPEED = 1.9F;
    private static final float BOLT_SPREAD = 0.3F;

    // --- fatigue : le matraquage se paie
    /** Delai sans tirer au-dela duquel la fatigue retombe entierement. */
    public static final int FATIGUE_RESET = 20;           // 1 s
    private static final int FATIGUE_MAX = 4;
    private static final float FATIGUE_STEP = 0.10F;

    private static final String TAG_LAST_BOLT = "ArcenciumLastBolt";
    private static final String TAG_FATIGUE = "ArcenciumFatigue";

    public ArcenciumScepterItem(Properties properties) {
        super(properties);
    }

    // ----------------------------------------------------------- clic droit

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            concordWave(level, player);
            stack.hurtAndBreak(2, player, LivingEntity.getSlotForHand(hand));
        }
        player.getCooldowns().addCooldown(this, WAVE_COOLDOWN);
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Repousse les monstres, renforce les allies.
     *
     * Le recul est ce qui compte vraiment : il cree l'ouverture dont l'epeiste
     * a besoin. Les buffs ne font que prolonger cette ouverture.
     *
     * L'onde se voit en trois temps, sans quoi le geste ne se ressent pas :
     * un anneau qui s'ecarte reellement (particules lancees vers l'exterieur,
     * pas posees en cercle fixe), un dome au-dessus du lanceur, et surtout une
     * colonne montante sur CHAQUE allie protege -- c'est elle qui rend le
     * soutien lisible pour toute l'equipe.
     */
    private void concordWave(Level level, Player caster) {
        AABB box = caster.getBoundingBox().inflate(WAVE_RADIUS);
        List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e.distanceTo(caster) <= WAVE_RADIUS);

        ServerLevel server = level instanceof ServerLevel sl ? sl : null;
        int allies = 0;
        for (LivingEntity entity : around) {
            if (entity instanceof Player ally) {
                ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, 1));
                ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ARMOR_TICKS, 0));
                allies++;
                if (server != null) {
                    shieldColumn(server, ally);
                }
            } else if (entity instanceof Mob mob) {
                Vec3 away = mob.position().subtract(caster.position()).normalize();
                mob.knockback(KNOCKBACK, -away.x, -away.z);
                mob.push(0.0, 0.35, 0.0);          // un peu de souleve : le recul se voit
                mob.hurtMarked = true;
                if (server != null) {
                    server.sendParticles(ModParticles.PRISM_MOTE.get(),
                            mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
                            6, 0.2, 0.3, 0.2, 0.06);
                }
            }
        }

        playWaveSound(level, caster);
        if (server != null) {
            expandingRing(server, caster);
            dome(server, caster);
            if (allies > 0) {
                // une note de plus par allie protege : on entend qu'on a couvert du monde
                level.playSound(null, caster.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 0.9F, 1.0F + Math.min(0.5F, 0.1F * allies));
            }
        }
    }

    /**
     * Trois couches sonores : la detonation qui porte, le corps cristallin, et
     * le scintillement magique. Un seul echantillon vanilla sonne toujours
     * comme un bruit de bloc -- c'est l'empilement qui fait la competence.
     */
    private static void playWaveSound(Level level, Player caster) {
        var pos = caster.blockPosition();
        level.playSound(null, pos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.45F, 1.9F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.3F, 0.9F);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.7F);
    }

    /** Anneau reellement en expansion : la vitesse est dans les particules. */
    private static void expandingRing(ServerLevel server, Player caster) {
        for (int i = 0; i < 120; i++) {
            double a = i / 120.0 * Math.PI * 2;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            server.sendParticles(ModParticles.PRISM_MOTE.get(),
                    caster.getX() + dx * 1.2, caster.getY() + 0.25, caster.getZ() + dz * 1.2,
                    0, dx, 0.06, dz, 0.85);
        }
    }

    /** Voute au-dessus du lanceur : signale la zone couverte, vue de loin. */
    private static void dome(ServerLevel server, Player caster) {
        for (int i = 0; i < 70; i++) {
            double t = i / 70.0;
            double a = t * Math.PI * 6;
            double lift = Math.sin(t * Math.PI * 0.5);
            double r = WAVE_RADIUS * 0.55 * Math.cos(t * Math.PI * 0.5);
            server.sendParticles(ModParticles.PRISM_MOTE.get(),
                    caster.getX() + Math.cos(a) * r,
                    caster.getY() + 0.4 + lift * 3.0,
                    caster.getZ() + Math.sin(a) * r,
                    1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    /** Colonne montante autour d'un allie : la marque visible de la protection. */
    private static void shieldColumn(ServerLevel server, Player ally) {
        for (int i = 0; i < 26; i++) {
            double t = i / 26.0;
            double a = t * Math.PI * 4;
            double r = 0.75;
            server.sendParticles(ModParticles.CRYSTAL_GREEN.get(),
                    ally.getX() + Math.cos(a) * r,
                    ally.getY() + t * 2.2,
                    ally.getZ() + Math.sin(a) * r,
                    1, 0.0, 0.04, 0.0, 0.01);
        }
        server.sendParticles(ModParticles.PRISM_MOTE.get(),
                ally.getX(), ally.getY() + 1.1, ally.getZ(), 14, 0.4, 0.7, 0.4, 0.03);
    }

    // ---------------------------------------------------------- clic gauche

    /**
     * Tente un tir. Appele depuis le serveur a reception du paquet d'attaque
     * (voir com.emerald.network) : un clic gauche dans le vide ne remonte pas
     * au serveur autrement. Le serveur revalide donc ici la cadence.
     *
     * @return vrai si le trait est parti
     */
    public static boolean tryFire(Player player, ItemStack stack) {
        long now = player.level().getGameTime();
        long last = player.getPersistentData().getLong(TAG_LAST_BOLT);
        if (last != 0 && now - last < BOLT_COOLDOWN) {
            return false;
        }
        float power = advanceFatigue(player, now, last);
        player.getPersistentData().putLong(TAG_LAST_BOLT, now);
        fireBolt(player, stack, power);
        return true;
    }

    /**
     * Fait monter la fatigue quand les tirs s'enchainent, et la laisse retomber
     * d'un coup apres une seconde de repos.
     *
     * L'intention : le tir a distance reste disponible en continu, mais le
     * matraquer rapporte de moins en moins. On ne bloque jamais le joueur, on
     * rend simplement la rafale moins rentable que le tir place.
     */
    private static float advanceFatigue(Player player, long now, long last) {
        int fatigue = player.getPersistentData().getInt(TAG_FATIGUE);
        boolean resting = last == 0 || now - last >= FATIGUE_RESET;
        fatigue = resting ? 0 : Math.min(FATIGUE_MAX, fatigue + 1);
        player.getPersistentData().putInt(TAG_FATIGUE, fatigue);
        return 1.0F - FATIGUE_STEP * fatigue;
    }

    private static void fireBolt(Player player, ItemStack stack, float power) {
        Level level = player.level();
        if (level.isClientSide) {
            return;
        }
        PrismaticBoltEntity bolt = new PrismaticBoltEntity(level, player);
        bolt.setPower(power);
        bolt.shootFromRotation(player, player.getXRot(), player.getYRot(),
                0.0F, BOLT_SPEED, BOLT_SPREAD);
        level.addFreshEntity(bolt);
        // SHULKER_SHOOT est le seul echantillon vanilla qui sonne vraiment
        // comme un tir d'energie ; CONDUIT_ATTACK_TARGET lui donne sa queue
        // magique. L'amethyste sonnait comme un bloc qu'on tape, pas comme
        // une arme qui part.
        var pos = player.blockPosition();
        // le tir s'affaiblit aussi a l'oreille : plus mat et plus grave
        level.playSound(null, pos, SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS,
                0.45F + 0.30F * power, 1.35F + 0.20F * power);
        level.playSound(null, pos, SoundEvents.CONDUIT_ATTACK_TARGET, SoundSource.PLAYERS,
                0.35F * power, 1.9F);
        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
    }

    // ------------------------------------------------------------- divers

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 22;
    }
}
