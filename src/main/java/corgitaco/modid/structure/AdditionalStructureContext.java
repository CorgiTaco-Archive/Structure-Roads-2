package corgitaco.modid.structure;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.ChunkPos;

import javax.annotation.Nullable;
import java.util.Set;

public class AdditionalStructureContext {

    private final String name;
    private int tier;
    private final Set<Long> connections;
    private long lastLoadedTime;
    @Nullable private ChunkPos harbourPos;

    public AdditionalStructureContext(String name) {
        this(name, 0, new LongArraySet(), 0L);
    }

    public AdditionalStructureContext(CompoundNBT nbt) {
        this(nbt.getString("name"), nbt.getInt("tier"), new LongArraySet(nbt.getLongArray("connections")), nbt.getLong("lastLoadedTime"));

        harbourPos = readChunkPos(nbt.getCompound("harbour"));
    }

    public AdditionalStructureContext(String name, int tier, Set<Long> connections, long lastLoadedTime) {
        this.name = name;
        this.tier = tier;
        this.connections = connections;
        this.lastLoadedTime = lastLoadedTime;
    }

    public String getName() {
        return name;
    }

    public CompoundNBT write() {
        CompoundNBT compoundNBT = new CompoundNBT();
        compoundNBT.putString("name", this.name);
        compoundNBT.putInt("tier", this.tier);
        compoundNBT.putLongArray("connections", new LongArrayList(this.connections));
        if(harbourPos != null){
            compoundNBT.put("harbour", writeChunkPos(harbourPos));
        }
        compoundNBT.putLong("lastLoadedTime", this.lastLoadedTime);
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

    public long getLastLoadedTime() {
        return lastLoadedTime;
    }

    public void setLastLoadedTime(long lastLoadedTime) {
        this.lastLoadedTime = lastLoadedTime;
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