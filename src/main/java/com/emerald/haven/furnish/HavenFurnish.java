package com.emerald.haven.furnish;

import com.emerald.block.HipHogBlocks;
import com.emerald.block.ModBlocks;
import com.emerald.haven.Haven;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Le coffre d'amenagement sans fond (cahier §107) : tout ce qui decore une salle, par onglets, et
 * autant qu'on veut -- chaque clic donne une pile entiere, et le coffre ne se vide jamais.
 *
 * LE CATALOGUE se dresse a la premiere ouverture, depuis le registre des objets : les blocs du mod
 * (Hip Hog, vitres de Jak 3), les lumieres, meubles, portes et fenetres des mods du modpack qui sont
 * charges (Macaw's, Handcrafted, Refurbished, Simply Light, Additional Lights, Chipped), et ce que le
 * jeu de base a de decoratif. Rien que des blocs. Supplementaries n'y est pas : certains de ses
 * blocs agissent (canons...).
 *
 * UN MENU DE COFFRE DU JEU DE BASE (9 x 6), mene cote serveur : cinq rangees de catalogue, et en bas
 * la page precedente, les sept onglets, la page suivante (accroupi : dix pages d'un coup). Un clic
 * donne une pile dans la main, un clic accroupi la range dans l'inventaire ; un objet du coffre
 * repose sur le catalogue y retourne (il disparait). Les cases du catalogue n'acceptent rien.
 *
 * LES OBJETS DU COFFRE RESTENT A HAVEN : marques (CustomData {@link #TAG}), ils sont retires de
 * l'inventaire en quittant la ville -- sinon le Defi recevrait des materiaux gratuits.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenFurnish {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** La marque des objets du coffre. */
    public static final String TAG = "emeraldweapons_amenagement";
    private static final int ROWS = 6;
    private static final int PER_PAGE = 45;

    /** Les onglets. */
    public enum Category {
        JAK3("jak3"), LUMIERES("lumieres"), MEUBLES("meubles"), MENUISERIE("menuiserie"), TISSUS("tissus"),
        BLOCS("blocs"), VARIANTES("variantes");

        final String key;

        Category(String key) {
            this.key = key;
        }

        Component title() {
            return Component.translatable("gui.emeraldweapons.amenagement." + this.key);
        }
    }

    /** Les espaces de noms des mods, par onglet ; ceux qui ne sont pas charges sont simplement vides. */
    private static final Map<Category, List<String>> MODS = Map.of(
            Category.LUMIERES, List.of("simplylight", "mcwlights", "additional_lights"),
            Category.MEUBLES, List.of("mcwfurnitures", "handcrafted", "refurbished_furniture", "mcwholidays"),
            Category.MENUISERIE, List.of("mcwdoors", "mcwwindows", "mcwtrapdoors", "mcwfences", "mcwroofs", "mcwstairs",
                    "mcwpaths", "mcwbridges"),
            Category.VARIANTES, List.of("chipped"));

    private static final Pattern VANILLA_LIGHT = Pattern.compile(
            "(soul_)?lantern|(soul_)?torch|sea_lantern|glowstone|shroomlight|.*froglight|end_rod|redstone_lamp"
                    + "|jack_o_lantern|(.*_)?candle");
    private static final Pattern VANILLA_FURNITURE = Pattern.compile(
            "bookshelf|chiseled_bookshelf|lectern|barrel|chest|flower_pot|decorated_pot|crafting_table|loom"
                    + "|cartography_table|fletching_table|smithing_table|stonecutter|composter|.*_sign|.*_hanging_sign");
    private static final Pattern VANILLA_JOINERY = Pattern.compile(
            ".*_door|.*_trapdoor|.*_fence|.*_fence_gate|ladder|iron_bars|chain|.*glass_pane|scaffolding");
    private static final Pattern VANILLA_FABRIC = Pattern.compile(".*_carpet|.*_wool|.*_bed|.*_banner");
    private static final Pattern VANILLA_BLOCKS = Pattern.compile(
            ".*(planks|_log|_wood|stone|brick|deepslate|tuff|blackstone|quartz|prismarine|purpur|sandstone|copper"
                    + "|concrete|terracotta|glass|calcite|basalt|andesite|diorite|granite|mosaic|mud).*");
    /** Ce qui agit, blesse, tombe ou n'a rien de decoratif. */
    private static final Pattern VANILLA_EXCLUDED = Pattern.compile(
            "redstone|redstone_(block|torch|ore)|.*(_ore|infested|spawner|grindstone|lodestone|dispenser|dropper|piston"
                    + "|observer|button|pressure_plate|raw_|budding|reinforced|command|structure|jigsaw|_powder|pointed"
                    + "|comparator|repeater|lever|tripwire|daylight|sculk|tnt|hopper|target|crafter).*");

    @Nullable
    private static Map<Category, List<Item>> catalogue;

    private HavenFurnish() {
    }

    // ================================================================ le catalogue

    /** Le catalogue, dresse a la premiere ouverture. */
    public static Map<Category, List<Item>> catalogue() {
        if (catalogue != null) {
            return catalogue;
        }
        Map<Category, Set<Item>> out = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            out.put(category, new LinkedHashSet<>());
        }
        // Jak 3 : les blocs du bar du Hip Hog, et les vitres
        for (var block : HipHogBlocks.all()) {
            out.get(Category.JAK3).add(block.get().asItem());
        }
        out.get(Category.JAK3).add(ModBlocks.HAVEN_WINDOW.get().asItem());
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BlockItem block) || block.getBlock() == Blocks.AIR) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String ns = id.getNamespace();
            String path = id.getPath();
            for (Map.Entry<Category, List<String>> mods : MODS.entrySet()) {
                if (mods.getValue().contains(ns)) {
                    out.get(mods.getKey()).add(item);
                }
            }
            if (!"minecraft".equals(ns) || VANILLA_EXCLUDED.matcher(path).matches()) {
                continue;
            }
            if (VANILLA_LIGHT.matcher(path).matches()) {
                out.get(Category.LUMIERES).add(item);
            } else if (VANILLA_FABRIC.matcher(path).matches()) {
                out.get(Category.TISSUS).add(item);
            } else if (VANILLA_FURNITURE.matcher(path).matches()) {
                out.get(Category.MEUBLES).add(item);
            } else if (VANILLA_JOINERY.matcher(path).matches()) {
                out.get(Category.MENUISERIE).add(item);
            } else if (VANILLA_BLOCKS.matcher(path).matches()) {
                out.get(Category.BLOCS).add(item);
            }
        }
        Map<Category, List<Item>> built = new EnumMap<>(Category.class);
        StringBuilder counts = new StringBuilder();
        for (Map.Entry<Category, Set<Item>> entry : out.entrySet()) {
            built.put(entry.getKey(), List.copyOf(entry.getValue()));
            counts.append(entry.getKey().key).append(' ').append(entry.getValue().size()).append(", ");
        }
        catalogue = built;
        LOGGER.info("Coffre d'amenagement : catalogue dresse -- {}", counts);
        return catalogue;
    }

    /** Une pile entiere de cet objet, marquee du coffre. */
    public static ItemStack stack(Item item) {
        ItemStack stack = new ItemStack(item, item.getDefaultMaxStackSize());
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** Un objet venu du coffre ? */
    public static boolean marked(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(TAG);
    }

    /** Ouvre le coffre pour ce joueur. */
    public static void open(ServerPlayer player, BlockPos chest) {
        open(player, chest, 0);
    }

    /** Ouvre le coffre sur cet onglet (l'automate de photos). */
    public static void open(ServerPlayer player, BlockPos chest, int category) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> {
            Menu menu = new Menu(id, inventory, chest);
            menu.category = Category.values()[Math.floorMod(category, Category.values().length)];
            menu.refresh();
            return menu;
        }, Component.translatable("block.emeraldweapons.haven_furnish_chest")));
    }

    /**
     * Pour le banc : un vrai menu sur ce joueur, et les clics d'un joueur -- l'onglet des lumieres, la
     * page suivante, une pile prise puis rendue, une pile rangee, un depot refuse.
     *
     * @return null si tout va, sinon ce qui cloche
     */
    @Nullable
    public static String benchMenu(ServerPlayer player, BlockPos chest) {
        Menu menu = new Menu(0, player.getInventory(), chest);
        List<Item> jak = catalogue().get(Category.JAK3);
        List<Item> lights = catalogue().get(Category.LUMIERES);
        if (jak.isEmpty() || lights.isEmpty() || !menu.getSlot(0).getItem().is(jak.get(0))) {
            return "premiere case : " + menu.getSlot(0).getItem();
        }
        menu.clicked(47, 0, ClickType.PICKUP, player);
        if (!menu.getSlot(0).getItem().is(lights.get(0)) || !menu.getSlot(47).getItem().has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) {
            return "onglet des lumieres : " + menu.getSlot(0).getItem();
        }
        if (lights.size() > PER_PAGE) {
            menu.clicked(53, 0, ClickType.PICKUP, player);
            if (!menu.getSlot(0).getItem().is(lights.get(PER_PAGE))) {
                return "page suivante : " + menu.getSlot(0).getItem();
            }
            menu.clicked(45, 0, ClickType.PICKUP, player);
        }
        menu.clicked(0, 0, ClickType.PICKUP, player);
        ItemStack carried = menu.getCarried();
        if (!carried.is(lights.get(0)) || carried.getCount() != carried.getMaxStackSize() || !marked(carried)
                || !menu.getSlot(0).getItem().is(lights.get(0))) {
            return "pile prise : " + carried + ", case " + menu.getSlot(0).getItem();
        }
        menu.clicked(1, 0, ClickType.PICKUP, player);
        if (!menu.getCarried().isEmpty()) {
            return "pile rendue : " + menu.getCarried();
        }
        int before = player.getInventory().countItem(lights.get(0));
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        int after = player.getInventory().countItem(lights.get(0));
        if (after - before != new ItemStack(lights.get(0)).getMaxStackSize()) {
            return "pile rangee : " + before + " -> " + after;
        }
        ItemStack mine = new ItemStack(Items.DIRT, 3);
        menu.setCarried(mine);
        menu.clicked(5, 0, ClickType.PICKUP, player);
        if (!menu.getCarried().is(Items.DIRT) || menu.getSlot(5).getItem().is(Items.DIRT)) {
            return "depot sur le catalogue : " + menu.getCarried() + ", case " + menu.getSlot(5).getItem();
        }
        menu.setCarried(ItemStack.EMPTY);
        return null;
    }

    // ================================================================ le menu

    /** Un menu de coffre du jeu de base, dont les cases du haut sont le catalogue. */
    static final class Menu extends ChestMenu {

        private final SimpleContainer shown;
        private final BlockPos chest;
        private Category category = Category.JAK3;
        private int page;

        Menu(int id, Inventory inventory, BlockPos chest) {
            this(id, inventory, new SimpleContainer(9 * ROWS), chest);
        }

        private Menu(int id, Inventory inventory, SimpleContainer shown, BlockPos chest) {
            super(MenuType.GENERIC_9x6, id, inventory, shown, ROWS);
            this.shown = shown;
            this.chest = chest;
            // les cases du catalogue ne prennent ni ne rendent rien d'elles-memes
            for (int i = 0; i < 9 * ROWS; i++) {
                Slot old = this.slots.get(i);
                Slot locked = new Locked(shown, i, old.x, old.y);
                locked.index = i;
                this.slots.set(i, locked);
            }
            this.refresh();
        }

        private List<Item> items() {
            return catalogue().getOrDefault(this.category, List.of());
        }

        private int pages() {
            return Math.max(1, (this.items().size() + PER_PAGE - 1) / PER_PAGE);
        }

        private void refresh() {
            List<Item> items = this.items();
            int pages = this.pages();
            this.page = Math.max(0, Math.min(this.page, pages - 1));
            for (int i = 0; i < PER_PAGE; i++) {
                int n = this.page * PER_PAGE + i;
                this.shown.setItem(i, n < items.size() ? new ItemStack(items.get(n)) : ItemStack.EMPTY);
            }
            this.shown.setItem(45, button(Items.ARROW, Component.translatable("gui.emeraldweapons.amenagement.precedente",
                    this.page + 1, pages), false));
            Category[] all = Category.values();
            for (int c = 0; c < all.length && c < 7; c++) {
                List<Item> list = catalogue().getOrDefault(all[c], List.of());
                Item icon = list.isEmpty() ? Items.GRAY_STAINED_GLASS_PANE : list.get(0);
                this.shown.setItem(46 + c, button(icon, all[c].title().copy().append(" (" + list.size() + ")"),
                        all[c] == this.category));
            }
            this.shown.setItem(53, button(Items.SPECTRAL_ARROW, Component.translatable("gui.emeraldweapons.amenagement.suivante",
                    this.page + 1, pages), false));
        }

        private static ItemStack button(Item item, Component name, boolean selected) {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(style -> style.withItalic(false)
                    .withColor(selected ? ChatFormatting.GOLD : ChatFormatting.WHITE)));
            if (selected) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable(
                    "gui.emeraldweapons.amenagement.aide").withStyle(ChatFormatting.GRAY))));
            return stack;
        }

        @Override
        public void clicked(int slot, int button, ClickType type, Player player) {
            if (type == ClickType.PICKUP_ALL || type == ClickType.QUICK_CRAFT && slot >= 0 && slot < 9 * ROWS) {
                return;                 // ni ramasser tout, ni etaler sur le catalogue
            }
            if (slot >= 9 * ROWS || slot < 0) {
                if (type == ClickType.QUICK_MOVE) {
                    return;             // rien ne descend de l'inventaire dans le catalogue
                }
                super.clicked(slot, button, type, player);
                return;
            }
            boolean shift = type == ClickType.QUICK_MOVE;
            if (slot == 45 || slot == 53) {
                int step = shift ? 10 : 1;
                int pages = this.pages();
                this.page = Math.floorMod(this.page + (slot == 53 ? step : -step), pages);
                this.refresh();
                return;
            }
            if (slot > 45) {
                int c = slot - 46;
                if (c < Category.values().length) {
                    this.category = Category.values()[c];
                    this.page = 0;
                    this.refresh();
                }
                return;
            }
            int n = this.page * PER_PAGE + slot;
            List<Item> items = this.items();
            if (n >= items.size() || (type != ClickType.PICKUP && type != ClickType.QUICK_MOVE)) {
                return;
            }
            ItemStack carried = this.getCarried();
            if (!carried.isEmpty()) {
                if (marked(carried)) {
                    this.setCarried(ItemStack.EMPTY);   // l'objet retourne au coffre
                }
                return;
            }
            ItemStack given = stack(items.get(n));
            if (shift) {
                player.getInventory().add(given);
            } else {
                this.setCarried(given);
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return player.distanceToSqr(this.chest.getX() + 0.5, this.chest.getY() + 0.5, this.chest.getZ() + 0.5) <= 64.0;
        }

        @Override
        public Container getContainer() {
            return this.shown;
        }
    }

    /** Une case du catalogue : on n'y pose rien, on n'y prend rien d'elle-meme. */
    private static final class Locked extends Slot {

        Locked(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    // ================================================================ ils restent a Haven

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getFrom().equals(Haven.LEVEL) && event.getEntity() instanceof ServerPlayer player) {
            strip(player);
        }
    }

    /** Retire de l'inventaire les objets du coffre ; dit combien. */
    public static int strip(ServerPlayer player) {
        int removed = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (marked(inventory.getItem(i))) {
                inventory.setItem(i, ItemStack.EMPTY);
                removed++;
            }
        }
        if (marked(player.containerMenu.getCarried())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            removed++;
        }
        if (removed > 0) {
            player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.appart.restent")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return removed;
    }
}
