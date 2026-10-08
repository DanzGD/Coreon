package id.anarchy.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import java.util.concurrent.ThreadLocalRandom;

public class RTP {
 private final AnarchyCore p;private final Storage s;
 RTP(AnarchyCore p,Storage s){this.p=p;this.s=s;}
 void rtp(Player pl){long now=System.currentTimeMillis(),cd=p.getConfig().getLong("rtp.cooldown-seconds",300)*1000L,last=s.rtpCooldown.getOrDefault(pl.getUniqueId(),0L);if(now-last<cd){long x=(cd-(now-last)+999)/1000;pl.sendMessage(Component.text("RTP cooldown: "+x+" detik.",NamedTextColor.RED));return;}int min=p.getConfig().getInt("rtp.min-radius",1000),max=p.getConfig().getInt("rtp.max-radius",10000);Location base=pl.getLocation();for(int i=0;i<12;i++){double a=ThreadLocalRandom.current().nextDouble(0,Math.PI*2),r=ThreadLocalRandom.current().nextDouble(min,max);int x=(int)(base.getX()+Math.cos(a)*r),z=(int)(base.getZ()+Math.sin(a)*r);World w=pl.getWorld();int y=w.getHighestBlockYAt(x,z)+1;if(y<=w.getMinHeight()||y>=w.getMaxHeight()-2)continue;Location to=new Location(w,x+.5,y,z+.5,pl.getYaw(),pl.getPitch());if(w.getBlockAt(x,y-1,z).isPassable()||w.getBlockAt(x,y,z).isLiquid())continue;s.rtpCooldown.put(pl.getUniqueId(),now);s.dirty();pl.teleport(to);pl.sendMessage(Component.text("RTP -> "+x+" "+y+" "+z,NamedTextColor.GREEN));return;}pl.sendMessage(Component.text("RTP gagal menemukan lokasi aman. Coba lagi.",NamedTextColor.RED));}
}