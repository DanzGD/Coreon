package id.anarchy.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class Bounty {
    private final JavaPlugin plugin;
    private final Storage st;
    private final Market market;
    private final Map<String, Long> recent = new HashMap<>();

    Bounty(JavaPlugin plugin, Storage st, Market market) {
        this.plugin = plugin;
        this.st = st;
        this.market = market;
    }

    long of(UUID id) { return st.bounty.getOrDefault(id, 0L); }

    private void set(UUID id, long cents) {
        if (cents <= 0) st.bounty.remove(id);
        else st.bounty.put(id, cents);
        st.dirty();
    }

    private static String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    private static void say(CommandSender s, String text, NamedTextColor c) {
        s.sendMessage(Component.text(text, c));
    }

    void onKill(UUID killer, UUID victim) {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("bounty.enabled", true)) return;

        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, cfg.getLong("bounty.same-victim-cooldown-minutes", 10)) * 60_000L;
        String key = killer + ":" + victim;
        Long last = recent.get(key);
        if (last != null && now - last < cooldown) return;
        recent.put(key, now);
        if (recent.size() > 500) recent.values().removeIf(t -> now - t >= cooldown);

        long victimBounty = of(victim);
        double pct = Math.max(0, Math.min(1, cfg.getDouble("bounty.claim-percent", 0.5)));
        long reward = Math.round(victimBounty * pct);
        if (reward > 0) {
            set(victim, victimBounty - reward);
            st.money.merge(killer, reward, Long::sum);
            Bukkit.broadcast(Component.text(name(killer) + " membunuh " + name(victim)
                    + " dan mengklaim bounty " + market.fmt(reward) + "!", NamedTextColor.GOLD));
        }

        long perKill = Math.round(cfg.getDouble("bounty.per-kill", 500) * 100);
        long max = Math.round(cfg.getDouble("bounty.max", 1_000_000) * 100);
        long before = of(killer);
        long after = Math.min(max, before + perKill);
        if (after != before) {
            set(killer, after);
            Player kp = Bukkit.getPlayer(killer);
            if (kp != null) say(kp, "Bounty di kepalamu naik menjadi " + market.fmt(after) + ".", NamedTextColor.RED);
        }
        st.dirty();
    }

    void command(CommandSender s, String[] args) {
        if (args.length == 0) {
            if (!(s instanceof Player p)) {
                say(s, "Gunakan: /bounty <pemain|top|add>", NamedTextColor.RED);
                return;
            }
            say(p, "Bounty di kepalamu: " + market.fmt(of(p.getUniqueId())), NamedTextColor.GOLD);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("top")) top(s);
        else if (sub.equals("add")) add(s, args);
        else {
            OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (t == null) { say(s, "Pemain tidak ditemukan.", NamedTextColor.RED); return; }
            say(s, "Bounty " + t.getName() + ": " + market.fmt(of(t.getUniqueId())), NamedTextColor.GOLD);
        }
    }

    private void top(CommandSender s) {
        List<Map.Entry<UUID, Long>> rows = new ArrayList<>(st.bounty.entrySet());
        rows.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        say(s, "== Top 10 Bounty ==", NamedTextColor.GOLD);
        if (rows.isEmpty()) say(s, "Belum ada bounty.", NamedTextColor.YELLOW);
        for (int i = 0; i < Math.min(10, rows.size()); i++)
            say(s, (i + 1) + ". " + name(rows.get(i).getKey()) + " - " + market.fmt(rows.get(i).getValue()), NamedTextColor.YELLOW);
    }

    private void add(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { say(s, "Command ini hanya untuk pemain.", NamedTextColor.RED); return; }
        if (args.length < 3) { say(p, "Gunakan: /bounty add <pemain> <jumlah>", NamedTextColor.RED); return; }
        OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (t == null) { say(p, "Pemain tidak ditemukan (harus pernah main di server).", NamedTextColor.RED); return; }
        if (t.getUniqueId().equals(p.getUniqueId())) { say(p, "Tidak bisa menaruh bounty pada diri sendiri.", NamedTextColor.RED); return; }
        double amount;
        try { amount = Double.parseDouble(args[2]); }
        catch (NumberFormatException e) { say(p, "Jumlah tidak valid.", NamedTextColor.RED); return; }
        if (!Double.isFinite(amount) || amount <= 0 || amount > 1_000_000_000) { say(p, "Jumlah tidak valid.", NamedTextColor.RED); return; }
        var cfg = plugin.getConfig();
        long cents = Math.round(amount * 100);
        long min = Math.round(cfg.getDouble("bounty.min-add", 100) * 100);
        long max = Math.round(cfg.getDouble("bounty.max", 1_000_000) * 100);
        if (cents < min) { say(p, "Minimal bounty yang bisa ditaruh: " + market.fmt(min) + ".", NamedTextColor.RED); return; }
        UUID id = p.getUniqueId();
        long bal = st.money.getOrDefault(id, 0L);
        if (cents > bal) { say(p, "Saldo tidak cukup (" + market.fmt(bal) + ").", NamedTextColor.RED); return; }
        long cur = of(t.getUniqueId());
        if (cur + cents > max) { say(p, "Melebihi batas bounty (" + market.fmt(max) + ").", NamedTextColor.RED); return; }
        if (bal - cents <= 0) st.money.remove(id); else st.money.put(id, bal - cents);
        set(t.getUniqueId(), cur + cents);
        Bukkit.broadcast(Component.text(p.getName() + " menaruh bounty " + market.fmt(cents) + " pada "
                + t.getName() + " (total " + market.fmt(cur + cents) + ").", NamedTextColor.GOLD));
    }
}
