package corgitaco.modid.core;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.server.ServerWorld;

import java.util.Map;

public class StructureRegion {
    private final Object2ObjectArrayMap<Structure<?>, StructureData> dataByStructure;
    private final long pos;
    private final ServerWorld world;
    private boolean arePathsLoaded;

    public StructureRegion(long pos, ServerWorld world){
        this.pos = pos;
        this.world = world;
        this.dataByStructure = new Object2ObjectArrayMap<>();
    }

    public StructureRegion(CompoundNBT nbt, ServerWorld world) {
        this.pos = nbt.getLong("pos");
        this.world = world;
        this.dataByStructure = nbt.contains("structureData") ? dataByStructureFromDisk(nbt.getCompound("structureData"), this) : new Object2ObjectArrayMap<>();
    }

    public StructureData structureData(Structure<?> structure) {
        return dataByStructure.computeIfAbsent(structure, (structure1) -> new StructureData(this, structure));
    }

    public CompoundNBT saveTag() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putLong("pos", pos);
        for (Map.Entry<Structure<?>, StructureData> entry : this.dataByStructure.entrySet()) {
            nbt.put(Registry.STRUCTURE_FEATURE.getKey(entry.getKey()).toString(), entry.getValue().saveTag());
        }
        return nbt;
    }

    public static Object2ObjectArrayMap<Structure<?>, StructureData> dataByStructureFromDisk(CompoundNBT nbt, StructureRegion region) {
        Object2ObjectArrayMap<Structure<?>, StructureData> serialized = new Object2ObjectArrayMap<>();
        for (String key : nbt.getAllKeys()) {
            Structure<?> structure = Registry.STRUCTURE_FEATURE.get(new ResourceLocation(key));
            serialized.put(structure, new StructureData(nbt.getCompound(key), region, structure));
        }

        return serialized;
    }

    public ServerWorld getServerLevel() {
        return world;
    }

    public long getPos() {
        return pos;
    }
}
