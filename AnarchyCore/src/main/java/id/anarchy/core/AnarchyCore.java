package id.anarchy.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AnarchyCore extends JavaPlugin {
    private volatile Map<UUID, int[]> cache = Map.of();
    private File statsDir;
    private Storage storage;
    private Market market;
    private volatile DiscordBridge bridge;
    private BukkitTask lagTask;
    private Bounty bounty;
    private Combat combat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        statsDir = new File(Bukkit.getWorlds().get(0).getWorldFolder(), "stats");
        storage = new Storage(this);
        market = new Market(this, storage);
        getServer().getPluginManager().registerEvents(new Events(this), this);
        bounty = new Bounty(this, storage, market);
        combat = new Combat(this, bounty);
        getServer().getPluginManager().registerEvents(combat, this);
        combat.start();
        startBridge();
        if (statusOn()) bridge.event("\uD83D\uDFE2 Server online");
        Bukkit.getScheduler().runTaskTimer(this, storage::saveAsync, 2400L, 2400L);
        long period = Math.max(1, getConfig().getLong("stats-refresh-minutes", 5)) * 1200L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::refresh, 20L, period);
        scheduleLagGuard();
    }

    @Override
    public void onDisable() {
        if (bridge != null) bridge.stop(statusOn() ? "\uD83D\uDD34 Server offline" : null);
        if (combat != null) combat.stop();
        if (storage != null) storage.saveNow();
    }

    DiscordBridge bridge() { return bridge; }

    private boolean statusOn() {
        return getConfig().getBoolean("discord.events.server-status", true);
    }

    private void startBridge() {
        bridge = new DiscordBridge(this);
        bridge.start();
    }

    private void reloadAll() {
        reloadConfig();
        market.reload();
        combat.reload();
        bridge.stop(null);
        startBridge();
        scheduleLagGuard();
    }

    private void scheduleLagGuard() {
        if (lagTask != null) lagTask.cancel();
        lagTask = null;
        long mins = getConfig().getLong("lag-guard.item-clean-minutes", 0);
        if (mins <= 0) return;
        long period = mins * 1200L;
        lagTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long age = getConfig().getLong("lag-guard.item-min-age-minutes", 10);
            Bukkit.broadcast(Component.text("Item di tanah yang lebih dari " + age
                    + " menit akan dibersihkan 30 detik lagi.", NamedTextColor.YELLOW));
            Bukkit.getScheduler().runTaskLater(this, this::cleanItems, 600L);
        }, period, period);
    }

    private void cleanItems() {
        long minAge = getConfig().getLong("lag-guard.item-min-age-minutes", 10) * 1200L;
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Item i : w.getEntitiesByClass(Item.class)) {
                if (i.getTicksLived() >= minAge) {
                    i.remove();
                    n++;
                }
            }
        }
        if (n > 0 && getConfig().getBoolean("lag-guard.announce", true)) {
            Bukkit.broadcast(Component.text("Dibersihkan " + n + " item dari tanah.", NamedTextColor.YELLOW));
        }
    }

    private void refresh() {
        File[] files = statsDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return;
        Map<UUID, int[]> m = new HashMap<>(files.length * 2);
        for (File f : files) {
            try (BufferedReader r = Files.newBufferedReader(f.toPath())) {
                JsonObject stats = JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject("stats");
                JsonObject custom = stats == null ? null : stats.getAsJsonObject("minecraft:custom");
                if (custom == null) continue;
                UUID id = UUID.fromString(f.getName().substring(0, f.getName().length() - 5));
                int time = Math.max(num(custom, "minecraft:play_time"), num(custom, "minecraft:play_one_minute"));
                m.put(id, new int[]{num(custom, "minecraft:player_kills"), num(custom, "minecraft:deaths"), time});
            } catch (Exception ignored) {}
        }
        cache = m;
    }

    private static int num(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null ? 0 : e.getAsInt();
    }

    private int[] statsOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            return new int[]{p.getStatistic(Statistic.PLAYER_KILLS), p.getStatistic(Statistic.DEATHS),
                    p.getStatistic(Statistic.PLAY_ONE_MINUTE)};
        }
        return cache.get(id);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (!getConfig().getBoolean("commands." + name, true)) {
            sender.sendMessage(Component.text("Unknown command.", NamedTextColor.RED));
            return true;
        }
        switch (name) {
            case "stats" -> {
                OfflinePlayer target;
                if (args.length > 0) target = Bukkit.getOfflinePlayerIfCached(args[0]);
                else if (sender instanceof Player p) target = p;
                else { sender.sendMessage("Gunakan: /stats <pemain>"); return true; }
                int[] s = target == null ? null : statsOf(target.getUniqueId());
                if (s == null) { sender.sendMessage(Component.text("Data pemain tidak ditemukan.", NamedTextColor.RED)); return true; }
                double kd = s[1] == 0 ? s[0] : Math.round(s[0] * 100.0 / s[1]) / 100.0;
                sender.sendMessage(Component.text("== " + target.getName() + " ==", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("Kill: " + s[0] + " | Death: " + s[1] + " | K/D: " + kd, NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Waktu main: " + s[2] / 72000 + " jam", NamedTextColor.YELLOW));
                long bb = bounty.of(target.getUniqueId());
                if (bb > 0) sender.sendMessage(Component.text("Bounty: " + market.fmt(bb), NamedTextColor.YELLOW));
            }
            case "top" -> {
                List<Map.Entry<UUID, int[]>> list = new ArrayList<>(cache.entrySet());
                list.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
                sender.sendMessage(Component.text("== Top 10 Kill ==", NamedTextColor.GOLD));
                for (int i = 0; i < Math.min(10, list.size()); i++) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(list.get(i).getKey());
                    String n = op.getName() == null ? "?" : op.getName();
                    sender.sendMessage(Component.text((i + 1) + ". " + n + " - " + list.get(i).getValue()[0], NamedTextColor.YELLOW));
                }
            }
            case "discord" -> sender.sendMessage(Component.text(
                    getConfig().getString("discord-link", "-"), NamedTextColor.AQUA));
            case "baltop" -> market.baltop(sender);
            case "bounty" -> bounty.command(sender, args);
            case "eco" -> market.eco(sender, args);
            case "anarchycore" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                    reloadAll();
                    sender.sendMessage(Component.text("Config AnarchyCore dimuat ulang.", NamedTextColor.GREEN));
                } else sender.sendMessage(Component.text("Gunakan: /anarchycore reload", NamedTextColor.RED));
            }
            case "balance", "pay", "sell" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Command ini hanya untuk pemain.");
                    return true;
                }
                switch (name) {
                    case "balance" -> market.balance(p);
                    case "pay" -> market.pay(p, args);
                    default -> market.sell(p, args);
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        List<String> opts;
        switch (n) {
            case "sell" -> opts = args.length == 1 ? List.of("hand", "all", "price", "list") : List.of();
            case "eco" -> {
                if (args.length == 1) opts = List.of("give", "take", "set");
                else if (args.length == 2) return null;
                else opts = List.of();
            }
            case "anarchycore" -> opts = args.length == 1 ? List.of("reload") : List.of();
            case "bounty" -> {
                if (args.length == 1) opts = List.of("top", "add");
                else if (args.length == 2 && args[0].equalsIgnoreCase("add")) return null;
                else opts = List.of();
            }
            case "pay" -> { return args.length == 1 ? null : List.of(); }
            default -> { return null; }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return opts.stream().filter(o -> o.startsWith(last)).toList();
    }
}
