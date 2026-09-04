package com.emerald.menu;

import com.emerald.artifact.Artifact;
import com.emerald.artifact.ArtifactItem;
import com.emerald.artifact.Artifacts;
import com.emerald.block.ModBlocks;
import com.emerald.item.ModItems;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * L'Etabli de Sertissage.
 *
 * Trois emplacements : la piece d'equipement, l'artefact, et le resultat.
 *
 * Le sertissage n'est pas une fusion : la piece ressort identique, avec un
 * artefact en plus. Si elle en portait deja un, celui-ci est DETRUIT -- on peut
 * changer d'avis, mais cela coute. C'est ce qui donne du poids au choix sans
 * jamais l'enfermer.
 */
public class SocketBenchMenu extends AbstractContainerMenu {

    // Index dans le MENU : c'est ce que manipulent quickMoveStack et le reseau.
    public static final int SLOT_GEAR = 0;
    public static final int SLOT_ARTIFACT = 1;
    public static final int SLOT_RESULT = 2;

    /**
     * Index dans le CONTENEUR de resultat, qui ne compte qu'une case.
     *
     * Le second parametre de Slot est l'index dans son conteneur, pas dans le
     * menu. Y passer SLOT_RESULT revenait a lire la case 2 d'un tableau de
     * longueur 1, et le client se deconnectait des l'ouverture de l'ecran.
     */
    private static final int RESULT_IN_CONTAINER = 0;

    private final ContainerLevelAccess access;
    /**
     * Le joueur, retenu pour une seule raison : le metal de l'amelioration
     * vit dans SON sac et non dans l'etabli.
     *
     * L'etabli n'a que deux cases d'entree, et en ajouter une troisieme
     * demanderait de refaire la texture et l'ecran. Lire le sac evite ce
     * chantier et se joue mieux : on n'a pas a deplacer neuf lingots pour
     * tenter une amelioration.
     */
    private final Player owner;
    private final Container inputs = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            SocketBenchMenu.this.slotsChanged(this);
        }
    };
    private final Container result = new SimpleContainer(1);

    public SocketBenchMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public SocketBenchMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.SOCKET_BENCH.get(), id);
        this.access = access;
        this.owner = inventory.player;

        this.addSlot(new Slot(this.inputs, SLOT_GEAR, 27, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // toute piece capable d'accueillir un artefact OU une rune
                for (Artifact artifact : Artifact.values()) {
                    if (artifact.fits(stack)) {
                        return true;
                    }
                }
                for (com.emerald.rune.RuneFamily family
                        : com.emerald.rune.RuneFamily.values()) {
                    if (family.accepts(stack)) {
                        return true;
                    }
                }
                return false;
            }
        });
        this.addSlot(new Slot(this.inputs, SLOT_ARTIFACT, 76, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // le meme emplacement sert aux trois usages de l'etabli :
                // sertir un artefact, graver une rune, ou tenter une montee de
                // rarete. Un seul lieu pour tout ce qui modifie une piece.
                return stack.is(com.emerald.item.ModItems.FATE_SHARD.get())
                        || stack.getItem() instanceof ArtifactItem && Artifacts.of(stack) != null
                        || com.emerald.rune.Runes.of(stack) != null
                        || stack.is(com.emerald.item.ModItems.FORGE_STONE.get());
            }
        });
        this.addSlot(new Slot(this.result, RESULT_IN_CONTAINER, 134, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                // LE TIRAGE SE FAIT ICI, au moment ou la piece quitte l'etabli.
                //
                // C'est le seul instant honnete : plus tot, il faudrait
                // afficher un resultat qui pourrait encore changer ; plus tard,
                // la piece serait deja dans l'inventaire. Le nombre d'eclats
                // consommes est celui qui etait pose -- on n'en rend pas.
                ItemStack fee = SocketBenchMenu.this.inputs.getItem(SLOT_ARTIFACT);
                if (fee.is(com.emerald.item.ModItems.FATE_SHARD.get())) {
                    com.emerald.item.GearRarity before =
                            com.emerald.item.GearRarity.of(stack);
                    com.emerald.item.GearRarity after = com.emerald.item.GearRarity.roll(
                            stack, fee.getCount(), player.level().random);
                    com.emerald.item.GearRarity.set(stack, after);
                    player.displayClientMessage(after == before
                            ? net.minecraft.network.chat.Component.translatable(
                                    "socket.emeraldweapons.rarity.kept")
                                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                            : net.minecraft.network.chat.Component.translatable(
                                    "socket.emeraldweapons.rarity.risen", after.label())
                                    .withStyle(style -> style.withColor(after.colour())),
                            false);
                    player.level().playSound(null, player.blockPosition(),
                            after == before
                                    ? net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK
                                    : net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.8F,
                            0.7F + after.rank() * 0.08F);
                    if (after != before) {
                        com.emerald.util.Celebration.rarity(player, after.label(),
                                after.colour(), after.rank());
                    }
                }
                if (fee.is(com.emerald.item.ModItems.FORGE_STONE.get())) {
                    // ON PAIE D'ABORD, ON TIRE ENSUITE. Un tirage qui se
                    // solderait par un echec de paiement laisserait la piece
                    // amelioree sans que rien n'ait ete depense.
                    int before = com.emerald.item.Upgrade.of(stack);
                    if (com.emerald.item.Upgrade.charge(player, stack)) {
                        int after = com.emerald.item.Upgrade.attempt(
                                before, player.level().random);
                        com.emerald.item.Upgrade.set(stack, after);
                        boolean won = after > before;
                        boolean lost = after < before;
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable(
                                        won ? "socket.emeraldweapons.upgrade.won"
                                                : lost ? "socket.emeraldweapons.upgrade.lost"
                                                : "socket.emeraldweapons.upgrade.kept",
                                        after)
                                        .withStyle(won
                                                ? net.minecraft.ChatFormatting.GREEN
                                                : lost ? net.minecraft.ChatFormatting.RED
                                                : net.minecraft.ChatFormatting.GRAY),
                                false);
                        player.level().playSound(null, player.blockPosition(),
                                won ? net.minecraft.sounds.SoundEvents.ANVIL_USE
                                        : net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                                net.minecraft.sounds.SoundSource.PLAYERS, 0.9F,
                                won ? 0.9F + after * 0.06F : 0.6F);
                        if (won) {
                            com.emerald.util.Celebration.upgrade(player, after);
                        }
                    }
                }
                com.emerald.rune.RuneMark graved =
                        com.emerald.rune.Runes.of(fee);
                if (graved != null) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable(
                                    "socket.emeraldweapons.rune.engraved", graved.label()),
                            false);
                    player.level().playSound(null, player.blockPosition(),
                            net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.9F,
                            0.8F + graved.rank() * 0.05F);
                }
                SocketBenchMenu.this.inputs.setItem(SLOT_GEAR, ItemStack.EMPTY);
                // L'HEURE DOREE NE PREND PAS L'ECLAT DU DESTIN.
                //
                // L'Eclat est la seule monnaie du mode qu'on ne puisse pas
                // produire : le rendre pendant la fenetre, c'est offrir une
                // tentative de rarete entiere, et donner une raison de rentrer
                // au village au lieu d'en partir. Seul l'Eclat est rendu -- une
                // rune ou un artefact serti reste consomme, sans quoi la
                // fenetre ne serait plus une aubaine mais une exploitation.
                ItemStack spent = SocketBenchMenu.this.inputs.getItem(SLOT_ARTIFACT);
                boolean golden = com.emerald.weather.WeatherManager.current()
                        == com.emerald.weather.Weather.HEURE_DOREE
                        && spent.is(com.emerald.item.ModItems.FATE_SHARD.get());
                if (!golden) {
                    SocketBenchMenu.this.inputs.setItem(SLOT_ARTIFACT, ItemStack.EMPTY);
                } else if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                    "socket.emeraldweapons.golden")
                            .withStyle(net.minecraft.ChatFormatting.GOLD), true);
                }
                super.onTake(player, stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        this.result.setItem(0, socket(this.inputs.getItem(SLOT_GEAR),
                                      this.inputs.getItem(SLOT_ARTIFACT),
                                      this.owner));
        this.broadcastChanges();
    }

    /** Le resultat, ou une pile vide si la combinaison ne tient pas. */
    @Nullable
    private static ItemStack socket(ItemStack gear, ItemStack artifactStack, Player forge) {
        // L'AMELIORATION : comme les eclats, on montre la piece TELLE QUELLE.
        //
        // Le cran se joue aux des ; afficher un +8 avant de l'avoir obtenu
        // serait un mensonge, et afficher le +7 actuel dit exactement la
        // verite -- voila ce que vous posez, voila ce que vous risquez.
        if (forge != null && !gear.isEmpty()
                && com.emerald.item.Upgrade.of(gear) < com.emerald.item.GearEligibility.upgradeMax(gear)
                && com.emerald.item.Upgrade.affordable(forge, gear)) {
            return gear.copy();
        }

        // LES ECLATS : l'etabli montre la piece telle quelle, prete a etre
        // tentee. On ne peut pas montrer le resultat d'un tirage avant de le
        // tirer, et faire semblant serait pire que ne rien montrer : le rang
        // se decide au moment ou l'on prend.
        if (artifactStack.is(com.emerald.item.ModItems.FATE_SHARD.get()) && !gear.isEmpty()) {
            int max = com.emerald.item.GearEligibility.rarityMax(gear);
            if (com.emerald.item.GearRarity.of(gear).rank() >= max
                    && com.emerald.item.GearEligibility.isVanillaGear(gear)) {
                // TROP FAIBLE POUR MONTER PLUS HAUT : pas de resultat, donc rien
                // a prendre et rien de consomme -- et l'on dit pourquoi
                if (forge instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "socket.emeraldweapons.rarity.weak",
                            com.emerald.item.GearRarity.values()[max].label()), true);
                }
                return ItemStack.EMPTY;
            }
            return gear.copy();
        }
        // LA GRAVURE, elle, se montre en entier : contrairement au tirage de
        // rarete, son resultat est deja connu. Le joueur voit donc la piece
        // gravee avant de la prendre, et peut renoncer.
        com.emerald.rune.RuneMark mark = com.emerald.rune.Runes.of(artifactStack);
        if (mark != null) {
            if (!com.emerald.rune.Runes.canEngrave(gear, mark)) {
                return ItemStack.EMPTY;
            }
            ItemStack graved = gear.copy();
            com.emerald.rune.Runes.engrave(graved, mark);
            return graved;
        }

        Artifact artifact = Artifacts.of(artifactStack);
        if (gear.isEmpty() || artifact == null || !artifact.fits(gear)) {
            return ItemStack.EMPTY;
        }
        if (Artifacts.has(gear, artifact)) {
            return ItemStack.EMPTY;          // deja serti du meme : rien a faire
        }
        ItemStack out = gear.copy();
        Artifacts.set(out, artifact);
        return out;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return moved;
        }
        ItemStack stack = slot.getItem();
        moved = stack.copy();
        if (index <= SLOT_RESULT) {
            if (!this.moveItemStackTo(stack, SLOT_RESULT + 1, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, moved);
        } else if (!this.moveItemStackTo(stack, SLOT_GEAR, SLOT_RESULT, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> this.clearContainer(player, this.inputs));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.SOCKET_BENCH.get());
    }
}
