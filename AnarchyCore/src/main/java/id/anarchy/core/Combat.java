package id.anarchy.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Combat implements Listener {
    private static final String BYPASS = "anarchycore.combat.bypass";
    private final AnarchyCore plugin;
    private final Bounty bounty;
    private final Map<UUID, Long> tagUntil = new HashMap<>();
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();
    private final Map<UUID, UUID> pendingLog = new HashMap<>();
    private final Set<String> blocked = new HashSet<>();
    private BukkitTask task;
    private boolean enabled;
    private boolean punishLogout;
    private boolean noElytra;
    private long tagMs;

    Combat(AnarchyCore plugin, Bounty bounty) {
        this.plugin = plugin;
        this.bounty = bounty;
        reload();
    }

    void reload() {
        var cfg = plugin.getConfig();
        enabled = cfg.getBoolean("combat.enabled", true);
        punishLogout = cfg.getBoolean("combat.punish-logout", true);
        noElytra = cfg.getBoolean("combat.disable-elytra", true);
        tagMs = Math.max(1, cfg.getLong("combat.seconds", 20)) * 1000L;
        blocked.clear();
        for (String s : cfg.getStringList("combat.blocked-commands")) {
            blocked.add(s.toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
        }
    }

    void start() { task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L); }

    void stop() {
        if (task != null) task.cancel();
        tagUntil.clear();
        lastAttacker.clear();
        pendingLog.clear();
    }

    private boolean inCombat(Player p) {
        Long until = tagUntil.get(p.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    private static boolean exempt(Player p) {
        GameMode g = p.getGameMode();
        return (g != GameMode.SURVIVAL && g != GameMode.ADVENTURE) || p.hasPermission(BYPASS);
    }

    private void tag(Player p, Player other) {
        long now = System.currentTimeMillis();
        Long old = tagUntil.put(p.getUniqueId(), now + tagMs);
        lastAttacker.put(p.getUniqueId(), other.getUniqueId());
        if (old == null || old < now) {
            p.sendMessage(Component.text("Kamu masuk combat! Jangan logout selama "
                    + tagMs / 1000 + " detik.", NamedTextColor.RED));
            if (noElytra && p.isGliding()) p.setGliding(false);
        }
    }

    private void tick() {
        if (tagUntil.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = tagUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            long left = e.getValue() - now;
            if (p == null) {
                it.remove();
                lastAttacker.remove(e.getKey());
            } else if (left <= 0) {
                it.remove();
                lastAttacker.remove(e.getKey());
                p.sendActionBar(Component.text("Combat selesai, kamu aman.", NamedTextColor.GREEN));
            } else {
                p.sendActionBar(Component.text("Combat: " + (left + 999) / 1000 + "s", NamedTextColor.RED));
            }
        }
    }

    private static Player attacker(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!enabled || !(e.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (exempt(attacker) || exempt(victim)) return;
        tag(victim, attacker);
        tag(attacker, victim);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!enabled || blocked.isEmpty() || !inCombat(e.getPlayer())) return;
        String cmd = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = cmd.indexOf(':');
        if (colon >= 0) cmd = cmd.substring(colon + 1);
        if (blocked.contains(cmd)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Component.text("Command ini tidak bisa dipakai saat combat.", NamedTextColor.RED));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (!enabled || !noElytra || !e.isGliding()) return;
        if (e.getEntity() instanceof Player p && inCombat(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        boolean tagged = inCombat(p);
        UUID attacker = lastAttacker.get(id);
        tagUntil.remove(id);
        lastAttacker.remove(id);

        if (!enabled || !punishLogout || !tagged) return;
        if (Bukkit.isStopping() || e.getReason() == PlayerQuitEvent.QuitReason.KICKED) return;
        if (p.isDead() || p.getHealth() <= 0 || p.hasPermission(BYPASS)) return;

        if (attacker != null) pendingLog.put(id, attacker);
        Bukkit.broadcast(Component.text(p.getName() + " keluar saat combat dan mati.", NamedTextColor.RED));
        p.setHealth(0.0);
        pendingLog.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        UUID vid = victim.getUniqueId();
        tagUntil.remove(vid);
        lastAttacker.remove(vid);
        Player k = victim.getKiller();
        UUID killer = k != null ? k.getUniqueId() : pendingLog.remove(vid);
        if (killer != null && !killer.equals(vid)) bounty.onKill(killer, vid);
    }
}
