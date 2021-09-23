package corgitaco.modid.structure;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import javax.annotation.Nullable;
import java.util.Set;

public class AdditionalStructureContext {

    private final String name;
    private int tier;
    private final Set<Long> connections;
    @Nullable private ChunkPos harbourPos;

    public AdditionalStructureContext(String name) {
        this(name, 0, new LongArraySet());
    }

    public AdditionalStructureContext(CompoundNBT nbt) {
        this(nbt.getString("name"), nbt.getInt("tier"), new LongArraySet(nbt.getLongArray("connections")));

        harbourPos = readChunkPos(nbt.getCompound("harbour"));
    }

    public AdditionalStructureContext(String name, int tier, Set<Long> connections) {
        this.name = name;
        this.tier = tier;
        this.connections = connections;
    }

    public String getName() {
        return name;
    }

    public static AdditionalStructureContext read(CompoundNBT readNBT) {
        return new AdditionalStructureContext(readNBT.getString("name"), readNBT.getInt("tier"), new LongArraySet(readNBT.getLongArray("connections")));
    }

    public CompoundNBT write() {
        CompoundNBT compoundNBT = new CompoundNBT();
        compoundNBT.putString("name", this.name);
        compoundNBT.putInt("tier", this.tier);
        compoundNBT.putLongArray("connections", new LongArrayList(this.connections));
        if(harbourPos != null){
            compoundNBT.put("harbour", writeChunkPos(harbourPos));
        }
        return compoundNBT;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public void setHarbourPos(ChunkPos harbourPos) {
        this.harbourPos = harbourPos;
    }

    public Set<Long> getConnections() {
        return connections;
    }

    public ChunkPos getHarbourPos(){
        return harbourPos;
    }

    private static CompoundNBT writeChunkPos(ChunkPos chunk){
        CompoundNBT nbt = new CompoundNBT();
        nbt.putInt("x", chunk.x);
        nbt.putInt("z", chunk.z);
        return nbt;
    }

    private static ChunkPos readChunkPos(CompoundNBT nbt){
        if(nbt == null) return null;

        return new ChunkPos(nbt.getInt("x"), nbt.getInt("z"));
    }
}