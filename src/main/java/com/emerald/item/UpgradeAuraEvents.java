package com.emerald.item;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.joml.Vector3f;

/**
 * Ce que l'aura fait DANS L'AIR : l'onde du coup, et les braises de l'armure.
 *
 * IL Y AVAIT UNE TROISIEME CHOSE, UNE HELICE AUTOUR DE LA LAME, ET ELLE ETAIT
 * FAUSSE PAR CONSTRUCTION. Le serveur devinait la position de la main a partir
 * du regard ; or la main est dessinee par le CLIENT, animee par le balancement
 * des membres et par les transformations propres a l'objet tenu. Aucun reglage
 * ne pouvait rattraper cela -- le serveur n'a tout simplement pas l'information.
 * Le nuage flottait a cote du porteur, et ne signifiait rien.
 *
 * La lecon vaut au-dela de ce cas : UNE PARTICULE SERVEUR NE PEUT PAS SUIVRE UN
 * POINT QUI N'EXISTE QUE DANS LE RENDU. Elle sait viser une entite, un bloc, un
 * point du monde -- jamais une main, jamais le bout d'une lame. Ce qui doit
 * epouser l'arme se fait au rendu ; c'est le role du halo, et il le fait bien.
 *
 * Restent ici les deux effets qui visent quelque chose que le serveur CONNAIT :
 * l'onde part de la victime, les braises montent du corps du porteur.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class UpgradeAuraEvents {


    private UpgradeAuraEvents() {
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)
                || !(player.level() instanceof ServerLevel level)
                || level.getGameTime() % 3 != 0) {
            return;
        }
        embers(level, player);
    }

    // ------------------------------------------------------------- l'onde

    /**
     * L'ONDE du coup : au +8 et au-dela, chaque coup porte fait partir un
     * anneau de la lame vers l'exterieur.
     *
     * C'est le moment qu'on retient. Le halo est la tout le temps et finit par
     * se fondre dans le paysage ; l'onde n'existe qu'un instant et rappelle, a
     * chaque coup, ce qu'on tient. Douze particules
     * lancees vers l'exterieur, dans le plan perpendiculaire au coup : elles
     * s'ecartent puis s'eteignent, et la cible est au centre.
     */
    @SubscribeEvent
    public static void onHit(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
                || !(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        UpgradeGlow.Aura aura = UpgradeGlow.of(attacker.getMainHandItem());
        if (!aura.large()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        Vec3 centre = victim.position().add(0, victim.getBbHeight() * 0.55, 0);
        Vec3 axis = attacker.getLookAngle().normalize();
        Vec3 up = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = axis.cross(up).normalize();
        Vec3 v = axis.cross(u).normalize();

        DustParticleOptions dust = new DustParticleOptions(
                new Vector3f(aura.red(), aura.green(), aura.blue()), 1.6F);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6.0;
            Vec3 dir = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
            Vec3 at = centre.add(dir.scale(0.35));
            // la vitesse est portee par le decalage : la poussiere ne bouge pas
            // d'elle-meme, on la pose deja en chemin sur trois rayons
            for (int r = 0; r < 3; r++) {
                Vec3 p = at.add(dir.scale(r * 0.22));
                level.sendParticles(dust, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    // ----------------------------------------------------------- les braises

    /**
     * Les BRAISES de l'armure : au +8 et au-dela, la piece laisse monter de
     * lentes etincelles de sa couleur.
     *
     * C'est le pendant aerien du calque emissif : le calque chauffe la piece
     * de l'interieur, les braises en sont ce qui s'echappe. Une par tiers de
     * seconde et par piece, montant lentement -- assez rare pour rester une
     * lueur, assez regulier pour qu'on comprenne d'ou elle vient.
     */
    private static void embers(ServerLevel level, LivingEntity entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            ItemStack worn = entity.getItemBySlot(slot);
            if (!(worn.getItem() instanceof ArmorItem)) {
                continue;
            }
            UpgradeGlow.Aura aura = UpgradeGlow.of(worn);
            if (!aura.large()) {
                continue;
            }
            double height = switch (slot) {
                case HEAD -> 1.55;
                case CHEST -> 1.05;
                case LEGS -> 0.55;
                default -> 0.15;
            };
            double dx = (level.getRandom().nextDouble() - 0.5) * 0.7;
            double dz = (level.getRandom().nextDouble() - 0.5) * 0.7;
            DustParticleOptions dust = new DustParticleOptions(
                    new Vector3f(aura.red(), aura.green(), aura.blue()), 0.8F);
            level.sendParticles(dust, entity.getX() + dx, entity.getY() + height,
                    entity.getZ() + dz, 1, 0.0, 0.04, 0.0, 0.0);
        }
    }

}
