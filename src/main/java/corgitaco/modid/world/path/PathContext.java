package corgitaco.modid.world.path;

import corgitaco.modid.structure.AdditionalStructureContext;
import corgitaco.modid.util.DataForChunk;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.gen.feature.structure.Structure;

import java.util.List;

public class PathContext {

    public static final int MAX_CAPACITY = 4096;

    private final Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<AdditionalStructureContext>> contextCacheForLevel;
    private final LongSet completedRegionStructureCachesForLevel;
    private final LongSet completedLinkedRegionsForLevel;
    private final Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<AdditionalStructureContext>> surroundingRegionStructureCachesForRegionForLevel;
    private final Long2ReferenceOpenHashMap<List<IPathGenerator<Structure<?>>>> pathGenerators;
    private final Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>> dataCache = new Long2ReferenceOpenHashMap<>();

    public PathContext() {
        this(new Long2ReferenceOpenHashMap<>(), new LongArraySet(), new LongArraySet(), new Long2ReferenceOpenHashMap<>(), new Long2ReferenceOpenHashMap<>());
    }

    public PathContext(Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<AdditionalStructureContext>> contextCacheForLevel, LongSet completedRegionStructureCachesForLevel, LongSet completedLinkedRegionsForLevel, Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<AdditionalStructureContext>> surroundingRegionStructureCachesForRegionForLevel, Long2ReferenceOpenHashMap<List<IPathGenerator<Structure<?>>>> pathGenerators) {
        this.contextCacheForLevel = contextCacheForLevel;
        this.completedRegionStructureCachesForLevel = completedRegionStructureCachesForLevel;
        this.completedLinkedRegionsForLevel = completedLinkedRegionsForLevel;
        this.surroundingRegionStructureCachesForRegionForLevel = surroundingRegionStructureCachesForRegionForLevel;
        this.pathGenerators = pathGenerators;
    }

    public Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<AdditionalStructureContext>> getContextCacheForLevel() {
        if (contextCacheForLevel.size() > MAX_CAPACITY) {
            contextCacheForLevel.clear();
        }
        return contextCacheForLevel;
    }

    public LongSet getCompletedRegionStructureCachesForLevel() {
        if (completedRegionStructureCachesForLevel.size() > MAX_CAPACITY) {
            completedRegionStructureCachesForLevel.clear();
        }
        return completedRegionStructureCachesForLevel;
    }

    public LongSet getCompletedLinkedRegionsForLevel() {
        if (completedLinkedRegionsForLevel.size() > MAX_CAPACITY) {
            completedLinkedRegionsForLevel.clear();
        }
        return completedLinkedRegionsForLevel;
    }

    public Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<AdditionalStructureContext>> getSurroundingRegionStructureCachesForRegionForLevel() {
        if (surroundingRegionStructureCachesForRegionForLevel.size() > MAX_CAPACITY) {
            surroundingRegionStructureCachesForRegionForLevel.clear();
        }
        return surroundingRegionStructureCachesForRegionForLevel;
    }

    public Long2ReferenceOpenHashMap<List<IPathGenerator<Structure<?>>>> getPathGenerators() {
        if (pathGenerators.size() > MAX_CAPACITY) {
            pathGenerators.clear();
        }
        return pathGenerators;
    }

    public Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>> getDataCache() {
        if (dataCache.size() > 50000) {
            dataCache.clear();
        }
        return dataCache;
    }

    public interface Access {

        PathContext getPathContext();
    }
}
