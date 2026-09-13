package com.emerald.menu;

import com.emerald.block.ModBlocks;
import com.emerald.game.GameState;
import com.emerald.haven.HavenState;
import com.emerald.haven.HavenVote;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Le menu de l'urne du QG : deux boutons, le decompte, la liste des electeurs.
 *
 * AUCUN PAQUET A NOUS. Les boutons passent par clickMenuButton, le mecanisme
 * vanilla des menus, que tout joueur peut utiliser -- pas de commande a
 * permission 2, donc les non-operateurs votent. Le serveur revalide chaque
 * vote (HavenVote.cast). Les chiffres reviennent par ContainerData, relue a
 * chaque tique ; les NOMS, qu'un ContainerData ne sait pas porter (des entiers
 * courts), viennent avec l'ouverture du menu, dans les donnees d'ouverture de
 * NeoForge. Quand la liste des electeurs change, HavenVote rouvre le menu.
 *
 * Donnees, par index :
 * 0 votes Defi, 1 votes Libre, 2 electeurs, 3 electeurs dans le bar,
 * 4 tiques du compte a rebours (0 : rien ne court), 5 le vote de celui qui
 * regarde, 6 1 s'il est electeur (0 : operateur en chantier), puis une case par
 * electeur de la liste : son vote (0 aucun, 1 Defi, 2 Libre), plus 4 s'il est
 * dans le bar, 8 s'il n'est plus electeur.
 */
public class HavenVoteMenu extends AbstractContainerMenu {

    public static final Component TITLE = Component.translatable("container.emeraldweapons.haven_vote");

    public static final int BUTTON_DEFI = 0;
    public static final int BUTTON_LIBRE = 1;

    /** Electeurs detailles a l'ecran ; les suivants sont comptes, pas nommes. */
    public static final int MAX_ROSTER = 12;

    public static final int DATA_DEFI = 0;
    public static final int DATA_LIBRE = 1;
    public static final int DATA_ELECTORS = 2;
    public static final int DATA_IN_BAR = 3;
    public static final int DATA_COUNTDOWN = 4;
    public static final int DATA_MINE = 5;
    public static final int DATA_ELIGIBLE = 6;
    public static final int DATA_ROSTER = 7;
    public static final int DATA_COUNT = DATA_ROSTER + MAX_ROSTER;

    public static final int VOTE_NONE = 0;
    public static final int VOTE_DEFI = 1;
    public static final int VOTE_LIBRE = 2;
    public static final int ENTRY_VOTE_MASK = 3;
    public static final int ENTRY_IN_BAR = 4;
    public static final int ENTRY_GONE = 8;

    private final ContainerLevelAccess access;
    @Nullable
    private final BlockPos pos;
    private final List<UUID> roster;
    private final List<String> names;
    private final ContainerData data;

    /** Cote client : la liste des electeurs arrive avec l'ouverture. */
    public HavenVoteMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, ContainerLevelAccess.NULL, null, readIds(buf), new ArrayList<>(), new SimpleContainerData(DATA_COUNT));
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            this.names.add(buf.readUtf(64));
        }
    }

    private HavenVoteMenu(int id, ContainerLevelAccess access, @Nullable BlockPos pos, List<UUID> roster,
                          List<String> names, ContainerData data) {
        super(ModMenus.HAVEN_VOTE.get(), id);
        this.access = access;
        this.pos = pos;
        this.roster = roster;
        this.names = names;
        this.data = data;
        this.addDataSlots(data);
    }

    private static List<UUID> readIds(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<UUID> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(buf.readUUID());
        }
        return ids;
    }

    /** Les identifiants d'une liste d'electeurs, dans son ordre. */
    public static List<UUID> rosterIds(List<HavenVote.Elector> electors) {
        List<UUID> ids = new ArrayList<>(electors.size());
        for (HavenVote.Elector elector : electors) {
            ids.add(elector.id());
        }
        return ids;
    }

    /** Ouvre (ou rouvre) l'urne pour un joueur, avec la liste des electeurs de cette tique. */
    public static void open(ServerPlayer player, BlockPos pos) {
        List<HavenVote.Elector> electors = HavenVote.current();
        List<UUID> ids = rosterIds(electors);
        List<String> names = new ArrayList<>(electors.size());
        for (HavenVote.Elector elector : electors) {
            names.add(elector.name());
        }
        ContainerLevelAccess access = ContainerLevelAccess.create(player.level(), pos);
        BlockPos at = pos.immutable();
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) ->
                        new HavenVoteMenu(id, access, at, ids, names, serverData(player, ids)), TITLE),
                buf -> {
                    buf.writeVarInt(ids.size());
                    for (UUID uuid : ids) {
                        buf.writeUUID(uuid);
                    }
                    buf.writeVarInt(names.size());
                    for (String name : names) {
                        buf.writeUtf(name, 64);
                    }
                });
    }

    /** Les chiffres, relus a chaque tique dans l'etat du vote : rien n'est recopie. */
    private static ContainerData serverData(ServerPlayer viewer, List<UUID> roster) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                HavenVote.Tally tally = HavenVote.currentTally();
                return switch (index) {
                    case DATA_DEFI -> tally.defi();
                    case DATA_LIBRE -> tally.libre();
                    case DATA_ELECTORS -> tally.electors();
                    case DATA_IN_BAR -> tally.inBar();
                    case DATA_COUNTDOWN -> HavenVote.remaining();
                    case DATA_MINE -> code(HavenState.get(viewer.server).vote(viewer.getUUID()));
                    case DATA_ELIGIBLE -> find(viewer.getUUID()) != null ? 1 : 0;
                    default -> {
                        int k = index - DATA_ROSTER;
                        if (k < 0 || k >= roster.size()) {
                            yield 0;
                        }
                        HavenVote.Elector elector = find(roster.get(k));
                        yield elector == null ? ENTRY_GONE
                                : code(elector.vote()) | (elector.inBar() ? ENTRY_IN_BAR : 0);
                    }
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    @Nullable
    private static HavenVote.Elector find(UUID id) {
        for (HavenVote.Elector elector : HavenVote.current()) {
            if (elector.id().equals(id)) {
                return elector;
            }
        }
        return null;
    }

    private static int code(@Nullable GameState.Mode mode) {
        return mode == GameState.Mode.DEFI ? VOTE_DEFI : mode == GameState.Mode.LIBRE ? VOTE_LIBRE : VOTE_NONE;
    }

    public int value(int index) {
        return this.data.get(index);
    }

    public List<UUID> roster() {
        return this.roster;
    }

    public List<String> names() {
        return this.names;
    }

    /** La position de l'urne, cote serveur seulement. */
    @Nullable
    public BlockPos pos() {
        return this.pos;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer server)) {
            return false;
        }
        GameState.Mode mode = id == BUTTON_DEFI ? GameState.Mode.DEFI
                : id == BUTTON_LIBRE ? GameState.Mode.LIBRE : null;
        if (mode == null) {
            return false;
        }
        HavenVote.cast(server, mode);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;           // aucune case
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.HAVEN_VOTE.get());
    }
}
