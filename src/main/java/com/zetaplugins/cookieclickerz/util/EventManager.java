package com.zetaplugins.cookieclickerz.util;

import com.zetaplugins.cookieclickerz.listeners.*;
import com.zetaplugins.cookieclickerz.listeners.inventory.*;
import org.bukkit.event.Listener;
import com.zetaplugins.cookieclickerz.CookieClickerZ;
import com.zetaplugins.cookieclickerz.listeners.*;
import com.zetaplugins.cookieclickerz.listeners.inventory.*;

public class EventManager {
    private final CookieClickerZ plugin;

    public EventManager(CookieClickerZ plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers all listeners
     */
    public void registerListeners() {
        registerListener(new PlayerInteractionListener(plugin));
        registerListener(new PlayerJoinListener(plugin));
        registerListener(new PlayerQuitListener(plugin));
        registerListener(new BlockBreakListener(plugin));
        registerListener(new InventoryCloseListener());
        registerListener(new MainGuiClickListener(plugin));
        registerListener(new UpgradeGuiClickListener(plugin));
        registerListener(new PrestigeGuiClickListener(plugin));
        registerListener(new TopGuiClickListener(plugin));
        registerListener(new AchievementsGuiClickListener());
    }

    /**
     * Registers a listener
     *
     * @param listener The listener to register
     */
    private void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }
}
