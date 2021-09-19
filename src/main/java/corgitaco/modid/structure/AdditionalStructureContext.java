package corgitaco.modid.structure;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import net.minecraft.nbt.CompoundNBT;

import java.util.Set;

public class AdditionalStructureContext {

    private final String name;
    private int tier;
    private final Set<Long> connections;

    public AdditionalStructureContext(String name) {
        this(name, 0, new LongArraySet());
    }

    public AdditionalStructureContext(CompoundNBT nbt) {
        this(nbt.getString("name"), nbt.getInt("tier"), new LongArraySet(nbt.getLongArray("connections")));
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
        return compoundNBT;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public Set<Long> getConnections() {
        return connections;
    }
}