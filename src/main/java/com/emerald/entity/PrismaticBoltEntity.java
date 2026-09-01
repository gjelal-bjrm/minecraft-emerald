package com.emerald.entity;

import com.emerald.init.Jak3Registry;
import com.emerald.particles.ModParticles;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;

/**
 * Le trait du Sceptre d'Arcencium.
 *
 * Il ne porte aucun modele : tout son rendu tient dans la trainee de particules
 * qu'il seme a chaque tick. C'est coherent avec ce qu'il represente -- un
 * eclat de lumiere -- et cela evite un renderer d'entite pour un projectile
 * qui ne dure qu'une seconde.
 *
 * Sur un ennemi il blesse peu ; sur un allie il soigne. C'est ce double
 * comportement qui fait du sceptre une arme de soutien et non une troisieme
 * arme offensive : viser juste rapporte plus que viser fort.
 */
public class PrismaticBoltEntity extends ThrowableProjectile {

    public static final float DAMAGE = 2.5F;
    public static final float HEAL = 2.0F;

    /** Un meme allie ne peut etre soigne qu'a cet intervalle. */
    public static final int ALLY_HEAL_COOLDOWN = 30;      // 1,5 s

    private static final String TAG_LAST_HEAL = "ArcenciumLastHeal";

    /** Points interpoles par tick : en dessous, le rayon redevient pointille. */
    private static final int BEAM_STEPS = 11;

    /** Epaisseur du trait. Fin volontairement : au-dela il se brouille. */
    private static final float BEAM_WIDTH = 0.42F;

    private static final String TAG_POWER = "ArcenciumPower";

    /**
     * Part de degats que porte ce trait, de 0,6 a 1. Elle baisse quand le
     * joueur enchaine les tirs (voir ArcenciumScepterItem) et se lit sur le
     * rayon lui-meme : un trait affaibli est plus pale et plus fin.
     */
    private float power = 1.0F;

    public void setPower(float power) {
        this.power = power;
    }

    public float getPower() {
        return this.power;
    }

    public PrismaticBoltEntity(EntityType<? extends PrismaticBoltEntity> type, Level level) {
        super(type, level);
    }

    public PrismaticBoltEntity(Level level, LivingEntity shooter) {
        super(Jak3Registry.PRISMATIC_BOLT.get(), shooter, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // aucune donnee a synchroniser : la trainee est purement visuelle
    }

    @Override
    protected double getDefaultGravity() {
        return 0.003;                 // presque plat, mais pas tout a fait
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            drawBeam();
        }
        if (this.tickCount > 80) {
            this.discard();           // quatre secondes de vol au maximum
        }
    }

    /**
     * Trace le trait entre la position du tick precedent et l'actuelle.
     *
     * Un seul point par tick laisse un pointille : a cette vitesse les points
     * sont espaces de deux blocs. On interpole donc le segment.
     *
     * En revanche le trait doit rester FIN et NET. Une premiere version lui
     * ajoutait un halo de particules autour d'un coeur epais : le rayon y
     * gagnait en presence mais s'y noyait, et on ne distinguait plus la ligne.
     * Un seul point par pas, petit et sature, se lit beaucoup mieux -- c'est
     * la continuite qui fait le laser, pas l'epaisseur.
     */
    private void drawBeam() {
        double dx = this.getX() - this.xo;
        double dy = this.getY() - this.yo;
        double dz = this.getZ() - this.zo;

        for (int i = 0; i < BEAM_STEPS; i++) {
            double t = i / (double) BEAM_STEPS;

            // la teinte defile le long du trait ET dans le temps : le rayon
            // parait parcouru d'un courant plutot que peint d'une couleur
            float hue = (float) (((this.tickCount + t) * 0.09) % 1.0);
            // un trait epuise perd sa saturation et son eclat : la penalite
            // de matraquage se voit sans qu'aucune interface ne l'annonce
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.55F + 0.30F * this.power,
                                              0.55F + 0.45F * this.power);
            Vector3f color = new Vector3f(((rgb >> 16) & 0xFF) / 255F,
                                          ((rgb >> 8) & 0xFF) / 255F,
                                          (rgb & 0xFF) / 255F);

            this.level().addParticle(
                    new DustParticleOptions(color, BEAM_WIDTH * (0.7F + 0.3F * this.power)),
                    this.xo + dx * t, this.yo + dy * t, this.zo + dz * t, 0, 0, 0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide || !(result.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Entity owner = this.getOwner();
        if (isAlly(target, owner)) {
            heal(target);
        } else {
            target.hurt(this.damageSources().indirectMagic(this, owner), DAMAGE * this.power);
        }
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat(TAG_POWER, this.power);
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_POWER)) {
            this.power = tag.getFloat(TAG_POWER);
        }
    }

    /** Un allie : le tireur lui-meme, un autre joueur, ou une creature apprivoisee. */
    private static boolean isAlly(LivingEntity target, Entity owner) {
        if (target instanceof Player) {
            return true;
        }
        return target instanceof TamableAnimal tame && tame.isTame()
                && owner instanceof LivingEntity living && tame.isOwnedBy(living);
    }

    /**
     * Le soin est plafonne dans le temps par cible, sinon un tir soutenu sur
     * un coequipier le rendrait invulnerable a peu de frais.
     */
    private void heal(LivingEntity target) {
        long now = target.level().getGameTime();
        long last = target.getPersistentData().getLong(TAG_LAST_HEAL);
        if (last != 0 && now - last < ALLY_HEAL_COOLDOWN) {
            return;
        }
        target.getPersistentData().putLong(TAG_LAST_HEAL, now);
        target.heal(HEAL);

        // LE CALICE DE CONCORDE rend au porteur la moitie de ce qu'il donne.
        //
        // La moitie et non la totalite : le Sceptre doit rester une arme qui
        // soigne LES AUTRES. Un soin plein ferait du tir sur coequipier la
        // meilleure facon de se soigner soi-meme, et le role de soutien
        // deviendrait un role d'egoiste.
        if (getOwner() instanceof net.minecraft.world.entity.player.Player caster
                && caster != target
                && com.emerald.artifact.Artifacts.wearing(caster,
                        com.emerald.artifact.Artifact.CALICE_DE_CONCORDE)) {
            caster.heal(HEAL * 0.5F);
        }
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.CRYSTAL_GREEN.get(),
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    8, 0.3, 0.4, 0.3, 0.02);
            server.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.8F, 1.6F);
        }
    }

    /**
     * L'impact compte autant que le trajet : sans eclat a l'arrivee, le rayon
     * parait s'eteindre au lieu de frapper.
     */
    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLASH,
                    this.getX(), this.getY(), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            server.sendParticles(ModParticles.PRISM_MOTE.get(),
                    this.getX(), this.getY(), this.getZ(), 18, 0.25, 0.25, 0.25, 0.16);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    this.getX(), this.getY(), this.getZ(), 10, 0.15, 0.15, 0.15, 0.20);
            server.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK,
                    SoundSource.PLAYERS, 0.7F, 1.7F);
        }
        this.discard();
    }
}
