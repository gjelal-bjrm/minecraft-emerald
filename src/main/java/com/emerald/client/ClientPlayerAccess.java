package com.emerald.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Le joueur local, pour les info-bulles qui veulent parler de LUI : cette
 * classe n'est chargee que cote client, derriere une garde de distribution.
 */
public final class ClientPlayerAccess {

    private ClientPlayerAccess() {
    }

    public static Player player() {
        return Minecraft.getInstance().player;
    }
}
