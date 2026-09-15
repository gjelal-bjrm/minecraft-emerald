package com.emerald.jak.gun;

import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Le numero du lobby, sauvegarde avec le monde : une arme porte celui du lobby
 * qui l'a donnee, et une pile d'un autre numero est remplacee par une neuve.
 *
 * POURQUOI ICI ET NON DANS HAVENSTATE. Le plan faisait incrementer le numero par
 * HavenState a la reouverture ; HavenArrival.reopen n'est pas a ce chantier. On
 * observe donc la reouverture a chaque tique, par ce qu'elle laisse :
 *  - le lobby passe de ferme a ouvert (depart puis /arcencium setup, pose
 *    finie) ;
 *  - ou, lobby deja ouvert, les appartements sont tous oublies d'un coup
 *    (reopen appelle clearApartments : un /arcencium setup pendant le lobby).
 * Un redemarrage du serveur pendant le lobby ne change rien : l'etat « ouvert »
 * est sauvegarde, et les joueurs gardent leur arme.
 *
 * Le cas vise par la critique : un joueur deconnecte dans la ville pendant le
 * depart, qui revient apres la reouverture avec l'arme et les reserves de
 * l'ancien lobby. Son arme porte l'ancien numero : le gardien la remplace.
 */
public final class MorphGunState extends SavedData {

    public static final String KEY = "emeraldweapons_morph_gun";

    private long lobby;
    private boolean wasOpen;
    private int apartments;

    public static MorphGunState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(MorphGunState::new, MorphGunState::load), KEY);
    }

    private static MorphGunState load(CompoundTag tag, HolderLookup.Provider registries) {
        MorphGunState state = new MorphGunState();
        state.lobby = tag.getLong("Lobby");
        state.wasOpen = tag.getBoolean("WasOpen");
        state.apartments = tag.getInt("Apartments");
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("Lobby", this.lobby);
        tag.putBoolean("WasOpen", this.wasOpen);
        tag.putInt("Apartments", this.apartments);
        return tag;
    }

    public long lobby() {
        return this.lobby;
    }

    /**
     * Suit l'ouverture du lobby ; appele a chaque tique du serveur.
     *
     * @return vrai si un nouveau lobby vient de commencer
     */
    public boolean observe(MinecraftServer server) {
        boolean open = HavenArrival.lobbyOpen(server);
        int count = HavenState.get(server).apartmentCount();
        boolean fresh = open && (!this.wasOpen || (this.apartments > 0 && count == 0));
        if (fresh) {
            this.lobby++;
        }
        if (fresh || open != this.wasOpen || count != this.apartments) {
            this.wasOpen = open;
            this.apartments = count;
            setDirty();
        }
        return fresh;
    }
}
