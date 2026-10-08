package id.anarchy.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Storage {
    private final JavaPlugin plugin;
    private final File file;
    private boolean dirty;
    final Map<UUID, Long> money = new HashMap<>();
    final Map<UUID, Long> bounty = new HashMap<>();
    final Map<Material, double[]> market = new HashMap<>();

    Storage(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection b = y.getConfigurationSection("money");
        if (b != null) {
            for (String key : b.getKeys(false)) {
                try {
                    long c = b.getLong(key);
                    if (c != 0) money.put(UUID.fromString(key), c);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        ConfigurationSection bs = y.getConfigurationSection("bounty");
        if (bs != null) {
            for (String key : bs.getKeys(false)) {
                try {
                    long c = bs.getLong(key);
                    if (c > 0) bounty.put(UUID.fromString(key), c);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        ConfigurationSection m = y.getConfigurationSection("market");
        if (m != null) {
            for (String key : m.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat != null) market.put(mat, new double[]{m.getDouble(key + ".s"), m.getLong(key + ".t")});
            }
        }
    }

    void dirty() { dirty = true; }

    private String build() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : money.entrySet()) y.set("money." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, Long> e : bounty.entrySet()) y.set("bounty." + e.getKey(), e.getValue());
        for (Map.Entry<Material, double[]> e : market.entrySet()) {
            y.set("market." + e.getKey().name() + ".s", e.getValue()[0]);
            y.set("market." + e.getKey().name() + ".t", (long) e.getValue()[1]);
        }
        return y.saveToString();
    }

    void saveAsync() {
        if (!dirty) return;
        dirty = false;
        String data = build();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(data));
    }

    void saveNow() {
        dirty = false;
        write(build());
    }

    private synchronized void write(String data) {
        try {
            Path tmp = file.toPath().resolveSibling("data.yml.tmp");
            Files.writeString(tmp, data);
            Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Gagal menyimpan data.yml: " + e.getMessage());
        }
    }
}
