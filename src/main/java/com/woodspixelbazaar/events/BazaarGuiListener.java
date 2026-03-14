package com.woodspixelbazaar.events;

import com.woodspixelbazaar.gui.BazaarGuiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class BazaarGuiListener implements Listener {

    private final BazaarGuiManager guiManager;

    public BazaarGuiListener(BazaarGuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (!title.startsWith("§2Bazaar") && !title.startsWith("§2Item") && !title.startsWith("§2History")) {
            return;
        }

        event.setCancelled(true);
        guiManager.handleClick(player, title, event.getInventory(), event.getRawSlot());
    }
}
