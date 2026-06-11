package com.emerald.world.structure;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import com.emerald.init.Jak3Registry;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

import java.util.Optional;

/*
 * Haven City — ville industrielle en ruines inspirée de Jak 3.
 *
 * Composition :
 *   - Base en béton (deepslate / iron blocks) avec rues en grille
 *   - 3 à 5 tours métalliques de hauteur variable (10-20 blocs)
 *   - Ponts surélevés reliant les tours
 *   - Zone KG (secteur occupé) avec barricades et barbelés
 *   - Éclairage avec lanternes et chaînes
 *   - Quelques bâtiments effondrés (moitié détruits) pour l'ambiance post-guerre
 */
public class HavenCityStructure extends Structure {

    public static final MapCodec<HavenCityStructure> CODEC =
            simpleCodec(HavenCityStructure::new);

    private static final int CITY_SIZE   = 24; // demi-côté de la ville
    private static final int BLOCK_SIZE  = 14; // taille d'un îlot urbain
    private static final int ROAD_WIDTH  = 4;  // largeur des rues

    public HavenCityStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> {
            BlockPos origin = new BlockPos(
                    context.chunkPos().getMiddleBlockX(),
                    64,
                    context.chunkPos().getMiddleBlockZ()
            );
            builder.addPiece(new SpargusCityStructure.SpargusCityPiece(origin));
        });
    }

    private void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
        BlockPos origin = new BlockPos(
                context.chunkPos().getMiddleBlockX(),
                64,
                context.chunkPos().getMiddleBlockZ()
        );
        builder.addPiece(new HavenCityPiece(origin));
    }

    @Override
    public StructureType<?> type() {
        return Jak3Registry.HAVEN_CITY.get();
    }

    // -------------------------------------------------------------------------
    public static class HavenCityPiece extends StructurePiece {

        public HavenCityPiece(BlockPos origin) {
            super(Jak3Registry.HAVEN_CITY_PIECE.get(),
                    0,
                    new BoundingBox(
                            origin.getX() - CITY_SIZE, origin.getY() - 1,  origin.getZ() - CITY_SIZE,
                            origin.getX() + CITY_SIZE, origin.getY() + 25, origin.getZ() + CITY_SIZE
                    ));
        }

        public HavenCityPiece(StructurePieceSerializationContext ctx, net.minecraft.nbt.CompoundTag tag) {
            super(Jak3Registry.HAVEN_CITY_PIECE.get(), tag);
        }

        @Override
        protected void addAdditionalSaveData(
                net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext ctx,
                net.minecraft.nbt.CompoundTag tag) {}

        @Override
        public void postProcess(WorldGenLevel level, StructureManager sm,
                                ChunkGenerator gen, RandomSource rng,
                                BoundingBox box, ChunkPos chunkPos, BlockPos origin) {

            int cx = boundingBox.minX() + CITY_SIZE;
            int cy = boundingBox.minY() + 1;
            int cz = boundingBox.minZ() + CITY_SIZE;
            BlockPos center = new BlockPos(cx, cy, cz);

            generateUrbanFloor(level, center, rng);
            generateStreetGrid(level, center);
            int[][] towerPositions = generateTowers(level, center, rng);
            generateBridges(level, center, towerPositions);
            generateKGZone(level, center, rng);
            generateLighting(level, center, rng);
        }

        // --- Dalle urbaine de base ---
        private void generateUrbanFloor(WorldGenLevel level, BlockPos center, RandomSource rng) {
            for (int x = -CITY_SIZE; x <= CITY_SIZE; x++) {
                for (int z = -CITY_SIZE; z <= CITY_SIZE; z++) {
                    BlockPos pos = center.offset(x, 0, z);
                    // Béton fissuri : mélange deepslate et iron block
                    BlockState floor = switch (rng.nextInt(6)) {
                        case 0  -> Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
                        case 1  -> Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState();
                        default -> Blocks.DEEPSLATE_TILES.defaultBlockState();
                    };
                    setBlock(level, pos, floor);
                    for (int y = 1; y <= 5; y++) {
                        setBlock(level, pos.above(y), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        // --- Grille de rues (bandes claires tous les BLOCK_SIZE blocs) ---
        private void generateStreetGrid(WorldGenLevel level, BlockPos center) {
            for (int x = -CITY_SIZE; x <= CITY_SIZE; x++) {
                for (int z = -CITY_SIZE; z <= CITY_SIZE; z++) {
                    boolean isStreetX = (Math.abs(x) % (BLOCK_SIZE + ROAD_WIDTH)) < ROAD_WIDTH;
                    boolean isStreetZ = (Math.abs(z) % (BLOCK_SIZE + ROAD_WIDTH)) < ROAD_WIDTH;
                    if (isStreetX || isStreetZ) {
                        setBlock(level, center.offset(x, 0, z),
                                Blocks.BLACKSTONE.defaultBlockState());
                        // Ligne blanche centrale tous les 2 blocs
                        if (isStreetX && isStreetZ) {
                            setBlock(level, center.offset(x, 0, z),
                                    Blocks.GRAY_CONCRETE.defaultBlockState());
                        }
                    }
                }
            }
        }

        // --- Tours métalliques (3 à 5 tours de hauteur variable) ---
        private int[][] generateTowers(WorldGenLevel level, BlockPos center, RandomSource rng) {
            int towerCount = 3 + rng.nextInt(3);
            int[][] positions = new int[towerCount][3]; // [x, z, height]

            for (int i = 0; i < towerCount; i++) {
                int tx = (rng.nextInt(2 * CITY_SIZE / BLOCK_SIZE) - CITY_SIZE / BLOCK_SIZE) * BLOCK_SIZE;
                int tz = (rng.nextInt(2 * CITY_SIZE / BLOCK_SIZE) - CITY_SIZE / BLOCK_SIZE) * BLOCK_SIZE;
                int th = 10 + rng.nextInt(12); // 10 à 21 blocs de haut

                positions[i][0] = tx;
                positions[i][1] = tz;
                positions[i][2] = th;

                generateTower(level, center.offset(tx, 0, tz), th, rng);
            }
            return positions;
        }

        private void generateTower(WorldGenLevel level, BlockPos base, int height, RandomSource rng) {
            int w = 5 + rng.nextInt(3); // largeur 5-7

            for (int y = 0; y <= height; y++) {
                for (int x = -w; x <= w; x++) {
                    for (int z = -w; z <= w; z++) {
                        boolean isShell = (Math.abs(x) == w || Math.abs(z) == w);
                        boolean isFloor = (y % 5 == 0); // plancher tous les 5 niveaux
                        boolean isRoof  = (y == height);

                        if (isRoof) {
                            setBlock(level, base.offset(x, y, z),
                                    Blocks.IRON_BARS.defaultBlockState());
                        } else if (isShell) {
                            // Mur de la tour avec fenêtres espacées
                            boolean isWindow = (y % 3 == 1 && (x == w || x == -w) && z % 2 == 0)
                                    || (y % 3 == 1 && (z == w || z == -w) && x % 2 == 0);
                            BlockState mat = isWindow
                                    ? Blocks.GLASS_PANE.defaultBlockState()
                                    : (rng.nextInt(5) == 0
                                        ? Blocks.CHISELED_DEEPSLATE.defaultBlockState()
                                        : Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                            setBlock(level, base.offset(x, y, z), mat);
                        } else if (isFloor) {
                            setBlock(level, base.offset(x, y, z),
                                    Blocks.SMOOTH_STONE.defaultBlockState());
                        } else {
                            setBlock(level, base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }

            // Antenne/structure au sommet
            for (int y = height + 1; y <= height + 4; y++) {
                setBlock(level, base.above(y), Blocks.LIGHTNING_ROD.defaultBlockState());
            }

            // Dommages de guerre sur le bas des tours (blocs manquants aléatoires)
            for (int i = 0; i < 8 + rng.nextInt(8); i++) {
                int dx = rng.nextInt(w * 2 + 1) - w;
                int dz = rng.nextInt(w * 2 + 1) - w;
                int dy = rng.nextInt(4);
                setBlock(level, base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
            }
        }

        // --- Ponts surélevés entre les tours ---
        private void generateBridges(WorldGenLevel level, BlockPos center, int[][] towers) {
            // Connecte chaque tour à la suivante
            for (int i = 0; i < towers.length - 1; i++) {
                int x1 = towers[i][0],   z1 = towers[i][1],   h1 = towers[i][2];
                int x2 = towers[i+1][0], z2 = towers[i+1][1], h2 = towers[i+1][2];
                int bridgeY = Math.min(h1, h2) - 2; // niveau du pont : sous la plus basse des deux tours

                int steps = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
                for (int s = 0; s <= steps; s++) {
                    float t = steps == 0 ? 0 : (float) s / steps;
                    int bx = Math.round(x1 + (x2 - x1) * t);
                    int bz = Math.round(z1 + (z2 - z1) * t);

                    // Plancher du pont
                    setBlock(level, center.offset(bx, bridgeY, bz),
                            Blocks.SMOOTH_STONE.defaultBlockState());
                    setBlock(level, center.offset(bx + 1, bridgeY, bz),
                            Blocks.SMOOTH_STONE.defaultBlockState());

                    // Rambardes
                    setBlock(level, center.offset(bx, bridgeY + 1, bz),
                            Blocks.IRON_BARS.defaultBlockState());
                    setBlock(level, center.offset(bx + 1, bridgeY + 1, bz),
                            Blocks.IRON_BARS.defaultBlockState());
                }
            }
        }

        // --- Zone KG : secteur barricadé au nord-est ---
        private void generateKGZone(WorldGenLevel level, BlockPos center, RandomSource rng) {
            int kgX = CITY_SIZE / 2;
            int kgZ = -CITY_SIZE / 2;
            int kgSize = 16;

            // Barricades périmètre
            for (int x = -kgSize; x <= kgSize; x += 2) {
                for (int y = 0; y <= 2; y++) {
                    setBlock(level, center.offset(kgX + x, y, kgZ - kgSize),
                            Blocks.GRAY_CONCRETE.defaultBlockState());
                    setBlock(level, center.offset(kgX + x, y, kgZ + kgSize),
                            Blocks.GRAY_CONCRETE.defaultBlockState());
                }
            }
            for (int z = -kgSize; z <= kgSize; z += 2) {
                for (int y = 0; y <= 2; y++) {
                    setBlock(level, center.offset(kgX - kgSize, y, kgZ + z),
                            Blocks.GRAY_CONCRETE.defaultBlockState());
                    setBlock(level, center.offset(kgX + kgSize, y, kgZ + z),
                            Blocks.GRAY_CONCRETE.defaultBlockState());
                }
            }

            // Barbelés au sommet des barricades
            for (int x = -kgSize; x <= kgSize; x++) {
                setBlock(level, center.offset(kgX + x, 3, kgZ - kgSize),
                        Blocks.TRIPWIRE.defaultBlockState());
                setBlock(level, center.offset(kgX + x, 3, kgZ + kgSize),
                        Blocks.TRIPWIRE.defaultBlockState());
            }

            // Poste de commandement central (bunker bas)
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    for (int y = 0; y <= 4; y++) {
                        boolean isShell = (Math.abs(x) == 4 || Math.abs(z) == 4);
                        boolean isRoof  = (y == 4);
                        if (isShell || isRoof) {
                            setBlock(level, center.offset(kgX + x, y, kgZ + z),
                                    Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
                        }
                    }
                }
            }

            // Caisses et débris dans la zone KG
            for (int i = 0; i < 12; i++) {
                int dx = rng.nextInt(kgSize * 2) - kgSize;
                int dz = rng.nextInt(kgSize * 2) - kgSize;
                setBlock(level, center.offset(kgX + dx, 1, kgZ + dz),
                        Blocks.IRON_BLOCK.defaultBlockState());
            }
        }

        // --- Lanternes et éclairage dans les rues ---
        private void generateLighting(WorldGenLevel level, BlockPos center, RandomSource rng) {
            // Lampadaires tous les 8 blocs le long des rues principales
            for (int x = -CITY_SIZE; x <= CITY_SIZE; x += 8) {
                for (int z = -CITY_SIZE; z <= CITY_SIZE; z += 8) {
                    if (rng.nextInt(3) == 0) continue; // quelques lampadaires détruits

                    BlockPos lampBase = center.offset(x, 0, z);
                    // Poteau
                    for (int y = 1; y <= 4; y++) {
                        setBlock(level, lampBase.above(y), Blocks.IRON_BARS.defaultBlockState());
                    }
                    // Chaîne et lanterne au sommet
                    setBlock(level, lampBase.above(5), Blocks.CHAIN.defaultBlockState());
                    setBlock(level, lampBase.above(6), Blocks.LANTERN.defaultBlockState());
                }
            }
        }

        private void setBlock(WorldGenLevel level, BlockPos pos, BlockState state) {
            if (level.ensureCanWrite(pos)) {
                level.setBlock(pos, state, 2);
            }
        }
    }
}
