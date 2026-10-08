package id.anarchy.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public class Market {
    private record Tier(String name,double price,double depth){}
    private final JavaPlugin plugin; private final Storage st; private final Map<Material,Tier> tiers=new HashMap<>();
    private double halfLifeMs,minMult,amplitude,periodMs,enchantBonus,enchantCap,minDurability;
    Market(JavaPlugin plugin,Storage st){this.plugin=plugin;this.st=st;reload();}
    void reload(){
        var c=plugin.getConfig(); halfLifeMs=Math.max(.01,c.getDouble("market.half-life-hours",6))*3600000;
        minMult=c.getDouble("market.min-multiplier",.2); amplitude=c.getDouble("market.trend-amplitude",.12);
        periodMs=Math.max(.01,c.getDouble("market.trend-period-hours",24))*3600000;
        enchantBonus=c.getDouble("market.enchant-bonus-per-level",.08); enchantCap=c.getDouble("market.enchant-bonus-cap",1.5);
        minDurability=c.getDouble("market.min-durability-factor",.05); tiers.clear();
        ConfigurationSection ts=c.getConfigurationSection("tiers");
        if(ts!=null) for(String n:ts.getKeys(false)){var t=ts.getConfigurationSection(n);if(t==null)continue;double p=t.getDouble("price",1),d=Math.max(1,t.getDouble("depth",32));for(String id:t.getStringList("items")){Material m=Material.matchMaterial(id);if(m!=null)tiers.put(m,new Tier(n,p,d));}}
        ConfigurationSection ov=c.getConfigurationSection("overrides");
        if(ov!=null) for(String id:ov.getKeys(false)){Material m=Material.matchMaterial(id);if(m==null)continue;Tier old=tiers.get(m);tiers.put(m,new Tier(old==null?"CUSTOM":old.name(),ov.getDouble(id),old==null?c.getDouble("market.default-depth",32):old.depth()));}
        if(c.getBoolean("market.auto-price.enabled",true)){double d=Math.max(1,c.getDouble("market.auto-price.depth",64));for(Material m:Material.values())if(!m.isAir()&&m.isItem()&&!tiers.containsKey(m))tiers.put(m,autoTier(m,d));plugin.getLogger().info("Auto pricing aktif: "+tiers.size()+" item memiliki harga jual.");}
    }
    private Tier autoTier(Material m,double d){String n=m.name();double p=2;String t="AUTO";
        if(n.endsWith("_LOG")||n.endsWith("_PLANKS")||n.endsWith("_LEAVES")||n.endsWith("_SAPLING")||Set.of("DIRT","COBBLESTONE","GRAVEL","SAND","CLAY_BALL").contains(n)){p=1;t="COMMON";}
        else if(n.contains("ORE")||n.endsWith("_INGOT")||n.endsWith("_NUGGET")||Set.of("COAL","RAW_IRON","RAW_GOLD","RAW_COPPER").contains(n)){p=8;t="UNCOMMON";}
        else if(n.contains("DIAMOND")||n.contains("EMERALD")||n.contains("AMETHYST")||n.contains("QUARTZ")||n.contains("OBSIDIAN")){p=35;t="RARE";}
        else if(n.contains("NETHERITE")||n.contains("ELYTRA")||n.contains("TOTEM")||n.contains("DRAGON")||n.contains("NETHER_STAR")||n.contains("BEACON")){p=250;t="EPIC";}
        else if(n.endsWith("_SWORD")||n.endsWith("_PICKAXE")||n.endsWith("_AXE")||n.endsWith("_SHOVEL")||n.endsWith("_HOE")||n.endsWith("_HELMET")||n.endsWith("_CHESTPLATE")||n.endsWith("_LEGGINGS")||n.endsWith("_BOOTS")||Set.of("BOW","CROSSBOW","TRIDENT").contains(n)){p=12;t="UNCOMMON";}
        else if(n.contains("APPLE")||n.endsWith("_STEAK")||n.endsWith("_CHOPS")||n.endsWith("_MEAT")||Set.of("BREAD","GOLDEN_CARROT").contains(n)){p=5;t="COMMON";}
        else if(n.contains("SPAWN_EGG")||n.contains("FIREWORK")||n.contains("POTION")||n.contains("BOOK")||n.contains("MUSIC_DISC")){p=20;t="UNCOMMON";}
        if(m.getMaxStackSize()==1&&p<10)p*=2; return new Tier(t,p,d);
    }
    private double sat(Material m,long now){double[] a=st.market.get(m);return a==null?0:a[0]*Math.pow(.5,Math.max(0,now-a[1])/halfLifeMs);}
    private double trend(Material m,long now){double phase=(m.name().hashCode()&65535)/65535.0*2*Math.PI;return 1+amplitude*Math.sin(now/periodMs*2*Math.PI+phase);}
    private double factor(ItemStack s){double f=1;ItemMeta meta=s.getItemMeta();if(meta==null)return f;int max=s.getType().getMaxDurability();if(max>0&&meta instanceof Damageable d)f*=Math.max(minDurability,1-d.getDamage()/(double)max);int lv=0;for(int x:meta.getEnchants().values())lv+=x;if(meta instanceof EnchantmentStorageMeta e)for(int x:e.getStoredEnchants().values())lv+=x;return f*(1+Math.min(enchantCap,lv*enchantBonus));}
    private double quote(ItemStack s,long now){Tier t=tiers.get(s.getType());if(t==null)return 0;int n=s.getAmount();double r=Math.exp(-1/t.depth()),sum=Math.exp(-sat(s.getType(),now)/t.depth())*(1-Math.pow(r,n))/(1-r);return t.price()*trend(s.getType(),now)*factor(s)*Math.max(sum,n*minMult);}
    private void apply(ItemStack s,long now){st.market.put(s.getType(),new double[]{sat(s.getType(),now)+s.getAmount(),now});st.dirty();}
    private double demand(Material m,long now){Tier t=tiers.get(m);return Math.max(minMult,Math.exp(-sat(m,now)/t.depth()));}
    void sell(Player p,String[] a){String s=a.length==0?"gui":a[0].toLowerCase(Locale.ROOT);switch(s){case"gui"->openSellGui(p);case"hand"->sellHand(p);case"all"->sellAll(p);case"price"->price(p);case"list"->list(p);default->say(p,"Gunakan: /sell [gui|hand|all|price|list]",NamedTextColor.RED);}}
    private static final class SellHolder implements org.bukkit.inventory.InventoryHolder{
        private org.bukkit.inventory.Inventory inventory; private boolean completed;
        void setInventory(org.bukkit.inventory.Inventory inventory){this.inventory=inventory;}
        public org.bukkit.inventory.Inventory getInventory(){return inventory;}
        void complete(){completed=true;} boolean completed(){return completed;}
    }
    private void openSellGui(Player p){
        SellHolder h=new SellHolder();org.bukkit.inventory.Inventory inv=Bukkit.createInventory(h,54,Component.text("Sell Items",NamedTextColor.GREEN));h.setInventory(inv);
        for(int i=45;i<54;i++)inv.setItem(i,button(Material.GRAY_STAINED_GLASS_PANE," "));
        inv.setItem(49,button(Material.EMERALD_BLOCK,"SELL ITEMS","Klik untuk menjual item di GUI."));
        inv.setItem(53,button(Material.REDSTONE_BLOCK,"CANCEL","Tutup tanpa menjual."));
        p.openInventory(inv);
    }
    private ItemStack button(Material m,String name,String... lore){ItemStack x=new ItemStack(m);ItemMeta meta=x.getItemMeta();meta.displayName(Component.text(name,NamedTextColor.WHITE));if(lore.length>0)meta.lore(Arrays.stream(lore).map(v->Component.text(v,NamedTextColor.GRAY)).toList());x.setItemMeta(meta);return x;}
    @org.bukkit.event.EventHandler public void onSellClick(org.bukkit.event.inventory.InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;org.bukkit.inventory.Inventory top=e.getView().getTopInventory();if(!(top.getHolder() instanceof SellHolder h))return;int raw=e.getRawSlot();
        if(raw>=0&&raw<45)return;
        if(raw==49){e.setCancelled(true);finishSellGui(p,h,top);return;}
        if(raw==53){e.setCancelled(true);p.closeInventory();return;}
        if(e.isShiftClick()&&e.getClickedInventory()==e.getView().getBottomInventory()){e.setCancelled(true);ItemStack s=e.getCurrentItem();if(s!=null&&!s.getType().isAir()&&addToSellGui(top,s.clone()))e.getClickedInventory().setItem(e.getSlot(),null);return;}
        if(raw>=45)e.setCancelled(true);
    }
    @org.bukkit.event.EventHandler public void onSellDrag(org.bukkit.event.inventory.InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof SellHolder)for(int slot:e.getRawSlots())if(slot<45){e.setCancelled(true);return;}}
    @org.bukkit.event.EventHandler public void onSellClose(org.bukkit.event.inventory.InventoryCloseEvent e){if(!(e.getPlayer() instanceof Player p)||!(e.getInventory().getHolder() instanceof SellHolder h)||h.completed())return;returnGuiItems(p,e.getInventory());}
    private boolean addToSellGui(org.bukkit.inventory.Inventory inv,ItemStack s){for(int i=0;i<45;i++)if(inv.getItem(i)==null||inv.getItem(i).getType().isAir()){inv.setItem(i,s);return true;}return false;}
    private void finishSellGui(Player p,SellHolder h,org.bukkit.inventory.Inventory inv){
        long now=System.currentTimeMillis();double total=0;int count=0;
        for(int i=0;i<45;i++){ItemStack s=inv.getItem(i);if(s==null||s.getType().isAir())continue;if(!tiers.containsKey(s.getType()))continue;total+=quote(s,now);count+=s.getAmount();apply(s,now);inv.setItem(i,null);}
        if(count==0){say(p,"Tidak ada item yang bisa dijual.",NamedTextColor.RED);return;}h.complete();settle(p,total,count);p.closeInventory();
    }
    private void returnGuiItems(Player p,org.bukkit.inventory.Inventory inv){for(int i=0;i<45;i++){ItemStack s=inv.getItem(i);if(s==null||s.getType().isAir())continue;Map<Integer,ItemStack> left=p.getInventory().addItem(s);for(ItemStack x:left.values())p.getWorld().dropItemNaturally(p.getLocation(),x);inv.setItem(i,null);}}
    private void sellHand(Player p){ItemStack s=p.getInventory().getItemInMainHand();if(s.getType().isAir()||!tiers.containsKey(s.getType())){say(p,"Item ini tidak bisa dijual. Lihat /sell list.",NamedTextColor.RED);return;}long n=System.currentTimeMillis();double v=quote(s,n);int count=s.getAmount();apply(s,n);p.getInventory().setItemInMainHand(null);settle(p,v,count);}
    private void sellAll(Player p){long n=System.currentTimeMillis();var inv=p.getInventory();var c=inv.getStorageContents();double total=0;int count=0;for(int i=0;i<c.length;i++){ItemStack s=c[i];if(s==null||!tiers.containsKey(s.getType()))continue;total+=quote(s,n);apply(s,n);count+=s.getAmount();c[i]=null;}if(count==0){say(p,"Tidak ada item yang bisa dijual di inventory.",NamedTextColor.RED);return;}inv.setStorageContents(c);settle(p,total,count);}
    private void settle(Player p,double value,int count){long cents=Math.round(value*100),total=st.money.merge(p.getUniqueId(),cents,Long::sum);st.dirty();say(p,"Terjual "+count+" item, dapat "+fmt(cents)+". Saldo: "+fmt(total)+".",NamedTextColor.GREEN);}
    private void price(Player p){ItemStack s=p.getInventory().getItemInMainHand();Tier t=tiers.get(s.getType());if(s.getType().isAir()||t==null){say(p,"Item ini tidak bisa dijual.",NamedTextColor.RED);return;}long n=System.currentTimeMillis();ItemStack one=s.clone();one.setAmount(1);say(p,"Rarity: "+t.name()+" | harga dasar: "+fmt(Math.round(t.price()*100)),NamedTextColor.GOLD);say(p,"Per item sekarang: "+fmt(Math.round(quote(one,n)*100))+" (tren x"+String.format(Locale.ROOT,"%.2f",trend(s.getType(),n))+", permintaan "+Math.round(demand(s.getType(),n)*100)+"%)",NamedTextColor.YELLOW);if(s.getAmount()>1)say(p,"Satu stack ("+s.getAmount()+"): "+fmt(Math.round(quote(s,n)*100)),NamedTextColor.YELLOW);}
    private void list(Player p){long n=System.currentTimeMillis();List<Map.Entry<Material,Double>> rows=new ArrayList<>();for(Material m:tiers.keySet())rows.add(Map.entry(m,tiers.get(m).price()*trend(m,n)*demand(m,n)));rows.sort((a,b)->Double.compare(b.getValue(),a.getValue()));say(p,"== Harga jual sekarang (per item) ==",NamedTextColor.GOLD);for(int i=0;i<Math.min(12,rows.size());i++){Material m=rows.get(i).getKey();say(p,m.name().toLowerCase(Locale.ROOT).replace('_',' ')+": "+fmt(Math.round(rows.get(i).getValue()*100))+" ("+tiers.get(m).name()+", permintaan "+Math.round(demand(m,n)*100)+"%)",NamedTextColor.YELLOW);}}
    void info(org.bukkit.command.CommandSender s,String[] a){long now=System.currentTimeMillis();say(s,"== SUPPLY & DEMAND MARKET ==",NamedTextColor.GOLD);say(s,"Supply tinggi = harga turun | supply rendah = harga naik.",NamedTextColor.GRAY);List<Map.Entry<Material,Double>> rows=new ArrayList<>();for(Material x:tiers.keySet())rows.add(Map.entry(x,demand(x,now)));rows.sort((a,b)->Double.compare(a.getValue(),b.getValue()));for(int i=0;i<Math.min(10,rows.size());i++){Material x=rows.get(i).getKey();say(s,x.name().toLowerCase(Locale.ROOT)+" | demand "+Math.round(rows.get(i).getValue()*100)+"% | "+fmt(Math.round(tiers.get(x).price()*trend(x,now)*rows.get(i).getValue()*100)),NamedTextColor.YELLOW);}}\n    void balance(Player p){say(p,"Saldo: "+fmt(st.money.getOrDefault(p.getUniqueId(),0L)),NamedTextColor.GOLD);}
    void pay(Player p,String[] a){if(a.length<2){say(p,"Gunakan: /pay <pemain> <jumlah>",NamedTextColor.RED);return;}Player t=Bukkit.getPlayerExact(a[0]);if(t==null){say(p,"Pemain harus sedang online.",NamedTextColor.RED);return;}if(t.equals(p)){say(p,"Tidak bisa membayar diri sendiri.",NamedTextColor.RED);return;}double x;try{x=Double.parseDouble(a[1]);}catch(Exception e){say(p,"Jumlah tidak valid.",NamedTextColor.RED);return;}if(!Double.isFinite(x)||x<.01||x>1e9){say(p,"Jumlah tidak valid.",NamedTextColor.RED);return;}long c=Math.round(x*100),bal=st.money.getOrDefault(p.getUniqueId(),0L);if(c>bal){say(p,"Saldo tidak cukup ("+fmt(bal)+").",NamedTextColor.RED);return;}st.money.put(p.getUniqueId(),bal-c);st.money.merge(t.getUniqueId(),c,Long::sum);st.dirty();say(p,"Kamu mengirim "+fmt(c)+" ke "+t.getName()+".",NamedTextColor.GREEN);say(t,p.getName()+" mengirim "+fmt(c)+" kepadamu.",NamedTextColor.GREEN);}
    void baltop(CommandSender s){List<Map.Entry<UUID,Long>> rows=new ArrayList<>(st.money.entrySet());rows.sort((a,b)->Long.compare(b.getValue(),a.getValue()));say(s,"== Top 10 Terkaya ==",NamedTextColor.GOLD);for(int i=0;i<Math.min(10,rows.size());i++){OfflinePlayer op=Bukkit.getOfflinePlayer(rows.get(i).getKey());say(s,(i+1)+". "+(op.getName()==null?"?":op.getName())+" - "+fmt(rows.get(i).getValue()),NamedTextColor.YELLOW);}}
    void eco(CommandSender s,String[] a){if(a.length<3){say(s,"Gunakan: /eco <give|take|set> <pemain> <jumlah>",NamedTextColor.RED);return;}OfflinePlayer t=Bukkit.getOfflinePlayerIfCached(a[1]);if(t==null){say(s,"Pemain tidak ditemukan.",NamedTextColor.RED);return;}double x;try{x=Double.parseDouble(a[2]);}catch(Exception e){say(s,"Jumlah tidak valid.",NamedTextColor.RED);return;}if(!Double.isFinite(x)||x<0||x>1e9){say(s,"Jumlah tidak valid.",NamedTextColor.RED);return;}long c=Math.round(x*100),bal=st.money.getOrDefault(t.getUniqueId(),0L),r;switch(a[0].toLowerCase(Locale.ROOT)){case"give"->r=bal+c;case"take"->r=Math.max(0,bal-c);case"set"->r=c;default->{say(s,"Gunakan: /eco <give|take|set> <pemain> <jumlah>",NamedTextColor.RED);return;}}if(r<=0)st.money.remove(t.getUniqueId());else st.money.put(t.getUniqueId(),r);st.dirty();say(s,"Saldo "+t.getName()+" sekarang "+fmt(r)+".",NamedTextColor.GREEN);}
    String fmt(long cents){return plugin.getConfig().getString("currency-symbol","$")+String.format(Locale.ROOT,"%,.2f",cents/100.0);}
    private static void say(CommandSender s,String x,NamedTextColor c){s.sendMessage(Component.text(x,c));}
}
