import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;

/** S5 server-only integration fixture; no production gameplay commands. */
public class IntegrationProbe extends JavaPlugin implements Listener {
  World over,nether;
  final NamespacedKey marker=new NamespacedKey("civilizations","mob-id");
  final int[][] towns={{440,520},{1570,630},{1110,1530}};
  final int[][] habitats={{550,700},{1510,800},{1220,1450}};
  final int[][] basins={{1020,530},{1190,1180},{900,1430}};
  int[][] portals=new int[3][3];
  List<Map<String,Object>> seedSlots=new ArrayList<>(), zones=new ArrayList<>(), farms=new ArrayList<>();
  public void onEnable(){
    if(Bukkit.getPort()!=25583 || !Files.exists(Path.of("S5-FIXTURE")))throw new IllegalStateException("S5 fixture only");
    getCommand("s5probe").setExecutor(this);
    over=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));nether=Bukkit.getWorld(NamespacedKey.minecraft("the_nether"));
    Bukkit.getPluginManager().registerEvents(this,this);
    if(getConfig().getBoolean("prepared"))pinRelay();
    for(int i=0;i<3;i++)if(getConfig().contains("portal."+i)){
      var v=getConfig().getIntegerList("portal."+i);portals[i]=new int[]{v.get(0),v.get(1),v.get(2)};
    }
  }
  void check(boolean value,String message){if(!value)throw new IllegalStateException(message);getLogger().info("PASS "+message);}
  void later(long ticks,Runnable r){Bukkit.getScheduler().runTaskLater(this,()->{try{r.run();}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"S5 delayed failure",e);}},ticks);}
  void pinRelay(){for(int x=-5;x<=5;x++){pin(nether,x*16,0);pin(nether,x*16,-1);}}
  int pad(World w,int x,int z,int radius){int y=w.getHighestBlockYAt(x,z);for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
    w.getBlockAt(x+dx,y,z+dz).setType(Material.GRASS_BLOCK,false);
    for(int dy=1;dy<=4;dy++)w.getBlockAt(x+dx,y+dy,z+dz).setType(Material.AIR,false);
  }return y+1;}
  void pin(World w,int x,int z){w.setChunkForceLoaded(x>>4,z>>4,true);}
  void frame(World w,int x,int y,int z){
    for(int dx=-2;dx<=4;dx++)for(int dz=-2;dz<=4;dz++){
      w.getBlockAt(x+dx,y-1,z+dz).setType(Material.STONE,false);
      for(int dy=0;dy<4;dy++)w.getBlockAt(x+dx,y+dy,z+dz).setType(Material.AIR,false);
    }
    for(int dx=-1;dx<=2;dx++)for(int dy=-1;dy<=3;dy++){
      Block b=w.getBlockAt(x+dx,y+dy,z);
      if(dx==-1 || dx==2 || dy==-1 || dy==3)b.setType(Material.OBSIDIAN,false);
      else{Orientable d=(Orientable)Material.NETHER_PORTAL.createBlockData();d.setAxis(Axis.X);b.setBlockData(d,false);}
    }pin(w,x,z);
  }
  Map<String,Object> zone(String id,String kind,int[] b){return Map.of("id",id,"resource",kind,"bounds",b);}
  void prepare()throws Exception {
    check(!getConfig().getBoolean("prepared"),"preparation runs once");
    check(Bukkit.getWorlds().size()==2 && nether!=null && Bukkit.getWorlds().stream().noneMatch(w->w.getEnvironment()==World.Environment.THE_END),"only Overworld and Nether loaded; End closed");
    check(nether.getGenerator()!=null && nether.getGenerator().getClass().getName().startsWith("TransitGenerator"),"Nether uses empty transit generator");
    over.getWorldBorder().setCenter(1024,1024);over.getWorldBorder().setSize(2048);
    nether.getWorldBorder().setCenter(0,0);nether.getWorldBorder().setSize(256);
    for(int x=-80;x<=80;x++)for(int z=-8;z<=8;z++)nether.getBlockAt(x,64,z).setType(Material.STONE,false);
    for(int x=-80;x<=80;x++)for(int z:new int[]{-8,8})nether.getBlockAt(x,65,z).setType(Material.OAK_FENCE,false);
    nether.setSpawnLocation(1,65,4);pinRelay();
    for(int i=0;i<3;i++){
      int x=habitats[i][0],z=habitats[i][1],y=pad(over,x,z,10);
      zones.add(zone("herd-"+i,"CATTLE",new int[]{x-24,y-5,z-24,x+24,y+10,z+24}));
      for(int dx=-10;dx<=10;dx++)for(int dz=-10;dz<=10;dz++)if(Math.abs(dx)==10 || Math.abs(dz)==10)over.getBlockAt(x+dx,y,z+dz).setType(Material.OAK_FENCE,false);
      for(int n=0;n<8;n++){
        int sx=x-3+(n%4)*2,sz=z-2+(n/4)*4;
        seedSlots.add(Map.of("id","herd-"+i+"-"+n,"world-uuid",over.getUID().toString(),"x",sx,"y",y,"z",sz));pin(over,sx,sz);
      }
      x=basins[i][0];z=basins[i][1];y=pad(over,x,z,5);
      zones.add(zone("cane-"+i,"SUGAR_CANE",new int[]{x-16,y-1,z-16,x+16,y+3,z+16}));
      for(int dz=-4;dz<=4;dz++){over.getBlockAt(x,y-1,z+dz).setType(Material.WATER,false);over.getBlockAt(x+1,y,z+dz).setType(Material.SUGAR_CANE,false);}
      getConfig().set("cane."+i,List.of(x+1,y,z));pin(over,x,z);
      x=towns[i][0]-20;z=towns[i][1]-20;y=pad(over,x,z,4);frame(over,x,y,z);portals[i]=new int[]{x,y,z};getConfig().set("portal."+i,List.of(x,y,z));
      frame(nether,-64+i*64,65,0);
      int fx=towns[i][0]+40,fz=towns[i][1],fy=pad(over,fx,fz,4);
      for(int dx=-3;dx<=3;dx++){
        over.getBlockAt(fx+dx,fy-1,fz).setType(Material.WATER,false);
        var soil=over.getBlockAt(fx+dx,fy-1,fz+1);soil.setType(Material.FARMLAND,false);var fd=(org.bukkit.block.data.type.Farmland)soil.getBlockData();fd.setMoisture(fd.getMaximumMoisture());soil.setBlockData(fd,false);
        over.getBlockAt(fx+dx,fy,fz+1).setType(Material.WHEAT,false);
      }
      var chestBlock=over.getBlockAt(fx,fy,fz+3);chestBlock.setType(Material.CHEST,false);
      ((Chest)chestBlock.getState()).getBlockInventory().addItem(new ItemStack(Material.WHEAT_SEEDS,24),new ItemStack(Material.CARROT,16),new ItemStack(Material.POTATO,16));
      farms.add(Map.of("name",List.of("Westhaven","Eastwatch","Southmeadow").get(i),"x",fx,"y",fy,"z",fz+1));pin(over,fx,fz);
      getConfig().set("farm."+i,List.of(fx,fy,fz+1));
    }
    getConfig().set("prepared",true);saveConfig();
    var layout=new LinkedHashMap<String,Object>();layout.put("overworld",over.getUID().toString());layout.put("nether",nether.getUID().toString());layout.put("zones",zones);layout.put("seeds",seedSlots);layout.put("portals",portals);layout.put("farms",farms);
    Files.writeString(Path.of("verification/layout.json"),new GsonBuilder().setPrettyPrinting().create().toJson(layout));
    // One asynchronous chunk generation at a time, before any activation or player access.
    pregen(-12,-12);
  }
  void pregen(int x,int z){
    if(x>11){getLogger().info("PASS Nether staging pregeneration complete: 576 chunks, four-chunk buffer outside border");return;}
    nether.getChunkAtAsync(x,z,true).whenComplete((c,e)->later(1,()->{
      if(e!=null)throw new IllegalStateException(e);
      if(z==11)pregen(x+1,-12);else pregen(x,z+1);
    }));
  }
  List<Cow> cows(){return Bukkit.getWorlds().stream().flatMap(w->w.getEntitiesByClass(Cow.class).stream()).filter(c->c.getPersistentDataContainer().has(marker)).toList();}
  void food(){
    for(int i=0;i<3;i++){
      var p=getConfig().getIntegerList("farm."+i);Block b=over.getBlockAt(p.get(0),p.get(1),p.get(2));b.setType(Material.WHEAT,false);
      for(int n=0;n<15 && ((org.bukkit.block.data.Ageable)b.getBlockData()).getAge()<7;n++)b.applyBoneMeal(BlockFace.UP);
      check(((org.bukkit.block.data.Ageable)b.getBlockData()).getAge()==7,"public wheat matures at settlement "+i);
      check(b.breakNaturally(),"public wheat harvest remains available "+i);
    }
    Chicken chicken=over.spawn(over.getSpawnLocation(),Chicken.class);check(chicken.isValid(),"non-selected food animals remain available");chicken.remove();
  }
  void cane(){
    var p=getConfig().getIntegerList("cane.0");Block root=over.getBlockAt(p.get(0),p.get(1),p.get(2));
    for(int dy=1;dy<5;dy++)root.getRelative(0,dy,0).setType(Material.AIR,false);
    for(int i=0;i<90;i++)root.randomTick();check(root.getRelative(0,1,0).getType()==Material.SUGAR_CANE,"native cane grows in registered basin");
    for(int i=0;i<90;i++)root.getRelative(0,1,0).randomTick();check(root.getRelative(0,2,0).getType()==Material.SUGAR_CANE,"native cane reaches three blocks");
    for(int i=0;i<90;i++)root.getRelative(0,2,0).randomTick();check(root.getRelative(0,3,0).isEmpty(),"cane height cap survives integrated policy");
    int x=towns[0][0]+45,z=towns[0][1]+8,y=pad(over,x,z,2);over.getBlockAt(x-1,y-1,z).setType(Material.WATER,false);
    Block outside=over.getBlockAt(x,y,z);outside.setType(Material.SUGAR_CANE,false);
    for(int i=0;i<90;i++)outside.randomTick();check(outside.getRelative(BlockFace.UP).isEmpty(),"native growth denied outside basin");outside.setType(Material.AIR,false);
  }
  void relocate(){
    var west=cows().stream().filter(c->c.getWorld()==over && Math.abs(c.getLocation().getX()-550)<20).limit(2).toList();
    check(west.size()==2,"two registered west adults ready for relocation");
    for(int i=0;i<west.size();i++){
      Cow c=west.get(i);getConfig().set("relocated."+i,c.getUniqueId().toString());
      check(c.teleport(new Location(over,portals[0][0]+.5,portals[0][1],portals[0][2]+.5)),"stage cow at registered western crossing");
    }saveConfig();
    later(80,()->{
      check(west.stream().allMatch(c->c.getWorld()==nether),"both managed cows cross native western portal");
      new BukkitRunnable(){int ticks=0;public void run(){
        ticks+=20;
        for(Cow c:west)if(c.getWorld()==nether)c.getPathfinder().moveTo(new Location(nether,.5,65,.5),1.4);
        if(west.stream().allMatch(c->c.getWorld()==over && c.getLocation().getX()>1400)){
          getLogger().info("PASS herd walked Nether relay and crossed native eastern portal; ticks="+ticks);cancel();
        }else if(ticks>2400){cancel();throw new IllegalStateException("herd relay timeout");}
      }}.runTaskTimer(this,1,20);
    });
  }
  void breed(){
    var parents=new ArrayList<Cow>();for(int i=0;i<2;i++)parents.add((Cow)Bukkit.getEntity(UUID.fromString(getConfig().getString("relocated."+i))));
    check(parents.stream().allMatch(c->c!=null && c.getWorld()==over && c.getLocation().getX()>1400 && c.hasAI()),"relocated registered adults reconciled outside origin habitat");
    var target=new Location(over,portals[1][0]+1.5,portals[1][1],portals[1][2]+3.5);
    for(Cow c:parents){check(c.teleport(target),"position relocated parent at receiver");c.setLoveModeTicks(600);}
    int before=cows().size();getConfig().set("before-breed",before);saveConfig();
    later(240,()->{check(cows().size()==before+1,"one native birth outside original habitats after relocation");getConfig().set("after-breed",cows().size());saveConfig();});
  }
  Inventory market(int i){
    var p=getConfig().getIntegerList("farm."+i);Block b=over.getBlockAt(p.get(0)+2,p.get(1),p.get(2)+2);
    b.setType(Material.BARREL,false);getConfig().set("market."+i,List.of(b.getX(),b.getY(),b.getZ()));
    return ((Barrel)b.getState()).getInventory();
  }
  int count(Inventory inv,Material material){return Arrays.stream(inv.getContents()).filter(Objects::nonNull).filter(i->i.getType()==material).mapToInt(ItemStack::getAmount).sum();}
  void harvest(Block block,Material material,Inventory recipient){
    var before=new HashSet<UUID>();for(Item i:over.getEntitiesByClass(Item.class))before.add(i.getUniqueId());
    check(block.breakNaturally(new ItemStack(Material.NETHERITE_PICKAXE)),"native harvest "+block.getLocation());
    for(Item i:over.getEntitiesByClass(Item.class))if(!before.contains(i.getUniqueId()) && i.getItemStack().getType()==material){
      check(recipient.addItem(i.getItemStack().clone()).isEmpty(),"harvest deposited without overflow");i.remove();
    }
  }
  void move(Inventory from,Inventory to,Material material,int amount){
    check(count(from,material)>=amount,"barter source has "+amount+" "+material);
    check(from.removeItem(new ItemStack(material,amount)).isEmpty(),"barter debit complete");
    check(to.addItem(new ItemStack(material,amount)).isEmpty(),"barter credit complete");
  }
  void exchanges(int x,int y,int z){
    check(!getConfig().getBoolean("exchanged"),"scripted exchanges run once");
    Inventory west=market(0),east=market(1),south=market(2);
    Block ore=over.getBlockAt(x,y,z);check(ore.getType()==Material.DIAMOND_ORE || ore.getType()==Material.DEEPSLATE_DIAMOND_ORE,"finite deposit present before extraction");
    harvest(ore,Material.DIAMOND,west);check(ore.isEmpty(),"finite ore visibly depleted");
    var p=getConfig().getIntegerList("cane.0");
    for(int dz=-2;dz<=2;dz++){
      Block base=over.getBlockAt(p.get(0),p.get(1),p.get(2)+dz);
      for(int n=0;n<100;n++)base.randomTick();
      if(base.getRelative(BlockFace.UP).getType()==Material.SUGAR_CANE)harvest(base.getRelative(BlockFace.UP),Material.SUGAR_CANE,east);
    }
    p=getConfig().getIntegerList("farm.2");Block wheat=over.getBlockAt(p.get(0)+1,p.get(1),p.get(2));
    for(int n=0;n<15 && ((org.bukkit.block.data.Ageable)wheat.getBlockData()).getAge()<7;n++)wheat.applyBoneMeal(BlockFace.UP);
    harvest(wheat,Material.WHEAT,south);
    int diamonds=count(west,Material.DIAMOND),cane=count(east,Material.SUGAR_CANE),food=count(south,Material.WHEAT);
    check(diamonds==1 && cane>=4 && food>=1,"all barter stock comes from native harvests");
    move(west,east,Material.DIAMOND,1);move(east,west,Material.SUGAR_CANE,2);
    move(south,east,Material.WHEAT,1);move(east,south,Material.SUGAR_CANE,2);
    check(count(east,Material.DIAMOND)==diamonds && count(east,Material.SUGAR_CANE)+count(west,Material.SUGAR_CANE)+count(south,Material.SUGAR_CANE)==cane && count(east,Material.WHEAT)==food,"two SCRIPTED barter exchanges conserve harvested resources");
    getConfig().set("exchanged",true);getConfig().set("depleted",List.of(x,y,z));getConfig().set("cane-total",cane);saveConfig();
  }
  void restartCheck(){
    check(Bukkit.getWorlds().size()==2 && over.getWorldBorder().getSize()==2048 && nether.getWorldBorder().getSize()==256,"borders and End closure survive restart");
    var p=getConfig().getIntegerList("depleted");check(over.getBlockAt(p.get(0),p.get(1),p.get(2)).isEmpty(),"harvested ore remains absent after restart");
    for(int i=0;i<3;i++){
      p=getConfig().getIntegerList("market."+i);Inventory inv=((Barrel)over.getBlockAt(p.get(0),p.get(1),p.get(2)).getState()).getInventory();
      check(i==1?count(inv,Material.DIAMOND)==1 && count(inv,Material.WHEAT)==1:count(inv,Material.SUGAR_CANE)==2,"scripted recipient stock persists "+i);
    }
  }
  Object make(String name,Object...args)throws Exception {
    for(var ctor:Class.forName("io.bennyc.civilizations."+name).getDeclaredConstructors()){
      var types=ctor.getParameterTypes();
      if(types.length==args.length+1 && types[types.length-1].getSimpleName().equals("DefaultConstructorMarker")){
        ctor.setAccessible(true);return ctor.newInstance(Arrays.copyOf(args,args.length+1));
      }
    }throw new IllegalStateException(name);
  }
  void access()throws Exception {
    var plugin=Bukkit.getPluginManager().getPlugin("Civilizations");var f=plugin.getClass().getDeclaredField("runtime");f.setAccessible(true);
    var runtime=(io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime)f.get(plugin);
    var ready=(io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState.Ready)runtime.getState();
    var policy=ready.getActiveSeason().getProtection();
    for(int who=0;who<3;who++)for(int where=0;where<3;where++){
      var actor=UUID.fromString("00000000-0000-0000-0000-00000000006"+(who+1));
      var p=getConfig().getIntegerList("farm."+where);
      var req=(io.bennyc.civilizations.application.protection.PlayerProtectionRequest)make("application.protection.PlayerProtectionRequest",actor,io.bennyc.civilizations.application.protection.PlayerProtectionAction.BLOCK_BREAK,make("domain.claim.BlockPosition2D","minecraft:overworld",p.get(0),p.get(2)),null,false,io.bennyc.civilizations.application.protection.ConflictAuthorization.None.INSTANCE);
      check(policy.decidePlayerAction(req) instanceof io.bennyc.civilizations.application.protection.ProtectionDecision.Allowed,"public farm access: civilization "+who+" farm "+where);
      req=(io.bennyc.civilizations.application.protection.PlayerProtectionRequest)make("application.protection.PlayerProtectionRequest",actor,io.bennyc.civilizations.application.protection.PlayerProtectionAction.BLOCK_BREAK,make("domain.claim.BlockPosition2D","minecraft:overworld",towns[where][0],towns[where][1]),null,false,io.bennyc.civilizations.application.protection.ConflictAuthorization.None.INSTANCE);
      check((policy.decidePlayerAction(req) instanceof io.bennyc.civilizations.application.protection.ProtectionDecision.Allowed)==(who==where),"town claims remain owner-only "+who+"/"+where);
    }
  }
  UUID portalSubject;int deniedPortalEvents;
  @EventHandler(priority=EventPriority.MONITOR)
  public void observe(org.bukkit.event.entity.EntityPortalEvent e){if(e.getEntity().getUniqueId().equals(portalSubject) && e.isCancelled())deniedPortalEvents++;}
  void unregistered(){
    frame(over,100,100,100);var bird=over.spawn(new Location(over,100.5,100,100.5),Chicken.class);bird.setAI(false);portalSubject=bird.getUniqueId();deniedPortalEvents=0;
    later(80,()->{check(deniedPortalEvents>0 && bird.getWorld()==over,"native unregistered portal contact denied");bird.remove();
      for(int x=100;x<=101;x++)for(int y=100;y<=102;y++)over.getBlockAt(x,y,100).setType(Material.AIR,false);
    });
  }
  public boolean onCommand(CommandSender s,Command c,String label,String[] a){if(!(s instanceof ConsoleCommandSender))return true;try{
    switch(a[0]){
      case "prepare" -> prepare();case "access" -> access();case "unregistered" -> unregistered();case "exchange" -> exchanges(Integer.parseInt(a[1]),Integer.parseInt(a[2]),Integer.parseInt(a[3]));case "restart" -> restartCheck();case "food" -> food();case "cane" -> cane();case "relocate" -> relocate();case "breed" -> breed();
      case "status" -> {getLogger().info("Managed cows="+cows().size());for(Cow cow:cows())getLogger().info("COW "+cow.getUniqueId()+" logical="+cow.getPersistentDataContainer().get(marker,PersistentDataType.STRING)+" "+cow.getLocation()+" AI="+cow.hasAI()+" age="+cow.getAge());}
    }
  }catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"S5 probe failed",e);}return true;}
}
