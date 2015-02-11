package fr.Alphart.BungeePlayerCounter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
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
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public class BPC extends JavaPlugin implements PluginMessageListener, Listener, CommandExecutor {

	private static BPC instance;
	private String currentServer;
	private Group currentGroup;
	private List<String> serversList;
	private String channel; // Channel use for plugin message system only
							// PlayerCount (can be bungee or redis)
	private int maxPlayers = -1;
	// For other message, the BungeeCord channel will be used
	private Scoreboard SB;
	// ** Config variable
	private String networkName;
	private Boolean serverIndicatorEnabled;
	private String serverIndicator;
	private Boolean automaticDisplay;
	private Integer updateInterval;
	// Constant
	private final String MD_TOGGLE = "BPC_toggled";
	// Manual display related : Associate a server's name to his group
	private Map<String, Group> serversGroups;
	// Adress regex
	private final String ipAddressRegex = 
			"^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5]):\\d{1,5}$";
	// ** Perm
	private String DISPLAY_PERM = "bungeeplayercounter.display";
	private String RELOAD_PERM = "bungeeplayercounter.reload";
	private String TOGGLE_PERM = "bungeeplayercounter.toggle";

	public void onEnable() {
		BPC.instance = this;
		getServer().getPluginManager().registerEvents(this, this);
		getCommand("bpc").setExecutor(this);
		saveDefConfig();
		loadConfig();
		getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
		getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
		initScoreboard();
	}

	public void reload() {
		reloadConfig();
		Bukkit.getScheduler().cancelTasks(this);
		currentServer = null;
		loadConfig();
		initScoreboard();

		// Try to init the system now (almost same code that in PlayerJoinEvent
		// except the scheduler) because there may be online player
		if (serversList.isEmpty() || (serverIndicatorEnabled && currentServer == null)) {
			try {
				final Player p = Bukkit.getOnlinePlayers()[0];
				if (p == null) {
					return;
				}
				// Define plugin messages
				final List<byte[]> plugMessage = new ArrayList<byte[]>();
				ByteArrayOutputStream b = new ByteArrayOutputStream();
				DataOutputStream out = new DataOutputStream(b);
				if (serversList.isEmpty()) {
					out.writeUTF("GetServers");
					plugMessage.add(b.toByteArray());
				}
				if (serverIndicatorEnabled && currentServer == null) {
					b = new ByteArrayOutputStream();
					out = new DataOutputStream(b);
					out.writeUTF("GetServer");
					plugMessage.add(b.toByteArray());
				}
				for (byte[] message : plugMessage) {
					p.sendPluginMessage(this, "BungeeCord", message);
				}
				for (Player players : Bukkit.getOnlinePlayers()) {
					if (players.hasPermission(DISPLAY_PERM))
						players.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
				}
			} catch (IOException exception) {
				getLogger().severe("Error during message sending. ErrorCode: 1");
				exception.printStackTrace();
			}
		}
		Bukkit.broadcast(ChatColor.translateAlternateColorCodes('&',
				"&4[&6BPC&4]&B BungeePlayerCounter has been successfully reloaded !"), RELOAD_PERM);
	}

	public void saveDefConfig() {
		if (!new File(getDataFolder(), "config.yml").exists()) {
			saveResource("config.yml", true);
			reloadConfig();
		}

		// Update the config

		if (!YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml")).contains("groups")) {
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
		}
		if (!YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml")).contains("datasource")) {
			getLogger().info("Config file's update in progress ...");
			getConfig().set("datasource", "default");
		}
		if (!YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml")).contains("offlinePrefix")) {
			getConfig().set("offlinePrefix", "&c[OFFLINE]");
		}
		if (!YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml")).contains("onlinePrefix")) {
			getConfig().set("onlinePrefix", "&a[ON]");
			getConfig().set("serverIndicator", "&a>");
			getConfig().set("proxyIP", "127.0.0.1:25577");
		}
		
		// Update the header
		getConfig().options().header(YamlConfiguration.loadConfiguration(BPC.getInstance().getResource("config.yml")).options().header());
		
		saveConfig();
	}

	public void loadConfig() {
		// Get some config vars
		networkName = ChatColor.translateAlternateColorCodes('&', "&n" + getConfig().getString("name"));
		serverIndicatorEnabled = getConfig().getBoolean("enableServerIndicator");
		if(serverIndicatorEnabled){
			serverIndicator = getConfig().getString("serverIndicator");
		}
		automaticDisplay = getConfig().getBoolean("automaticDisplay");
		updateInterval = getConfig().getInt("interval");
		if(!getConfig().getString("proxyIP").isEmpty()){
			final InetSocketAddress proxyAddress;
			if(!getConfig().getString("proxyIP").isEmpty()){
				final String strProxyIP = getConfig().getString("proxyIP").replace("localhost", "127.0.0.1");
				if(strProxyIP.matches(ipAddressRegex)){
					String[] addressArray = getConfig().getString("proxyIP").split(":");
					try {
						proxyAddress = new InetSocketAddress(InetAddress.getByName(addressArray[0]), Integer.parseInt(addressArray[1]));	
						Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
							@Override
							public void run() {
								final Ping ping = new Ping("bungee", proxyAddress);
								ping.run();
								maxPlayers = ping.getMaxPlayers();
							}
						}, 20L, 20L * BPC.getInstance().getUpdateInterval());
					} catch (NumberFormatException | UnknownHostException e) {
						getLogger().warning("The adress of the bungee proxy is not correct.");
						e.printStackTrace();
					}
				}else{
					getLogger().warning("The adress of the bungee proxy is not correct.");
				}
			}
		}
		switch (getConfig().getString("datasource")) {
		case "default":
			channel = "BungeeCord";
			break;
		case "redis":
			channel = "RedisBungee";
			if (!getServer().getMessenger().isIncomingChannelRegistered(this, channel))
				getServer().getMessenger().registerIncomingPluginChannel(this, channel, this);
			if (!getServer().getMessenger().isOutgoingChannelRegistered(this, channel))
				getServer().getMessenger().registerOutgoingPluginChannel(this, channel);

			break;
		default:
			channel = "BungeeCord";
			getConfig().set("datasource", "default");
			saveConfig();
			break;
		}
		
		// Init some objects
		serversList = new ArrayList<String>();
		serversGroups = new HashMap<String, Group>();

		// If automatic display is disable, load groups list and init each
		// object
		if (!automaticDisplay) {
			Set<String> groupsList = getConfig().getConfigurationSection("groups").getKeys(false);
			for (String groupName : groupsList) {
				Integer groupNB = (new HashSet<Group>(serversGroups.values())).size();
				// If there is more than 14 entry, the SB will bug so we stop
				// the loading here
				// Note : Use an HashSet to avoid counting duplicate value (same
				// group for different servers)
				if (groupNB > 14) {
					getLogger()
							.warning(
									"You've set more than 14 groups config. Only 14 groups have been loaded to avoid SB being buggy. Set 14 groups or less in your config to disable this message.");
					return;
				}
				ConfigurationSection groupConfig = getConfig().getConfigurationSection("groups." + groupName);
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
						if(groupConfig.getString("address").matches(ipAddressRegex)){
							String[] addressArray = groupConfig.getString("address").split(":");
							try {
								address = new InetSocketAddress(InetAddress.getByName(addressArray[0]), Integer.parseInt(addressArray[1]));
							} catch (NumberFormatException | UnknownHostException e) {
								e.printStackTrace();
							}
						}else{
							getLogger().warning("The adress of the group " + groupName + " is not correct.");
						}
					}

					// Init the group, put it in the serversGroups map, and add
					// servers of this group to serversList
					Group group;
					if(address != null){
						 group = new Group(groupDisplayName, servers, address, this);
					}else{
						 group = new Group(groupDisplayName, servers);
					}
					
					for (String server : servers) {
						// In the case were the same server is used in different
						// list
						if (!serversGroups.containsKey(server))
							serversGroups.put(server, group);
						else
							getLogger().warning(
									server + " can't be added to group " + group.getName()
											+ " because the same server is already being used in another group !");
					}

				}
			}
			saveConfig();
		}
	}

	public void initScoreboard() {
		SB = Bukkit.getScoreboardManager().getMainScoreboard();
		for (final String entries : SB.getEntries()) {
			SB.resetScores(entries);
		}
		if (SB.getObjective("playercounter") != null) {
			SB.getObjective("playercounter").unregister();
		}
		if(SB.getTeam("offline") != null){
			SB.getTeam("offline").unregister();
		}
		if(SB.getTeam("online") != null){
			SB.getTeam("online").unregister();
		}
		Objective objective = SB.registerNewObjective("playercounter", "dummy");
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		
		// Online and offline stuff
		String offlinePrefix = getConfig().getString("offlinePrefix");
		if(!offlinePrefix.isEmpty()){
			if(offlinePrefix.length() > 16){
				offlinePrefix = offlinePrefix.substring(0, 16);
				getLogger().warning("The offlinePrefix length is bigger than 16 chars...");
			}
			SB.registerNewTeam("offline").setPrefix(ChatColor.translateAlternateColorCodes('&', offlinePrefix));
		}
		String onlinePrefix = getConfig().getString("onlinePrefix");
		if(!onlinePrefix.isEmpty()){
			if(onlinePrefix.length() > 16){
				onlinePrefix = onlinePrefix.substring(0, 16);
				getLogger().warning("The onlinePrefix length is bigger than 16 chars...");
			}
			SB.registerNewTeam("online").setPrefix(ChatColor.translateAlternateColorCodes('&', onlinePrefix));
		}
	}

	public void updateSB() {
		for (Player p : Bukkit.getOnlinePlayers())
			if (p.hasPermission(DISPLAY_PERM) && !p.hasMetadata(MD_TOGGLE))
				p.setScoreboard(SB);
	}

	public static BPC getInstance() {
		return BPC.instance;
	}

	public List<String> getServersList() {
		return serversList;
	}

	public Boolean getAutomaticDisplay() {
		return automaticDisplay;
	}

	public Integer getUpdateInterval() {
		return updateInterval;
	}

	/**
	 * Launch the task responsible of sending a player count request each
	 * seconds (20 ticks)
	 */
	public void launchUpdateTask() {
		Bukkit.getScheduler().runTaskTimer(this, new Runnable() {

			@Override
			public void run() {
				try {
					if (Bukkit.getOnlinePlayers().length == 0)
						return;
					Player p = Bukkit.getOnlinePlayers()[0];

					// Ask playercount for every servers
					for (String server : BPC.getInstance().getServersList()) {
						ByteArrayOutputStream b = new ByteArrayOutputStream();
						DataOutputStream out = new DataOutputStream(b);
						out.writeUTF("PlayerCount");
						out.writeUTF(server);
						p.sendPluginMessage(BPC.getInstance(), channel, b.toByteArray());
					}

					// Get player count for the full network
					ByteArrayOutputStream b = new ByteArrayOutputStream();
					DataOutputStream out = new DataOutputStream(b);
					out.writeUTF("PlayerCount");
					out.writeUTF("ALL");
					p.sendPluginMessage(BPC.getInstance(), channel, b.toByteArray());

					updateSB();
				} catch (IOException e) {
					e.printStackTrace();
					getLogger().severe("Error during message sending. ErrorCode: 2");
				}
			}

		}, 20L, 20L * updateInterval);
	}

	@Override
	public void onPluginMessageReceived(final String channel, final Player player, final byte[] message) {
		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
			final String subchannel = in.readUTF();
			if (subchannel.equals("PlayerCount")) {
				String server = in.readUTF();
				int playerCount = in.readInt();

				// Total player count
				if (server.equals("ALL")) {
					String objectiveName = ChatColor.translateAlternateColorCodes('&', networkName).replace(
							"%totalplayercount%", String.valueOf(playerCount)).replace("%maxplayers%", String.valueOf(maxPlayers));
					if (objectiveName.length() > 32)
						objectiveName = objectiveName.substring(0, 32);
					SB.getObjective(DisplaySlot.SIDEBAR).setDisplayName(objectiveName);
					return;
				}

				// Automatic display part
				if (automaticDisplay) {
					// If the currentServer is not defined, it can cause
					// duplicate scoreboard entry so we cancel until we've got
					// the currentServer
					if (serverIndicatorEnabled && currentServer == null)
						return;

					// If this is the current server player count and the server
					// indicator is on, show the server indicator
					if (server.equals(currentServer) && serverIndicatorEnabled) {
						server = ChatColor.translateAlternateColorCodes('&', serverIndicator + server + ChatColor.RED + ":");
						server = (server.length() > 16) ? server.substring(0, 16) : server;
						
						// Avoid having more than 15 entry in the SB which makes
						// it crash
						if (SB.getEntries().size() < 14) {
							// Note: if the score of a player is set to 0, it will not be shown each time, 
							// so we need to force it setting it to any non null number
							if(playerCount == 0){
								SB.getObjective(DisplaySlot.SIDEBAR).getScore(server)
								.setScore(-1);
							}
							SB.getObjective(DisplaySlot.SIDEBAR).getScore(server)
									.setScore(playerCount);
						}
					}

					// Else just show player count of the server
					else {
						server = ChatColor.translateAlternateColorCodes('&', server + ChatColor.RED + ":");
						server = (server.length() > 16) ? server.substring(0, 16) : server;

						if (SB.getEntries().size() < 14) {
							if(playerCount == 0){
								SB.getObjective(DisplaySlot.SIDEBAR).getScore(server)
								.setScore(-1);
							}
							SB.getObjective(DisplaySlot.SIDEBAR).getScore(server)
									.setScore(playerCount);
						}
					}
				}

				// Manual display part
				else {
					// Cancel if this server isn't in any group
					if (!serversGroups.containsKey(server))
						return;
					
					// Get group and update player count
					Group group = serversGroups.get(server);
					group.updatePlayerCount(server, playerCount);
					
					// If the server is in the same group as currentServer zzand
					// the server indicator is on, show the server indicator
					if (group.equals(currentGroup) && serverIndicatorEnabled) {
						server = ChatColor.translateAlternateColorCodes('&', serverIndicator + group.getName());
						server = (server.length() > 16) ? server.substring(0, 16) : server;

						// Sometimes the current group shows without the special
						// mark, so we gonna remove it
						SB.resetScores(group.getName());

						if (SB.getEntries().size() < 14) {
							SB.getObjective(DisplaySlot.SIDEBAR).getScore(server)
									.setScore(group.getPlayerCount());
						}
					}

					// Else just show player count of the server
					else {
						server = group.getName();
						if (SB.getEntries().size() < 14) {
							// Note: if the score of a player is set to 0, it will not be shown each time, 
							// so we need to force it setting it to any non null number
							if(group.getPlayerCount() == 0){
								SB.getObjective(DisplaySlot.SIDEBAR).getScore(server)
								.setScore(-1);
							}
							SB.getObjective(DisplaySlot.SIDEBAR).getScore(server)
									.setScore(group.getPlayerCount());
						}
						
						// Assign offline or online state
						if(!group.isOnline()){
							if(SB.getTeam("offline") != null){
								SB.getTeam("offline").addEntry(server);
							}
							if(SB.getTeam("online") != null){
								SB.getTeam("online").removeEntry(server);
							}
						}
						else{
							if(SB.getTeam("offline") != null){
								SB.getTeam("offline").removeEntry(server);
							}
							if(SB.getTeam("online") != null){
								SB.getTeam("online").addEntry(server);
							}
						}
					}
				}
				return;
			} else if (subchannel.equals("GetServers")) {
				// If serversList == null the task has not been already launched
				if (serversList.isEmpty()) {
					serversList = new ArrayList<String>(Arrays.asList(in.readUTF().split(", ")));
					launchUpdateTask();
				} else
					serversList = new ArrayList<String>(Arrays.asList(in.readUTF().split(", ")));
			} else if (subchannel.equals("GetServer")) {
				currentServer = in.readUTF();
				if (!automaticDisplay)
					currentGroup = serversGroups.get(currentServer);
			}
		} catch (IOException e) {
		    if(e instanceof EOFException){
		        return;
		    }
			getLogger().severe(
					"Error during reception of plugin message! Cause: IOException\nPlease report this stacktrace :");
			e.printStackTrace();
		}

	}

	@EventHandler
	public void onPlayerJoin(final PlayerJoinEvent e) {
		// If the serversList is not initialized, we send the plugin message to
		// init it
		if (serversList.isEmpty() || (serverIndicatorEnabled && currentServer == null)) {
			try {
				final Player p = e.getPlayer();
				if (p == null) {
					return;
				}

				// Define plugin messages
				final List<byte[]> plugMessage = new ArrayList<byte[]>();
				ByteArrayOutputStream b = new ByteArrayOutputStream();
				DataOutputStream out = new DataOutputStream(b);
				if (serversList.isEmpty()) {
					out.writeUTF("GetServers");
					plugMessage.add(b.toByteArray());
				}
				if (serverIndicatorEnabled && currentServer == null) {
					b = new ByteArrayOutputStream();
					out = new DataOutputStream(b);
					out.writeUTF("GetServer");
					plugMessage.add(b.toByteArray());
				}

				// RunTaskLater because when PlayerJoinEvent we can't immediatly
				// send a plugin message
				Bukkit.getScheduler().runTaskLater(this, new Runnable() {
					@Override
					public void run() {
						for (byte[] message : plugMessage)
							p.sendPluginMessage(BPC.getInstance(), "BungeeCord", message);
					}

				}, 2L);
			} catch (IOException exception) {
				getLogger().severe("Error during message sending. ErrorCode: 1");
				exception.printStackTrace();
			}
		} else {
			final Player p = e.getPlayer();
			if (p.hasPermission(DISPLAY_PERM))
				Bukkit.getScheduler().runTaskLater(this, new Runnable() {
					@Override
					public void run() {
						if (!p.hasMetadata(MD_TOGGLE)){
							p.setScoreboard(SB);
						}
					}
				}, 2L);
		}
	}

	public void toggle(Player player) {
		// Toggle on
		if (player.hasMetadata(MD_TOGGLE)) {
			player.removeMetadata(MD_TOGGLE, this);
			player.setScoreboard(SB);
		}
		// Toggle off
		else {
			player.setMetadata(MD_TOGGLE, new FixedMetadataValue(this, "off"));
			player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (args.length == 1) {
			if (args[0].equalsIgnoreCase("reload") && sender.hasPermission(RELOAD_PERM))
				reload();
			else if (args[0].equalsIgnoreCase("toggle") && sender.hasPermission(TOGGLE_PERM)
					&& sender.hasPermission(DISPLAY_PERM)) {
				if (sender instanceof Player) {
					toggle((Player) sender);
					sender.sendMessage(ChatColor.YELLOW + "[BPC] Scoreboard toggled !");
				} else
					sender.sendMessage(ChatColor.YELLOW + "[BPC] You must be a player to use this command !");
			}
		} else
			sender.sendMessage(ChatColor.YELLOW + "[BPC] Invalid command or insufficient permission !");
		return true;
	}
}