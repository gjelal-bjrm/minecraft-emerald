package com.emerald.world.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * Pose UN template, centre sur le chunk de depart.
 *
 * POURQUOI cette classe plutot que le jigsaw vanilla. Deux limites du moteur
 * se contredisent des qu'un batiment depasse 128 blocs de cote :
 *
 *  - Portee des references : chaque chunk ne cherche les structures que dans
 *    un rayon de 8 chunks (128 blocs). Un batiment pose depuis son coin et
 *    large de 163 blocs voit son dernier tiers hors de portee -- il est
 *    genere tronque.
 *  - Boite des pieces enfants : le jigsaw n'accepte une piece enfant que dans
 *    une boite cubique autour du depart. Ancrer le batiment par son centre
 *    (pour resoudre le point precedent) le transforme en piece enfant, et ses
 *    250 blocs de haut le font rejeter en entier.
 *
 * Ici on court-circuite les deux : on construit la piece nous-memes, decalee
 * de la moitie de sa taille, si bien que le centre du batiment tombe sur le
 * chunk de depart. Rien ne depasse alors 5,1 chunks, et aucun test de jigsaw
 * ne s'applique. C'est l'approche qu'emploie Dungeons Arise pour ses colosses.
 */
public class CenteredTemplateStructure extends Structure {

    public static final MapCodec<CenteredTemplateStructure> CODEC = RecordCodecBuilder.mapCodec(
            inst -> inst.group(
                    settingsCodec(inst),
                    ResourceLocation.CODEC.fieldOf("template")
                            .forGetter(s -> s.template),
                    Codec.INT.optionalFieldOf("base_y", 0)
                            .forGetter(s -> s.baseY),
                    Codec.BOOL.optionalFieldOf("project_to_surface", false)
                            .forGetter(s -> s.projectToSurface)
            ).apply(inst, CenteredTemplateStructure::new));

    private final ResourceLocation template;
    private final int baseY;
    private final boolean projectToSurface;

    public CenteredTemplateStructure(StructureSettings settings, ResourceLocation template,
                                     int baseY, boolean projectToSurface) {
        super(settings);
        this.template = template;
        this.baseY = baseY;
        this.projectToSurface = projectToSurface;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        StructureTemplate tpl = context.structureTemplateManager().getOrCreate(this.template);
        Vec3i size = tpl.getSize();
        if (size.getX() == 0 || size.getZ() == 0) {
            return Optional.empty();          // template absent : rien a poser
        }
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMiddleBlockX();
        int cz = chunk.getMiddleBlockZ();
        int y = this.baseY;
        if (this.projectToSurface) {
            y += context.chunkGenerator().getFirstOccupiedHeight(
                    cx, cz, Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState());
        }
        // l'origine recule d'une demi-taille : le centre du batiment tombe
        // sur le chunk de depart, donc tout reste a portee des references
        BlockPos origin = new BlockPos(cx - size.getX() / 2, y, cz - size.getZ() / 2);
        BlockPos center = new BlockPos(cx, y, cz);
        return Optional.of(new GenerationStub(center, builder ->
                builder.addPiece(new CenteredTemplatePiece(
                        context.structureTemplateManager(), this.template, origin))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.CENTERED_TEMPLATE.get();
    }
}
