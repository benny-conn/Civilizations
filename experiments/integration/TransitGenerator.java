import org.bukkit.*;
import org.bukkit.generator.*;
import org.bukkit.block.Biome;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.nio.file.*;

/** Resource-free transit dimension for the isolated S5 fixture only. */
public class TransitGenerator extends JavaPlugin {
  public ChunkGenerator getDefaultWorldGenerator(String name,String id) {
    if(Bukkit.getPort()!=25583 || !Files.exists(Path.of("S5-FIXTURE"))) throw new IllegalStateException("S5 fixture only");
    return new ChunkGenerator() {
      public boolean shouldGenerateNoise(){return false;}
      public boolean shouldGenerateSurface(){return false;}
      public boolean shouldGenerateBedrock(){return false;}
      public boolean shouldGenerateCaves(){return false;}
      public boolean shouldGenerateDecorations(){return false;}
      public boolean shouldGenerateMobs(){return false;}
      public boolean shouldGenerateStructures(){return false;}
      public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
          public Biome getBiome(WorldInfo w,int x,int y,int z){return Biome.THE_VOID;}
          public List<Biome> getBiomes(WorldInfo w){return List.of(Biome.THE_VOID);}
        };
      }
    };
  }
}
