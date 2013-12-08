package fr.Alphart.BungeePlayerCounter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
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

public class BPC extends JavaPlugin implements PluginMessageListener, Listener{
	private String DISPLAY_PERM = "bungeeplayercounter.display";
	private static BPC instance;
	private String networkName;
	private Boolean serverIndicator;
	private String currentServer;
	private String[] serversList;
	private FileConfiguration config;
	private Scoreboard SB;
	
	public void onEnable(){
		BPC.instance = this;
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
        getServer().getPluginManager().registerEvents(this, this);
        saveConfig();
		networkName = ChatColor.translateAlternateColorCodes('&', "&n" +config.getString("name"));
		serverIndicator = config.getBoolean("enableServerIndicator");
		initScoreboard();		
	}
	
	public void saveConfig(){
		if(!new File(getDataFolder(), "config.yml").exists()){
			saveResource("config.yml", true);
		}
		config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
		// Update the config
		if(!config.contains("enableServerIndicator")){
			getLogger().info("Update of config file ...");
			config.set("enableServerIndicator", true);
			config.options().header("Bungee Player Counter configuration file");
        	try {
				config.save(new File(getDataFolder()+ File.separator +"config.yml"));
			} catch (IOException e) {
				getLogger().severe("[BPC] An error happens during the update of the config file. Please delete manually the file config.yml and then restart the server");
				e.printStackTrace();
			}
		}
		reloadConfig();
	}
	
	public static BPC getInstance(){
		return BPC.instance;
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
	
	public String[] getServersList(){
		return serversList;
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
					
					// Actualize serversList
					b = new ByteArrayOutputStream();
					out = new DataOutputStream(b);
					out.writeUTF("GetServers");
					p.sendPluginMessage(BPC.getInstance(), "BungeeCord", b.toByteArray());
				} catch (IOException e) {
					e.printStackTrace();
					getLogger().severe("Error during message sending. ErrorCode: 2");
				}
			}
			
		}, 20L, 20L);
	}

	@Override
	public void onPluginMessageReceived(final String channel,final Player player,final byte[] message) {
		try {
	        DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
			final String subchannel = in.readUTF();
			if (subchannel.equals("PlayerCount")) {
				String server = in.readUTF();
				int playercount = in.readInt();
				
				// Total player count
				if(server.equals("ALL"))
					SB.getObjective(DisplaySlot.SIDEBAR).setDisplayName(ChatColor.translateAlternateColorCodes('&',ChatColor.GOLD + networkName + "&r&7&L (" + playercount + ")"));
				
				// If this is the current server player count and the server indicator is on, show the server indicator
				else if(server.equals(currentServer) && serverIndicator){
					if(server.length() > 14)
						server = server.substring(0, 14);
					server = ChatColor.GREEN + ">" + server + ChatColor.RED + ":";
		        	SB.getObjective(DisplaySlot.SIDEBAR).getScore(Bukkit.getOfflinePlayer(server)).setScore(playercount);
				}
				
				// Else just show player count of the server
				else{
					if(server.length() > 15)
						server = server.substring(0, 15);
					server = server + ChatColor.RED + ":";
		        	SB.getObjective(DisplaySlot.SIDEBAR).getScore(Bukkit.getOfflinePlayer(ChatColor.AQUA + server)).setScore(playercount);
				}
				
	        	updateSB();
	        	return;
	        } 
			else if (subchannel.equals("GetServers")) {
				// If serversList == null the task has not been already launched
				if(serversList == null){
					serversList = in.readUTF().split(", ");
					launchUpdateTask();
				}
				else
					serversList = in.readUTF().split(", ");
				System.out.println("Iteration sur ServersList");
				for(String serverName : serversList){
					System.out.println(serverName);
				}
	        } 
			else if (subchannel.equals("GetServer")) {
				currentServer = in.readUTF();
	        }
		} catch (IOException e) {
			getLogger().severe("Error during reception of plugin message! Cause: IOException \n Please report this stacktrace :");
			e.printStackTrace();
		}
        
	}

	@EventHandler
	public void onPlayerJoin(final PlayerJoinEvent e){
		// If the serversList is not initialized, we send the plugin message to init it
		if(serversList == null){
			try {
				final Player p = e.getPlayer();
				if(p == null){
					return;
				}
				
				// Define plugin messages
				ByteArrayOutputStream b = new ByteArrayOutputStream();
				DataOutputStream out = new DataOutputStream(b);
				out.writeUTF("GetServers");
				final byte[] getServersMsg = b.toByteArray();
				b = new ByteArrayOutputStream();
				out = new DataOutputStream(b);
				out.writeUTF("GetServer");
				final byte[] getCurrentServerMsg = b.toByteArray();
				
				// RunTaskLater because when PlayerJoinEvent we can't immediatly send a plugin message
				Bukkit.getScheduler().runTaskLater(this, new Runnable(){
					@Override
					public void run() {
						p.sendPluginMessage(BPC.getInstance(), "BungeeCord", getServersMsg);
						p.sendPluginMessage(BPC.getInstance(), "BungeeCord", getCurrentServerMsg);
						if(p.hasPermission(DISPLAY_PERM))
							p.setScoreboard(SB);
					}
					
				}, 2L);
			} catch (IOException exception) {
				getLogger().severe("Error during message sending. ErrorCode: 1");
				exception.printStackTrace();
			}
		}
	}
}
