package com.emerald.world.structure;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import com.emerald.init.Jak3Registry;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure.GenerationStub;

import java.util.Optional;

/*
 * Spargus City — ville du désert inspirée de Jak 3.
 *
 * Composition :
 *   - Remparts extérieurs en grès rouge (sandstone / red sandstone)
 *   - Arène centrale circulaire avec gradins
 *   - 6 à 10 maisons basses réparties autour de l'arène
 *   - 4 tours de guet aux coins du rempart
 *   - Sol en dalle de grès, rues en sable rouge
 *
 * La génération est entièrement procédurale — pas de NBT —
 * pour rester indépendante de la version et facile à modifier.
 */
public class SpargusCityStructure extends Structure {

    public static final MapCodec<SpargusCityStructure> CODEC =
            simpleCodec(SpargusCityStructure::new);

    // Dimensions globales de la ville
    private static final int CITY_RADIUS   = 24;  // rayon depuis le centre
    private static final int WALL_THICK    = 3;   // épaisseur des remparts
    private static final int WALL_HEIGHT   = 8;   // hauteur des remparts
    private static final int ARENA_RADIUS  = 7;  // rayon de l'arène centrale
    private static final int ARENA_HEIGHT  = 6;   // hauteur des gradins

    public SpargusCityStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> {
            int x = context.chunkPos().getMiddleBlockX();
            int z = context.chunkPos().getMiddleBlockZ();
            // Récupère la vraie hauteur du terrain au centre
            int y = context.heightAccessor().getMinBuildHeight() +
                    context.chunkGenerator().getFirstFreeHeight(
                            x, z,
                            Heightmap.Types.WORLD_SURFACE_WG,
                            context.heightAccessor(),
                            context.randomState()
                    );
            BlockPos origin = new BlockPos(x, y, z);
            builder.addPiece(new SpargusCityPiece(origin));
        });
    }

    private void generatePieces(GenerationStub stub, GenerationContext context) {
        BlockPos origin = new BlockPos(
                context.chunkPos().getMiddleBlockX(),
                64,
                context.chunkPos().getMiddleBlockZ()
        );
        stub.getPiecesBuilder().addPiece(new SpargusCityPiece(origin));
    }

    @Override
    public StructureType<?> type() {
        return Jak3Registry.SPARGUS_CITY.get();
    }

    // -------------------------------------------------------------------------
    // Pièce de génération principale
    // -------------------------------------------------------------------------
    public static class SpargusCityPiece extends StructurePiece {

        private static final int PIECE_TYPE = 0;

        public SpargusCityPiece(BlockPos origin) {
            super(Jak3Registry.SPARGUS_CITY_PIECE.get(),
                    0,
                    new BoundingBox(
                            origin.getX() - CITY_RADIUS, origin.getY() - 1,  origin.getZ() - CITY_RADIUS,
                            origin.getX() + CITY_RADIUS, origin.getY() + 20, origin.getZ() + CITY_RADIUS
                    ));
        }

        public SpargusCityPiece(StructurePieceSerializationContext ctx, net.minecraft.nbt.CompoundTag tag) {
            super(Jak3Registry.SPARGUS_CITY_PIECE.get(), tag);
        }

        @Override
        protected void addAdditionalSaveData(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext ctx,
                                             net.minecraft.nbt.CompoundTag tag) {}

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager,
                                ChunkGenerator generator, RandomSource random,
                                BoundingBox box, ChunkPos chunkPos, BlockPos origin) {

            int cx = boundingBox.minX() + CITY_RADIUS;
            int cy = boundingBox.minY() + 1;
            int cz = boundingBox.minZ() + CITY_RADIUS;
            BlockPos center = new BlockPos(cx, cy, cz);

            generateFloor(level, center, random);
            generateWalls(level, center);
            generateCornerTowers(level, center);
            generateArena(level, center);
            generateHouses(level, center, random);
            generateGate(level, center);
        }

        // --- Sol de la ville ---
        private void generateFloor(WorldGenLevel level, BlockPos center, RandomSource rng) {
            for (int x = -CITY_RADIUS + WALL_THICK; x <= CITY_RADIUS - WALL_THICK; x++) {
                for (int z = -CITY_RADIUS + WALL_THICK; z <= CITY_RADIUS - WALL_THICK; z++) {
                    double dist = Math.sqrt(x * x + z * z);
                    if (dist > CITY_RADIUS - WALL_THICK) continue;

                    BlockPos pos = center.offset(x, 0, z);
                    // Alternance dalle grès / grès rouge pour les rues
                    BlockState floor = (rng.nextInt(5) == 0)
                            ? Blocks.RED_SAND.defaultBlockState()
                            : Blocks.SANDSTONE.defaultBlockState();
                    setBlock(level, pos, floor);
                    // Remplace les blocs au-dessus par de l'air (aplatit le terrain)
                    for (int y = 1; y <= 4; y++) {
                        setBlock(level, pos.above(y), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        // --- Remparts extérieurs circulaires ---
        private void generateWalls(WorldGenLevel level, BlockPos center) {
            for (int angle = 0; angle < 360; angle++) {
                double rad = Math.toRadians(angle);
                int wx = (int) (Math.cos(rad) * CITY_RADIUS);
                int wz = (int) (Math.sin(rad) * CITY_RADIUS);

                for (int t = 0; t < WALL_THICK; t++) {
                    int tx = (int) (Math.cos(rad) * (CITY_RADIUS - t));
                    int tz = (int) (Math.sin(rad) * (CITY_RADIUS - t));
                    for (int y = 0; y <= WALL_HEIGHT; y++) {
                        BlockState mat = (y == WALL_HEIGHT)
                                ? Blocks.RED_SANDSTONE_SLAB.defaultBlockState()
                                : Blocks.RED_SANDSTONE.defaultBlockState();
                        setBlock(level, center.offset(tx, y, tz), mat);
                    }
                }

                // Créneaux au sommet du rempart — un créneau tous les 3 degrés
                if (angle % 3 == 0) {
                    setBlock(level, center.offset(wx, WALL_HEIGHT + 1, wz),
                            Blocks.RED_SANDSTONE_WALL.defaultBlockState());
                }
            }
        }

        // --- Tours de guet aux 4 coins (coins du carré inscrit dans le cercle) ---
        private void generateCornerTowers(WorldGenLevel level, BlockPos center) {
            int offset = (int) (CITY_RADIUS * 0.7);
            int[][] corners = {{offset, offset}, {offset, -offset}, {-offset, offset}, {-offset, -offset}};

            for (int[] c : corners) {
                generateTower(level, center.offset(c[0], 0, c[1]));
            }
        }

        private void generateTower(WorldGenLevel level, BlockPos base) {
            int r = 3; // rayon de la tour
            for (int y = 0; y <= WALL_HEIGHT + 4; y++) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        double dist = Math.sqrt(x * x + z * z);
                        boolean isShell = dist >= r - 0.8 && dist <= r;
                        boolean isFloor = (y == 0 || y == WALL_HEIGHT + 4);
                        if (isShell || isFloor) {
                            BlockState mat = (y == WALL_HEIGHT + 4)
                                    ? Blocks.RED_SANDSTONE_SLAB.defaultBlockState()
                                    : Blocks.CUT_RED_SANDSTONE.defaultBlockState();
                            setBlock(level, base.offset(x, y, z), mat);
                        } else {
                            setBlock(level, base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
        }

        // --- Arène centrale circulaire avec gradins ---
        private void generateArena(WorldGenLevel level, BlockPos center) {
            // Sol de l'arène en grès lisse
            for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
                for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                    if (Math.sqrt(x * x + z * z) <= ARENA_RADIUS) {
                        setBlock(level, center.offset(x, 0, z),
                                Blocks.SMOOTH_SANDSTONE.defaultBlockState());
                    }
                }
            }

            // Gradins en cercles concentriques
            for (int tier = 0; tier < 3; tier++) {
                int innerR = ARENA_RADIUS + tier * 2;
                int outerR = innerR + 2;
                int y = tier + 1;

                for (int angle = 0; angle < 360; angle++) {
                    double rad = Math.toRadians(angle);
                    for (int r = innerR; r <= outerR; r++) {
                        int bx = (int) (Math.cos(rad) * r);
                        int bz = (int) (Math.sin(rad) * r);
                        setBlock(level, center.offset(bx, y, bz),
                                Blocks.SANDSTONE_STAIRS.defaultBlockState());
                    }
                }
            }

            // Mur de l'arène
            for (int angle = 0; angle < 360; angle++) {
                double rad = Math.toRadians(angle);
                int bx = (int) (Math.cos(rad) * (ARENA_RADIUS));
                int bz = (int) (Math.sin(rad) * (ARENA_RADIUS));
                for (int y = 1; y <= 3; y++) {
                    setBlock(level, center.offset(bx, y, bz),
                            Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState());
                }
            }
        }

        // --- Maisons réparties autour de l'arène ---
        private void generateHouses(WorldGenLevel level, BlockPos center, RandomSource rng) {
            int houseCount = 6 + rng.nextInt(4); // 6 à 9 maisons
            for (int i = 0; i < houseCount; i++) {
                double angle = (2 * Math.PI * i / houseCount) + rng.nextDouble() * 0.3;
                int dist = 22 + rng.nextInt(8);
                int hx = (int) (Math.cos(angle) * dist);
                int hz = (int) (Math.sin(angle) * dist);
                int w = 5 + rng.nextInt(4); // largeur 5-8
                int d = 5 + rng.nextInt(4); // profondeur 5-8
                int h = 3 + rng.nextInt(3); // hauteur 3-5
                generateHouse(level, center.offset(hx, 0, hz), w, d, h, rng);
            }
        }

        private void generateHouse(WorldGenLevel level, BlockPos origin,
                                   int w, int d, int h, RandomSource rng) {
            // Murs
            for (int x = 0; x <= w; x++) {
                for (int z = 0; z <= d; z++) {
                    for (int y = 0; y <= h; y++) {
                        boolean isWall = (x == 0 || x == w || z == 0 || z == d);
                        boolean isRoof = (y == h);
                        boolean isDoor = (x == w / 2 && z == 0 && y <= 2);

                        if (isDoor) {
                            setBlock(level, origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
                        } else if (isRoof) {
                            setBlock(level, origin.offset(x, y, z),
                                    Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState());
                        } else if (isWall) {
                            // Variation de matériaux pour l'aspect usé
                            BlockState mat = rng.nextInt(4) == 0
                                    ? Blocks.CHISELED_SANDSTONE.defaultBlockState()
                                    : Blocks.SANDSTONE.defaultBlockState();
                            setBlock(level, origin.offset(x, y, z), mat);
                        } else {
                            setBlock(level, origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }

            // Fenêtres sur les murs latéraux
            setBlock(level, origin.offset(0, 2, d / 2),
                    Blocks.GLASS_PANE.defaultBlockState());
            setBlock(level, origin.offset(w, 2, d / 2),
                    Blocks.GLASS_PANE.defaultBlockState());
        }

        // --- Porte principale (brèche dans le rempart sud) ---
        private void generateGate(WorldGenLevel level, BlockPos center) {
            int gateZ = CITY_RADIUS;
            // Ouvre une brèche de 5 blocs de large dans le rempart sud
            for (int x = -2; x <= 2; x++) {
                for (int y = 0; y <= 5; y++) {
                    for (int t = 0; t < WALL_THICK + 1; t++) {
                        setBlock(level,
                                center.offset(x, y, gateZ - t),
                                Blocks.AIR.defaultBlockState());
                    }
                }
            }
            // Linteau de la porte
            for (int x = -3; x <= 3; x++) {
                setBlock(level, center.offset(x, 6, gateZ),
                        Blocks.CHISELED_RED_SANDSTONE.defaultBlockState());
                setBlock(level, center.offset(x, 6, gateZ - 1),
                        Blocks.CHISELED_RED_SANDSTONE.defaultBlockState());
            }
        }

        // Wrapper sécurisé — évite d'écraser des blocs hors chunk
        private void setBlock(WorldGenLevel level, BlockPos pos, BlockState state) {
            if (level.ensureCanWrite(pos)) {
                level.setBlock(pos, state, 2);
            }
        }
    }
}
