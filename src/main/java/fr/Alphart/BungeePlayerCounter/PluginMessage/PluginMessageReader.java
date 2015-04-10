package fr.Alphart.BungeePlayerCounter.PluginMessage;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

import fr.Alphart.BungeePlayerCounter.BPC;
import fr.Alphart.BungeePlayerCounter.Servers.ServerCoordinator;
import fr.Alphart.BungeePlayerCounter.Servers.ServerGroup;

public class PluginMessageReader implements PluginMessageListener{
    private final ServerCoordinator serverCoordinator;
    private final Set<String> channels = Sets.newHashSet();
    
    public PluginMessageReader(final ServerCoordinator serverCoordinator, final String channelName) {
        this.serverCoordinator = serverCoordinator;
        
        channels.add("BungeeCord"); // Always need to register to the B/C channel in order to receive GetServer message
        if(!channelName.equalsIgnoreCase("BungeeCord")){
            channels.add(channelName);
        }
        for(final String channel : channels){
            Bukkit.getMessenger().registerIncomingPluginChannel(BPC.getInstance(), channel, this);
            BPC.debug("Registering incoming plugin message channel. Channel=%s", channel);
        }
    }

    @Override
    public void onPluginMessageReceived(final String channel, final Player player, final byte[] message) {
        if(!channels.contains(channel)){
            return;
        }
        
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            final String subchannel = in.readUTF();
            if (subchannel.equals("PlayerCount")) {
                // If we're using redis as data source, we should ignore B/C playercount message
                if(channels.size() > 1 && channel.equalsIgnoreCase("BungeeCord")){ 
                    return;
                }
                
                String server = in.readUTF();
                int playerCount = in.readInt();
                serverCoordinator.updateServerPC(server, playerCount);
                BPC.debug("PlayerCount message received. Server=%s PlayerCount=%d", server, playerCount);
            } else if (subchannel.equals("GetServers")) {
                if(serverCoordinator.getServerGroups().size() <= 1){
                    final List<String> serversList = Arrays.asList(in.readUTF().split(", "));
                    for(final String server : serversList){
                        serverCoordinator.addGroup(server, new ServerGroup("§e"+server, Arrays.asList(server)));
                    }
                    BPC.debug("GetServers message received. Servers=%s", Joiner.on(',').join(serversList));
                }else{
                    BPC.debug("GetServers message received but ignored as servers are already known.");
                }
            } else if (subchannel.equals("GetServer")) {
                final String currentServer = in.readUTF();
                serverCoordinator.setCurrentServer(currentServer);
                BPC.debug("GetServer message received. CurrentServer=%s", currentServer);
            }
        } catch (final IOException e) {
            if(e instanceof EOFException){
                BPC.debug("An error occured while reading a plugin message on channel " + channel, e);
                return;
            }
            BPC.severe("Unexpected error while reading plugin message. Please report this : ", e);
        }
    }
    
}