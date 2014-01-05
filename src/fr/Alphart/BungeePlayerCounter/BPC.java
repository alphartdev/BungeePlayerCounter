package fr.Alphart.BungeePlayerCounter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public class BPC extends JavaPlugin implements PluginMessageListener, Listener, CommandExecutor{

	private static BPC instance;
	private String currentServer;
	private Group currentGroup;
	private List<String> serversList;
	private Scoreboard SB;
	// ** Config variable
	private String networkName;
	private Boolean serverIndicator;
	private Boolean automaticDisplay;
	private Integer updateInterval;
	// Manual display related : Associate a server's name to his group
	private Map<String, Group> serversGroups;
	// ** Perm
	private String DISPLAY_PERM = "bungeeplayercounter.display";
	private String RELOAD_PERM = "bungeeplayercounter.reload";
	
	public void onEnable(){
		BPC.instance = this;
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("bpc").setExecutor(this);
        saveDefConfig();
        loadConfig();
		initScoreboard();		
	}
	
	public void reload(){
		reloadConfig();
		Bukkit.getScheduler().cancelTasks(this);
		currentServer = null;
		loadConfig();
		initScoreboard();
		
		// Try to init the system now (almost same code that in PlayerJoinEvent except the scheduler)
		if(serversList.isEmpty() || (serverIndicator && currentServer == null)){
			try {
				final Player p = Bukkit.getOnlinePlayers()[0];
				if(p == null){
					return;
				}
				// Define plugin messages
				final List<byte[]> plugMessage = new ArrayList<byte[]>();
				ByteArrayOutputStream b = new ByteArrayOutputStream();
				DataOutputStream out = new DataOutputStream(b);
				if(serversList.isEmpty()){
					out.writeUTF("GetServers");
					plugMessage.add(b.toByteArray());
				}
				if(serverIndicator && currentServer == null){
					b = new ByteArrayOutputStream();
					out = new DataOutputStream(b);
					out.writeUTF("GetServer");
					plugMessage.add(b.toByteArray());
				}
				for(byte[] message : plugMessage)
					p.sendPluginMessage(BPC.getInstance(), "BungeeCord", message);
			} catch (IOException exception) {
				getLogger().severe("Error during message sending. ErrorCode: 1");
				exception.printStackTrace();
			}
		}
		Bukkit.broadcast(ChatColor.translateAlternateColorCodes('&', "&4[&6BPC&4]&B BungeePlayerCounter has been successfully reloaded !"), RELOAD_PERM);
	}
	
	public void saveDefConfig(){
		if(!new File(getDataFolder(), "config.yml").exists()){
			saveResource("config.yml", true);
			reloadConfig();
		}
		if(!YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml")).contains("groups")){
			getLogger().info("Config file's update in progress ...");
			// Save the userData
			String networkName = getConfig().getString("name");
			Boolean serverIndicator = getConfig().getBoolean("enableServerIndicator");
			// Replace the old config file by the new one
			saveResource("config.yml", true);
			// Set the old value
			reloadConfig();
			getConfig().set("name", networkName + "  &7&L(%totalplayercount%)");
			getConfig().set("enableServerIndicator", serverIndicator);
			saveConfig();
		}
		reloadConfig();
	}
	public void loadConfig(){
		// Get some config vars
		networkName = ChatColor.translateAlternateColorCodes('&', "&n" + getConfig().getString("name"));
		serverIndicator = getConfig().getBoolean("enableServerIndicator");
		automaticDisplay = getConfig().getBoolean("automaticDisplay");
		updateInterval = getConfig().getInt("interval");
		
		// Init some objects
		serversList = new ArrayList<String>();
		serversGroups = new HashMap<String, Group>();
		
		// If automatic display is disable, load groups list and init each object
		if(!automaticDisplay){
			Set<String> groupsList = getConfig().getConfigurationSection("groups").getKeys(false);
			for(String groupName : groupsList){
				Integer groupNB = (new HashSet<Group>(serversGroups.values())).size();
				// If there is more than 15 entry, the SB will crash so we stop the loading here
				// Note : Use an HashSet to avoid counting duplicate value (same group for different servers)
				if(groupNB > 14){
					getLogger().warning(
					"You've set more than 15 groups config. Only 15 groups have been loaded to avoid SB crashing. Set 15 groups or less in your config to disable this message.");
					return;
				}			
				ConfigurationSection groupConfig = getConfig().getConfigurationSection("groups."+groupName);
				// Only init group if it must be displayed
				if(groupConfig.getBoolean("display")){
					String serverName = groupConfig.getString("displayName");
					List<String> servers = Arrays.asList(groupConfig.getString("servers").split("\\+"));
					
					// Init the group, put it in the serversGroups map, and add servers of this group to serversList
					Group group = new Group(serverName, servers);
					for(String server : servers){
						// In the case were the same server is used in different list
						if(!serversGroups.containsKey(server))
							serversGroups.put(server, group);
						else
							getLogger().warning(server + " can't be added to group " + group.getName() + " because the same server is already being used in another group !");
					}
					
				}
			}
		}
	}

	
	public void initScoreboard() {
		SB = Bukkit.getScoreboardManager().getNewScoreboard();
		Objective objective = SB.registerNewObjective("playercounter", "dummy");
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
	}	
	public void updateSB(){
		for(Player p : Bukkit.getOnlinePlayers())
    		if(p.hasPermission(DISPLAY_PERM))
				p.setScoreboard(SB);
	}
	
	
	public static BPC getInstance(){
		return BPC.instance;
	}
	public List<String> getServersList(){
		return serversList;
	}	
	public Boolean getAutomaticDisplay(){
		return automaticDisplay;
	}
	
	/**
	 * Launch the task responsible of sending a player count request each seconds (20 ticks)
	 */
	public void launchUpdateTask(){
		Bukkit.getScheduler().runTaskTimer(this, new Runnable(){

			@Override
			public void run() {
				try {
					if(Bukkit.getOnlinePlayers().length == 0)
						return;
					Player p = Bukkit.getOnlinePlayers()[0];
					
					// Ask playercount for every servers
					for(String server : BPC.getInstance().getServersList()){
						ByteArrayOutputStream b = new ByteArrayOutputStream();
						DataOutputStream out = new DataOutputStream(b);
						out.writeUTF("PlayerCount");
						out.writeUTF(server);
						p.sendPluginMessage(BPC.getInstance(), "BungeeCord", b.toByteArray());
					}
					
					// Get player count for the full network
					ByteArrayOutputStream b = new ByteArrayOutputStream();
					DataOutputStream out = new DataOutputStream(b);
					out.writeUTF("PlayerCount");
					out.writeUTF("ALL");
					p.sendPluginMessage(BPC.getInstance(), "BungeeCord", b.toByteArray());
				} catch (IOException e) {
					e.printStackTrace();
					getLogger().severe("Error during message sending. ErrorCode: 2");
				}
			}
			
		}, 20L, 20L * updateInterval);
	}

	@Override
	public void onPluginMessageReceived(final String channel,final Player player,final byte[] message) {
		try {
	        DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
			final String subchannel = in.readUTF();
			if (subchannel.equals("PlayerCount")) {
				String server = in.readUTF();
				int playerCount = in.readInt();
				
				// Total player count
				if(server.equals("ALL")){
					String objectiveName = ChatColor.translateAlternateColorCodes('&',networkName).replaceAll("%totalplayercount%", String.valueOf(playerCount));
					if(objectiveName.length() > 32)
						objectiveName = objectiveName.substring(0, 32);
					SB.getObjective(DisplaySlot.SIDEBAR).setDisplayName(objectiveName);
					updateSB();
		        	return;
				}

				// Automatic display part
				if(automaticDisplay){
					// If the currentServer is not defined, it can cause duplicate scoreboard entry so we cancel until we've got the currentServer
					if(serverIndicator && currentServer == null)
						return;
					
					// If this is the current server player count and the server indicator is on, show the server indicator
					if( server.equals(currentServer) && serverIndicator){
						if(server.length() > 9)
							server = server.substring(0, 9);
						server = ChatColor.GREEN + ">" + server + ChatColor.RED + ":";
						
						// Avoid having more than 15 entry in the SB which makes it crash
						if(SB.getPlayers().size() < 15)
							SB.getObjective(DisplaySlot.SIDEBAR).getScore(Bukkit.getOfflinePlayer(server)).setScore(playerCount);
					}
					
					// Else just show player count of the server
					else{
						if(server.length() > 12)
							server = server.substring(0, 13);
						server = server + ChatColor.RED + ":";
						
						if(SB.getPlayers().size() < 15)
							SB.getObjective(DisplaySlot.SIDEBAR).getScore(Bukkit.getOfflinePlayer(server)).setScore(playerCount);
					}
				}
				
				// Manual display part
				else{
					// Cancel if this server isn't in any group
					if(!serversGroups.containsKey(server))
						return;

					// Get group and update player count
					Group group = serversGroups.get(server);
					group.updatePlayerCount(server, playerCount);
					
					// If the server is in the same group as currentServer zzand the server indicator is on, show the server indicator
					if(group.equals(currentGroup) && serverIndicator){
						
						// Handle displayname: truncate it if too big and then set it in the scoreboard
						String groupName = group.getName();
						groupName = ChatColor.GREEN + ">" + groupName;
						if(groupName.length() > 16)
							groupName = groupName.substring(0, 16);
						
						// Sometimes the current group shows without the special mark, so we gonna remove it
						SB.resetScores(Bukkit.getOfflinePlayer(group.getName()));
						
			        	SB.getObjective(DisplaySlot.SIDEBAR).getScore(Bukkit.getOfflinePlayer(groupName)).setScore(group.getPlayerCount());
					}
					
					// Else just show player count of the server
					else{					
						String name = group.getName();
			        	SB.getObjective(DisplaySlot.SIDEBAR).getScore(Bukkit.getOfflinePlayer(name)).setScore(group.getPlayerCount());
					}
				}

	        	updateSB();
	        	return;
			}
			else if (subchannel.equals("GetServers")) {
				// If serversList == null the task has not been already launched
				if(serversList.isEmpty()){
					serversList = new ArrayList<String>(Arrays.asList(in.readUTF().split(", ")));
					launchUpdateTask();
				}
				else
					serversList = new ArrayList<String>(Arrays.asList(in.readUTF().split(", ")));
	        } 
			else if (subchannel.equals("GetServer")) {
				currentServer = in.readUTF();
				if(!automaticDisplay)
					currentGroup = serversGroups.get(currentServer);
	        }
		} catch (IOException e) {
			getLogger().severe("Error during reception of plugin message! Cause: IOException\nPlease report this stacktrace :");
			e.printStackTrace();
		}
        
	}

	@EventHandler
	public void onPlayerJoin(final PlayerJoinEvent e){
		// If the serversList is not initialized, we send the plugin message to init it
		if(serversList.isEmpty() || (serverIndicator && currentServer == null)){
			try {
				final Player p = e.getPlayer();
				if(p == null){
					return;
				}
				
				// Define plugin messages
				final List<byte[]> plugMessage = new ArrayList<byte[]>();
				ByteArrayOutputStream b = new ByteArrayOutputStream();
				DataOutputStream out = new DataOutputStream(b);
				if(serversList.isEmpty()){
					out.writeUTF("GetServers");
					plugMessage.add(b.toByteArray());
				}
				if(serverIndicator && currentServer == null){
					b = new ByteArrayOutputStream();
					out = new DataOutputStream(b);
					out.writeUTF("GetServer");
					plugMessage.add(b.toByteArray());
				}
				
				// RunTaskLater because when PlayerJoinEvent we can't immediatly send a plugin message
				Bukkit.getScheduler().runTaskLater(this, new Runnable(){
					@Override
					public void run() {
						for(byte[] message : plugMessage)
							p.sendPluginMessage(BPC.getInstance(), "BungeeCord", message);
					}
					
				}, 2L);
			} catch (IOException exception) {
				getLogger().severe("Error during message sending. ErrorCode: 1");
				exception.printStackTrace();
			}
		}
		else{
			final Player p = e.getPlayer();
    		if(p.hasPermission(DISPLAY_PERM))
    			Bukkit.getScheduler().runTaskLater(this, new Runnable(){
    				@Override
					public void run() {
						p.setScoreboard(SB);
					}		
    			}, 2L);
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (args.length == 1 && args[0].equalsIgnoreCase("reload") && sender.hasPermission(RELOAD_PERM)) {
			reload();
		}
		else
			sender.sendMessage(ChatColor.YELLOW+ "[BPC] Invalid command !");
		return true;
	}
}