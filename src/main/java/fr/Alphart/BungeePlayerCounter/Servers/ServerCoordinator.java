package fr.Alphart.BungeePlayerCounter.Servers;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;

import fr.Alphart.BungeePlayerCounter.BPC;
import fr.Alphart.BungeePlayerCounter.PluginMessage.PluginMessageReader;

public class ServerCoordinator {
    public static final String globalGroupName = "globalCount";
    private SetMultimap<String, ServerGroup> serverGroups = HashMultimap.create();
    @Setter @Getter
    private String currentServer = "";
    private int bungeeMaxPlayer = -1;
    
    public ServerCoordinator(){
        new PluginMessageReader(this, BPC.getInstance().getConf().getPluginMessageChannel());
        Bukkit.getScheduler().runTaskTimer(BPC.getInstance(), new UpdatePlayerCountTask(), 20L, 
                20L * BPC.getInstance().getConf().getUpdateInterval());
        
        if(BPC.getInstance().getConf().getProxyAddress() != null){
            Bukkit.getScheduler().runTaskTimerAsynchronously(BPC.getInstance(), new UpdateProxyPlayerCountTask(), 20L, 
                    20L * BPC.getInstance().getConf().getUpdateInterval());
        }
        
        // This entry is for the global player count
        serverGroups.put("ALL", new ServerGroup(globalGroupName, Arrays.asList("ALL")));
        serverGroups.putAll(BPC.getInstance().getConf().getServersGroups());
    }
    
    public void updateServerPC(final String server, final int playerCount){
        final Set<ServerGroup> groups = serverGroups.get(server);
        for(final ServerGroup group : groups){
            group.updatePlayerCount(server, playerCount);
        }
    }
    
    public void addGroup(final String serverName, final ServerGroup serverGroup){
        serverGroups.put(serverName, serverGroup);
    }
    
    public Set<ServerGroup> getGroupsByServer(final String server){
        return serverGroups.get(server);
    }
    
    public Collection<ServerGroup> getServerGroups(){
        return ImmutableSet.copyOf(serverGroups.values());
    }
    
    public Collection<String> getAllServersName(){
        return ImmutableSet.copyOf(serverGroups.keySet());
    }
    
    public int getGlobalPlayerCount(){
        return serverGroups.get("ALL").iterator().next().getPlayerCount();
    }
    
    public int getBungeeMaxPlayer(){
        return bungeeMaxPlayer;
    }
    
    class UpdatePlayerCountTask implements Runnable{

        @Override
        public void run() {
            if(Bukkit.getOnlinePlayers().isEmpty()){
                return;
            }
            final Player sender = Bukkit.getOnlinePlayers().iterator().next();
            BPC.getInstance().getPmWriter().sendGetPlayerCountMessage(sender, getAllServersName());
        }
        
    }
    
    /**
     * This task should be runned async as it uses the network and might induce high latency
     */
    class UpdateProxyPlayerCountTask implements Runnable{
        final Pinger ping = new Pinger("bungee", BPC.getInstance().getConf().getProxyAddress());
        
        @Override
        public void run() {
            ping.run();
            if(ping.isOnline()){
                bungeeMaxPlayer = ping.getMaxPlayers();
            }
        }
            
    }
    
}