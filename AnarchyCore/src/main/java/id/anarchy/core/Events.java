package id.anarchy.core;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class Events implements Listener {
    private final AnarchyCore plugin;
    Events(AnarchyCore plugin) { this.plugin = plugin; }

    @EventHandler
    public void chat(AsyncChatEvent e) {
        if (plugin.getConfig().getBoolean("discord.events.chat", true)) {
            e.getPlayer();
            plugin.bridge().event(e.getPlayer().getName() + ": " + Component.text().append(e.message()).build().toString());
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        if (plugin.getConfig().getBoolean("hide-join-quit", false)) e.joinMessage(null);
        if (plugin.getConfig().getBoolean("discord.events.join-quit", true))
            plugin.bridge().event("🟢 " + e.getPlayer().getName() + " joined");
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        if (plugin.getConfig().getBoolean("hide-join-quit", false)) e.quitMessage(null);
        if (plugin.getConfig().getBoolean("discord.events.join-quit", true))
            plugin.bridge().event("🔴 " + e.getPlayer().getName() + " left");
    }

    @EventHandler
    public void death(PlayerDeathEvent e) {
        if (plugin.getConfig().getBoolean("discord.events.death", true))
            plugin.bridge().event("☠ " + e.getDeathMessage());
    }

    @EventHandler
    public void advancement(PlayerAdvancementDoneEvent e) {
        if (plugin.getConfig().getBoolean("discord.events.advancement", false))
            plugin.bridge().event("🏆 " + e.getPlayer().getName() + " mendapat advancement");
    }
}
