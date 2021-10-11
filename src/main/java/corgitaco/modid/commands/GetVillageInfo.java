package corgitaco.modid.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.datafixers.util.Pair;
import corgitaco.modid.core.StructureData;
import corgitaco.modid.core.StructureRegion;
import corgitaco.modid.core.StructureRegionManager;
import corgitaco.modid.structure.AdditionalStructureContext;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.server.ServerWorld;

import java.util.Collection;

public class GetVillageInfo {
    private static final int VILLAGE_CONTEXT_SEARCH_RANGE = 16;

    public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher) {
        return Commands.literal("villageInfo").executes(cs -> getVillageInfo(cs.getSource()));
    }

    private static int getVillageInfo(CommandSource source){
        ServerWorld level = source.getLevel();
        BlockPos pos = new BlockPos(source.getPosition());
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        long chunkPosLong = ChunkPos.asLong(chunkX, chunkZ);

        StructureRegionManager regionManager = ((StructureRegionManager.Access) level).getStructureRegionManager();
        long regionKey = StructureRegionManager.chunkToRegionKey(chunkPosLong);
        StructureRegion region = regionManager.getStructureRegion(regionKey);
        StructureData villageData = region.structureData(Structure.VILLAGE);
        Long2ReferenceMap<AdditionalStructureContext> regionContexts = villageData.getLocationContextData(true);

        Pair<AdditionalStructureContext, Long> searchResult = searchForVillageContext(regionContexts, chunkX, chunkZ);
        if(searchResult != null){
            BlockPos villagePos = StructureRegionManager.getPosFromChunk(searchResult.getSecond());
            AdditionalStructureContext villageContext = searchResult.getFirst();
            source.sendSuccess(new TranslationTextComponent("Found a village at " + villagePos.getX() + ", " + villagePos.getZ()), true);
            source.sendSuccess(new TranslationTextComponent("Name: " + villageContext.getName()), false);
            source.sendSuccess(new TranslationTextComponent("Tier: " + villageContext.getTier()), false);
            if(villageContext.getHarbourPos() != null){
                int harbourX = villageContext.getHarbourPos().x * 16 + 8;
                int harbourZ = villageContext.getHarbourPos().z * 16 + 8;
                source.sendSuccess(new TranslationTextComponent("Harbour: " + "[" + harbourX + " " + harbourZ + "]"), false);
            }else{
                source.sendSuccess(new TranslationTextComponent("Village has no harbour"), false);
            }
            Collection<Long> connections = villageContext.getConnections();
            for(long connection : connections){
                int x = ChunkPos.getX(connection) * 16 + 8;
                int z = ChunkPos.getZ(connection) * 16 + 8;
                source.sendSuccess(new TranslationTextComponent("Connected To: " + "[" + x + " " + z + "]").withStyle((style) -> {
                    return style.withColor(TextFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + x + " ~ " + z));
                }), true);
            }
        }else {
            source.sendFailure(new TranslationTextComponent("Could not find a village (Searched up to " + VILLAGE_CONTEXT_SEARCH_RANGE * 16 + " blocks away)"));
        }

        return 1;
    }

    //Definitely could be made more efficient
    private static Pair<AdditionalStructureContext, Long> searchForVillageContext(Long2ReferenceMap<AdditionalStructureContext> regionContexts, int chunkX, int chunkZ) {
        for(int searchRange = 0; searchRange < VILLAGE_CONTEXT_SEARCH_RANGE; searchRange++){
            for(int xOffset = -searchRange; xOffset <= searchRange; xOffset++){
                for(int zOffset = -searchRange; zOffset <= searchRange; zOffset++){
                    if(Math.max(Math.abs(xOffset), Math.abs(zOffset)) != searchRange) continue;

                    long chunkKey = ChunkPos.asLong(chunkX + xOffset, chunkZ + zOffset);
                    if(regionContexts.containsKey(chunkKey)){
                        return new Pair<>(regionContexts.get(chunkKey), chunkKey);
                    }
                }
            }
        }

        return null;
    }
}
