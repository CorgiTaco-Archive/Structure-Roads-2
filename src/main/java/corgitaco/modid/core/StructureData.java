package corgitaco.modid.core;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import corgitaco.modid.Main;
import corgitaco.modid.mixin.access.UtilAccess;
import corgitaco.modid.structure.AdditionalStructureContext;
import corgitaco.modid.world.path.IPathGenerator;
import corgitaco.modid.world.path.PathfindingPathGenerator;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraft.world.server.ServerWorld;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class StructureData {
    private static final ExecutorService PATH_EXECUTOR = UtilAccess.invokeMakeExecutor("path"); //TODO: Find a better way / time to create this
    private final Generated<Long2ReferenceOpenHashMap<AdditionalStructureContext>> locationContextData;
    private final Generated<Map<PathKey, IPathGenerator<?>>> pathGenerators;
    private final StructureRegion region;
    private final ServerWorld world;
    private final Structure<?> structure;
    private final Long2ReferenceOpenHashMap<Set<PathKey>> pathGeneratorReferences;
    private final StructureSeparationSettings config;

    private final ConcurrentHashMap<PathKey, IPathGenerator<?>> neighborPathGenerators = new ConcurrentHashMap<>();

    public StructureData(StructureRegion structureRegion, Structure<?> structure) {
        StructureSeparationSettings config = structureRegion.getServerLevel().getChunkSource().getGenerator().getSettings().getConfig(structure);
        if (config == null) {
            throw new IllegalArgumentException(String.format("No structure separation data for: \"%s\".", Registry.STRUCTURE_FEATURE.getKey(structure)));
        }

        this.config = config;
        locationContextData = new Generated<>(new Long2ReferenceOpenHashMap<>());
        pathGenerators = new Generated<>(new ConcurrentHashMap<>());
        pathGeneratorReferences = new Long2ReferenceOpenHashMap<>();
        this.region = structureRegion;
        this.structure = structure;
        this.world = structureRegion.getServerLevel();
    }

    public StructureData(CompoundNBT nbt, StructureRegion structureRegion, Structure<?> structure) {
        StructureSeparationSettings config = structureRegion.getServerLevel().getChunkSource().getGenerator().getSettings().getConfig(structure);
        if (config == null) {
            throw new IllegalArgumentException(String.format("No structure separation data for: \"%s\".", Registry.STRUCTURE_FEATURE.getKey(structure)));
        }

        this.config = config;

        locationContextData = allRegionStructurePositionsFromFile(nbt);
        pathGenerators = allRegionPathGeneratorFromFile(nbt);
        pathGeneratorReferences = allPathGeneratorReferencesFromFile(nbt);
        this.region = structureRegion;
        this.structure = structure;
        this.world = structureRegion.getServerLevel();
    }

    public Long2ReferenceOpenHashMap<AdditionalStructureContext> getLocationContextData(boolean loadAll) {
        if (!locationContextData.generated && loadAll) {
            StructureRegionManager structureRegionManager = ((StructureRegionManager.Access) this.world).getStructureRegionManager();
            ChunkGenerator generator = this.world.getChunkSource().generator;
            structureRegionManager.collectRegionStructures(this.world.getSeed(), generator.getBiomeSource(), this.structure, this.config, this.region.getPos());
            locationContextData.setGenerated();
        }

        return locationContextData.value;
    }

    public Map<PathKey, IPathGenerator<?>> getPathGenerators(boolean loadAll) {
        if (!pathGenerators.generated && loadAll) {
            long startTime = System.currentTimeMillis();
            StructureRegionManager structureRegionManager = ((StructureRegionManager.Access) this.world).getStructureRegionManager();

            Long2ReferenceOpenHashMap<AdditionalStructureContext> locationContextData = this.getLocationContextData(true);
            CompletionService<IPathGenerator<?>> completionService = new ExecutorCompletionService<>(PATH_EXECUTOR);
            int pathsSubmitted = 0;
            for (Long2ReferenceMap.Entry<AdditionalStructureContext> entry : locationContextData.long2ReferenceEntrySet()) {
                long structurePos = entry.getLongKey();
                AdditionalStructureContext value = entry.getValue();
                Pair<Integer, Integer> gridXZ = StructureRegionManager.getGridXZ(structurePos, this.config.spacing());

                pathsSubmitted += structureRegionManager.linkNeighbors(this.world, this.world.getSeed(), this.world.getChunkSource().getGenerator().getBiomeSource(), this.structure, this.config, structureRegionManager.dataForLocation, StructureRegionManager.NAMES[this.world.random.nextInt(StructureRegionManager.NAMES.length - 1)], 1, gridXZ.getFirst(), gridXZ.getSecond(), structurePos, value, completionService);
                pathGenerators.setGenerated();
            }

            int pathsCreated = 0;
            while (pathsCreated < pathsSubmitted){
                try {
                    IPathGenerator pathGenerator = completionService.poll(10, TimeUnit.MINUTES).get(); //TODO: This will throw a NullPointerException upon timeout should handle this better. Also, this is a very long timeout
                    structureRegionManager.addPath(pathGenerator, this.structure);
                    pathsCreated++;
                } catch (ExecutionException | InterruptedException e) {
                    throw new IllegalStateException("Failed to create path", e);
                }
            }

            System.out.println("Created all paths in region in " + (System.currentTimeMillis() - startTime) + " ms");
        }

        return pathGenerators.value;
    }

    public Long2ReferenceOpenHashMap<Set<PathKey>> getPathGeneratorReferences() {
        return pathGeneratorReferences;
    }

    public Map<PathKey, IPathGenerator<?>> getPathGeneratorNeighbors() {
        StructureRegionManager structureRegionManager = ((StructureRegionManager.Access) this.world).getStructureRegionManager();

        for (Long2ReferenceMap.Entry<Set<PathKey>> entry : this.pathGeneratorReferences.long2ReferenceEntrySet()) {
            long regionKey = entry.getLongKey();
            StructureRegion structureRegion = structureRegionManager.getStructureRegion(regionKey);

            Set<PathKey> pathKeys = entry.getValue();
            for (PathKey pathKey : pathKeys) {
                this.neighborPathGenerators.computeIfAbsent(pathKey, (pathKey1 -> {
                    IPathGenerator pathGenerator = structureRegion.structureData(this.structure).getPathGenerators(true).get(pathKey1);
                    if (pathGenerator == null) {
                        throw new IllegalStateException("Value not found!");
                    }

                    return pathGenerator;
                }));
            }
        }

        return this.neighborPathGenerators;
    }

    public static Generated<Long2ReferenceOpenHashMap<AdditionalStructureContext>> allRegionStructurePositionsFromFile(CompoundNBT nbt) {
        Long2ReferenceOpenHashMap<AdditionalStructureContext> structureToContext = new Long2ReferenceOpenHashMap<>();
        for (String key : nbt.getAllKeys()) {
            if (!key.equals("generated")) {
                structureToContext.put(Long.parseLong(key), new AdditionalStructureContext(nbt.getCompound(key)));
            }
        }
        return new Generated<>(structureToContext, nbt.getBoolean("generated"));
    }

    public static Generated<Map<PathKey, IPathGenerator<?>>> allRegionPathGeneratorFromFile(CompoundNBT nbt) {
        Map<PathKey, IPathGenerator<?>> pathGenerators = new HashMap<>();
        if (nbt.contains("path_generators", 10)) {
            CompoundNBT pathGeneratorsNBT = nbt.getCompound("path_generators");
            for (String key : nbt.getAllKeys()) {
                if (!key.equals("generated")) {
                    PathKey pathKey = new PathKey(key);
                    pathGenerators.put(pathKey, IPathGenerator.CODEC.decode(NBTDynamicOps.INSTANCE, pathGeneratorsNBT.get(pathKey.compoundKey())).result().get().getFirst());
                }
            }
        }
        return new Generated<>(pathGenerators, nbt.getBoolean("generated"));
    }

    public static Long2ReferenceOpenHashMap<Set<PathKey>> allPathGeneratorReferencesFromFile(CompoundNBT nbt) {
        Long2ReferenceOpenHashMap<Set<PathKey>> result = new Long2ReferenceOpenHashMap<>();
        if (nbt.contains("path_generator_references")) {
            CompoundNBT pathGeneratorReferences = nbt.getCompound("path_generator_references");
            for (String key : pathGeneratorReferences.getAllKeys()) {
                long regionKey = Long.parseLong(key);
                result.put(regionKey, Arrays.stream(pathGeneratorReferences.getString(key).replace("[", "").replace("]", "").split(",")).map(PathKey::new).collect(Collectors.toSet()));
            }
        }
        return result;
    }

    public CompoundNBT saveTag() {
        CompoundNBT nbt = new CompoundNBT();

        CompoundNBT pathGenerators = new CompoundNBT();
        pathGenerators.putBoolean("generated", this.pathGenerators.generated);

        this.pathGenerators.value.forEach((pathKey, pathGenerator) -> {
            DataResult<INBT> dataResult = IPathGenerator.CODEC.encodeStart(NBTDynamicOps.INSTANCE, pathGenerator);
            Optional<INBT> result = dataResult.result();
            if (result.isPresent()) {
                pathGenerators.put(pathKey.compoundKey(), result.get());
            } else {
                Main.LOGGER.warn("Incomplete data for:" + result.toString());
            }
        });
        nbt.put("path_generators", pathGenerators);

        CompoundNBT pathGeneratorReferences = new CompoundNBT();
        for (Long2ReferenceMap.Entry<Set<PathKey>> pathKeyEntry : this.pathGeneratorReferences.long2ReferenceEntrySet()) {
            long regionKey = pathKeyEntry.getLongKey();
            Set<PathKey> value = pathKeyEntry.getValue();

            pathGeneratorReferences.putString(Long.toString(regionKey), Arrays.toString(value.stream().map(PathKey::compoundKey).toArray()));
        }
        nbt.put("path_generator_references", pathGeneratorReferences);

        CompoundNBT structureData = new CompoundNBT();
        structureData.putBoolean("generated", this.locationContextData.generated);

        for (Long2ReferenceMap.Entry<AdditionalStructureContext> additionalStructureContextEntry : locationContextData.value.long2ReferenceEntrySet()) {
            long pos = additionalStructureContextEntry.getLongKey();
            AdditionalStructureContext context = additionalStructureContextEntry.getValue();
            structureData.put(Long.toString(pos), context.write());
        }
        nbt.put("structureData", pathGeneratorReferences);
        return nbt;
    }


    public static class Generated<T> {
        private boolean generated;
        private final T value;


        public Generated(T value) {
            this(value, false);
        }

        public Generated(T value, boolean generated) {
            this.value = value;
            this.generated = generated;
        }

        public boolean isGenerated() {
            return generated;
        }

        public void setGenerated() {
            generated = true;
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
            String[] split = key.split(",");
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
            return startPos + "," + endPos;
        }


        @Override
        public int hashCode() {
            return Objects.hash(startPos, endPos);
        }
    }
}
