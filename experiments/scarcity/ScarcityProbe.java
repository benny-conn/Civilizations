import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.inventory.*;
import org.bukkit.loot.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import io.bennyc.civilizations.domain.damage.*;
import io.bennyc.civilizations.domain.repair.*;
import io.bennyc.civilizations.domain.protection.*;
import io.bennyc.civilizations.application.repair.*;

/** Disposable server-thread adapter probe. Never install on a user server. */
public class ScarcityProbe extends JavaPlugin {
  static final String BASE="io.bennyc.civilizations.";
  final UUID id=UUID.fromString("00000000-0000-0000-0000-000000000041");
  public void onEnable() {
    if (Bukkit.getPort()!=25582 || !Files.exists(Path.of("S4-FIXTURE"))) throw new IllegalStateException("Isolated fixture only");
    getCommand("s4probe").setExecutor(this);
  }
  void check(boolean ok,String text) {if(!ok)throw new IllegalStateException(text);getLogger().info("PASS "+text);}
  Object field(Object o,String name)throws Exception {var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
  void set(Object o,String name,Object value)throws Exception {var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
  Object make(String name,Object...args)throws Exception {
    var type=Class.forName(BASE+name);
    for(var c:type.getDeclaredConstructors()) {
      var types=c.getParameterTypes();
      if(types.length==args.length+1 && types[types.length-1].getSimpleName().equals("DefaultConstructorMarker")) {
        c.setAccessible(true);return c.newInstance(Arrays.copyOf(args,args.length+1));
      }
      if(types.length==args.length && !types[types.length-1].getSimpleName().equals("DefaultConstructorMarker")) {
        c.setAccessible(true);return c.newInstance(args);
      }
    }
    throw new IllegalStateException("Constructor "+name+" "+args.length);
  }
  Object position(Block block)throws Exception {return make("domain.damage.BlockPosition3D",block.getWorld().getKey().asString(),block.getX(),block.getY(),block.getZ());}
  RepairWorkItem work(Block b, SimpleBlockSnapshot original)throws Exception {
    var change=(BattleBlockChange)make("domain.damage.BattleBlockChange",id,id,id,id,position(b),original,BlockMutationCause.PLAYER_BREAK,id,Instant.EPOCH);
    var report=(BattleDamageReportEntry)make("domain.damage.BattleDamageReportEntry",id,id,id,new SimpleBlockSnapshot("minecraft:air"),DamageReportEligibility.ELIGIBLE,DamageCostCategory.RESTORE_ORIGINAL_BLOCK);
    var item=(RepairJobItem)make("domain.repair.RepairJobItem",id,id,id,0L,0L,RepairJobItemStatus.PENDING,null,null);
    return new RepairWorkItem(item,new ReportedBattleBlockChange(change,report));
  }
  boolean admitted(Block b)throws Exception {
    var type=Class.forName(BASE+"infrastructure.paper.protection.SimpleBattleBlockPolicy");
    return (boolean)type.getMethod("allowsBreak",Block.class).invoke(type.getField("INSTANCE").get(null),b);
  }
  void repair()throws Exception {
    var plugin=Bukkit.getPluginManager().getPlugin("Civilizations");
    var battle=field(plugin,"repairCoordinator");var execute=battle.getClass().getDeclaredMethod("execute",RepairWorkItem.class);execute.setAccessible(true);
    World world=Bukkit.getWorlds().getFirst();Block b=world.getBlockAt(8,200,8);
    for(Material material:List.of(Material.DIAMOND_ORE,Material.DEEPSLATE_DIAMOND_ORE,Material.DIAMOND_BLOCK)) {
      b.setType(material,false);check(!admitted(b),material+" journal admission denied");
      var original=new SimpleBlockSnapshot(b.getBlockData().getAsString());
      check(b.breakNaturally(new ItemStack(Material.NETHERITE_PICKAXE)),material+" native ordinary harvest");
      Object result=execute.invoke(battle,work(b,original));
      check(result.getClass().getSimpleName().equals("Unavailable"),material+" historical battle repair refused");
      check(b.getType()==Material.AIR && !b.breakNaturally(new ItemStack(Material.NETHERITE_PICKAXE)),material+" second harvest has no block");
    }
    b.setType(Material.STONE,false);check(admitted(b),"ordinary stone remains journalable");
    var stone=new SimpleBlockSnapshot("minecraft:stone");b.setType(Material.AIR,false);
    check(execute.invoke(battle,work(b,stone)).getClass().getSimpleName().equals("Completed") && b.getType()==Material.STONE,"ordinary battle reconstruction still restores stone");
    // Inject an old item at the production exposure runner boundary. No database rows are forged.
    b.setType(Material.DIAMOND_BLOCK,false);b.breakNaturally(new ItemStack(Material.NETHERITE_PICKAXE));
    var exposure=field(plugin,"landProtectionCoordinator");
    var job=make("domain.protection.ProtectionRepairJob",id,id,id,null,"fixture",10000,1L,0L,1L,0L,1L,0L,0L,0L,null,ProtectionRepairJobStatus.RUNNING,0L,0L,0L,0L,Instant.EPOCH,Instant.EPOCH,null,null);
    var item=make("domain.protection.ProtectionRepairJobItem",id,id,0L,position(b),new SimpleBlockSnapshot("minecraft:air"),new SimpleBlockSnapshot("minecraft:diamond_block"),0L,ProtectionRepairItemStatus.PENDING,null,null);
    set(exposure,"activeJob",job);set(exposure,"activeItem",item);
    var tick=exposure.getClass().getDeclaredMethod("tickJob");tick.setAccessible(true);tick.invoke(exposure);
    check((boolean)field(exposure,"storageBusy") && b.getType()==Material.AIR,"historical exposure work enters pause before world mutation");
    Bukkit.getScheduler().runTaskLater(this,()->check(b.getType()==Material.AIR && !b.breakNaturally(new ItemStack(Material.NETHERITE_PICKAXE)),"exposure second harvest has no block"),20L);
  }
  void loot(boolean expectedFiltered) {
    var table=Bukkit.getLootTable(NamespacedKey.minecraft("chests/end_city_treasure"));
    int selected=0,other=0;
    for(int seed=0;seed<32;seed++) {
      var inventory=Bukkit.createInventory(null,27);
      table.fillInventory(inventory,new Random(seed),new LootContext.Builder(Bukkit.getWorlds().getFirst().getSpawnLocation()).build());
      for(var item:inventory.getContents())if(item!=null){
        if(item.getType().name().startsWith("DIAMOND"))selected+=item.getAmount();else other+=item.getAmount();
      }
    }
    check(other>0 && (expectedFiltered?selected==0:selected>0),"native loot fill selected="+selected+" other="+other+" filtered="+expectedFiltered);
  }
  public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
    if(!(sender instanceof ConsoleCommandSender))return true;
    try {
      switch(args[0]) {
        case "repair" -> repair();
        case "loot-off" -> loot(false);
        case "loot-on" -> loot(true);
        case "spawn-on" -> {
          var w=Bukkit.getWorlds().getFirst();
          var zombie=w.spawn(new Location(w,10,200,10),org.bukkit.entity.Zombie.class,z->z.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET)));
          check(!zombie.isValid(),"native new diamond-equipped mob denied");
        }
        case "vault-on" -> {
          var event=new org.bukkit.event.block.BlockDispenseLootEvent(null,Bukkit.getWorlds().getFirst().getBlockAt(10,200,10),new ArrayList<>(List.of(new ItemStack(Material.DIAMOND),new ItemStack(Material.EMERALD))),Bukkit.getLootTable(NamespacedKey.minecraft("chests/end_city_treasure")));
          Bukkit.getPluginManager().callEvent(event);
          check(event.getDispensedLoot().size()==1 && event.getDispensedLoot().getFirst().getType()==Material.EMERALD,"vault event dispatch filters only selected loot (synthetic event)");
        }
        case "world" -> {var w=Bukkit.getWorlds().getFirst();getLogger().info("WORLD "+w.getKey()+" "+w.getUID());}
        case "block" -> {var w=Bukkit.getWorlds().getFirst();var b=w.getBlockAt(Integer.parseInt(args[1]),Integer.parseInt(args[2]),Integer.parseInt(args[3]));check(b.getType()==Material.DIAMOND_ORE || b.getType()==Material.DEEPSLATE_DIAMOND_ORE,"prepared ore loads natively at "+b.getLocation());}
      }
    }catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"S4 probe failed",e);}return true;
  }
}
