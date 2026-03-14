package com.woodspixelbazaar.utils;

import com.woodspixelbazaar.WoodsPixelBazaar;
import com.woodspixelbazaar.data.BazaarDataStore;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ItemResolver {

    private final WoodsPixelBazaar plugin;
    private final BazaarDataStore dataStore;

    public ItemResolver(WoodsPixelBazaar plugin, BazaarDataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    public String normalizeItemId(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String cleaned = input.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        Map<String, String> aliases = dataStore.getAliases();
        if (aliases.containsKey(cleaned)) {
            return aliases.get(cleaned);
        }

        Material material = Material.matchMaterial(cleaned, true);
        if (material != null) {
            return material.name().toLowerCase(Locale.ROOT);
        }

        plugin.getLogger().fine("Unknown item id requested: " + input);
        return cleaned;
    }

    public Material toMaterial(String itemId) {
        if (itemId == null) {
            return Material.STONE;
        }
        Material material = Material.matchMaterial(itemId, true);
        return material != null ? material : Material.PAPER;
    }

    public ItemStack createDisplayStack(String itemId, int amount) {
        return new ItemStack(toMaterial(itemId), Math.max(1, Math.min(64, amount)));
    }

    public List<String> suggestItems(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (String item : dataStore.getBuyPrices().keySet()) {
            if (item.startsWith(lower)) {
                suggestions.add(item);
            }
        }
        for (String alias : dataStore.getAliases().keySet()) {
            if (alias.startsWith(lower)) {
                suggestions.add(alias);
            }
        }
        suggestions.sort(String::compareToIgnoreCase);
        return suggestions.stream().limit(25).toList();
    }
}
