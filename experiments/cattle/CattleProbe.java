import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.command.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime;
import io.bennyc.civilizations.application.ApplicationResult;
import java.util.*;

public class CattleProbe extends JavaPlugin implements Listener,CommandExecutor {
  World w; NamespacedKey key; boolean crashBirth; boolean crashDeath;
  public void onEnable(){if(Bukkit.getPort()!=25581 || !java.nio.file.Files.exists(java.nio.file.Path.of(".cattle-experiment")))throw new IllegalStateException("fixture only");w=Bukkit.getWorlds().getFirst();key=new NamespacedKey("civilizations","mob-id");getCommand("cattleprobe").setExecutor(this);Bukkit.getPluginManager().registerEvents(this,this);}
  void later(long n,Runnable r){Bukkit.getScheduler().runTaskLater(this,r,n);}
  void check(boolean b,String s){if(!b)throw new IllegalStateException("FAIL "+s);getLogger().info("PASS "+s);}
  List<Cow> cows(){return w.getEntitiesByClass(Cow.class).stream().filter(c->c.getPersistentDataContainer().has(key)).toList();}
  public boolean onCommand(CommandSender s,Command cmd,String l,String[] a){try {if(!(s instanceof ConsoleCommandSender))throw new IllegalStateException("Console only");switch(a[0]){
    case "setup" -> {for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)w.setChunkForceLoaded(x,z,true);for(int x=0;x<16;x++)for(int z=0;z<16;z++){w.getBlockAt(x,63,z).setType(Material.STONE);if(x==0||x==15||z==0||z==15)for(int y=64;y<=66;y++)w.getBlockAt(x,y,z).setType(Material.GLASS);}Cow old=w.spawn(new Location(w,5,64,5),Cow.class);getConfig().set("old",old.getUniqueId().toString());saveConfig();getLogger().info("WORLD "+w.getUID());}
    case "check" -> {getLogger().info("Managed "+cows().size());for(Cow c:cows())getLogger().info("COW "+c.getUniqueId()+" logical="+c.getPersistentDataContainer().get(key,PersistentDataType.STRING)+" age="+c.getAge()+" AI="+c.hasAI()+" health="+c.getHealth());Entity old=Bukkit.getEntity(UUID.fromString(getConfig().getString("old")));check(old instanceof Cow && !((Cow)old).hasAI() && old.isInvulnerable(),"preexisting unregistered cow contained");}
    case "deny" -> {Cow c=w.spawn(new Location(w,8,64,8),Cow.class);check(!c.isValid(),"unauthorized CUSTOM spawn cancelled");}
    case "breed","birthcrash" -> {var parents=cows().stream().filter(c->c.getAge()==0 && c.hasAI()).limit(2).toList();check(parents.size()==2,"two adult managed parents ready");crashBirth=a[0].equals("birthcrash");parents.forEach(c->c.setLoveModeTicks(600));later(240,()->{check(cows().size()>=3,"native breeding produced managed child");});}
    case "deathcrash" -> {Cow c=cows().getFirst();getConfig().set("dead",c.getUniqueId().toString());saveConfig();((org.bukkit.craftbukkit.CraftServer)Bukkit.getServer()).getServer().saveEverything(false,true,true);c.damage(1000);later(80,()->{check(Bukkit.getEntity(c.getUniqueId())==null,"managed death completed");getLogger().info("HALT after durable death, before next world save");Runtime.getRuntime().halt(0);});}
    case "unload" -> {var ids=cows().stream().map(Cow::getUniqueId).toList();for(Chunk chunk:w.getForceLoadedChunks())w.setChunkForceLoaded(chunk.getX(),chunk.getZ(),false);later(60,()->{check(w.unloadChunk(0,0,true),"chunk unloaded");check(ids.stream().allMatch(id->Bukkit.getEntity(id)==null),"unload removes only live presence");w.getChunkAt(0,0).getEntities();w.setChunkForceLoaded(0,0,true);later(40,()->{check(ids.stream().allMatch(id->Bukkit.getEntity(id) instanceof Cow cow && cow.hasAI()),"same cows reconciled after unload and reload");});});}
    case "pendingcrash" -> {Cow c=cows().getFirst();getConfig().set("pending",c.getUniqueId().toString());saveConfig();crashDeath=true;c.damage(1000);}
    case "pendingcheck" -> {var id=UUID.fromString(getConfig().getString("pending"));var c=(Cow)Bukkit.getEntity(id);check(c!=null && !c.hasAI() && c.isInvulnerable(),"pending death contained after restart");getLogger().info("SETTLE "+id);}
    case "deadcheck" -> {check(Bukkit.getEntity(UUID.fromString(getConfig().getString("dead")))==null,"tombstoned world snapshot suppressed after restart");}
  }}catch(Exception ex){getLogger().log(java.util.logging.Level.SEVERE,"probe failed",ex);}return true;}
  @EventHandler(priority=EventPriority.MONITOR)public void death(EntityDeathEvent e){if(crashDeath && e.isCancelled() && e.getEntity() instanceof Cow){crashDeath=false;blockAndHalt();}if(e.getEntity() instanceof Cow)getLogger().info("DEATH cancelled="+e.isCancelled()+" items="+e.getDrops()+" xp="+e.getDroppedExp());}
  @EventHandler(priority=EventPriority.MONITOR)public void spawn(CreatureSpawnEvent e){if(crashBirth && e.getEntity() instanceof Cow && e.getEntity().getPersistentDataContainer().has(key)) {crashBirth=false;blockAndHalt();}
  }
  void blockAndHalt(){try {
      var plugin=Bukkit.getPluginManager().getPlugin("Civilizations");var field=plugin.getClass().getDeclaredField("runtime");field.setAccessible(true);var runtime=(CivilizationsRuntime)field.get(plugin);
      runtime.<Boolean>submitMobOperation(scope->{try{Thread.sleep(20000);}catch(InterruptedException ex){Thread.currentThread().interrupt();}return new ApplicationResult.Applied<Boolean>(true);},out->io.bennyc.civilizations.lib.kotlin.Unit.INSTANCE);
      later(1,()->{((org.bukkit.craftbukkit.CraftServer)Bukkit.getServer()).getServer().saveEverything(false,true,true);getLogger().info("HALT world saved while next SQL operation blocked");Runtime.getRuntime().halt(0);});
    }catch(Exception ex){throw new RuntimeException(ex);}
  }
}
