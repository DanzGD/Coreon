package id.anarchy.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public class Security implements Listener {
 private final AnarchyCore p;private final Map<UUID,Integer> ore=new HashMap<>(),alerts=new HashMap<>();private final Map<UUID,Long> last=new HashMap<>();
 Security(AnarchyCore p){this.p=p;}
 @EventHandler(ignoreCancelled=true) public void ore(BlockBreakEvent e){if(!p.getConfig().getBoolean("anticheat.xray.enabled",true))return;Material m=e.getBlock().getType();if(!isOre(m))return;Player x=e.getPlayer();int n=ore.merge(x.getUniqueId(),1,Integer::sum);if(n< p.getConfig().getInt("anticheat.xray.min-ores",25))return;double ratio=n/(double)Math.max(1,x.getStatistic(org.bukkit.Statistic.MINE_BLOCK,m));if(ratio>0.8){int a=alerts.merge(x.getUniqueId(),1,Integer::sum);if(a%5==0&&x.hasPermission("anarchycore.admin"))x.sendMessage(Component.text("Xray heuristic alert: "+x.getName()+" ore pattern suspicious ("+a+").",NamedTextColor.RED));}}
 @EventHandler(ignoreCancelled=true) public void move(PlayerMoveEvent e){if(!p.getConfig().getBoolean("anticheat.freecam.enabled",true))return;Player x=e.getPlayer();if(x.getGameMode()!=GameMode.SURVIVAL)return;Location a=e.getFrom(),b=e.getTo();if(b==null)return;double dy=Math.abs(b.getY()-a.getY());if(dy>p.getConfig().getDouble("anticheat.freecam.max-normal-y-move",6.0)&&!x.isGliding()){long now=System.currentTimeMillis(),old=last.getOrDefault(x.getUniqueId(),0L);if(now-old>3000){last.put(x.getUniqueId(),now);if(x.hasPermission("anarchycore.admin"))x.sendMessage(Component.text("Movement heuristic triggered for "+x.getName(),NamedTextColor.RED));}}}
 private boolean isOre(Material m){String n=m.name();return n.endsWith("_ORE")||Set.of("ANCIENT_DEBRIS","RAW_IRON_BLOCK","RAW_GOLD_BLOCK","RAW_COPPER_BLOCK").contains(n);}
}