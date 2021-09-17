package corgitaco.modid.core;

import corgitaco.modid.world.path.PathfindingPathGenerator;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.gen.feature.structure.Structure;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class StructureRegion {

    private final LoadedAll<Object2ObjectArrayMap<Structure<?>, LongSet>> regionStructures = new LoadedAll<>(new Object2ObjectArrayMap<>());
    private final LoadedAll<Map<PathKey, PathfindingPathGenerator>> pathGenerators = new LoadedAll<>(new HashMap<>());
    private final LoadedAll<Long2ReferenceOpenHashMap<Set<PathKey>>> pathGeneratorReferences = new LoadedAll<>(new Long2ReferenceOpenHashMap<>());
    private boolean arePathsLoaded;

    public LongSet regionStructurePositionsFromFile(CompoundNBT nbt, Structure<?> structure) {
        return regionStructures.o.computeIfAbsent(structure, (structure1 -> {
            if (nbt.contains("structure_positions", 10)) {
                CompoundNBT structurePositions = nbt.getCompound("structure_positions");
                String key = Registry.STRUCTURE_FEATURE.getKey(structure).toString();
                if (structurePositions.contains(key)) {
                    return new LongOpenHashSet(structurePositions.getLongArray(key));
                }
            }
            return new LongOpenHashSet();
        }));
    }

    public Object2ObjectArrayMap<Structure<?>, LongSet> allRegionStructurePositionsFromFile(CompoundNBT nbt) {
        if (!regionStructures.loadedAll) {
            if (nbt.contains("structure_positions", 10)) {
                CompoundNBT structurePositions = nbt.getCompound("structure_positions");
                for (String key : structurePositions.getAllKeys()) {
                    regionStructures.o.computeIfAbsent(Registry.STRUCTURE_FEATURE.get(new ResourceLocation(key)), (structure -> {
                        return new LongOpenHashSet();
                    })).addAll(new LongArraySet(structurePositions.getLongArray(key)));
                }
            }
            this.regionStructures.loadAll();
        }
        return this.regionStructures.o;
    }

    @Nullable
    public PathfindingPathGenerator regionPathGeneratorByKeyFromFile(CompoundNBT nbt, PathKey pathKey) {
        return pathGenerators.o.computeIfAbsent(pathKey, (structure1 -> {
            if (nbt.contains("path_generators", 10)) {
                CompoundNBT pathGenerators = nbt.getCompound("path_generators");
                if (pathGenerators.contains(pathKey.compoundKey())) {
                    return PathfindingPathGenerator.CODEC.decode(NBTDynamicOps.INSTANCE, pathGenerators.get(pathKey.compoundKey())).result().get().getFirst();
                }
            }
            return null;
        }));
    }

    public Map<PathKey, PathfindingPathGenerator> allRegionPathGeneratorFromFile(CompoundNBT nbt) {
        if (!pathGenerators.loadedAll) {
            if (nbt.contains("path_generators", 10)) {
                CompoundNBT pathGenerators = nbt.getCompound("path_generators");
                for (String key : nbt.getAllKeys()) {
                    PathKey pathKey = new PathKey(key);
                    this.pathGenerators.o.put(pathKey, PathfindingPathGenerator.CODEC.decode(NBTDynamicOps.INSTANCE, pathGenerators.get(pathKey.compoundKey())).result().get().getFirst());
                }
            }
            this.pathGenerators.loadAll();
        }
        return this.pathGenerators.o;
    }

    public Long2ReferenceOpenHashMap<Set<PathKey>> allPathGeneratorReferencesFromFile(CompoundNBT nbt) {
        if (!this.pathGeneratorReferences.loadedAll) {
            if (nbt.contains("path_generator_references")) {
                CompoundNBT pathGeneratorReferences = nbt.getCompound("path_generator_references");
                for (String key : pathGeneratorReferences.getAllKeys()) {
                    long regionKey = Long.parseLong(key);
                    this.pathGeneratorReferences.o.put(regionKey, Arrays.stream(pathGeneratorReferences.getString(key).replace("[", "").replace("]", "").split(",")).map(PathKey::new).collect(Collectors.toSet()));
                }
            }
            this.pathGeneratorReferences.loadAll();
        }
        return this.pathGeneratorReferences.o;
    }

    public CompoundNBT saveTag() {
        CompoundNBT nbt = new CompoundNBT();

        CompoundNBT structurePositions = new CompoundNBT();
        for (Structure<?> structure : this.regionStructures.o.keySet()) {
            String key = Registry.STRUCTURE_FEATURE.getKey(structure).toString();
            structurePositions.putLongArray(key, this.regionStructures.o.get(structure).toLongArray());
        }
        nbt.put("structure_positions", structurePositions);

        CompoundNBT pathGenerators = new CompoundNBT();
        this.pathGenerators.o.forEach((pathKey, pathfindingPathGenerator) -> {
            pathGenerators.put(pathKey.compoundKey(), PathfindingPathGenerator.CODEC.encodeStart(NBTDynamicOps.INSTANCE, pathfindingPathGenerator).result().get());
        });
        nbt.put("path_generators", pathGenerators);

        CompoundNBT pathGeneratorReferences = new CompoundNBT();
        for (Long2ReferenceMap.Entry<Set<PathKey>> pathKeyEntry : this.pathGeneratorReferences.o.long2ReferenceEntrySet()) {
            long regionKey = pathKeyEntry.getLongKey();
            Set<PathKey> value = pathKeyEntry.getValue();

            pathGeneratorReferences.putString(Long.toString(regionKey), Arrays.toString(value.stream().map(PathKey::compoundKey).toArray()));
        }
        nbt.put("path_generator_references", pathGeneratorReferences);

        return nbt;
    }


    public Object2ObjectArrayMap<Structure<?>, LongSet> getRegionStructures() {
        return regionStructures.o;
    }

    public Map<PathKey, PathfindingPathGenerator> pathGenerators() {
        return pathGenerators.o;
    }

    public Long2ReferenceOpenHashMap<Set<PathKey>> regionToPathGeneratorReferences() {
        return pathGeneratorReferences.o;
    }

    public boolean arePathsLoaded() {
        return arePathsLoaded;
    }

    public void pathsAreLoaded() {
        this.arePathsLoaded = true;
    }

    public static class LoadedAll<T> {
        private boolean loadedAll;
        private final T o;

        public LoadedAll(T o) {
            this.o = o;
        }

        private LoadedAll<T> loadAll() {
            this.loadedAll = true;
            return this;
        }

        private boolean loadedAll() {
            return loadedAll;
        }
    }

    public static class PathKey {

        private final long startPos;
        private final long endPos;

        public PathKey(long startPos, long endPos) {
            this.startPos = startPos;
            this.endPos = endPos;
        }

        public PathKey(String key) {
            String[] split = key.split("-");
            this.startPos = Long.parseLong(split[0]);
            this.endPos = Long.parseLong(split[1]);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PathKey pathKey = (PathKey) o;
            return startPos == pathKey.startPos && endPos == pathKey.endPos;
        }

        public CompoundNBT toNBT() {
            CompoundNBT nbt = new CompoundNBT();
            nbt.putLong("startPos", startPos);
            nbt.putLong("endPos", endPos);
            return nbt;
        }

        public String compoundKey() {
            return startPos + "-" + endPos;
        }


        @Override
        public int hashCode() {
            return Objects.hash(startPos, endPos);
        }
    }
}
