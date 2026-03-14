package com.woodspixelbazaar;

import com.woodspixelbazaar.commands.BazaarCommand;
import com.woodspixelbazaar.data.BazaarDataStore;
import com.woodspixelbazaar.events.BazaarGuiListener;
import com.woodspixelbazaar.gui.BazaarGuiManager;
import com.woodspixelbazaar.utils.BazaarService;
import com.woodspixelbazaar.utils.ItemResolver;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class WoodsPixelBazaar extends JavaPlugin {

    private Economy economy;
    private BazaarDataStore dataStore;
    private ItemResolver itemResolver;
    private BazaarService bazaarService;
    private BazaarGuiManager bazaarGuiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault economy provider not found. Disabling WoodsPixelBazaar.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.dataStore = new BazaarDataStore(this);
        this.dataStore.loadAll();

        this.itemResolver = new ItemResolver(this, dataStore);
        this.bazaarService = new BazaarService(this, economy, dataStore, itemResolver);
        this.bazaarGuiManager = new BazaarGuiManager(bazaarService, itemResolver, dataStore);

        BazaarCommand bazaarCommand = new BazaarCommand(this, bazaarService, bazaarGuiManager, itemResolver, dataStore);
        PluginCommand command = getCommand("bazaar");
        if (command != null) {
            command.setExecutor(bazaarCommand);
            command.setTabCompleter(bazaarCommand);
        }

        Bukkit.getPluginManager().registerEvents(new BazaarGuiListener(bazaarGuiManager), this);
        getLogger().info(ChatColor.GREEN + "WoodsPixelBazaar enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (dataStore != null) {
            dataStore.saveAll();
        }
    }

    public void reloadBazaar() {
        reloadConfig();
        dataStore.loadAll();
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            return false;
        }
        economy = provider.getProvider();
        return economy != null;
    }
}
