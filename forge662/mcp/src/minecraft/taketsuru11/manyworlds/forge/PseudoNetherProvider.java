package taketsuru11.manyworlds.forge;

import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManagerHell;

public class PseudoNetherProvider extends WorldProviderHell {

    public void registerWorldChunkManager()
    {
    	int savedDimension = dimensionId;
        super.registerWorldChunkManager();
        this.dimensionId = savedDimension;
    }

}
