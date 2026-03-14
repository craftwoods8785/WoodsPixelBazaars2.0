package com.woodspixelbazaar.gui;

import com.woodspixelbazaar.data.BazaarDataStore;
import com.woodspixelbazaar.model.PricePoint;
import com.woodspixelbazaar.utils.BazaarService;
import com.woodspixelbazaar.utils.ItemResolver;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BazaarGuiManager {

    public static final String MAIN_TITLE = ChatColor.DARK_GREEN + "Bazaar Categories";
    public static final String ITEM_LIST_TITLE = ChatColor.DARK_GREEN + "Bazaar - ";
    public static final String ITEM_DETAIL_TITLE = ChatColor.DARK_GREEN + "Item - ";
    public static final String HISTORY_TITLE = ChatColor.DARK_GREEN + "History - ";

    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final BazaarService bazaarService;
    private final ItemResolver itemResolver;
    private final BazaarDataStore dataStore;

    private final Map<UUID, String> selectedItems = new HashMap<>();
    private final Map<String, List<String>> categories = new LinkedHashMap<>();

    public BazaarGuiManager(BazaarService bazaarService, ItemResolver itemResolver, BazaarDataStore dataStore) {
        this.bazaarService = bazaarService;
        this.itemResolver = itemResolver;
        this.dataStore = dataStore;
        seedCategories();
    }

    public void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, MAIN_TITLE);
        int slot = 10;
        for (String category : categories.keySet()) {
            inventory.setItem(slot, menuItem(categoryMaterial(category), ChatColor.GOLD + category,
                    ChatColor.GRAY + "Browse " + category + " items",
                    ChatColor.YELLOW + "Click to open"));
            slot++;
        }
        inventory.setItem(22, menuItem(Material.COMPASS, ChatColor.AQUA + "Search", ChatColor.GRAY + "Tip: /bazaar price <item>"));
        player.openInventory(inventory);
    }

    public void openCategory(Player player, String category) {
        List<String> entries = categories.getOrDefault(category, List.of());
        Inventory inventory = Bukkit.createInventory(null, 54, ITEM_LIST_TITLE + category);
        for (int i = 0; i < Math.min(entries.size(), 45); i++) {
            String itemId = entries.get(i);
            inventory.setItem(i, menuItem(itemResolver.toMaterial(itemId), ChatColor.GREEN + itemId,
                    ChatColor.GRAY + "Buy: " + PRICE_FORMAT.format(bazaarService.getBuyPrice(itemId)),
                    ChatColor.GRAY + "Sell: " + PRICE_FORMAT.format(bazaarService.getSellPrice(itemId)),
                    ChatColor.YELLOW + "Click for options"));
        }
        inventory.setItem(49, menuItem(Material.BARRIER, ChatColor.RED + "Back"));
        player.openInventory(inventory);
    }

    public void openItemDetail(Player player, String itemId) {
        selectedItems.put(player.getUniqueId(), itemId);
        Inventory inventory = Bukkit.createInventory(null, 27, ITEM_DETAIL_TITLE + itemId);
        inventory.setItem(10, menuItem(Material.LIME_CONCRETE, ChatColor.GREEN + "Buy x64",
                ChatColor.GRAY + "Unit: " + PRICE_FORMAT.format(bazaarService.getBuyPrice(itemId))));
        inventory.setItem(13, menuItem(itemResolver.toMaterial(itemId), ChatColor.GOLD + itemId,
                ChatColor.GRAY + "Balance-aware trading",
                ChatColor.GRAY + "Inventory-aware selling"));
        inventory.setItem(16, menuItem(Material.RED_CONCRETE, ChatColor.RED + "Sell x64",
                ChatColor.GRAY + "Unit: " + PRICE_FORMAT.format(bazaarService.getSellPrice(itemId))));
        inventory.setItem(22, menuItem(Material.BOOK, ChatColor.AQUA + "View History"));
        player.openInventory(inventory);
    }

    public void openHistory(Player player, String itemId) {
        List<PricePoint> history = dataStore.getHistory(itemId);
        Inventory inventory = Bukkit.createInventory(null, 54, HISTORY_TITLE + itemId);
        int slot = 0;

        int start = Math.max(0, history.size() - 45);
        for (int i = start; i < history.size() && slot < 45; i++) {
            PricePoint point = history.get(i);
            inventory.setItem(slot++, menuItem(Material.PAPER,
                    ChatColor.YELLOW + TIME_FORMAT.format(Instant.ofEpochMilli(point.getTimestamp())),
                    ChatColor.GREEN + "Buy: " + PRICE_FORMAT.format(point.getBuyPrice()),
                    ChatColor.RED + "Sell: " + PRICE_FORMAT.format(point.getSellPrice()),
                    sparkline(point.getBuyPrice(), point.getSellPrice())));
        }

        inventory.setItem(49, menuItem(Material.BARRIER, ChatColor.RED + "Back"));
        player.openInventory(inventory);
    }

    public boolean handleClick(Player player, String title, Inventory inventory, int slot) {
        if (MAIN_TITLE.equals(title)) {
            if (slot >= 10 && slot < 10 + categories.size()) {
                String category = new ArrayList<>(categories.keySet()).get(slot - 10);
                openCategory(player, category);
                return true;
            }
            return false;
        }

        if (title.startsWith(ITEM_LIST_TITLE)) {
            if (slot == 49) {
                openMain(player);
                return true;
            }
            ItemStack clicked = inventory.getItem(slot);
            if (clicked != null && clicked.getType() != Material.AIR && clicked.hasItemMeta()) {
                String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                if (name != null) {
                    openItemDetail(player, name.toLowerCase(Locale.ROOT));
                    return true;
                }
            }
        }

        if (title.startsWith(ITEM_DETAIL_TITLE)) {
            String itemId = selectedItems.getOrDefault(player.getUniqueId(), title.substring(ITEM_DETAIL_TITLE.length()).toLowerCase(Locale.ROOT));
            if (slot == 10) {
                player.sendMessage(bazaarService.buy(player, itemId, 64));
                openItemDetail(player, itemId);
                return true;
            }
            if (slot == 16) {
                player.sendMessage(bazaarService.sell(player, itemId, 64));
                openItemDetail(player, itemId);
                return true;
            }
            if (slot == 22) {
                openHistory(player, itemId);
                return true;
            }
        }

        if (title.startsWith(HISTORY_TITLE) && slot == 49) {
            String itemId = title.substring(HISTORY_TITLE.length()).toLowerCase(Locale.ROOT);
            openItemDetail(player, itemId);
            return true;
        }

        return false;
    }

    private ItemStack menuItem(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Material categoryMaterial(String category) {
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "farming" -> Material.WHEAT;
            case "mining" -> Material.IRON_PICKAXE;
            case "combat" -> Material.IRON_SWORD;
            case "foraging" -> Material.OAK_LOG;
            default -> Material.CHEST;
        };
    }

    private String sparkline(double buy, double sell) {
        int b = (int) Math.min(10, Math.max(1, Math.round(buy / 10.0)));
        int s = (int) Math.min(10, Math.max(1, Math.round(sell / 10.0)));
        return ChatColor.GRAY + "Buy " + "▮".repeat(b) + " Sell " + "▯".repeat(s);
    }

    private void seedCategories() {
        Set<String> all = bazaarService.allKnownItems();
        categories.put("Farming", filterByPrefix(all, List.of("wheat", "carrot", "potato", "melon", "pumpkin")));
        categories.put("Mining", filterByPrefix(all, List.of("stone", "cobble", "coal", "iron", "gold", "diamond")));
        categories.put("Combat", filterByPrefix(all, List.of("rotten", "bone", "string", "spider", "arrow")));
        categories.put("Foraging", filterByPrefix(all, List.of("log", "wood", "sapling", "leaves")));
        categories.put("Misc", new ArrayList<>(all));
    }

    private List<String> filterByPrefix(Set<String> allItems, List<String> tokens) {
        List<String> result = new ArrayList<>();
        for (String item : allItems) {
            for (String token : tokens) {
                if (item.contains(token)) {
                    result.add(item);
                    break;
                }
            }
        }
        if (result.isEmpty()) {
            result.addAll(allItems);
        }
        return result.stream().sorted().limit(45).toList();
    }
}
