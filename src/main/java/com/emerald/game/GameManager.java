package com.emerald.game;

import com.emerald.block.ModBlocks;
import com.emerald.block.OathBladeBlock;
import com.emerald.block.PrismaticAnchorBlock;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le chef d'orchestre du Mode Arcencium.
 *
 * Il tient les sieges en cours et enchaine les etapes : prologue au village,
 * rituels d'ancre, puis affrontement final. L'etat durable vit dans
 * {@link GameState} ; ce qui est ici est volatil et se reconstruit au besoin.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public class GameManager {

    /** Arcencium exige par palier de siege. */
    private static final int[] ANCHOR_COST = {8, 16, 32};

    /** Composition des vagues, par palier. */
    private static final int[][] WAVES = {
            {5, 7, 9},
            {6, 8, 10, 12},
            {8, 10, 12, 14, 16},
    };

    /** Volontairement court : le prologue enseigne, il ne doit pas user. */
    private static final int[] PROLOGUE_WAVES = {3, 5, 6};

    @Nullable
    private static Siege prologue;

    /** Golems mis de cote le temps du siege, a rendre au village ensuite. */
    private static int borrowedGolems;
    private static final Map<BlockPos, Siege> anchorSieges = new HashMap<>();

    /**
     * Le Serment porte les defenseurs pendant toute la duree du prologue.
     *
     * Un buff de quarante secondes au retrait de la lame retombait bien avant la
     * derniere vague. Il est renouvele tant que le siege dure : c'est ce qui
     * rend le village tenable en armure de fer, et c'est coherent avec ce que
     * le Serment est cense etre.
     */
    private static void sustainOath(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 120, 0, true, false, true));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 120, 0, true, false, true));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.REGENERATION, 120, 0, true, false, true));
        }
    }

    /** Vrai pendant le siege du village : les regles de lobby s'assouplissent alors. */
    public static boolean prologueRunning() {
        return prologue != null;
    }

    // ------------------------------------------------------------- le tick

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        // Les armes ceremonielles n'existent QUE pendant le prologue. Un balayage
        // periodique vaut mieux qu'un nettoyage a chaque bascule : il rattrape
        // aussi les cas qu'on n'a pas prevus -- lame rangee dans un coffre,
        // laissee au sol, ou recuperee apres un echec, qui permettait sinon
        // d'en accumuler autant qu'on ratait de sieges.
        if (level.getGameTime() % 20 == 0
                && GameState.get(level).status() != GameState.Status.PROLOGUE) {
            dissolveCeremonial(level);
        }
        if (prologue != null) {
            sustainOath(level);
            prologue.tick();
            if (prologue.isDone()) {
                Siege finished = prologue;
                prologue = null;
                if (finished.isWon()) {
                    openTheGame(level, finished.center());
                } else {
                    announce(level, "game.emeraldweapons.village_lost",
                            "game.emeraldweapons.village_lost.sub", 0xFF616B);
                    GameState.get(level).returnToLobby();
                    dissolveCeremonial(level);
                    replantBlade(level, finished.center());
                    // sans repeuplement, la mission serait perdue pour toujours :
                    // sa condition d'echec est justement l'absence de villageois
                    surroundWithVillagers(level, finished.center());
                    WorldSetup.clearHostiles(level, finished.center());
                }
            }
        }
        tickAnchorSieges(level);
    }

    private static void tickAnchorSieges(ServerLevel level) {
        if (anchorSieges.isEmpty()) {
            return;
        }
        List<BlockPos> finished = new ArrayList<>();
        for (Map.Entry<BlockPos, Siege> entry : anchorSieges.entrySet()) {
            Siege siege = entry.getValue();
            siege.tick();
            if (siege.isDone()) {
                finished.add(entry.getKey());
            }
        }
        for (BlockPos pos : finished) {
            Siege siege = anchorSieges.remove(pos);
            resolveAnchor(level, pos, siege.isWon());
        }
    }

    /**
     * Ecarte les golems de fer le temps du siege.
     *
     * Ils abattaient les vagues a la place des joueurs, qui n'avaient plus
     * qu'a regarder : le prologue est cense apprendre a se battre avec les
     * armes du mode. Ils sont rendus au village des qu'il est tenu -- les
     * retirer definitivement priverait la suite de la partie de ses gardiens.
     */
    private static void setGolemsAside(ServerLevel level, BlockPos center) {
        var golems = level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.IronGolem.class,
                new net.minecraft.world.phys.AABB(center).inflate(Siege.LEASH * 2));
        borrowedGolems = Math.max(borrowedGolems, golems.size());
        golems.forEach(net.minecraft.world.entity.Entity::discard);
    }

    /** Rend au village autant de golems qu'on lui en avait pris. */
    private static void returnGolems(ServerLevel level, BlockPos center) {
        for (int i = 0; i < borrowedGolems; i++) {
            double angle = i / (double) Math.max(1, borrowedGolems) * Math.PI * 2;
            BlockPos spot = WorldSetup.findOpenGround(level, center.offset(
                    (int) Math.round(Math.cos(angle) * 7), 0,
                    (int) Math.round(Math.sin(angle) * 7)), 8);
            var golem = net.minecraft.world.entity.EntityType.IRON_GOLEM.spawn(level, spot,
                    net.minecraft.world.entity.MobSpawnType.EVENT);
            if (golem != null) {
                golem.setPlayerCreated(false);
            }
        }
        borrowedGolems = 0;
    }

    /**
     * Prete aux autres defenseurs un arc ou un sceptre, au hasard.
     *
     * Celui qui tire la lame porte l'epee ; les autres recoivent l'une des deux
     * autres armes du mode. La composition d'equipe existe donc des le prologue,
     * et chacun voit ce que le mode reserve sans qu'on court-circuite la
     * progression -- tout est repris a la fin du siege.
     */
    private static void lendCeremonialArms(ServerLevel level, Player puller) {
        for (ServerPlayer other : level.players()) {
            if (other == puller) {
                continue;
            }
            boolean bow = level.random.nextBoolean();
            ItemStack weapon = new ItemStack(bow
                    ? ModItems.ARCENCIUM_BOW.get() : ModItems.ARCENCIUM_SCEPTER.get());
            weapon.set(com.emerald.artifact.ModDataComponents.CEREMONIAL.get(),
                    net.minecraft.util.Unit.INSTANCE);
            if (bow) {
                // Infinite, plutot qu'une reserve qui s'epuise au milieu du siege.
                // Vanilla exige malgre tout une fleche en poche pour que
                // l'enchantement s'applique : on en laisse quelques-unes.
                enchant(other, weapon,
                        net.minecraft.world.item.enchantment.Enchantments.INFINITY, 1);
                other.getInventory().add(
                        new ItemStack(net.minecraft.world.item.Items.ARROW, 16));
            }
            other.getInventory().add(weapon);
        }
    }

    /** Vrai pour tout ce qui n'a ete prete que le temps du prologue. */
    private static boolean isCeremonial(ItemStack stack) {
        return stack.is(ModItems.OATH_BLADE.get())
                || stack.has(com.emerald.artifact.ModDataComponents.CEREMONIAL.get());
    }

    /** Efface toute arme ceremonielle du monde : inventaires et objets au sol. */
    private static void dissolveCeremonial(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (isCeremonial(inventory.getItem(slot))) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                    sparkle(level, player.blockPosition());
                }
            }
        }
        for (net.minecraft.world.entity.item.ItemEntity dropped
                : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(level.getSharedSpawnPos())
                                .inflate(GameState.PLAY_RADIUS),
                        e -> isCeremonial(e.getItem()))) {
            sparkle(level, dropped.blockPosition());
            dropped.discard();
        }
    }

    private static void sparkle(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0,
                pos.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.08);
    }

    // -------------------------------------------------------- mise en place

    /**
     * Prepare une partie : village, Lame du Serment, trois ancres.
     *
     * Le village est le point d'apparition du monde plutot qu'un village
     * genere : c'est le seul endroit dont on soit certain qu'il existe et qu'il
     * soit accessible, quel que soit le monde tire.
     */
    public static void setup(ServerLevel level, BlockPos center) {
        GameState state = GameState.get(level);
        removePreviousBlade(level, state);
        state.reset();

        BlockPos ground = WorldSetup.findOpenGround(level, center, 24);
        state.setVillage(ground);
        plantBlade(level, ground);
        surroundWithVillagers(level, ground);

        List<BlockPos> anchors = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(90 + i * 120);
            int x = ground.getX() + (int) Math.round(Math.cos(angle) * GameState.ANCHOR_DISTANCE);
            int z = ground.getZ() + (int) Math.round(Math.sin(angle) * GameState.ANCHOR_DISTANCE);
            anchors.add(WorldSetup.findOpenGround(level, new BlockPos(x, 0, z), 16));
        }
        state.setAnchors(anchors);
        state.returnToLobby();
        WorldSetup.clearHostiles(level, ground);

        BlockPos stand = playerSpot(level, ground);
        level.setDefaultSpawnPos(stand, 0.0F);
        for (ServerPlayer player : level.players()) {
            player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            player.setRespawnPosition(level.dimension(), stand, 0.0F, true, false);
            equipStarter(player);
        }
        announce(level, "game.emeraldweapons.village_intro",
                "game.emeraldweapons.village_intro.sub", 0x9CE8FF);
        // les coordonnees en clair : c'est la seule facon d'etre certain de
        // regarder la bonne lame quand une mise en place en a suivi une autre
        net.minecraft.network.chat.Component where = net.minecraft.network.chat.Component
                .translatable("game.emeraldweapons.locked.where", ground.getX(), ground.getY(),
                        ground.getZ(), 0)
                .withStyle(net.minecraft.ChatFormatting.AQUA);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(where);
        }
        org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID).info(
                "Lame du Serment posee en {}", ground);
    }

    /**
     * Ou poser les joueurs : a cote du socle, jamais dessus.
     *
     * Le bloc de la lame a une boite de collision : y teleporter un joueur
     * l'emmure. On cherche donc un appui degage a quelques pas, ce qui a aussi
     * l'avantage de lui faire voir le monument en arrivant.
     */
    private static BlockPos playerSpot(ServerLevel level, BlockPos blade) {
        // colle au socle : la lame doit etre sous les yeux des la premiere seconde
        return WorldSetup.findOpenGround(level, blade.offset(3, 0, 0), 4);
    }

    /**
     * Retire la lame de la mise en place precedente.
     *
     * Sans cela, refaire la mise en place en laisse DEUX dans le monde : la
     * nouvelle en surface, et l'ancienne la ou les regles d'alors l'avaient
     * posee -- souvent sous terre. Un joueur qui suit d'anciennes coordonnees
     * tombe sur la mauvaise et croit que rien n'a change.
     */
    private static void removePreviousBlade(ServerLevel level, GameState state) {
        BlockPos old = state.village();
        if (old.equals(BlockPos.ZERO)) {
            return;
        }
        if (level.getBlockState(old).is(ModBlocks.OATH_BLADE.get())) {
            level.setBlockAndUpdate(old, Blocks.AIR.defaultBlockState());
        }
    }

    /** Meme piege que pour la lame : sans chargement force, la hauteur vaut -64. */
    private static BlockPos surface(ServerLevel level, BlockPos around) {
        return new BlockPos(around.getX(),
                WorldSetup.surfaceY(level, around.getX(), around.getZ()), around.getZ());
    }

    /**
     * Plante la lame sur un socle.
     *
     * La lame seule, fichee dans l'herbe, ne se lit pas : c'est le socle qui
     * fait le monument. Trois anneaux, du plus large au plus etroit, avec des
     * lanternes aux angles -- de quoi la reperer de loin et de nuit.
     */
    private static void plantBlade(ServerLevel level, BlockPos ground) {
        BlockState rim = ModBlocks.POLISHED_GANGUE.get().defaultBlockState();
        BlockState core = ModBlocks.ARCENCIUM_BRICKS.get().defaultBlockState();
        BlockState crown = ModBlocks.CHISELED_ARCENCIUM.get().defaultBlockState();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                BlockPos base = ground.offset(dx, -1, dz);
                level.setBlockAndUpdate(base, ring == 0 ? crown : (ring == 1 ? core : rim));
                if (ring <= 1 && !(dx == 0 && dz == 0)) {
                    level.setBlockAndUpdate(base.above(), net.minecraft.world.level.block.Blocks.AIR
                            .defaultBlockState());
                }
                if (ring == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    level.setBlockAndUpdate(base.above(),
                            ModBlocks.ARCENCIUM_LANTERN.get().defaultBlockState());
                }
            }
        }
        // on degage la colonne au-dessus du socle : plante sous un plafond, la
        // lame serait invisible et les joueurs coinces
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy < 5; dy++) {
                    BlockPos above = ground.offset(dx, dy, dz);
                    if (!level.getBlockState(above).isAir()) {
                        level.setBlockAndUpdate(above,
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        level.setBlockAndUpdate(ground, ModBlocks.OATH_BLADE.get().defaultBlockState()
                .setValue(OathBladeBlock.PLANTED, true));
    }

    private static void replantBlade(ServerLevel level, BlockPos ground) {
        plantBlade(level, ground);
    }

    /** Nombre de villageois garanti avant chaque tentative de defense. */
    private static final int MIN_VILLAGERS = 6;

    /**
     * Les villageois autour de la lame.
     *
     * Ils sont l'appat qui attire les joueurs vers la place -- mais surtout,
     * ce sont EUX qu'il faut garder en vie : le prologue n'est perdu que
     * lorsqu'il n'en reste aucun. On en repose donc autant qu'il en manque
     * avant chaque tentative, sans quoi un village decime rendrait la mission
     * definitivement injouable.
     */
    private static void surroundWithVillagers(ServerLevel level, BlockPos center) {
        int present = level.getEntitiesOfClass(
                net.minecraft.world.entity.npc.Villager.class,
                new net.minecraft.world.phys.AABB(center).inflate(Siege.LEASH),
                e -> e.isAlive()).size();
        int missing = MIN_VILLAGERS - present;
        for (int i = 0; i < missing; i++) {
            double angle = i / (double) MIN_VILLAGERS * Math.PI * 2;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * 5);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * 5);
            var villager = EntityType.VILLAGER.spawn(level, surface(level, new BlockPos(x, 0, z)),
                    net.minecraft.world.entity.MobSpawnType.EVENT);
            if (villager != null) {
                // sans persistance, un villageois pose par le mod peut disparaitre
                // et faire echouer la mission tout seul
                villager.setPersistenceRequired();
                villager.restrictTo(center, Siege.LEASH);
            }
        }
    }

    /** Fer complet, bouclier, epee : assez pour tenir, pas pour se croire invincible. */
    public static void equipStarter(ServerPlayer player) {
        player.getInventory().clearContent();
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                enchanted(player, net.minecraft.world.item.Items.IRON_HELMET,
                        net.minecraft.world.item.enchantment.Enchantments.PROTECTION, 1));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
                enchanted(player, net.minecraft.world.item.Items.IRON_CHESTPLATE,
                        net.minecraft.world.item.enchantment.Enchantments.PROTECTION, 1));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS,
                enchanted(player, net.minecraft.world.item.Items.IRON_LEGGINGS,
                        net.minecraft.world.item.enchantment.Enchantments.PROTECTION, 1));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,
                enchanted(player, net.minecraft.world.item.Items.IRON_BOOTS,
                        net.minecraft.world.item.enchantment.Enchantments.PROTECTION, 1));
        player.getInventory().add(enchanted(player, net.minecraft.world.item.Items.IRON_SWORD,
                net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 1));
        player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.SHIELD));
        player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.BREAD, 16));
        // Rien ne peut etre casse avant le depart : sans de quoi monter, un
        // joueur au pied d'une falaise reste bloque. L'echafaudage sert a ca et
        // a rien d'autre -- il ne se transforme en aucune ressource.
        player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.SCAFFOLDING, 32));
    }

    /** Protection I et Tranchant I, comme prevu au cahier. */
    private static ItemStack enchanted(ServerPlayer player, net.minecraft.world.item.Item item,
                                       net.minecraft.resources.ResourceKey<
                                               net.minecraft.world.item.enchantment.Enchantment> key,
                                       int level) {
        return enchant(player, new ItemStack(item), key, level);
    }

    /** Pose un enchantement sur une pile existante, en resolvant son registre. */
    private static ItemStack enchant(ServerPlayer player, ItemStack stack,
                                     net.minecraft.resources.ResourceKey<
                                             net.minecraft.world.item.enchantment.Enchantment> key,
                                     int level) {
        player.level().registryAccess()
                .lookup(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .flatMap(registry -> registry.get(key))
                .ifPresent(holder -> stack.enchant(holder, level));
        return stack;
    }

    // ----------------------------------------------------- la Lame du Serment

    /** Le retrait de la lame : c'est ici, et nulle part ailleurs, que la partie commence. */
    public static void pullOathBlade(Level world, BlockPos pos, Player player) {
        if (!(world instanceof ServerLevel level)) {
            return;
        }
        GameState state = GameState.get(level);
        if (state.status() != GameState.Status.LOBBY
                && state.status() != GameState.Status.PROLOGUE) {
            return;
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        player.getInventory().add(new ItemStack(ModItems.OATH_BLADE.get()));

        lendCeremonialArms(level, player);

        // le Serment lie toute l'equipe, pas seulement celui qui a tire la lame
        for (ServerPlayer other : level.players()) {
            other.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 40 * 20, 0));
            other.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 40 * 20, 0));
        }

        level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.2F, 0.8F);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0,
                pos.getZ() + 0.5, 60, 0.5, 1.0, 0.5, 0.25);
        announce(level, "game.emeraldweapons.blade_pulled",
                "game.emeraldweapons.blade_pulled.sub", 0xFFD36B);

        state.beginPrologue();
        surroundWithVillagers(level, pos);
        setGolemsAside(level, pos);
        prologue = new Siege(level, pos, 1, PROLOGUE_WAVES,
                Component.translatable("game.emeraldweapons.siege.village"),
                BossEvent.BossBarColor.RED, Siege.Failure.VILLAGERS);
    }

    /**
     * Le village est tenu : la lame se dissout et devient les trois ancres.
     *
     * Enchainer les deux moments plutot que les juxtaposer -- la lame ne
     * disparait pas, elle donne naissance a l'objectif suivant.
     */
    private static void openTheGame(ServerLevel level, BlockPos center) {
        GameState state = GameState.get(level);
        dissolveCeremonial(level);
        returnGolems(level, center);
        level.sendParticles(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + 1.0,
                center.getZ() + 0.5, 120, 1.0, 1.5, 1.0, 0.5);
        level.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.4F, 0.9F);

        for (BlockPos anchor : state.anchors()) {
            level.setBlockAndUpdate(anchor, ModBlocks.PRISMATIC_ANCHOR.get().defaultBlockState());
            level.setBlockAndUpdate(anchor.below(),
                    ModBlocks.ARCENCIUM_BRICKS.get().defaultBlockState());
        }
        state.begin(level);
        announce(level, "game.emeraldweapons.anchors_risen",
                "game.emeraldweapons.anchors_risen.sub", 0x9CE8FF);
        // le titre passe ; les coordonnees restent dans le journal du chat, et
        // l'interface les rappelle en permanence
        int index = 1;
        for (BlockPos anchor : state.anchors()) {
            net.minecraft.network.chat.Component line = net.minecraft.network.chat.Component
                    .translatable("game.emeraldweapons.anchor.at", index++,
                            anchor.getX(), anchor.getY(), anchor.getZ())
                    .withStyle(net.minecraft.ChatFormatting.AQUA);
            for (ServerPlayer player : level.players()) {
                player.sendSystemMessage(line);
            }
        }
    }

    // ------------------------------------------------------------ les ancres

    public static void describeAnchor(Level world, BlockPos pos, Player player) {
        if (!(world instanceof ServerLevel level)) {
            return;
        }
        GameState state = GameState.get(level);
        if (state.isActivated(pos)) {
            player.displayClientMessage(Component.translatable(
                    "game.emeraldweapons.anchor.held").withStyle(ChatFormatting.AQUA), true);
            return;
        }
        int tier = state.nextTier();
        player.displayClientMessage(Component.translatable(
                "game.emeraldweapons.anchor.needs", ANCHOR_COST[tier - 1], tier)
                .withStyle(ChatFormatting.YELLOW), true);
    }

    /** Alimente une ancre en Arcencium. Vrai si le rituel demarre. */
    public static boolean feedAnchor(Level world, BlockPos pos, Player player, ItemStack stack) {
        if (!(world instanceof ServerLevel level)
                || !stack.is(ModItems.ARCENCIUM_INGOT.get())) {
            return false;
        }
        GameState state = GameState.get(level);
        if (state.status() != GameState.Status.RUNNING
                || state.isActivated(pos) || anchorSieges.containsKey(pos)) {
            return false;
        }
        int tier = state.nextTier();
        int cost = ANCHOR_COST[tier - 1];
        if (stack.getCount() < cost) {
            player.displayClientMessage(Component.translatable(
                    "game.emeraldweapons.anchor.needs", cost, tier)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        stack.shrink(cost);

        state.anchorStarted();
        anchorSieges.put(pos, new Siege(level, pos, tier, WAVES[tier - 1],
                Component.translatable("game.emeraldweapons.siege.anchor", tier),
                tier >= 3 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.BLUE,
                Siege.Failure.DEFENDERS));

        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.2F, 1.0F);
        announce(level, "game.emeraldweapons.ritual_begun",
                "game.emeraldweapons.ritual_begun.sub", 0xFFD36B);
        return true;
    }

    private static void resolveAnchor(ServerLevel level, BlockPos pos, boolean won) {
        GameState state = GameState.get(level);
        state.anchorFinished(pos, won);
        if (!won) {
            // l'Arcencium est perdu : c'est ce qui donne du poids au moment
            announce(level, "game.emeraldweapons.ritual_lost",
                    "game.emeraldweapons.ritual_lost.sub", 0xFF616B);
            return;
        }
        BlockState anchor = level.getBlockState(pos);
        if (anchor.hasProperty(PrismaticAnchorBlock.ACTIVE)) {
            level.setBlockAndUpdate(pos, anchor.setValue(PrismaticAnchorBlock.ACTIVE, true));
        }
        // une ancre tenue devient un point de reapparition
        for (ServerPlayer player : level.players()) {
            player.setRespawnPosition(level.dimension(), pos, 0.0F, true, false);
        }
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4F, 1.2F);
        announce(level, "game.emeraldweapons.anchor_held",
                "game.emeraldweapons.anchor_held.sub", 0x78E8AE);

        if (state.anchorsActive() >= 3) {
            announce(level, "game.emeraldweapons.rainbow",
                    "game.emeraldweapons.rainbow.sub", 0xB98CFF);
        }
    }

    // ------------------------------------------------------------- annonces

    /** Titre plein ecran : reserve aux bascules, pour qu'il garde son poids. */
    public static void announce(ServerLevel level, String title, String subtitle, int color) {
        announce(level,
                Component.translatable(title).withStyle(style -> style.withColor(color)),
                Component.translatable(subtitle).withStyle(ChatFormatting.GRAY));
    }

    /** Variante a composants deja construits, pour les titres parametres (meteo). */
    public static void announce(ServerLevel level, Component top, Component bottom) {
        for (ServerPlayer player : level.players()) {
            player.connection.send(new ClientboundSetTitleTextPacket(top));
            player.connection.send(new ClientboundSetSubtitleTextPacket(bottom));
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.MASTER, 0.8F, 1.2F);
        }
    }

    /** Abandonne tout siege en cours : arret de partie, ou rechargement du monde. */
    public static void clear() {
        if (prologue != null) {
            prologue.cancel();
            prologue = null;
        }
        anchorSieges.values().forEach(Siege::cancel);
        anchorSieges.clear();
    }
}
