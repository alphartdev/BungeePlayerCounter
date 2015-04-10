package fr.Alphart.BungeePlayerCounter.Servers;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import fr.Alphart.BungeePlayerCounter.BPC;

/**
 * This class is used to handle groups when manual display is enabled
 */
@Getter
public class ServerGroup {
    @Getter
	private String displayName;
    @Getter
    private int playerCount;
	private Map<String, Integer> serversPC;
	private Pinger ping = null;
	private InetSocketAddress address = null;

	/**
	 * Constructor
	 * 
	 * @param displayName
	 * @param servers
	 */
	public ServerGroup(String name, List<String> servers) {
		displayName = ChatColor.translateAlternateColorCodes('&', name);
		if (displayName.length() > 16)
			displayName = displayName.substring(0, 16);
		serversPC = new HashMap<String, Integer>();
		for (String serverName : servers) {
			serversPC.put(serverName, 0);
		}
	}

	public ServerGroup(String name, List<String> servers, InetSocketAddress address, int updateInterval) {
		displayName = ChatColor.translateAlternateColorCodes('&', name);
		if (displayName.length() > 16)
			displayName = displayName.substring(0, 16);
		serversPC = new HashMap<String, Integer>();
		for (String serverName : servers) {
			serversPC.put(serverName, 0);
		}
		this.address = address;
		ping = new Pinger(name, address);
		Bukkit.getScheduler().runTaskTimerAsynchronously(BPC.getInstance(), ping, 20L, 
                20L * updateInterval);
	}

	private void calculatePlayerCount() {
		int totalPC = 0;
		for (Integer serverPC : serversPC.values()) {
			totalPC += serverPC;
		}
		playerCount = totalPC;
	}

	/**
	 * Set the player count of a server and then update the total player count
	 * 
	 * @param server
	 * @param playerCount
	 */
	public void updatePlayerCount(String server, Integer playerCount) {
		if (serversPC.containsKey(server)) {
			serversPC.put(server, playerCount);
		}
		calculatePlayerCount();
	}

	public boolean isOnline() {
	    if(ping == null){
	        throw new IllegalStateException("This server group has no defined address and its current stat cannot be computed.");
	    }
		return ping.isOnline();
	}
	
	public boolean isAdressSet(){
	    return address != null;
	}
	
	/**
	 * Check if this group contain the current server
	 * (server where the plugin is running now)
	 */
	public boolean doesContainCurrentServer(){
	    return serversPC.containsKey(BPC.getInstance().getServerCoordinator().getCurrentServer());
	}
}