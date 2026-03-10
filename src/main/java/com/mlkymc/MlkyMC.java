package com.mlkymc;

import org.bukkit.plugin.java.JavaPlugin;

public class MlkyMC extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("mlkyMC has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("mlkyMC has been disabled!");
    }
}
