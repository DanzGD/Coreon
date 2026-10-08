package id.anarchy.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class Storage {
    private final JavaPlugin plugin; private final File file; private boolean dirty;
    final Map<UUID,Long> money=new HashMap<>(), bounty=new HashMap<>();
    final Map<Material,double[]> market=new HashMap<>();
    final Map<UUID,List<org.bukkit.inventory.ItemStack>> vaults=new HashMap<>();
    final Map<UUID,Long> rtpCooldown=new HashMap<>();
    final Map<UUID,Map<String,Long>> achievements=new HashMap<>();
    Storage(JavaPlugin p){plugin=p;p.getDataFolder().mkdirs();file=new File(p.getDataFolder(),"data.yml");load();}
    private void load(){YamlConfiguration y=YamlConfiguration.loadConfiguration(file);
      loadLongs(y.getConfigurationSection("money"),money,false); loadLongs(y.getConfigurationSection("bounty"),bounty,true);
      ConfigurationSection m=y.getConfigurationSection("market"); if(m!=null)for(String k:m.getKeys(false)){Material mat=Material.matchMaterial(k);if(mat!=null)market.put(mat,new double[]{m.getDouble(k+".s"),m.getLong(k+".t")});}
      ConfigurationSection r=y.getConfigurationSection("rtpCooldown");if(r!=null)for(String k:r.getKeys(false))try{rtpCooldown.put(UUID.fromString(k),r.getLong(k));}catch(Exception ignored){}
      ConfigurationSection a=y.getConfigurationSection("achievements");if(a!=null)for(String u:a.getKeys(false))try{UUID id=UUID.fromString(u);Map<String,Long> z=new HashMap<>();ConfigurationSection q=a.getConfigurationSection(u);if(q!=null)for(String k:q.getKeys(false))z.put(k,q.getLong(k));achievements.put(id,z);}catch(Exception ignored){}
      ConfigurationSection v=y.getConfigurationSection("vault");if(v!=null)for(String u:v.getKeys(false))try{UUID id=UUID.fromString(u);List<org.bukkit.inventory.ItemStack> list=new ArrayList<>();for(String k:v.getConfigurationSection(u).getKeys(false)){Object o=v.get(u+"."+k);if(o instanceof org.bukkit.inventory.ItemStack s)list.add(s);}vaults.put(id,list);}catch(Exception ignored){}
    }
    private void loadLongs(ConfigurationSection s,Map<UUID,Long> out,boolean positive){if(s==null)return;for(String k:s.getKeys(false))try{long v=s.getLong(k);if((positive&&v>0)||(!positive&&v!=0))out.put(UUID.fromString(k),v);}catch(Exception ignored){}}
    void dirty(){dirty=true;}
    private String build(){YamlConfiguration y=new YamlConfiguration();money.forEach((u,v)->y.set("money."+u,v));bounty.forEach((u,v)->y.set("bounty."+u,v));rtpCooldown.forEach((u,v)->y.set("rtpCooldown."+u,v));achievements.forEach((u,m)->m.forEach((k,v)->y.set("achievements."+u+"."+k,v)));market.forEach((m,a)->{y.set("market."+m.name()+".s",a[0]);y.set("market."+m.name()+".t",(long)a[1]);});return y.saveToString();}
    void saveAsync(){if(!dirty)return;dirty=false;String d=build();Bukkit.getScheduler().runTaskAsynchronously(plugin,()->write(d));}
    void saveNow(){dirty=false;write(build());}
    private synchronized void write(String d){try{Path t=file.toPath().resolveSibling("data.yml.tmp");Files.writeString(t,d);Files.move(t,file.toPath(),StandardCopyOption.REPLACE_EXISTING);}catch(IOException e){plugin.getLogger().warning("Gagal menyimpan data.yml: "+e.getMessage());}}
}