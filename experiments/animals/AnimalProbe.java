import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;

/** Disposable server experiment, never include in the production plugin. */
public final class AnimalProbe extends JavaPlugin implements Listener, CommandExecutor {
    World w; NamespacedKey key; boolean cancelBreed; boolean cancelDeath; int breeds, deaths, drops, xp, allDeaths;
    Cow a,b; UUID saved; Set<UUID> orbs=new HashSet<>();
    void log(String s){getLogger().info("OBS "+s);}
    void check(boolean ok,String s){if(!ok)throw new IllegalStateException("FAIL "+s);log("PASS "+s);}
    void later(long ticks,Runnable r){Bukkit.getScheduler().runTaskLater(this,r,ticks);}
    public void onEnable(){if(Bukkit.getPort()!=25579 || !Files.exists(Path.of(".animal-experiment")))throw new IllegalStateException("Disposable fixture only");w=Bukkit.getWorlds().getFirst();key=new NamespacedKey(this,"logical-id");getCommand("animalprobe").setExecutor(this);Bukkit.getPluginManager().registerEvents(this,this);}
    Cow cow(double x){Cow c=w.spawn(new Location(w,x,64,8),Cow.class);c.setPersistent(true);c.setRemoveWhenFarAway(false);c.getPersistentDataContainer().set(key,PersistentDataType.STRING,UUID.randomUUID().toString());return c;}
    void arena(){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)w.setChunkForceLoaded(x,z,true);for(int x=0;x<16;x++)for(int z=0;z<16;z++)w.getBlockAt(x,63,z).setType(Material.STONE);}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){try{
      if(!(sender instanceof ConsoleCommandSender))throw new IllegalStateException("Console only");
      switch(args[0]){
        case "breedcancel", "breedallow" -> {arena();for(Entity e:w.getEntities())if(e instanceof Cow || e instanceof Item || e instanceof ExperienceOrb)e.remove();breeds=drops=xp=0;cancelBreed=args[0].equals("breedcancel");a=cow(8);b=cow(9);a.setLoveModeTicks(600);b.setLoveModeTicks(600);later(240,()->{log("breed summary events="+breeds+" cows="+w.getEntitiesByClass(Cow.class).size()+" ages="+a.getAge()+","+b.getAge()+" love="+a.getLoveModeTicks()+","+b.getLoveModeTicks()+" xp="+xp);check(breeds>0,"native breed event occurred");});}
        case "death" -> {arena();drops=xp=deaths=0;cancelDeath=true;a=cow(8);a.setAI(false);a.damage(1000);check(a.isValid()&&!a.isDead()&&a.getHealth()==4,"cancelled lethal damage revives at requested health");later(30,()->{check(drops==0&&xp==0,"cancelled death emitted no drops or XP");cancelDeath=false;a.setHealth(0);later(30,()->{check(deaths==2,"completion fires another death callback");check(drops==1&&xp==7,"one controlled reward batch on completion");});});}
        case "food" -> {arena();a=cow(8);b=cow(9);a.setAI(false);b.setAI(false);cancelBreed=true;breeds=0;
          var level=((org.bukkit.craftbukkit.CraftWorld)w).getHandle();
          var player=new net.minecraft.server.level.ServerPlayer(((org.bukkit.craftbukkit.CraftServer)Bukkit.getServer()).getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AnimalFixture"),net.minecraft.server.level.ClientInformation.createDefault());
          var food=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT,4);
          player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,food);
          var first=((org.bukkit.craftbukkit.entity.CraftAnimals)a).getHandle();var second=((org.bukkit.craftbukkit.entity.CraftAnimals)b).getHandle();
          first.mobInteract(player,net.minecraft.world.InteractionHand.MAIN_HAND);second.mobInteract(player,net.minecraft.world.InteractionHand.MAIN_HAND);
          check(food.getCount()==2,"native feeding consumes two wheat before breeding");
          first.spawnChildFromBreeding(level,second);
          check(breeds==1&&food.getCount()==2,"cancelled native breeding does not refund wheat");
        }
        case "seed" -> {arena();a=cow(8);a.setAI(false);getConfig().set("uuid",a.getUniqueId().toString());getConfig().set("logical",a.getPersistentDataContainer().get(key,PersistentDataType.STRING));saveConfig();check(((org.bukkit.craftbukkit.CraftServer)Bukkit.getServer()).getServer().saveEverything(false,true,true),"world checkpoint flushed");log("seed saved "+a.getUniqueId());}
        case "unload" -> {UUID id=UUID.fromString(getConfig().getString("uuid"));for(Chunk c:w.getForceLoadedChunks())w.setChunkForceLoaded(c.getX(),c.getZ(),false);later(60,()->{check(w.unloadChunk(0,0,true),"chunk unload accepted");check(Bukkit.getEntity(id)==null,"unloaded UUID absent from live lookup");w.getChunkAt(0,0).getEntities();check(Bukkit.getEntity(id)!=null,"same UUID recovered after entity load");inspect();});}
        case "inspect" -> inspect();
        case "crashbirth" -> {arena();Cow child=cow(8);child.setBaby();child.setAI(false);getConfig().set("birth-uuid",child.getUniqueId().toString());getConfig().set("birth-status","PREPARED");saveConfig();check(((org.bukkit.craftbukkit.CraftServer)Bukkit.getServer()).getServer().saveEverything(false,true,true),"world checkpoint flushed");log("child saved with operation still PREPARED; halting disposable JVM");Runtime.getRuntime().halt(0);}
        case "inspectbirth" -> {w.getChunkAt(0,0).getEntities();Entity child=Bukkit.getEntity(UUID.fromString(getConfig().getString("birth-uuid")));check(child instanceof Cow && child.getPersistentDataContainer().has(key),"applied child survived before completion acknowledgment");check("PREPARED".equals(getConfig().getString("birth-status")),"operation remains prepared: respawn retry would duplicate child");}
        case "crashremove" -> {inspect();Entity e=Bukkit.getEntity(UUID.fromString(getConfig().getString("uuid")));int before=allDeaths;e.remove();check(allDeaths==before,"remove bypasses death callback");log("removal applied without world save; halting disposable JVM");Runtime.getRuntime().halt(0);}
        case "crashintent" -> {inspect();Path path=getDataFolder().toPath().resolve("pending-intent.txt");try(FileChannel f=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){f.write(ByteBuffer.wrap(("DEATH_PENDING "+getConfig().getString("uuid")).getBytes()));f.force(true);}log("intent fsynced before world mutation; halting disposable JVM");Runtime.getRuntime().halt(0);}
        default -> throw new IllegalArgumentException("Unknown experiment");
      }
    }catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"experiment failed",e);}return true;}
    void inspect(){w.getChunkAt(0,0).getEntities();Entity e=Bukkit.getEntity(UUID.fromString(getConfig().getString("uuid")));check(e!=null,"saved entity exists");check(getConfig().getString("logical").equals(e.getPersistentDataContainer().get(key,PersistentDataType.STRING)),"logical PDC survives reload/restart");log("pending intent="+Files.exists(getDataFolder().toPath().resolve("pending-intent.txt")));}
    @EventHandler public void breed(EntityBreedEvent e){if(e.getMother()!=a&&e.getFather()!=a)return;breeds++;log("breed callback childValid="+e.getEntity().isValid()+" parent ages="+a.getAge()+","+b.getAge()+" love="+a.getLoveModeTicks()+","+b.getLoveModeTicks()+" exp="+e.getExperience());e.setCancelled(cancelBreed);if(cancelBreed){a.setAI(false);b.setAI(false);}later(1,()->log("breed next tick ages="+a.getAge()+","+b.getAge()+" love="+a.getLoveModeTicks()+","+b.getLoveModeTicks()+" childValid="+e.getEntity().isValid()));}
    @EventHandler public void spawn(CreatureSpawnEvent e){if(e.getSpawnReason()==CreatureSpawnEvent.SpawnReason.BREEDING)log("BREEDING spawn after breed callback");}
    @EventHandler public void death(EntityDeathEvent e){allDeaths++;if(e.getEntity()!=a)return;deaths++;log("death callback="+deaths+" cancelled="+cancelDeath+" health="+a.getHealth());e.getDrops().clear();e.getDrops().add(new org.bukkit.inventory.ItemStack(Material.DIAMOND));e.setDroppedExp(7);if(cancelDeath){e.setReviveHealth(4);e.setCancelled(true);}}
    @EventHandler public void drop(ItemSpawnEvent e){drops++;log("item spawn "+e.getEntity().getItemStack());}
    @EventHandler public void exp(EntitySpawnEvent e){if(e.getEntity() instanceof ExperienceOrb orb && orbs.add(orb.getUniqueId())){xp+=orb.getExperience();log("XP spawn "+orb.getExperience());}}
    @EventHandler public void load(EntitiesLoadEvent e){for(Entity n:e.getEntities())if(n.getPersistentDataContainer().has(key))log("entity load "+n.getUniqueId());}
    @EventHandler public void unload(EntitiesUnloadEvent e){for(Entity n:e.getEntities())if(n.getPersistentDataContainer().has(key))log("entity unload "+n.getUniqueId());}
}
