package com.emerald.element;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * L'element d'une arme, et celui d'une creature.
 *
 * LE JOUEUR CHOISIT LE SIEN, LA CREATURE PORTE LE SIEN. C'est l'asymetrie qui
 * fait tout le systeme : sans elle il n'y aurait rien a preparer, car on ne
 * choisit pas contre quelque chose qui choisit aussi.
 *
 * L'ELEMENT EST CELUI DU JOUEUR, PAS DE L'ARME. J'avais d'abord fait porter
 * l'element par l'arme, ce qui obligeait a accorder chaque arme separement et
 * creait deux verites des qu'on en changeait. Le joueur en a UNE, il en change
 * quand il veut avec un cristal, et toutes ses armes la suivent. L'arme, elle,
 * ne dit plus que la PUISSANCE elementaire -- combien -- pendant que le joueur
 * dit lequel. C'est la repartition de NosTale, ou la fee porte l'element et
 * l'arme la force.
 *
 * La creature, elle, tire le sien de ce qu'elle EST : on le lit a ses traits et
 * jamais a une liste de noms, qui manquerait tout ce que le modpack ajoute. Le
 * mode peut malgre tout en imposer un, et c'est ainsi que la Lumiere existe --
 * aucune creature vanilla ne la porte naturellement.
 */
public class Attunement {

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(EmeraldWeaponsMod.MODID);

    /** L'element auquel une arme a ete accordee. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Element>>
            ELEMENT = COMPONENTS.registerComponentType("element", builder -> builder
                    .persistent(Element.CODEC)
                    .networkSynchronized(Element.STREAM_CODEC));

    /** Ce que le mode a impose a une creature, quand il l'a fait. */
    private static final String TAG = "ArcenciumElement";
    /** Le SECOND element, reserve aux boss. */
    private static final String TAG_SECOND = "ArcenciumElement2";
    /** L'element choisi par un joueur. */
    private static final String TAG_PLAYER = "ArcenciumPlayerElement";

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }

    // ------------------------------------------------------------- le joueur

    /**
     * L'element du joueur : son choix, libre et reversible.
     *
     * Range dans les donnees persistantes plutot que sur une piece : il doit
     * survivre a un changement d'arme, et un joueur qui pose son epee ne cesse
     * pas d'etre de Feu.
     */
    public static Element of(net.minecraft.world.entity.player.Player player) {
        String raw = player.getPersistentData().getString(TAG_PLAYER);
        return raw.isEmpty() ? Element.NEUTRE : Element.byName(raw);
    }

    /** Change l'element d'un joueur. */
    public static void set(net.minecraft.world.entity.player.Player player, Element element) {
        player.getPersistentData().putString(TAG_PLAYER, element.getSerializedName());
    }

    // --------------------------------------------------------------- l'objet

    /**
     * L'element que porte un OBJET.
     *
     * Ne sert plus qu'aux cristaux, qui doivent bien dire lequel ils
     * contiennent. Les armes n'en portent plus : c'est le joueur qui decide.
     */
    public static Element of(ItemStack stack) {
        Element element = stack.get(ELEMENT.get());
        return element == null ? Element.NEUTRE : element;
    }

    public static void set(ItemStack stack, Element element) {
        if (element == Element.NEUTRE) {
            stack.remove(ELEMENT.get());
        } else {
            stack.set(ELEMENT.get(), element);
        }
    }

    // ----------------------------------------------------------- la creature

    /** Impose un element a une creature : c'est ainsi que le mode place la Lumiere. */
    public static void set(LivingEntity entity, Element element) {
        entity.getPersistentData().putString(TAG, element.getSerializedName());
    }

    /**
     * Donne DEUX elements a une creature : c'est le privilege des boss.
     *
     * Un boss a deux elements est bien plus dur a exploiter. L'affinite se
     * calcule alors sur la MOYENNE des deux couples, si bien qu'aucune arme ne
     * trouve jamais l'avantage plein : contre un boss Obscur et Feu, une arme
     * de Lumiere obtient 1,6 sur l'Obscur et 1,0 sur le Feu, donc 1,3.
     *
     * ON MOYENNE PLUTOT QUE DE PRENDRE LE MINIMUM. Le minimum rendrait tout
     * boss bi-element parfaitement insensible au choix d'element -- il n'y
     * aurait plus rien a preparer avant de l'affronter, et la mecanique
     * disparaitrait au moment precis ou elle compte le plus.
     */
    public static void set(LivingEntity entity, Element first, Element second) {
        set(entity, first);
        entity.getPersistentData().putString(TAG_SECOND, second.getSerializedName());
    }

    /** Le second element d'une creature, ou NEUTRE si elle n'en a qu'un. */
    public static Element second(LivingEntity entity) {
        String raw = entity.getPersistentData().getString(TAG_SECOND);
        return raw.isEmpty() ? Element.NEUTRE : Element.byName(raw);
    }

    /** Vrai si la creature porte deux elements. */
    public static boolean dual(LivingEntity entity) {
        return second(entity) != Element.NEUTRE;
    }

    /**
     * L'affinite d'un element attaquant contre cette creature-ci.
     *
     * Passe par ici et non par {@link Element#against} directement : c'est le
     * seul endroit qui sache si la cible porte un ou deux elements, et le seul
     * ou la moyenne des deux couples ait un sens.
     */
    public static double affinity(Element attacker, LivingEntity victim) {
        Element first = of(victim);
        Element other = second(victim);
        if (other == Element.NEUTRE) {
            return attacker.against(first);
        }
        return (attacker.against(first) + attacker.against(other)) / 2.0;
    }

    /**
     * L'element d'une creature.
     *
     * Ce que le mode a impose passe avant tout ; sinon on le deduit de ses
     * traits. La table et les resistances vivent dans {@link MobElement}.
     */
    public static Element of(LivingEntity entity) {
        String forced = entity.getPersistentData().getString(TAG);
        if (!forced.isEmpty()) {
            return Element.byName(forced);
        }
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            return of(player);
        }
        return MobElement.natural(entity);
    }
}

