package corgitaco.modid.world.path;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;

public class PathGeneratorUtil {
    public static boolean canPathPassThrough(ServerWorld world, BlockPos pos){
        /*int x = Math.abs(pos.getX()) % 200;
        int z = Math.abs(pos.getZ()) % 200;

        int squaredDist = (x - 100) * (x - 100) + (z - 100) * (z - 100);

        return squaredDist > 2000;*/

        int height = getHeight(world, pos);

        return height > 60 && height < 100;
    }

    public static int getHeight(ServerWorld world, BlockPos pos){
        return world.getChunkSource().generator.getBaseHeight(pos.getX(), pos.getZ(), Heightmap.Type.OCEAN_FLOOR_WG);
    }
}
