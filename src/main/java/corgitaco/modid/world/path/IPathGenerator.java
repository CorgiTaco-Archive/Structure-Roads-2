package corgitaco.modid.world.path;

import com.google.common.io.BaseEncoding;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import corgitaco.modid.core.Registries;
import corgitaco.modid.util.CodecUtil;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.world.gen.feature.structure.Structure;

import java.util.Set;

public interface IPathGenerator<T> {
    Codec<IPathGenerator> CODEC = Registries.PATH_GENERATOR_TYPE.dispatch("path_generator_type", IPathGenerator::getType, PathGeneratorType::getCodec);

    Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<Set<BlockPos>>> getNodesByRegion();
    Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<Set<BlockPos>>> getLightsByRegion();
    BlockState debugState();
    MutableBoundingBox getBoundingBox();
    boolean createdSuccessfully();

    Point<T> getStart();
    Point<T> getEnd();

    long saveRegion();

    boolean dispose();

    void setLastLoadedGameTime(long gameTime);

    long lastLoadedGameTime();

    PathGeneratorType<? extends IPathGenerator<T>> getType();

    class Point<T> {

        public static final Codec<Point<Structure<?>>> POINT_STRUCTURE_CODEC = RecordCodecBuilder.create(builder -> {
           return builder.group(CodecUtil.STRUCTURE_CODEC.fieldOf("structure").forGetter(structurePoint -> {
                return structurePoint.structure;
            }), BlockPos.CODEC.fieldOf("pos").forGetter(structurePoint -> {
                return structurePoint.pos;
            })).apply(builder, Point::new);
        });

        private final T structure;
        private final BlockPos pos;

        public Point(T structure, BlockPos pos) {
            this.structure = structure;
            this.pos = pos;
        }

        public T getStructure() {
            return structure;
        }

        public BlockPos getPos() {
            return pos;
        }

        @Override
        public String toString() {
            return this.pos.toString();
        }
    }

}
