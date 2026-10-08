package id.anarchy.core;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class Events implements Listener {
    private final AnarchyCore plugin;
    Events(AnarchyCore plugin){this.plugin=plugin;}
    @EventHandler public void chat(AsyncChatEvent e){if(plugin.getConfig().getBoolean("discord.events.chat",true))plugin.bridge().chat(e.getPlayer().getName(),e.message().toString());}
    @EventHandler public void join(PlayerJoinEvent e){if(plugin.getConfig().getBoolean("hide-join-quit",false))e.joinMessage(null);if(plugin.getConfig().getBoolean("discord.events.join-quit",true))plugin.bridge().player("Player joined",e.getPlayer().getName(),0x57F287);}
    @EventHandler public void quit(PlayerQuitEvent e){if(plugin.getConfig().getBoolean("hide-join-quit",false))e.quitMessage(null);if(plugin.getConfig().getBoolean("discord.events.join-quit",true))plugin.bridge().player("Player left",e.getPlayer().getName(),0xED4245);}
    @EventHandler public void death(PlayerDeathEvent e){if(plugin.getConfig().getBoolean("discord.events.death",true))plugin.bridge().event("Player death",e.getDeathMessage()==null?e.getEntity().getName()+" died.":e.getDeathMessage(),0xED4245);}
    @EventHandler public void advancement(PlayerAdvancementDoneEvent e){if(plugin.getConfig().getBoolean("discord.events.advancement",false))plugin.bridge().event("Advancement unlocked",e.getPlayer().getName()+" unlocked an advancement.",0xFEE75C);}
}
