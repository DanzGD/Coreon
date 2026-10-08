package id.anarchy.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public class Vault {
 private final AnarchyCore p; private final Storage s;
 Vault(AnarchyCore p,Storage s){this.p=p;this.s=s;}
 void open(Player pl){Inventory inv=pl.getServer().createInventory(null,54,Component.text("Anarchy Vault",NamedTextColor.DARK_AQUA));List<ItemStack> items=s.vaults.getOrDefault(pl.getUniqueId(),new ArrayList<>());for(int i=0;i<Math.min(54,items.size());i++)if(items.get(i)!=null)inv.setItem(i,items.get(i));pl.openInventory(inv);}
 void save(Player pl,Inventory inv){List<ItemStack> items=new ArrayList<>();for(ItemStack x:inv.getContents())if(x!=null)items.add(x);s.vaults.put(pl.getUniqueId(),items);s.dirty();}
}