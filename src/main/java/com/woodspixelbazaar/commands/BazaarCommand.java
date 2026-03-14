package com.woodspixelbazaar.commands;

import com.woodspixelbazaar.WoodsPixelBazaar;
import com.woodspixelbazaar.data.BazaarDataStore;
import com.woodspixelbazaar.gui.BazaarGuiManager;
import com.woodspixelbazaar.utils.BazaarService;
import com.woodspixelbazaar.utils.ItemResolver;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class BazaarCommand implements CommandExecutor, TabCompleter {

    private final WoodsPixelBazaar plugin;
    private final BazaarService bazaarService;
    private final BazaarGuiManager guiManager;
    private final ItemResolver itemResolver;
    private final BazaarDataStore dataStore;

    public BazaarCommand(WoodsPixelBazaar plugin, BazaarService bazaarService, BazaarGuiManager guiManager, ItemResolver itemResolver, BazaarDataStore dataStore) {
        this.plugin = plugin;
        this.bazaarService = bazaarService;
        this.guiManager = guiManager;
        this.itemResolver = itemResolver;
        this.dataStore = dataStore;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use bazaar commands.");
            return true;
        }

        if (args.length == 0) {
            guiManager.openMain(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "buy" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /bazaar buy <item> <amount>");
                    return true;
                }
                Integer amount = parseAmount(args[2]);
                if (amount == null) {
                    player.sendMessage(ChatColor.RED + "Invalid amount.");
                    return true;
                }
                player.sendMessage(bazaarService.buy(player, args[1], amount));
                return true;
            }
            case "sell" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /bazaar sell <item> <amount>");
                    return true;
                }
                Integer amount = parseAmount(args[2]);
                if (amount == null) {
                    player.sendMessage(ChatColor.RED + "Invalid amount.");
                    return true;
                }
                player.sendMessage(bazaarService.sell(player, args[1], amount));
                return true;
            }
            case "price" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /bazaar price <item>");
                    return true;
                }
                player.sendMessage(bazaarService.getPriceMessage(args[1]));
                return true;
            }
            case "history" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /bazaar history <item>");
                    return true;
                }
                bazaarService.getHistoryLines(args[1]).forEach(player::sendMessage);
                return true;
            }
            case "reload" -> {
                if (!player.hasPermission("woodspixelbazaar.admin")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission.");
                    return true;
                }
                plugin.reloadBazaar();
                player.sendMessage(ChatColor.GREEN + "WoodsPixelBazaar reloaded.");
                return true;
            }
            case "setprice" -> {
                if (!player.hasPermission("woodspixelbazaar.admin")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission.");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /bazaar setprice <item> <price>");
                    return true;
                }
                try {
                    double price = Double.parseDouble(args[2]);
                    bazaarService.setPrice(args[1], price);
                    dataStore.saveAll();
                    player.sendMessage(ChatColor.GREEN + "Set buy price for " + args[1] + " to " + price + ".");
                } catch (NumberFormatException exception) {
                    player.sendMessage(ChatColor.RED + "Price must be numeric.");
                }
                return true;
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Unknown subcommand. Try /bazaar.");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("buy", "sell", "price", "history", "reload", "setprice"), args[0]);
        }

        if (args.length == 2 && List.of("buy", "sell", "price", "history", "setprice").contains(args[0].toLowerCase(Locale.ROOT))) {
            return itemResolver.suggestItems(args[1]);
        }

        if (args.length == 3 && List.of("buy", "sell").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(List.of("1", "16", "32", "64", "128"), args[2]);
        }

        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }

    private Integer parseAmount(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
