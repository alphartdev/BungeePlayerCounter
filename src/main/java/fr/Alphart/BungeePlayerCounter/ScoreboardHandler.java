package fr.Alphart.BungeePlayerCounter;

import java.util.Arrays;
import java.util.Collection;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import fr.Alphart.BungeePlayerCounter.Servers.ServerCoordinator;
import fr.Alphart.BungeePlayerCounter.Servers.ServerGroup;


public class ScoreboardHandler {
    private final String MD_TOGGLE = "BPC_toggled";
    
    private final ServerCoordinator serverCoordinator;
    private Scoreboard SB;
    
    public ScoreboardHandler(final ServerCoordinator serverCoordinator){
        this.serverCoordinator = serverCoordinator;
        initScoreboard();
        Bukkit.getScheduler().runTaskTimer(BPC.getInstance(), new Runnable() {
            @Override
            public void run() {
                update();
            }
        }, 20L, 20L * BPC.getInstance().getConf().getUpdateInterval());
    }
    
    private void initScoreboard(){
        SB = Bukkit.getScoreboardManager().getMainScoreboard();
        // Clean the scoreboard a bit ...
        for (final String entries : SB.getEntries()) {
            SB.resetScores(entries);
        }
        if (SB.getObjective("playercounter") != null) {
            SB.getObjective("playercounter").unregister();
        }
        for(final String teamName : Arrays.asList("online", "offline", "defaultbpc")){
            if(SB.getTeam(teamName) != null){
                SB.getTeam(teamName).unregister();
            }
        }
        
        final Objective objective = SB.registerNewObjective("playercounter", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        final String offlinePrefix = BPC.getInstance().getConf().getOfflinePrefix();
        if(!offlinePrefix.isEmpty()){
            SB.registerNewTeam("offline").setPrefix(offlinePrefix);
            SB.getTeam("offline").setSuffix(ChatColor.RED + ":");
        }
        final String onlinePrefix = BPC.getInstance().getConf().getOnlinePrefix();
        if(!onlinePrefix.isEmpty()){
            SB.registerNewTeam("online").setPrefix(onlinePrefix);
            SB.getTeam("online").setSuffix(ChatColor.RED + ":");
        }
        SB.registerNewTeam("defaultbpc").setSuffix(ChatColor.RED + ":");
        
    }
    
    public void toggleScoreboard(final Player player) {
        // Toggle on
        if (player.hasMetadata(MD_TOGGLE)) {
            player.removeMetadata(MD_TOGGLE, BPC.getInstance());
            player.setScoreboard(SB);
        }
        // Toggle off
        else {
            player.setMetadata(MD_TOGGLE, new FixedMetadataValue(BPC.getInstance(), "off"));
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
    }

    public void update(){
        String objectiveName = BPC.getInstance().getConf().getNetworkName()
                .replace("%totalplayercount%", String.valueOf(serverCoordinator.getGlobalPlayerCount()))
                .replace("%maxplayers%", String.valueOf(serverCoordinator.getBungeeMaxPlayer()));
        if (objectiveName.length() > 32){
            objectiveName = objectiveName.substring(0, 32);
        }
        SB.getObjective(DisplaySlot.SIDEBAR).setDisplayName(objectiveName);
        
        final Objective obj = SB.getObjective(DisplaySlot.SIDEBAR);
        for(final ServerGroup group : serverCoordinator.getServerGroups()){
            if(group.getDisplayName().equals(ServerCoordinator.globalGroupName)){
                continue;
            }
            if (SB.getEntries().size() < 14) { // Sidebar cannot contains more than 14 entries
                String line = group.getDisplayName();
                int playercount = group.getPlayerCount();
                if(group.doesContainCurrentServer() && BPC.getInstance().getConf().isServerIndicatorEnabled()){
                    SB.resetScores(group.getDisplayName()); // Remove eventually old entry
                    line = BPC.getInstance().getConf().getServerIndicator() + line;
                }
                
                if(line.length() > 16){
                    line = line.substring(0, 16);
                }
                // Apply score to scoreboard
                if(playercount == 0){ // For null score, the scoreboard may be buggy, so we set an "intermediar" value
                    obj.getScore(line).setScore(-1);
                }
                obj.getScore(line).setScore(playercount);
                
                // Set server status in the SB otherwise assign to a default team to use the suffix
                if(group.isAdressSet()){
                    if(group.isOnline()){
                        if(SB.getTeam("offline") != null){
                            SB.getTeam("offline").removeEntry(line);
                        }
                        if(SB.getTeam("online") != null){
                            SB.getTeam("online").addEntry(line);
                        }
                    }
                    else{
                        if(SB.getTeam("offline") != null){
                            SB.getTeam("offline").addEntry(line);
                        }
                        if(SB.getTeam("online") != null){
                            SB.getTeam("online").removeEntry(line);
                        }
                    }
                }else{
                    SB.getTeam("defaultbpc").addEntry(line);
                }
            }
        }
        
        sendScoreboardToPlayers(Bukkit.getOnlinePlayers());
        return;
    }
    
    /**
     * Send the BPC scoreboard to this player
     * @param player
     */
    public void sendScoreboard(final Player player){
        sendScoreboardToPlayers(Arrays.asList(player));
    }
    
    private void sendScoreboardToPlayers(final Collection<? extends Player> playersToUpdateSB) {
        for (final Player p : playersToUpdateSB){
            if (p.hasPermission(BaseCommands.DISPLAY_PERM) && !p.hasMetadata(MD_TOGGLE)){
                p.setScoreboard(SB);
            }
        }
    }
    
    
}