package fr.Alphart.BungeePlayerCounter;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

/**
 * This class is used to handle groups when manual display is enabled
 */
public class Group {
	private String displayName;
	// ServersPlayerCount: contains player count of each server's group
	private Map<String, Integer> serversPC;
	private Integer playerCount;
	private Ping ping = null;
	private InetSocketAddress address = null;

	/**
	 * Constructor
	 * 
	 * @param displayName
	 * @param servers
	 */
	public Group(String name, List<String> servers) {
		displayName = ChatColor.translateAlternateColorCodes('&', name);
		if (displayName.length() > 16)
			displayName = displayName.substring(0, 16);
		serversPC = new HashMap<String, Integer>();
		for (String serverName : servers) {
			serversPC.put(serverName, 0);
		}
	}

	public Group(String name, List<String> servers, InetSocketAddress address, BPC plugin) {
		displayName = ChatColor.translateAlternateColorCodes('&', name);
		if (displayName.length() > 16)
			displayName = displayName.substring(0, 16);
		serversPC = new HashMap<String, Integer>();
		for (String serverName : servers) {
			serversPC.put(serverName, 0);
		}
		this.address = address;
		ping = new Ping(name, address);
		Bukkit.getScheduler()
				.runTaskTimerAsynchronously(plugin, ping, 20L, 20L * BPC.getInstance().getUpdateInterval());
	}

	private void calculatePlayerCount() {
		Integer totalPC = 0;
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

	public String getName() {
		return displayName;
	}

	public Integer getPlayerCount() {
		return playerCount;
	}

	public boolean isOnline() {
		return (ping != null) ? ping.isOnline() : true;
	}
}