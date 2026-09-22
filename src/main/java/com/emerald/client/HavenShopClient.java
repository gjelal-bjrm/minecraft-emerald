package com.emerald.client;

import com.emerald.network.HavenShopPayload;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;

/** La derniere boutique de Tess recue du serveur ; l'ecran la lit (HavenShopScreen). */
public final class HavenShopClient {

    @Nullable
    private static HavenShopPayload last;

    private HavenShopClient() {
    }

    public static void accept(HavenShopPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.articles().isEmpty()) {
            // une boutique vide ferme l'ecran : c'est ainsi que le serveur reprend la main
            if (mc.screen instanceof HavenShopScreen) {
                mc.setScreen(null);
            }
            return;
        }
        last = payload;
        if (payload.open() && !(mc.screen instanceof HavenShopScreen)) {
            mc.setScreen(new HavenShopScreen());
        }
    }

    @Nullable
    public static HavenShopPayload last() {
        return last;
    }
}
