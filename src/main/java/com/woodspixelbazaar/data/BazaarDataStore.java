package com.woodspixelbazaar.data;

import com.woodspixelbazaar.WoodsPixelBazaar;
import com.woodspixelbazaar.model.PricePoint;
import com.woodspixelbazaar.model.TransactionRecord;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BazaarDataStore {

    private final WoodsPixelBazaar plugin;

    private File pricesFile;
    private File historyFile;
    private File transactionsFile;
    private File aliasesFile;

    private FileConfiguration pricesConfig;
    private FileConfiguration historyConfig;
    private FileConfiguration transactionsConfig;
    private FileConfiguration aliasesConfig;

    public BazaarDataStore(WoodsPixelBazaar plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        pricesFile = new File(plugin.getDataFolder(), "prices.yml");
        historyFile = new File(plugin.getDataFolder(), "history.yml");
        transactionsFile = new File(plugin.getDataFolder(), "transactions.yml");
        aliasesFile = new File(plugin.getDataFolder(), "aliases.yml");

        createIfMissing(pricesFile);
        createIfMissing(historyFile);
        createIfMissing(transactionsFile);
        createIfMissing(aliasesFile);

        pricesConfig = YamlConfiguration.loadConfiguration(pricesFile);
        historyConfig = YamlConfiguration.loadConfiguration(historyFile);
        transactionsConfig = YamlConfiguration.loadConfiguration(transactionsFile);
        aliasesConfig = YamlConfiguration.loadConfiguration(aliasesFile);

        bootstrapDefaults();
        saveAll();
    }

    public void saveAll() {
        try {
            pricesConfig.save(pricesFile);
            historyConfig.save(historyFile);
            transactionsConfig.save(transactionsFile);
            aliasesConfig.save(aliasesFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save bazaar data: " + exception.getMessage());
        }
    }

    public Map<String, Double> getBuyPrices() {
        return getPriceMap("buy");
    }

    public Map<String, Double> getSellPrices() {
        return getPriceMap("sell");
    }

    public double getBuyPrice(String itemId) {
        return pricesConfig.getDouble("buy." + itemId, 10.0);
    }

    public double getSellPrice(String itemId) {
        return pricesConfig.getDouble("sell." + itemId, Math.max(1.0, getBuyPrice(itemId) * 0.95));
    }

    public void setPrices(String itemId, double buyPrice, double sellPrice) {
        pricesConfig.set("buy." + itemId, buyPrice);
        pricesConfig.set("sell." + itemId, sellPrice);
    }

    public List<PricePoint> getHistory(String itemId) {
        List<Map<?, ?>> raw = historyConfig.getMapList("items." + itemId);
        List<PricePoint> points = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            long timestamp = ((Number) entry.getOrDefault("timestamp", System.currentTimeMillis())).longValue();
            double buy = ((Number) entry.getOrDefault("buy", 0.0D)).doubleValue();
            double sell = ((Number) entry.getOrDefault("sell", 0.0D)).doubleValue();
            points.add(new PricePoint(timestamp, buy, sell));
        }
        return points;
    }

    public void appendHistory(String itemId, PricePoint point) {
        List<Map<String, Object>> raw = new ArrayList<>();
        for (PricePoint current : getHistory(itemId)) {
            raw.add(serializePricePoint(current));
        }
        raw.add(serializePricePoint(point));
        int keep = 36;
        if (raw.size() > keep) {
            raw = raw.subList(raw.size() - keep, raw.size());
        }
        historyConfig.set("items." + itemId, raw);
    }

    public void appendTransaction(UUID playerId, TransactionRecord record) {
        String path = "players." + playerId;
        List<Map<String, Object>> raw = transactionsConfig.getMapList(path);
        raw.add(serializeTransaction(record));
        if (raw.size() > 100) {
            raw = raw.subList(raw.size() - 100, raw.size());
        }
        transactionsConfig.set(path, raw);
    }

    public Map<String, String> getAliases() {
        Map<String, String> aliases = new HashMap<>();
        ConfigurationSection section = aliasesConfig.getConfigurationSection("aliases");
        if (section == null) {
            return aliases;
        }
        for (String key : section.getKeys(false)) {
            aliases.put(key.toLowerCase(Locale.ROOT), section.getString(key, key));
        }
        return aliases;
    }

    public void setAlias(String alias, String itemId) {
        aliasesConfig.set("aliases." + alias.toLowerCase(Locale.ROOT), itemId);
    }

    private void bootstrapDefaults() {
        if (!pricesConfig.isConfigurationSection("buy")) {
            for (Material material : List.of(Material.WHEAT, Material.CARROT, Material.COBBLESTONE, Material.COAL, Material.OAK_LOG, Material.ROTTEN_FLESH, Material.STRING)) {
                String key = material.name().toLowerCase(Locale.ROOT);
                pricesConfig.set("buy." + key, 8.0 + material.ordinal() % 10);
                pricesConfig.set("sell." + key, 7.0 + material.ordinal() % 8);
            }
        }

        if (!aliasesConfig.isConfigurationSection("aliases")) {
            aliasesConfig.set("aliases.wheat", "wheat");
            aliasesConfig.set("aliases.carrot", "carrot");
            aliasesConfig.set("aliases.stone", "cobblestone");
            aliasesConfig.set("aliases.oaklog", "oak_log");
            aliasesConfig.set("aliases.string", "string");
        }
    }

    private Map<String, Double> getPriceMap(String root) {
        Map<String, Double> values = new HashMap<>();
        ConfigurationSection section = pricesConfig.getConfigurationSection(root);
        if (section == null) {
            return values;
        }
        for (String key : section.getKeys(false)) {
            values.put(key, section.getDouble(key));
        }
        return values;
    }

    private void createIfMissing(File file) {
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().severe("Failed creating " + file.getName() + ": " + exception.getMessage());
            }
        }
    }

    private Map<String, Object> serializePricePoint(PricePoint point) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", point.getTimestamp());
        map.put("buy", point.getBuyPrice());
        map.put("sell", point.getSellPrice());
        return map;
    }

    private Map<String, Object> serializeTransaction(TransactionRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", record.getTimestamp());
        map.put("player", record.getPlayerName());
        map.put("item", record.getItemId());
        map.put("type", record.getType());
        map.put("amount", record.getAmount());
        map.put("unit", record.getUnitPrice());
        map.put("total", record.getTotalPrice());
        return map;
    }
}
