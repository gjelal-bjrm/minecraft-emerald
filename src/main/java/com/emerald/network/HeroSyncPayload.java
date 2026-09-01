package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * La fiche du Heros, telle que le client la voit.
 *
 * Elle DOIT transiter. Les points vivent dans les donnees persistantes du
 * joueur, qui ne sont jamais synchronisees -- c'est exactement le piege ou la
 * jauge de Rage etait deja tombee : le serveur savait tout, l'ecran n'affichait
 * rien, et le systeme passait pour casse alors qu'il fonctionnait.
 *
 * Huit entiers, envoyes seulement quand ils changent. Le cout est nul et le
 * client n'a plus rien a deviner : le pourcentage, les paliers et les bonus se
 * recalculent chez lui a partir des memes tables.
 */
public record HeroSyncPayload(int level, int xp, int needed, int free,
                              int attaque, int element, int defense, int vitalite,
                              int slAttaque, int slElement, int slDefense, int slVitalite)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HeroSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "hero_sync"));

    /**
     * Ecrit a la main, faute de mieux.
     *
     * StreamCodec.composite s'arrete a six champs et la fiche en compte huit.
     * Plutot que de tasser deux valeurs dans une pour rentrer dans le moule --
     * ce qui rendrait le paquet illisible a la prochaine lecture -- on epelle
     * les huit dans l'ordre du disque. L'ordre d'ecriture et celui de lecture
     * doivent rester identiques : c'est la seule chose a surveiller ici.
     */
    public static final StreamCodec<ByteBuf, HeroSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.VAR_INT.encode(buf, payload.level());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.xp());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.needed());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.free());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.attaque());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.element());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.defense());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.vitalite());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.slAttaque());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.slElement());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.slDefense());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.slVitalite());
                    },
                    buf -> new HeroSyncPayload(
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf)));

    /** Le niveau ACHETE dans une voie, dans l'ordre de l'enumeration. */
    public int path(int ordinal) {
        return switch (ordinal) {
            case 0 -> attaque;
            case 1 -> element;
            case 2 -> defense;
            default -> vitalite;
        };
    }

    /**
     * Les niveaux OFFERTS par les runes SL, dans la meme voie.
     *
     * Separes de l'achete, et il le faut : les boutons de depense raisonnent
     * sur ce qu'on a paye -- c'est lui qui commande le prix du niveau suivant --
     * alors que l'affichage des effets doit montrer le total. Confondre les deux
     * ferait payer au joueur un prix calcule sur des niveaux qu'il n'a pas
     * achetes.
     */
    public int sl(int ordinal) {
        return switch (ordinal) {
            case 0 -> slAttaque;
            case 1 -> slElement;
            case 2 -> slDefense;
            default -> slVitalite;
        };
    }

    @Override
    public CustomPacketPayload.Type<HeroSyncPayload> type() {
        return TYPE;
    }
}
