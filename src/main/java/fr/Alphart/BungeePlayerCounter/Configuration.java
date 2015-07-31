package fr.Alphart.BungeePlayerCounter;

import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import lombok.AccessLevel;
import lombok.Getter;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.google.common.base.Charsets;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import fr.Alphart.BungeePlayerCounter.Servers.ServerGroup;

@Getter
public class Configuration {
    @Getter(value = AccessLevel.NONE)
    private final FileConfiguration config;
    
    public Configuration(final FileConfiguration config){
        this.config = config;
        
        saveDefConfig();
        loadConfig();
    }
    
    private String networkName;
    private boolean serverIndicatorEnabled;
    private String serverIndicator;
    private boolean automaticDisplay;
    private Integer updateInterval;
    private String pluginMessageChannel;
    private String onlinePrefix;
    private String offlinePrefix;
    private InetSocketAddress proxyAddress;
    private SetMultimap<String, ServerGroup> serversGroups;
    
    private void saveDefConfig() {
        final BPC plugin = BPC.getInstance();
        if (!new File(plugin.getDataFolder(), "config.yml").exists()) {
            plugin.saveResource("config.yml", true);
            plugin.reloadConfig();
        }

        // Update the config
        if (!YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml")).contains("groups")) {
            plugin.getLogger().info("Config file's update in progress ...");
            // Save the userData
            String networkName = plugin.getConfig().getString("name");
            Boolean serverIndicator = plugin.getConfig().getBoolean("enableServerIndicator");
            // Replace the old config file by the new one
            plugin.saveResource("config.yml", true);
            // Set the old value
            plugin.reloadConfig();
            plugin.getConfig().set("name", networkName + "  &7&L(%totalplayercount%)");
            plugin.getConfig().set("enableServerIndicator", serverIndicator);
        }
        if (!YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml")).contains("datasource")) {
            plugin.getLogger().info("Config file's update in progress ...");
            plugin.getConfig().set("datasource", "default");
        }
        if (!YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml")).contains("offlinePrefix")) {
            plugin.getConfig().set("offlinePrefix", "&c[OFFLINE]");
        }
        if (!YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml")).contains("onlinePrefix")) {
            plugin.getConfig().set("onlinePrefix", "&a[ON]");
            plugin.getConfig().set("serverIndicator", "&a>");
            plugin.getConfig().set("proxyIP", "127.0.0.1:25577");
        }
        
        // Update the header
        final FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(BPC.getInstance().getResource("config.yml"), Charsets.UTF_8));
        plugin.getConfig().options().header(defaultConfig.options().header());
        
        plugin.saveConfig();
    }

    private void loadConfig(){
        final Logger bpcLogger = BPC.getInstance().getLogger();
        // Get some config vars
        networkName = ChatColor.translateAlternateColorCodes('&', config.getString("name"));
        serverIndicatorEnabled = config.getBoolean("enableServerIndicator");
        if(serverIndicatorEnabled){
            serverIndicator = ChatColor.translateAlternateColorCodes('&', config.getString("serverIndicator"));
        }
        automaticDisplay = config.getBoolean("automaticDisplay");
        updateInterval = config.getInt("interval");
        
        onlinePrefix = ChatColor.translateAlternateColorCodes('&', config.getString("onlinePrefix"));
        offlinePrefix = ChatColor.translateAlternateColorCodes('&', config.getString("offlinePrefix"));
        if(offlinePrefix.length() > 16){
            offlinePrefix = offlinePrefix.substring(0, 16);
            bpcLogger.warning("The offlinePrefix length is bigger than 16 chars...");
        }
        if(onlinePrefix.length() > 16){
            onlinePrefix = onlinePrefix.substring(0, 16);
            bpcLogger.warning("The onlinePrefix length is bigger than 16 chars...");
        }
        
        if(!config.getString("proxyIP").isEmpty()){
            final String strProxyIP = config.getString("proxyIP").replace("localhost", "127.0.0.1");
            try{
                String[] addressArray = strProxyIP.split(":");
                proxyAddress = new InetSocketAddress(InetAddress.getByName(addressArray[0]), Integer.parseInt(addressArray[1]));
            } catch (final IllegalArgumentException | UnknownHostException | ArrayIndexOutOfBoundsException e) {
                bpcLogger.log(Level.WARNING, "The address of the bungee proxy is not correct. It must have the following format: 'ip:port'", e);
            }
        }
        
        // Define channels used according to the datasource setting
        switch (config.getString("datasource")) {
            case "default":
                pluginMessageChannel = "BungeeCord";
                break;
            case "redis":
                pluginMessageChannel = "RedisBungee";
                break;
            default:
                pluginMessageChannel = "BungeeCord";
                config.set("datasource", "default");
                break;
        }
        
        serversGroups = HashMultimap.create();
        if (!automaticDisplay) {
            int groupNB = 0;
            final Set<String> groupsList = config.getConfigurationSection("groups").getKeys(false);
            for (String groupName : groupsList) {
                if (groupNB > 14) {
                    bpcLogger.warning("You've set more than 14 groups config. Only 14 groups have been loaded"
                            + " to avoid SB being buggy. Remove groups from your config to disable this message.");
                    break;
                }
                ConfigurationSection groupConfig = config.getConfigurationSection("groups." + groupName);
                
                // Add the default ip field to the group
                if(!groupConfig.contains("address")){
                    groupConfig.set("address", "127.0.0.1:25565");  
                }
                
                // Only init group if it must be displayed
                if (groupConfig.getBoolean("display")) {
                    String groupDisplayName = groupConfig.getString("displayName");
                    List<String> servers = Arrays.asList(groupConfig.getString("servers").split("\\+"));
                    InetSocketAddress address = null;
                    // Parse the address
                    if(!groupConfig.getString("address").isEmpty()){
                        final String strAddress = groupConfig.getString("address").replace("localhost", "127.0.0.1");
                        String[] addressArray = strAddress.split(":");
                        try {
                            address = new InetSocketAddress(InetAddress.getByName(addressArray[0]), Integer.parseInt(addressArray[1]));
                        } catch (IllegalArgumentException | UnknownHostException e) {
                            bpcLogger.log(Level.WARNING, "The address of the group " + groupName + " is not correct.", e);
                        }
                    }

                    final ServerGroup group;
                    if(address != null){
                         group = new ServerGroup(groupDisplayName, servers, address, updateInterval);
                    }else{
                         group = new ServerGroup(groupDisplayName, servers);
                    }
                    
                    for (String server : servers) {
                        serversGroups.put(server, group);
                    }
                    groupNB++;
                }
            }
        }
        
        BPC.getInstance().saveConfig(); // Set change that have been made to correct misconfiguration
    }
    
}