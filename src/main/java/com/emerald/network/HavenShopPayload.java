package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * La boutique de Tess, telle que le serveur la voit pour ce joueur (lot 3, cahier §86) : son
 * solde d'orbes et chaque article -- son nom, ce qu'il fait, son prix, s'il est a vendre,
 * possede ou ferme, et combien il en a deja (sceaux, provisions...). L'ecran (HavenShopScreen)
 * ne decide de rien : il montre la derniere boutique recue.
 *
 * @param open vrai pour ouvrir l'ecran ; faux pour mettre a jour un ecran deja ouvert
 */
public record HavenShopPayload(boolean open, int orbs, List<Article> articles) implements CustomPacketPayload {

    /** A vendre, possede, ferme (il manque quelque chose). */
    public static final int BUYABLE = 0;
    public static final int OWNED = 1;
    public static final int LOCKED = 2;

    /**
     * @param section 0 armes, 1 munitions, 2 bonus du Defi
     * @param color   la couleur de l'article (ARGB)
     * @param count   combien il en a deja (les articles qui s'accumulent)
     * @param info    ce qu'il fait, ou ce qui le ferme
     */
    public record Article(String id, int section, Component name, Component info, int price, int state, int count, int color) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Article> STREAM_CODEC = StreamCodec.of(
                (buf, a) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, a.id());
                    ByteBufCodecs.VAR_INT.encode(buf, a.section());
                    ComponentSerialization.STREAM_CODEC.encode(buf, a.name());
                    ComponentSerialization.STREAM_CODEC.encode(buf, a.info());
                    ByteBufCodecs.VAR_INT.encode(buf, a.price());
                    ByteBufCodecs.VAR_INT.encode(buf, a.state());
                    ByteBufCodecs.VAR_INT.encode(buf, a.count());
                    ByteBufCodecs.INT.encode(buf, a.color());
                },
                buf -> new Article(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf)));
    }

    public static final CustomPacketPayload.Type<HavenShopPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "haven_shop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HavenShopPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, HavenShopPayload::open,
            ByteBufCodecs.VAR_INT, HavenShopPayload::orbs,
            Article.STREAM_CODEC.apply(ByteBufCodecs.list()), HavenShopPayload::articles,
            HavenShopPayload::new);

    @Override
    public CustomPacketPayload.Type<HavenShopPayload> type() {
        return TYPE;
    }
}
