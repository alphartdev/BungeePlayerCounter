package fr.Alphart.BungeePlayerCounter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;

/**
 * This class is used to handle groups when manual display is enabled
 */
public class Group {
	private String displayName;
	// ServersPlayerCount: contains player count of each server's group
	private Map<String, Integer> serversPC;
	private Integer playerCount;
	
	/**
	 * Constructor
	 * @param displayName
	 * @param servers
	 */
	public Group(String name, List<String> servers){
		displayName = ChatColor.translateAlternateColorCodes('&', name);
		if(displayName.length() > 16)
			displayName = displayName.substring(0, 16);
		serversPC = new HashMap<String, Integer>();
		for(String serverName : servers){
			serversPC.put(serverName, 0);
		}
	}

	private void calculatePlayerCount(){
		Integer totalPC = 0;
		for(Integer serverPC : serversPC.values()){
			totalPC += serverPC;
		}
		playerCount = totalPC;
	}
	
	/**
	 * Set the player count of a server and then update the total player count
	 * @param server
	 * @param playerCount
	 */
	public void updatePlayerCount(String server, Integer playerCount){
		if(serversPC.containsKey(server)){
			serversPC.put(server, playerCount);
		}
		calculatePlayerCount();
	}

	public String getName(){
		return displayName;
	}
	
	public Integer getPlayerCount(){
		return playerCount;
	}	
}
