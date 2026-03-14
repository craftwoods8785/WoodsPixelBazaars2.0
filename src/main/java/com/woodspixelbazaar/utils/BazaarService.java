package com.woodspixelbazaar.utils;

import com.woodspixelbazaar.WoodsPixelBazaar;
import com.woodspixelbazaar.data.BazaarDataStore;
import com.woodspixelbazaar.model.PricePoint;
import com.woodspixelbazaar.model.TransactionRecord;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BazaarService {

    private static final long TRANSACTION_COOLDOWN_MS = 1_000;
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final WoodsPixelBazaar plugin;
    private final Economy economy;
    private final BazaarDataStore dataStore;
    private final ItemResolver itemResolver;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public BazaarService(WoodsPixelBazaar plugin, Economy economy, BazaarDataStore dataStore, ItemResolver itemResolver) {
        this.plugin = plugin;
        this.economy = economy;
        this.dataStore = dataStore;
        this.itemResolver = itemResolver;
    }

    public String buy(Player player, String requestedItem, int amount) {
        if (amount <= 0) {
            return ChatColor.RED + "Amount must be greater than 0.";
        }
        String itemId = itemResolver.normalizeItemId(requestedItem);
        if (!enforceCooldown(player)) {
            return ChatColor.RED + "You are transacting too quickly. Wait 1 second.";
        }

        double unitPrice = dataStore.getBuyPrice(itemId);
        double totalPrice = unitPrice * amount;

        if (economy.getBalance(player) < totalPrice) {
            return ChatColor.RED + "Insufficient funds. Need " + PRICE_FORMAT.format(totalPrice) + ".";
        }

        ItemStack stack = itemResolver.createDisplayStack(itemId, amount);
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
        int inserted = amount - leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        if (inserted <= 0) {
            return ChatColor.RED + "No inventory space to buy this item.";
        }

        double charged = unitPrice * inserted;
        EconomyResponse response = economy.withdrawPlayer(player, charged);
        if (!response.transactionSuccess()) {
            return ChatColor.RED + "Payment failed: " + response.errorMessage;
        }

        if (inserted < amount) {
            player.sendMessage(ChatColor.YELLOW + "Only " + inserted + " items fit in your inventory.");
        }

        updatePriceByDemand(itemId, inserted, true);
        recordTransaction(player, itemId, "BUY", inserted, unitPrice, charged);

        return ChatColor.GREEN + "Bought " + inserted + "x " + itemId + " for " + PRICE_FORMAT.format(charged) + " coins.";
    }

    public String sell(Player player, String requestedItem, int amount) {
        if (amount <= 0) {
            return ChatColor.RED + "Amount must be greater than 0.";
        }
        String itemId = itemResolver.normalizeItemId(requestedItem);
        if (!enforceCooldown(player)) {
            return ChatColor.RED + "You are transacting too quickly. Wait 1 second.";
        }

        Material material = itemResolver.toMaterial(itemId);
        int removable = removeItems(player, material, amount);
        if (removable <= 0) {
            return ChatColor.RED + "You do not have enough " + itemId + " to sell.";
        }

        double unitPrice = dataStore.getSellPrice(itemId);
        double total = unitPrice * removable;
        EconomyResponse response = economy.depositPlayer(player, total);
        if (!response.transactionSuccess()) {
            player.getInventory().addItem(new ItemStack(material, removable));
            return ChatColor.RED + "Payout failed: " + response.errorMessage;
        }

        updatePriceByDemand(itemId, removable, false);
        recordTransaction(player, itemId, "SELL", removable, unitPrice, total);
        return ChatColor.GREEN + "Sold " + removable + "x " + itemId + " for " + PRICE_FORMAT.format(total) + " coins.";
    }

    public String getPriceMessage(String requestedItem) {
        String itemId = itemResolver.normalizeItemId(requestedItem);
        double buy = dataStore.getBuyPrice(itemId);
        double sell = dataStore.getSellPrice(itemId);
        return ChatColor.GOLD + itemId + ChatColor.GRAY + " buy: " + ChatColor.GREEN + PRICE_FORMAT.format(buy)
                + ChatColor.GRAY + " | sell: " + ChatColor.RED + PRICE_FORMAT.format(sell);
    }

    public List<String> getHistoryLines(String requestedItem) {
        String itemId = itemResolver.normalizeItemId(requestedItem);
        List<PricePoint> history = dataStore.getHistory(itemId);
        if (history.isEmpty()) {
            return List.of(ChatColor.GRAY + "No history yet for " + itemId + ".");
        }

        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "History for " + itemId + ":");
        for (int i = Math.max(0, history.size() - 10); i < history.size(); i++) {
            PricePoint point = history.get(i);
            lines.add(ChatColor.GRAY + TIME_FORMAT.format(Instant.ofEpochMilli(point.getTimestamp()))
                    + " | " + ChatColor.GREEN + "B " + PRICE_FORMAT.format(point.getBuyPrice())
                    + ChatColor.RED + " S " + PRICE_FORMAT.format(point.getSellPrice()));
        }
        return lines;
    }

    public void setPrice(String requestedItem, double newBuyPrice) {
        String itemId = itemResolver.normalizeItemId(requestedItem);
        double sell = Math.max(0.01, newBuyPrice * 0.95D);
        dataStore.setPrices(itemId, Math.max(0.01, newBuyPrice), sell);
        dataStore.appendHistory(itemId, new PricePoint(System.currentTimeMillis(), newBuyPrice, sell));
        dataStore.saveAll();
    }

    public Set<String> allKnownItems() {
        return dataStore.getBuyPrices().keySet();
    }

    public double getBuyPrice(String itemId) {
        return dataStore.getBuyPrice(itemId);
    }

    public double getSellPrice(String itemId) {
        return dataStore.getSellPrice(itemId);
    }

    private boolean enforceCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long previous = cooldowns.get(player.getUniqueId());
        if (previous != null && now - previous < TRANSACTION_COOLDOWN_MS) {
            return false;
        }
        cooldowns.put(player.getUniqueId(), now);
        return true;
    }

    private int removeItems(Player player, Material material, int requested) {
        int removed = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && removed < requested; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(requested - removed, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            } else {
                player.getInventory().setItem(slot, stack);
            }
            removed += take;
        }
        return removed;
    }

    private void updatePriceByDemand(String itemId, int amount, boolean buy) {
        double currentBuy = dataStore.getBuyPrice(itemId);
        double currentSell = dataStore.getSellPrice(itemId);
        double movement = Math.max(0.01, amount * 0.02);
        double newBuy = buy ? currentBuy + movement : Math.max(0.05, currentBuy - movement);
        double newSell = buy ? currentSell + (movement * 0.9) : Math.max(0.05, currentSell - (movement * 0.9));

        dataStore.setPrices(itemId, newBuy, Math.min(newBuy, newSell));
        dataStore.appendHistory(itemId, new PricePoint(System.currentTimeMillis(), newBuy, Math.min(newBuy, newSell)));
        dataStore.saveAll();
    }

    private void recordTransaction(Player player, String itemId, String type, int amount, double unitPrice, double totalPrice) {
        dataStore.appendTransaction(player.getUniqueId(), new TransactionRecord(
                System.currentTimeMillis(),
                player.getName(),
                itemId,
                type,
                amount,
                unitPrice,
                totalPrice
        ));
        dataStore.saveAll();
        player.sendMessage(ChatColor.AQUA + "[Bazaar] " + ChatColor.WHITE + type + " " + amount + "x " + itemId + " @ " + PRICE_FORMAT.format(unitPrice));
    }
}
